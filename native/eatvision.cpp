#include <jni.h>
#include <android/bitmap.h>
#ifdef EATVISION_QA
#include <android/log.h>
#define TRACE(...) __android_log_print(ANDROID_LOG_INFO,"EatVisionQA",__VA_ARGS__)
#else
#define TRACE(...) ((void)0)
#endif
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include "focused_cutout.hpp"
#include "cutout_sampling.hpp"

namespace {
std::mutex lock;
std::shared_ptr<MNN::Interpreter> net;
MNN::Session* session = nullptr;
int inputSize = 320;
bool isnet = false;
bool focusedRefinement = false;
void release() { if (net && session) net->releaseSession(session); session = nullptr; net.reset(); }

// Each stage owns one session, and the encoder is destroyed before the decoder
// is created. No full-resolution camera bitmap reaches this layer.
struct Stage {
    std::unique_ptr<MNN::Interpreter> graph;
    MNN::Session* run = nullptr;
    explicit Stage(const char* path) : graph(MNN::Interpreter::createFromFile(path)) {
        if (!graph) return;
        graph->setSessionMode(MNN::Interpreter::Session_Release);
        graph->setSessionHint(MNN::Interpreter::WINOGRAD_MEMORY_LEVEL, 0);
        MNN::ScheduleConfig config; config.type=MNN_FORWARD_CPU; config.numThread=2;
        MNN::BackendConfig backend; backend.precision=MNN::BackendConfig::Precision_Low;
        backend.memory=MNN::BackendConfig::Memory_Low; config.backendConfig=&backend;
        run=graph->createSession(config);
        if (run) graph->releaseModel();
    }
    ~Stage() { if (graph && run) graph->releaseSession(run); }
    bool put(const char* name, const std::vector<float>& values) {
        if (!run) return false;
        auto* tensor=graph->getSessionInput(run,name);
        if (!tensor) return false;
        MNN::Tensor host(tensor,MNN::Tensor::CAFFE);
        // Device tensors can be NC4HW4 padded (RGB=4, three masks=4).
        // Compare the unpadded host shape, not device allocation size.
        if(host.elementSize()!=static_cast<int>(values.size())){TRACE("input %s shape %d expected %d",name,host.elementSize(),int(values.size()));return false;}
        std::copy(values.begin(),values.end(),host.host<float>());
        tensor->copyFromHostTensor(&host); return true;
    }
    std::vector<float> get(const char* name, int expected) {
        if (!run) return {};
        auto* tensor=graph->getSessionOutput(run,name);
        if (!tensor) return {};
        MNN::Tensor host(tensor,MNN::Tensor::CAFFE);
        if(host.elementSize()!=expected){TRACE("output %s shape %d expected %d",name,host.elementSize(),expected);return {};}
        tensor->copyToHostTensor(&host);
        return std::vector<float>(host.host<float>(),host.host<float>()+expected);
    }
};
jobject alphaBitmap(JNIEnv* env, const std::vector<jint>& colors, int w, int h) {
    jintArray array=env->NewIntArray(w*h); if (!array) return nullptr;
    env->SetIntArrayRegion(array,0,w*h,colors.data());
    jclass cls=env->FindClass("android/graphics/Bitmap");
    jclass config=env->FindClass("android/graphics/Bitmap$Config");
    jobject format=env->GetStaticObjectField(config,env->GetStaticFieldID(config,"ARGB_8888","Landroid/graphics/Bitmap$Config;"));
    return env->CallStaticObjectMethod(cls,env->GetStaticMethodID(cls,"createBitmap","([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"),array,w,h,format);
}
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_eatrecord_app_FoodCutoutEngine_loadCutoutModel(
        JNIEnv* env, jclass, jstring path, jint mode) {
    std::lock_guard<std::mutex> guard(lock);
    try {
    release();
    const char* chars = env->GetStringUTFChars(path, nullptr);
    net.reset(MNN::Interpreter::createFromFile(chars));
    env->ReleaseStringUTFChars(path, chars);
    if (!net) return JNI_FALSE;
    // Avoid allocating a session at an unadapted source resolution, and release
    // the serialized graph after preparing the one inference we will execute.
    net->setSessionMode(MNN::Interpreter::Session_Release);
    net->setSessionMode(MNN::Interpreter::Session_Resize_Defer);
    net->setSessionHint(MNN::Interpreter::WINOGRAD_MEMORY_LEVEL, 0);
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU; config.numThread = 2;
    MNN::BackendConfig backend;
    backend.precision = MNN::BackendConfig::Precision_Low;
    backend.memory = MNN::BackendConfig::Memory_Low;
    config.backendConfig = &backend;
    session = net->createSession(config);
    if (!session) { release(); return JNI_FALSE; }
    isnet = mode != 0;
    inputSize = isnet ? 512 : 320;
    net->resizeTensor(net->getSessionInput(session, nullptr), {1, 3, inputSize, inputSize});
    net->resizeSession(session);
    net->releaseModel();
    return JNI_TRUE;
    } catch (...) { release(); return JNI_FALSE; }
}

extern "C" JNIEXPORT jobject JNICALL Java_com_eatrecord_app_FoodCutoutEngine_cutoutFood(
        JNIEnv* env, jclass, jobject bitmap, jint) {
    std::lock_guard<std::mutex> guard(lock);
    bool pixelsLocked = false;
    try {
    if (!net || !session) return nullptr;
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return nullptr;
    void* raw = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &raw) != 0) return nullptr;
    pixelsLocked = true;
    std::vector<float> input(3 * inputSize * inputSize);
    const float mean[] = {.485f, .456f, .406f}, stddev[] = {.229f, .224f, .225f};
    auto* pixels = static_cast<uint8_t*>(raw);
    float maxValue = 1;
    if(isnet&&(info.width>static_cast<uint32_t>(inputSize)||info.height>static_cast<uint32_t>(inputSize))) {
        // Downsampling must integrate the full filter footprint. Four-point
        // sampling aliases fine tray texture into false confident foreground.
        // Keep the independently validated tiny-model preprocessing unchanged.
        auto rgb=eatvision::antialiasedRgb(pixels,int(info.width),int(info.height),int(info.stride),inputSize);
        for(int i=0;i<inputSize*inputSize;i++)for(int c=0;c<3;c++){
            float value=rgb[i*3+c];input[c*inputSize*inputSize+i]=value;maxValue=std::max(maxValue,value);
        }
    } else for (int y = 0; y < inputSize; y++) for (int x = 0; x < inputSize; x++) {
        float fx = (x + .5f) * info.width / inputSize - .5f, fy = (y + .5f) * info.height / inputSize - .5f;
        fx = std::max(0.f, std::min(fx, static_cast<float>(info.width - 1)));
        fy = std::max(0.f, std::min(fy, static_cast<float>(info.height - 1)));
        int x0 = static_cast<int>(fx), y0 = static_cast<int>(fy), x1 = std::min(x0 + 1, static_cast<int>(info.width - 1)), y1 = std::min(y0 + 1, static_cast<int>(info.height - 1));
        float dx = fx - x0, dy = fy - y0;
        for (int c = 0; c < 3; c++) {
            float a = pixels[y0 * info.stride + x0 * 4 + c] * (1-dx) + pixels[y0 * info.stride + x1 * 4 + c] * dx;
            float b = pixels[y1 * info.stride + x0 * 4 + c] * (1-dx) + pixels[y1 * info.stride + x1 * 4 + c] * dx;
            float value = a * (1-dy) + b * dy;
            input[c * inputSize * inputSize + y * inputSize + x] = value;
            maxValue = std::max(maxValue, value);
        }
    }
    // rembg / author preprocessing normalizes the resized RGB image. IS-Net
    // uses mean=.5, std=1; U2NetP uses ImageNet normalization.
    for (int c = 0; c < 3; c++) for (int i = 0; i < inputSize * inputSize; i++) {
        float& value = input[c * inputSize * inputSize + i];
        value = isnet ? value / maxValue - .5f : (value / maxValue - mean[c]) / stddev[c];
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    pixelsLocked = false;
    TRACE("saliency input %u x %u side %d antialias %d",info.width,info.height,inputSize,
        isnet&&(info.width>static_cast<uint32_t>(inputSize)||info.height>static_cast<uint32_t>(inputSize)));
    auto* tensor = net->getSessionInput(session, nullptr);
    MNN::Tensor host(tensor, MNN::Tensor::CAFFE);
    std::copy(input.begin(), input.end(), host.host<float>());
    tensor->copyFromHostTensor(&host);
    if (net->runSession(session) != MNN::NO_ERROR) return nullptr;
    // Pick the validated primary mask, never an auxiliary feature / side output.
    auto* output = net->getSessionOutput(session, isnet ? "output_image" : "1959");
    if (!output) return nullptr;
    MNN::Tensor mask(output, MNN::Tensor::CAFFE);
    output->copyToHostTensor(&mask);
    const int w = mask.width(), h = mask.height();
    const float* values = mask.host<float>();
    if (!values || w <= 0 || h <= 0) return nullptr;
    const auto extrema = std::minmax_element(values, values + w*h);
    float lo = *extrema.first, span = *extrema.second - lo;
    if (!std::isfinite(span) || span < .0001f) return nullptr;
    std::vector<jint> colors(w*h);
    for (int i = 0; i < w*h; i++) {
        int alpha = std::max(0, std::min(255, static_cast<int>((values[i] - lo) / span * 255)));
        colors[i] = (alpha << 24) | 0x00ffffff;
    }
    jintArray array = env->NewIntArray(w*h);
    if (!array) return nullptr;
    env->SetIntArrayRegion(array, 0, w*h, colors.data());
    jclass cls = env->FindClass("android/graphics/Bitmap");
    jclass config = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID field = env->GetStaticFieldID(config, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject format = env->GetStaticObjectField(config, field);
    jmethodID create = env->GetStaticMethodID(cls, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    return env->CallStaticObjectMethod(cls, create, array, w, h, format);
    } catch (...) { if (pixelsLocked) AndroidBitmap_unlockPixels(env, bitmap); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL Java_com_eatrecord_app_FoodCutoutEngine_releaseCutoutModel(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(lock); release();
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_eatrecord_app_FoodCutoutEngine_usedFocusedRefinement(JNIEnv*,jclass) {
    std::lock_guard<std::mutex> guard(lock);return focusedRefinement?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL Java_com_eatrecord_app_FoodCutoutEngine_refineFood(
        JNIEnv* env,jclass,jobject bitmap,jobject baseline,jstring encoderPath,jstring decoderPath) {
    std::lock_guard<std::mutex> guard(lock);
    release();
    focusedRefinement=false;
    void* raw=nullptr; void* maskRaw=nullptr;
    bool imageLocked=false,maskLocked=false;
    try {
        AndroidBitmapInfo info{},maskInfo{};
        if (AndroidBitmap_getInfo(env,bitmap,&info)!=0 || AndroidBitmap_getInfo(env,baseline,&maskInfo)!=0 ||
            info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888 || maskInfo.format!=ANDROID_BITMAP_FORMAT_RGBA_8888 ||
            info.width>1024 || info.height>1024 || maskInfo.width!=512 || maskInfo.height!=512) return nullptr;
        if (AndroidBitmap_lockPixels(env,baseline,&maskRaw)!=0) return nullptr;
        maskLocked=true;
        std::vector<float> old(512*512); int count=0,left=512,top=512,right=-1,bottom=-1;
        for (int y=0;y<512;y++) for(int x=0;x<512;x++) {
            float a=static_cast<uint8_t*>(maskRaw)[y*maskInfo.stride+x*4+3]/255.f;
            old[y*512+x]=a;
            if(a>.5f){count++;left=std::min(left,x);right=std::max(right,x);top=std::min(top,y);bottom=std::max(bottom,y);}
        }
        AndroidBitmap_unlockPixels(env,baseline);maskLocked=false;
        TRACE("baseline count %d bounds %d %d %d %d",count,left,top,right,bottom);
        if(count<512*512/100 || count>512*512*.8) return nullptr;
        if(AndroidBitmap_lockPixels(env,bitmap,&raw)!=0)return nullptr;
        imageLocked=true;
        std::vector<float> image(3*1024*1024);
        std::vector<unsigned char> lightDetail(512*512,0);
        const float means[]={.485f,.456f,.406f},stds[]={.229f,.224f,.225f};
        auto* bytes=static_cast<uint8_t*>(raw);
        for(int y=0;y<1024;y++)for(int x=0;x<1024;x++) {
            float fx=std::max(0.f,std::min((x+.5f)*info.width/1024-.5f,float(info.width-1)));
            float fy=std::max(0.f,std::min((y+.5f)*info.height/1024-.5f,float(info.height-1)));
            int x0=int(fx),y0=int(fy),x1=std::min(x0+1,int(info.width-1)),y1=std::min(y0+1,int(info.height-1));
            float dx=fx-x0,dy=fy-y0;
            float rgb[3];
            for(int c=0;c<3;c++) {
                float a=bytes[y0*info.stride+x0*4+c]*(1-dx)+bytes[y0*info.stride+x1*4+c]*dx;
                float b=bytes[y1*info.stride+x0*4+c]*(1-dx)+bytes[y1*info.stride+x1*4+c]*dx;
                rgb[c]=a*(1-dy)+b*dy;
                image[c*1024*1024+y*1024+x]=(rgb[c]/255.f-means[c])/stds[c];
            }
            if((x%2)==0&&(y%2)==0)lightDetail[(y/2)*512+x/2]=
                (rgb[0]+rgb[1]+rgb[2])/3.f>170.f&&
                *std::max_element(rgb,rgb+3)-*std::min_element(rgb,rgb+3)<45.f;
        }
        AndroidBitmap_unlockPixels(env,bitmap);imageLocked=false;
        const char* chars=env->GetStringUTFChars(encoderPath,nullptr);if(!chars)return nullptr;
        std::string encoder(chars);env->ReleaseStringUTFChars(encoderPath,chars);
        chars=env->GetStringUTFChars(decoderPath,nullptr);if(!chars)return nullptr;
        std::string decoder(chars);env->ReleaseStringUTFChars(decoderPath,chars);
        std::vector<float> embedding,high0,high1;
        {
            Stage stage(encoder.c_str());
            if(!stage.put("image",image)||stage.graph->runSession(stage.run)!=MNN::NO_ERROR)return nullptr;
            embedding=stage.get("embedding",256*64*64);high0=stage.get("high0",32*256*256);high1=stage.get("high1",64*128*128);
        }
        std::vector<float>().swap(image);
        if(embedding.empty()||high0.empty()||high1.empty())return nullptr;
        TRACE("encoder output ready");
        Stage stage(decoder.c_str());
        if(!stage.put("embedding",embedding)||!stage.put("high0",high0)||!stage.put("high1",high1))return nullptr;
        float best=.7f;std::vector<float> selected;
        for(float margin:{.04f,.08f}) {
            // MNN can alias inputs with intermediate workspace. Restore every
            // feature tensor for each prompt, not only the changed box.
            if(!stage.put("embedding",embedding)||!stage.put("high0",high0)||!stage.put("high1",high1))return nullptr;
            std::vector<float> box={std::max(0.f,left*2-margin*1024),std::max(0.f,top*2-margin*1024),
                std::min(1023.f,(right+1)*2+margin*1024),std::min(1023.f,(bottom+1)*2+margin*1024)};
            if(!stage.put("box",box)||stage.graph->runSession(stage.run)!=MNN::NO_ERROR)continue;
            auto masks=stage.get("masks",3*256*256),scores=stage.get("scores",3);
            if(masks.empty()||scores.empty())continue;
            for(int candidate=0;candidate<3;candidate++) {
                if(!std::isfinite(scores[candidate])||scores[candidate]<best){TRACE("candidate %.2f %d score %.3f rejected",margin,candidate,scores[candidate]);continue;}
                std::vector<float> alpha(512*512);int area=0,covered=0;
                const float* values=masks.data()+candidate*256*256;
                for(int y=0;y<512;y++)for(int x=0;x<512;x++) {
                    float fx=std::max(0.f,std::min(255.f,(x+.5f)*.5f-.5f)),fy=std::max(0.f,std::min(255.f,(y+.5f)*.5f-.5f));
                    int x0=int(fx),y0=int(fy),x1=std::min(255,x0+1),y1=std::min(255,y0+1);float dx=fx-x0,dy=fy-y0;
                    float v=(values[y0*256+x0]*(1-dx)+values[y0*256+x1]*dx)*(1-dy)+(values[y1*256+x0]*(1-dx)+values[y1*256+x1]*dx)*dy;
                    if(!std::isfinite(v))v=-80;
                    alpha[y*512+x]=std::max(0.f,std::min(1.f,v+.5f));
                    if(v>0){area++;if(old[y*512+x]>.5f)covered++;}
                }
                float ratio=float(area)/count;
                TRACE("candidate %.2f %d score %.3f coverage %.4f area %.4f",margin,candidate,scores[candidate],float(covered)/count,ratio);
                if(float(covered)/count<.94f||ratio<1.12f||ratio>2.3f||area>512*512*.85)continue;
                best=scores[candidate];selected.swap(alpha);
            }
        }
        if(selected.empty()) {
            auto subject=eatvision::focusedSubject(old,512);
            if(subject.count==0)return nullptr;
            // A separate, stricter correction path. The normal whole-meal
            // expansion rules above remain unchanged for established cutouts.
            if(!stage.put("embedding",embedding)||!stage.put("high0",high0)||!stage.put("high1",high1))return nullptr;
            std::vector<float> box={float(subject.left*2),float(subject.top*2),
                float(std::min(1023,(subject.right+1)*2)),float(std::min(1023,(subject.bottom+1)*2))};
            if(!stage.put("box",box)||stage.graph->runSession(stage.run)!=MNN::NO_ERROR)return nullptr;
            auto masks=stage.get("masks",3*256*256),scores=stage.get("scores",3);
            if(masks.empty()||scores.empty())return nullptr;
            float focusBest=.85f;
            for(int candidate=0;candidate<3;candidate++) {
                if(!std::isfinite(scores[candidate])||scores[candidate]<focusBest)continue;
                std::vector<float> alpha(512*512);const float* values=masks.data()+candidate*256*256;
                for(int y=0;y<512;y++)for(int x=0;x<512;x++) {
                    float fx=std::max(0.f,std::min(255.f,(x+.5f)*.5f-.5f)),fy=std::max(0.f,std::min(255.f,(y+.5f)*.5f-.5f));
                    int x0=int(fx),y0=int(fy),x1=std::min(255,x0+1),y1=std::min(255,y0+1);float dx=fx-x0,dy=fy-y0;
                    float v=(values[y0*256+x0]*(1-dx)+values[y0*256+x1]*dx)*(1-dy)+(values[y1*256+x0]*(1-dx)+values[y1*256+x1]*dx)*dy;
                    alpha[y*512+x]=std::isfinite(v)?std::max(0.f,std::min(1.f,v+.5f)):0.f;
                }
                auto focused=eatvision::focusedCandidate(old,alpha,lightDetail,subject,512,scores[candidate]);
                TRACE("focus candidate %d score %.3f core %d accepted %d",candidate,scores[candidate],subject.count,!focused.empty());
                if(!focused.empty()){focusBest=scores[candidate];selected.swap(focused);}
            }
            if(selected.empty())return nullptr;
            focusedRefinement=true;
            std::vector<jint> colors(512*512);
            for(int i=0;i<512*512;i++)colors[i]=(int(selected[i]*255)<<24)|0x00ffffff;
            return alphaBitmap(env,colors,512,512);
        }
        // Suppress sparse added background texture. Original saliency foreground
        // is restored afterwards, so fine food/utensil details are never eroded.
        std::vector<uint8_t> solid(512*512,0),visited(512*512,0);
        for(int y=0;y<512;y++)for(int x=0;x<512;x++) {
            int countNear=0,totalNear=0;
            for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++) {
                int nx=x+dx,ny=y+dy;if(nx<0||nx>=512||ny<0||ny>=512)continue;
                totalNear++;if(selected[ny*512+nx]>.5f)countNear++;
            }
            solid[y*512+x]=countNear*2>=totalNear;
        }
        std::vector<int> queue;queue.reserve(512*512);
        for(int seed=0;seed<512*512;seed++)if(solid[seed]&&!visited[seed]) {
            queue.clear();queue.push_back(seed);visited[seed]=1;int supported=0;
            for(size_t head=0;head<queue.size();head++) {
                int at=queue[head],x=at%512,y=at/512;if(old[at]>.5f)supported++;
                for(int next:{x>0?at-1:-1,x<511?at+1:-1,y>0?at-512:-1,y<511?at+512:-1})
                    if(next>=0&&solid[next]&&!visited[next]){visited[next]=1;queue.push_back(next);}
            }
            if(supported<16)for(int at:queue)solid[at]=0;
        }
        std::vector<jint> colors(512*512);
        for(int i=0;i<512*512;i++)colors[i]=(int(std::max(solid[i]?selected[i]:0.f,old[i])*255)<<24)|0x00ffffff;
        return alphaBitmap(env,colors,512,512);
    }catch(...){if(imageLocked)AndroidBitmap_unlockPixels(env,bitmap);if(maskLocked)AndroidBitmap_unlockPixels(env,baseline);return nullptr;}
}
