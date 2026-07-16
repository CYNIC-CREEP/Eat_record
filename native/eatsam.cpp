#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <MNN/expr/Executor.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Module.hpp>
#include <cv/cv.hpp>

#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "EatSam"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using namespace MNN;
using namespace MNN::Express;
using namespace MNN::CV;

namespace {
std::mutex gMutex;
std::shared_ptr<Executor::RuntimeManager> gRuntime;
std::shared_ptr<Module> gEmbed;
std::shared_ptr<Module> gSegment;
std::string gEmbedPath;
std::string gSegmentPath;

std::string jstringToString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? "" : chars;
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

bool ensureModels(const std::string& embedPath, const std::string& segmentPath) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gEmbed && gSegment && gEmbedPath == embedPath && gSegmentPath == segmentPath) {
        return true;
    }

    MNN::ScheduleConfig schedule;
    schedule.type = MNN_FORWARD_CPU;
    schedule.numThread = 4;
    BackendConfig backend;
    backend.precision = BackendConfig::Precision_Low;
    schedule.backendConfig = &backend;

    gRuntime = std::shared_ptr<Executor::RuntimeManager>(
            Executor::RuntimeManager::createRuntimeManager(schedule));
    if (!gRuntime) {
        LOGE("failed to create MNN runtime");
        return false;
    }

    Module* embed = Module::load(std::vector<std::string>{}, std::vector<std::string>{},
                                 embedPath.c_str(), gRuntime);
    if (embed == nullptr) {
        LOGE("failed to load embed model: %s", embedPath.c_str());
        return false;
    }

    std::vector<std::string> inputs = {
            "point_coords", "point_labels", "image_embeddings",
            "has_mask_input", "mask_input", "orig_im_size"
    };
    std::vector<std::string> outputs = {"iou_predictions", "low_res_masks", "masks"};
    Module* segment = Module::load(inputs, outputs, segmentPath.c_str(), gRuntime);
    if (segment == nullptr) {
        LOGE("failed to load segment model: %s", segmentPath.c_str());
        Module::destroy(embed);
        return false;
    }

    gEmbed = std::shared_ptr<Module>(embed, Module::destroy);
    gSegment = std::shared_ptr<Module>(segment, Module::destroy);
    gEmbedPath = embedPath;
    gSegmentPath = segmentPath;
    LOGI("SAM models loaded");
    return true;
}

VARP buildFloatInput(const std::vector<float>& data, const std::vector<int>& shape) {
    return _Const(static_cast<const void*>(data.data()), shape, NCHW, halide_type_of<float>());
}

bool bitmapToRgb(AndroidBitmapInfo& info, void* pixels, std::vector<uint8_t>& rgb) {
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("input bitmap must be RGBA_8888");
        return false;
    }
    const int width = static_cast<int>(info.width);
    const int height = static_cast<int>(info.height);
    rgb.resize(static_cast<size_t>(width) * height * 3);
    const uint8_t* src = static_cast<const uint8_t*>(pixels);
    for (int y = 0; y < height; y++) {
        const uint8_t* row = src + static_cast<size_t>(y) * info.stride;
        for (int x = 0; x < width; x++) {
            const uint8_t* p = row + x * 4;
            size_t out = (static_cast<size_t>(y) * width + x) * 3;
            rgb[out] = p[0];
            rgb[out + 1] = p[1];
            rgb[out + 2] = p[2];
        }
    }
    return true;
}

