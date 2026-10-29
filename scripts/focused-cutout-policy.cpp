// Desktop diagnostic driver for the exact production mask policy (no MNN/JNI).
// cl /std:c++17 /O2 /EHsc focused-cutout-policy.cpp /Fe:<output>
#include "../native/focused_cutout.hpp"
#include "../native/cutout_sampling.hpp"
#include <fstream>
#include <iostream>
#include <string>

template<class T> std::vector<T> read(const char* path) {
    std::ifstream in(path,std::ios::binary);
    if(!in)throw std::runtime_error("Cannot open mask input");
    std::vector<T> values(512*512);in.read(reinterpret_cast<char*>(values.data()),values.size()*sizeof(T));
    if(in.gcount()!=std::streamsize(values.size()*sizeof(T)))throw std::runtime_error("Invalid mask input size");
    return values;
}
int main(int argc,char** argv) {
    try {
        if(argc==7&&std::string(argv[1])=="--resample"){
            int width=std::stoi(argv[3]),height=std::stoi(argv[4]),side=std::stoi(argv[5]);
            std::ifstream in(argv[2],std::ios::binary);std::vector<unsigned char> pixels(width*height*4);
            in.read(reinterpret_cast<char*>(pixels.data()),pixels.size());
            if(in.gcount()!=std::streamsize(pixels.size()))throw std::runtime_error("Invalid source image size");
            auto result=eatvision::antialiasedRgb(pixels.data(),width,height,width*4,side);
            std::ofstream out(argv[6],std::ios::binary);out.write(reinterpret_cast<const char*>(result.data()),result.size());
            return 0;
        }
        if(argc!=2&&argc!=6)throw std::runtime_error("usage: policy old.f32 [candidate.f32 light.u8 score output.f32]");
        auto old=read<float>(argv[1]);auto subject=eatvision::focusedSubject(old,512);
        std::cout<<"{\"core\":"<<subject.count<<",\"bounds\":["<<subject.left<<","<<subject.top<<","<<subject.right<<","<<subject.bottom<<"]";
        if(argc==6){
            auto result=eatvision::focusedCandidate(old,read<float>(argv[2]),read<unsigned char>(argv[3]),subject,512,std::stof(argv[4]));
            std::cout<<",\"accepted\":"<<(result.empty()?"false":"true");
            if(!result.empty()){
                std::ofstream out(argv[5],std::ios::binary);out.write(reinterpret_cast<const char*>(result.data()),result.size()*sizeof(float));
            }
        }
        std::cout<<"}"<<std::endl;return 0;
    }catch(const std::exception& e){std::cerr<<e.what()<<std::endl;return 1;}
}
