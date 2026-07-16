cmake_minimum_required(VERSION 3.18)
project(eatsam)

add_library(eatsam SHARED eatsam.cpp)

target_include_directories(eatsam PRIVATE
    ${MNN_INCLUDE_DIR}
    ${MNN_CV_INCLUDE_DIR}
)

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION
    ${MNN_LIB_DIR}/libMNN.so)

add_library(MNN_Express SHARED IMPORTED)
set_target_properties(MNN_Express PROPERTIES IMPORTED_LOCATION
    ${MNN_LIB_DIR}/libMNN_Express.so)

add_library(MNNOpenCV SHARED IMPORTED)
set_target_properties(MNNOpenCV PROPERTIES IMPORTED_LOCATION
    ${MNN_LIB_DIR}/libMNNOpenCV.so)

target_link_libraries(eatsam
    MNN
    MNN_Express
    MNNOpenCV
    jnigraphics
    android
    log
)
