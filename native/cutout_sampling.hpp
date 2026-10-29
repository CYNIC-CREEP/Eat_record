#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace eatvision {
struct ResizeWeights { int start; std::vector<int32_t> weights; };
inline std::vector<ResizeWeights> resizeWeights(int source,int target) {
    std::vector<ResizeWeights> result(target);
    const double scale=double(source)/target,filterScale=std::max(1.,scale);
    for(int at=0;at<target;at++) {
        const double center=(at+.5)*scale;
        int start=std::max(0,int(center-filterScale+.5));
        int end=std::min(source,int(center+filterScale+.5));
        auto& entry=result[at];entry.start=start;double sum=0;std::vector<double> weights;
        for(int i=start;i<end;i++){
            double value=std::max(0.,1.-std::abs((i-center+.5)/filterScale));
            weights.push_back(value);sum+=value;
        }
        if(sum>0)for(double value:weights)entry.weights.push_back(int32_t(std::floor(value/sum*(1<<22)+.5)));
    }
    return result;
}
// Separable triangle filter widens its support when reducing an image. Sampling
// only four source pixels aliases tray texture/highlights into saliency evidence.
// Round each pass to RGB8 so inference preprocessing is reproducible on desktop.
inline std::vector<uint8_t> antialiasedRgb(const uint8_t* rgba,int width,int height,
        int stride,int side) {
    auto horizontal=resizeWeights(width,side),vertical=resizeWeights(height,side);
    std::vector<uint8_t> rows(height*side*3),output(side*side*3);
    for(int y=0;y<height;y++)for(int x=0;x<side;x++)for(int c=0;c<3;c++){
        const auto& filter=horizontal[x];int64_t value=1<<21;
        for(size_t i=0;i<filter.weights.size();i++)value+=rgba[y*stride+(filter.start+int(i))*4+c]*filter.weights[i];
        rows[(y*side+x)*3+c]=uint8_t(std::max<int64_t>(0,std::min<int64_t>(255,value>>22)));
    }
    for(int y=0;y<side;y++)for(int x=0;x<side;x++)for(int c=0;c<3;c++){
        const auto& filter=vertical[y];int64_t value=1<<21;
        for(size_t i=0;i<filter.weights.size();i++)value+=rows[((filter.start+int(i))*side+x)*3+c]*filter.weights[i];
        output[(y*side+x)*3+c]=uint8_t(std::max<int64_t>(0,std::min<int64_t>(255,value>>22)));
    }
    return output;
}
}