void writeTransparentSticker(AndroidBitmapInfo& inputInfo, void* inputPixels,
                             AndroidBitmapInfo& outputInfo, void* outputPixels,
                             const float* maskData, int maskWidth, int maskHeight) {
    const int width = static_cast<int>(inputInfo.width);
    const int height = static_cast<int>(inputInfo.height);
    uint8_t* dstBase = static_cast<uint8_t*>(outputPixels);
    const uint8_t* srcBase = static_cast<const uint8_t*>(inputPixels);
    for (int y = 0; y < height; y++) {
        const uint8_t* srcRow = srcBase + static_cast<size_t>(y) * inputInfo.stride;
        uint8_t* dstRow = dstBase + static_cast<size_t>(y) * outputInfo.stride;
        int my = std::min(maskHeight - 1, std::max(0, y));
        for (int x = 0; x < width; x++) {
            const uint8_t* src = srcRow + x * 4;
            uint8_t* dst = dstRow + x * 4;
            int mx = std::min(maskWidth - 1, std::max(0, x));
            float value = maskData[static_cast<size_t>(my) * maskWidth + mx];
            if (value > 0.0f) {
                dst[0] = src[0];
                dst[1] = src[1];
                dst[2] = src[2];
                dst[3] = 255;
            } else {
                dst[0] = 0;
                dst[1] = 0;
                dst[2] = 0;
                dst[3] = 0;
            }
        }
    }
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_eatrecord_app_MainActivity_nativeSamSticker(
        JNIEnv* env, jclass,
        jobject inputBitmap, jobject outputBitmap,
        jfloat pointX, jfloat pointY,
        jint candidateIndex,
        jstring embedModelPath, jstring segmentModelPath) {
    const std::string embedPath = jstringToString(env, embedModelPath);
    const std::string segmentPath = jstringToString(env, segmentModelPath);
    if (embedPath.empty() || segmentPath.empty() || inputBitmap == nullptr || outputBitmap == nullptr) {
        return JNI_FALSE;
    }
    if (!ensureModels(embedPath, segmentPath)) {
        return JNI_FALSE;
    }

    AndroidBitmapInfo inputInfo;
    AndroidBitmapInfo outputInfo;
    if (AndroidBitmap_getInfo(env, inputBitmap, &inputInfo) != ANDROID_BITMAP_RESULT_SUCCESS ||
        AndroidBitmap_getInfo(env, outputBitmap, &outputInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (inputInfo.width != outputInfo.width || inputInfo.height != outputInfo.height ||
        outputInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("bitmap size or format mismatch");
        return JNI_FALSE;
    }

    void* inputPixels = nullptr;
    void* outputPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, inputBitmap, &inputPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (AndroidBitmap_lockPixels(env, outputBitmap, &outputPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, inputBitmap);
        return JNI_FALSE;
    }

    std::vector<uint8_t> rgb;
    bool ok = bitmapToRgb(inputInfo, inputPixels, rgb);
    if (!ok) {
        AndroidBitmap_unlockPixels(env, outputBitmap);
        AndroidBitmap_unlockPixels(env, inputBitmap);
        return JNI_FALSE;
    }

    const int originH = static_cast<int>(inputInfo.height);
    const int originW = static_cast<int>(inputInfo.width);
    const int length = 1024;
    int newH;
    int newW;
    if (originH > originW) {
        newW = static_cast<int>(std::round(originW * static_cast<float>(length) / originH));
        newH = length;
    } else {
        newH = static_cast<int>(std::round(originH * static_cast<float>(length) / originW));
        newW = length;
    }
    const float scaleW = static_cast<float>(newW) / originW;
    const float scaleH = static_cast<float>(newH) / originH;

    try {
        VARP image = _Const(static_cast<const void*>(rgb.data()), {originH, originW, 3},
                            NHWC, halide_type_of<uint8_t>());
        VARP input = resize(image, Size(newW, newH), 0, 0, INTER_LINEAR, -1,
                            {123.675f, 116.28f, 103.53f},
                            {1.0f / 58.395f, 1.0f / 57.12f, 1.0f / 57.375f});
        std::vector<int> padVals = {0, length - newH, 0, length - newW, 0, 0};
        VARP pads = _Const(static_cast<const void*>(padVals.data()), {3, 2},
                           NCHW, halide_type_of<int>());
        input = _Pad(input, pads, CONSTANT);
        input = _Unsqueeze(input, {0});
        input = _Convert(input, NC4HW4);

        std::vector<VARP> embedOut = gEmbed->onForward({input});
        if (embedOut.empty()) {
            LOGE("empty embed output");
            AndroidBitmap_unlockPixels(env, outputBitmap);
            AndroidBitmap_unlockPixels(env, inputBitmap);
            return JNI_FALSE;
        }
        VARP imageEmbedding = _Convert(embedOut[0], NCHW);

        float px = std::min(std::max(0.0f, static_cast<float>(pointX)), static_cast<float>(originW - 1));
        float py = std::min(std::max(0.0f, static_cast<float>(pointY)), static_cast<float>(originH - 1));
        std::vector<float> points = {px * scaleW, py * scaleH, 0.0f, 0.0f};
        VARP pointCoords = buildFloatInput(points, {1, 2, 2});
        VARP pointLabels = buildFloatInput({1.0f, -1.0f}, {1, 2});
        VARP origSize = buildFloatInput({static_cast<float>(originH), static_cast<float>(originW)}, {2});
        VARP hasMaskInput = buildFloatInput({0.0f}, {1});
        std::vector<float> zeros(256 * 256, 0.0f);
        VARP maskInput = buildFloatInput(zeros, {1, 1, 256, 256});

        std::vector<VARP> segmentOut = gSegment->onForward({
                pointCoords, pointLabels, imageEmbedding, hasMaskInput, maskInput, origSize
        });
        if (segmentOut.size() < 3) {
            LOGE("bad segment output");
            AndroidBitmap_unlockPixels(env, outputBitmap);
            AndroidBitmap_unlockPixels(env, inputBitmap);
            return JNI_FALSE;
        }

        VARP masks = _Convert(segmentOut[2], NCHW);
        masks = _Squeeze(masks, {0});
        auto candidateInfo = masks->getInfo();
        if (candidateInfo != nullptr && candidateInfo->dim.size() >= 3) {
            int channels = std::max(1, candidateInfo->dim[0]);
            int selected = static_cast<int>(candidateIndex) % channels;
            if (selected < 0) {
                selected += channels;
            }
            masks = _Gather(masks, _Scalar<int>(selected));
        }
        auto info = masks->getInfo();
        if (info == nullptr || info->dim.size() < 2) {
            LOGE("mask info missing");
            AndroidBitmap_unlockPixels(env, outputBitmap);
            AndroidBitmap_unlockPixels(env, inputBitmap);
            return JNI_FALSE;
        }
        int maskH = info->dim[info->dim.size() - 2];
        int maskW = info->dim[info->dim.size() - 1];
        const float* maskPtr = masks->readMap<float>();
        if (maskPtr == nullptr) {
            LOGE("mask read failed");
            AndroidBitmap_unlockPixels(env, outputBitmap);
            AndroidBitmap_unlockPixels(env, inputBitmap);
            return JNI_FALSE;
        }
        writeTransparentSticker(inputInfo, inputPixels, outputInfo, outputPixels, maskPtr, maskW, maskH);
    } catch (...) {
        LOGE("SAM inference threw exception");
        AndroidBitmap_unlockPixels(env, outputBitmap);
        AndroidBitmap_unlockPixels(env, inputBitmap);
        return JNI_FALSE;
    }

    AndroidBitmap_unlockPixels(env, outputBitmap);
    AndroidBitmap_unlockPixels(env, inputBitmap);
    return JNI_TRUE;
}
