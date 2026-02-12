cmake_minimum_required(VERSION 3.22.1)
project("voiceassistant")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS_3RD_PARTY OFF CACHE BOOL "" FORCE)

set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(GGML_VULKAN_SHADERS_GEN_TOOLCHAIN
    "${CMAKE_SOURCE_DIR}/host-toolchain-llvm-mingw.cmake"
    CACHE FILEPATH ""
    FORCE)
set(GGML_VULKAN_SHADERS_GEN_RUNTIME_PATH
    "D:/plankton/.toolchain/llvm-mingw/llvm-mingw-20251216-ucrt-x86_64/bin"
    CACHE PATH ""
    FORCE)

if (ANDROID AND ANDROID_NDK)
    if (CMAKE_HOST_WIN32)
        set(_ggml_glslc "${ANDROID_NDK}/shader-tools/windows-x86_64/glslc.exe")
    elseif (CMAKE_HOST_APPLE)
        set(_ggml_glslc "${ANDROID_NDK}/shader-tools/darwin-x86_64/glslc")
    else()
        set(_ggml_glslc "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
    endif()
    if (EXISTS "${_ggml_glslc}")
        set(Vulkan_GLSLC_EXECUTABLE "${_ggml_glslc}" CACHE FILEPATH "" FORCE)
    endif()
endif()

add_subdirectory(${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp ${CMAKE_BINARY_DIR}/whisper)

add_library(whisper_jni SHARED native-lib.cpp)

set_target_properties(whisper_jni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED YES
)

target_include_directories(whisper_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp/include
    ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp/src
)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

target_link_libraries(whisper_jni PRIVATE whisper ${log-lib} ${android-lib} ${vulkan-lib})
