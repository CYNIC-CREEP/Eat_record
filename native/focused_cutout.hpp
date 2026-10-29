#pragma once
#include <algorithm>
#include <cmath>
#include <vector>

namespace eatvision {
// Saliency sometimes includes a tray/person at medium confidence. Only permit
// a smaller object prompt when essentially all strong evidence is one subject.
struct FocusedSubject {
    std::vector<unsigned char> core;
    int count=0, left=0, top=0, right=0, bottom=0;
};

inline std::vector<int> component(const std::vector<float>& mask, int side, float threshold) {
    const int n=side*side;
    std::vector<unsigned char> visited(n,0);
    std::vector<int> best, queue; queue.reserve(n);
    for(int seed=0;seed<n;seed++) if(mask[seed]>threshold&&!visited[seed]) {
        queue.clear(); queue.push_back(seed); visited[seed]=1;
        for(size_t head=0;head<queue.size();head++) {
            int at=queue[head],x=at%side,y=at/side;
            for(int next:{x>0?at-1:-1,x<side-1?at+1:-1,y>0?at-side:-1,y<side-1?at+side:-1})
                if(next>=0&&!visited[next]&&mask[next]>threshold){visited[next]=1;queue.push_back(next);}
        }
        if(queue.size()>best.size())best=queue;
    }
    return best;
}

inline FocusedSubject focusedSubject(const std::vector<float>& old, int side) {
    FocusedSubject result;
    int strong=0,foreground=0;
    for(float a:old){if(a>.9f)strong++;if(a>.5f)foreground++;}
    auto core=component(old,side,.9f);
    if(core.size()<size_t(side*side/50)||core.size()<strong*.98f||
            foreground<core.size()*1.55f||foreground>core.size()*3.f)return result;
    auto outline=component(old,side,.8f);
    result.core.assign(side*side,0);
    for(int at:core)result.core[at]=1;
    int supported=0,left=side,top=side,right=-1,bottom=-1;
    for(int at:outline){supported+=result.core[at];int x=at%side,y=at/side;
        left=std::min(left,x);right=std::max(right,x);top=std::min(top,y);bottom=std::max(bottom,y);}
    if(supported<core.size()*.995f||right<left||
            core.size()<size_t((right-left+1)*(bottom-top+1))*.5f)return FocusedSubject();
    result.count=int(core.size());result.left=left;result.top=top;result.right=right;result.bottom=bottom;
    return result;
}

// The contour still comes from EdgeTAM. Preserve strong food evidence and only
// connected light/neutral details already predicted by saliency (e.g. a spoon).
// Do not union the complete original mask: that would restore the tray/person.
inline std::vector<float> focusedCandidate(const std::vector<float>& old,
        const std::vector<float>& candidate,const std::vector<unsigned char>& light,
        const FocusedSubject& subject,int side,float score) {
    if(subject.count==0||!std::isfinite(score)||score<.85f)return {};
    const int n=side*side; int rawArea=0,rawSupported=0,overlap=0;
    std::vector<float> combined(n);
    for(int i=0;i<n;i++) {
        if(candidate[i]>.5f){rawArea++;if(old[i]>.5f)rawSupported++;if(subject.core[i])overlap++;}
        combined[i]=std::max(candidate[i],subject.core[i]?old[i]:0.f);
        if(light[i]&&old[i]>.65f)combined[i]=std::max(combined[i],old[i]);
    }
    if(rawArea==0||rawSupported<rawArea*.9f||overlap<subject.count*.2f)return {};
    std::vector<unsigned char> visited(n,0),kept(n,0);
    std::vector<int> queue;queue.reserve(n);
    for(int seed=0;seed<n;seed++)if(combined[seed]>.5f&&!visited[seed]){
        queue.clear();queue.push_back(seed);visited[seed]=1;int supported=0;
        for(size_t head=0;head<queue.size();head++){
            int at=queue[head],x=at%side,y=at/side;supported+=subject.core[at];
            for(int next:{x>0?at-1:-1,x<side-1?at+1:-1,y>0?at-side:-1,y<side-1?at+side:-1})
                if(next>=0&&!visited[next]&&combined[next]>.5f){visited[next]=1;queue.push_back(next);}
        }
        if(supported>=16)for(int at:queue)kept[at]=1;
    }
    int area=0,strong=0,preserved=0;
    for(int i=0;i<n;i++){area+=kept[i];if(old[i]>.9f){strong++;if(kept[i])preserved++;}}
    if(area<subject.count*1.03f||area>subject.count*1.45f||preserved<strong*.995f)return {};
    // Preserve the model's antialiasing next to a supported component only.
    for(int i=0;i<n;i++)if(!kept[i]){
        int x=i%side,y=i/side;bool near=false;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
            int nx=x+dx,ny=y+dy;if(nx>=0&&nx<side&&ny>=0&&ny<side&&kept[ny*side+nx])near=true;
        }
        if(!near)combined[i]=0;
    }
    return combined;
}
}
