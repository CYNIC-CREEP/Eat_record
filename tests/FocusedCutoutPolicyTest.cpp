#include "../native/focused_cutout.hpp"
#include "../native/cutout_sampling.hpp"
#include <iostream>
#include <stdexcept>

static int checks=0;
static void require(bool value,const char* message){checks++;if(!value)throw std::runtime_error(message);}
static void rect(std::vector<float>& mask,int side,int l,int t,int r,int b,float alpha){
    for(int y=t;y<b;y++)for(int x=l;x<r;x++)mask[y*side+x]=alpha;
}
int main(){
    try {
        const int side=64,n=side*side;
        std::vector<float> old(n),candidate(n);std::vector<unsigned char> light(n,0);
        // One food subject with a large uncertain tray, plus detached clothing.
        rect(old,side,10,10,54,46,.7f);rect(old,side,20,16,44,40,.99f);
        rect(old,side,4,56,12,62,.4f);
        auto subject=eatvision::focusedSubject(old,side);
        // A true compact core must occupy enough of its .8 outline; a rectangle
        // at .7 is not itself included in the box prompt.
        require(subject.count==576,"Concentrated strong subject was not found");
        require(subject.left==20&&subject.right==43,"Prompt must follow model evidence");
        rect(candidate,side,19,15,45,41,1.f);
        auto focused=eatvision::focusedCandidate(old,candidate,light,subject,side,.9f);
        require(!focused.empty(),"Confident compact candidate rejected");
        require(focused[20*side+20]>.9f,"Strong food evidence lost");
        require(focused[10*side+10]==0,"Uncertain tray restored");
        require(focused[58*side+8]==0,"Detached clothing restored");
        require(eatvision::focusedCandidate(old,candidate,light,subject,side,.84f).empty(),"Low-score candidate accepted");
        require(eatvision::focusedCandidate(old,candidate,light,subject,side,NAN).empty(),"Non-finite score accepted");
        auto broad=candidate;rect(broad,side,8,8,56,50,1.f);
        require(eatvision::focusedCandidate(old,broad,light,subject,side,.99f).empty(),"Oversized background accepted");
        auto offSubject=std::vector<float>(n);rect(offSubject,side,0,0,16,16,1.f);
        require(eatvision::focusedCandidate(old,offSubject,light,subject,side,.99f).empty(),"Unrelated high-score object accepted");
        // Two independently strong dishes cannot be reduced to the largest.
        auto multiple=old;rect(multiple,side,3,18,15,38,.99f);
        require(eatvision::focusedSubject(multiple,side).count==0,"Second strong dish would be dropped");
        // An established clean result never enters the subtractive fallback.
        auto clean=std::vector<float>(n);rect(clean,side,19,15,45,41,.99f);
        require(eatvision::focusedSubject(clean,side).count==0,"Clean baseline entered focus fallback");
        // Bright details are kept only if they connect to the food subject.
        auto detailOld=old;
        for(int x=10;x<20;x++){detailOld[24*side+x]=.8f;light[24*side+x]=1;}
        for(int x=3;x<8;x++){detailOld[54*side+x]=.8f;light[54*side+x]=1;}
        auto detail=eatvision::focusedCandidate(detailOld,candidate,light,subject,side,.9f);
        require(!detail.empty()&&detail[24*side+10]>.5f,"Connected salient spoon lost");
        require(detail[54*side+5]==0,"Detached light background retained");
        // Downsampling integrates texture instead of selecting only four pixels.
        std::vector<unsigned char> rgba(64*64*4);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            int at=(y*64+x)*4;rgba[at]=x%4==0?255:0;rgba[at+1]=98;rgba[at+2]=234;rgba[at+3]=255;
        }
        auto reduced=eatvision::antialiasedRgb(rgba.data(),64,64,64*4,16);
        require(reduced[(8*16+8)*3]==64,"Sparse texture aliased instead of averaging");
        require(reduced[(8*16+8)*3+1]==98&&reduced[(8*16+8)*3+2]==234,"Constant channels changed on resize");
        auto identity=eatvision::antialiasedRgb(rgba.data(),64,64,64*4,64);
        bool same=true;
        for(int i=0;i<64*64;i++)for(int c=0;c<3;c++)if(identity[i*3+c]!=rgba[i*4+c])same=false;
        require(same,"Identity resize changed source pixels");
        // Input row stride includes padding on Android; never treat it as pixels.
        std::vector<unsigned char> padded(64*272,0);
        for(int y=0;y<64;y++)std::copy(rgba.begin()+y*256,rgba.begin()+(y+1)*256,padded.begin()+y*272);
        require(eatvision::antialiasedRgb(padded.data(),64,64,272,16)==reduced,"Bitmap row stride ignored");
        std::cout<<"PASS: "<<checks<<" focused-cutout safety checks"<<std::endl;return 0;
    }catch(const std::exception& e){std::cerr<<e.what()<<std::endl;return 1;}
}
