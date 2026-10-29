cmake_minimum_required(VERSION 3.18)
project(eatvision)

add_library(eatvision SHARED eatvision.cpp)

target_include_directories(eatvision PRIVATE
    ${MNN_INCLUDE_DIR}
)

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION
    ${MNN_LIB_DIR}/libMNN.so)

target_compile_features(eatvision PRIVATE cxx_std_17)
if(EATVISION_QA)
    target_compile_definitions(eatvision PRIVATE EATVISION_QA=1)
endif()
target_link_options(eatvision PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(eatvision
    MNN
    jnigraphics
    android
    log
)
