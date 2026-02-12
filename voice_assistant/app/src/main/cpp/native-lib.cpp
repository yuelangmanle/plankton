#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {
constexpr int kTargetSampleRate = 16000;
constexpr int kMaxThreads = 8;
constexpr int kParallelMinSeconds = 30;

std::mutex g_mutex;
std::string g_last_error;

void set_error(const std::string &message) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_last_error = message;
    __android_log_print(ANDROID_LOG_ERROR, "whisper_jni", "%s", message.c_str());
}

void clear_error() {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_last_error.clear();
}

uint16_t read_u16_le(std::ifstream &stream) {
    uint8_t bytes[2] = {0, 0};
    stream.read(reinterpret_cast<char *>(bytes), 2);
    return static_cast<uint16_t>(bytes[0] | (bytes[1] << 8));
}

uint32_t read_u32_le(std::ifstream &stream) {
    uint8_t bytes[4] = {0, 0, 0, 0};
    stream.read(reinterpret_cast<char *>(bytes), 4);
    return static_cast<uint32_t>(bytes[0] | (bytes[1] << 8) | (bytes[2] << 16) | (bytes[3] << 24));
}

bool read_wav_file(const char *path, int &sample_rate, int &channels, int &bits_per_sample, std::vector<float> &samples) {
    std::ifstream stream(path, std::ios::binary);
    if (!stream) {
        set_error("无法打开音频文件");
        return false;
    }

    char riff[4] = {0};
    stream.read(riff, 4);
    if (std::strncmp(riff, "RIFF", 4) != 0) {
        set_error("不是有效的 RIFF/WAV 文件");
        return false;
    }

    read_u32_le(stream);

    char wave[4] = {0};
    stream.read(wave, 4);
    if (std::strncmp(wave, "WAVE", 4) != 0) {
        set_error("不是有效的 WAV 文件");
        return false;
    }

    bool found_fmt = false;
    bool found_data = false;
    uint16_t audio_format = 0;
    uint32_t data_size = 0;
    std::vector<uint8_t> data;

    while (stream && !(found_fmt && found_data)) {
        char chunk_id[4] = {0};
        stream.read(chunk_id, 4);
        if (stream.eof()) break;
        uint32_t chunk_size = read_u32_le(stream);

        if (std::strncmp(chunk_id, "fmt ", 4) == 0) {
            audio_format = read_u16_le(stream);
            channels = static_cast<int>(read_u16_le(stream));
            sample_rate = static_cast<int>(read_u32_le(stream));
            read_u32_le(stream);
            read_u16_le(stream);
            bits_per_sample = static_cast<int>(read_u16_le(stream));

            if (chunk_size > 16) {
                stream.seekg(chunk_size - 16, std::ios::cur);
            }
            found_fmt = true;
        } else if (std::strncmp(chunk_id, "data", 4) == 0) {
            data_size = chunk_size;
            data.resize(data_size);
            if (data_size > 0) {
                stream.read(reinterpret_cast<char *>(data.data()), data_size);
            }
            found_data = true;
        } else {
            stream.seekg(chunk_size, std::ios::cur);
        }
    }

    if (!found_fmt || !found_data) {
        set_error("WAV 文件缺少 fmt 或 data 块");
        return false;
    }

    if (channels <= 0 || sample_rate <= 0) {
        set_error("WAV 参数无效");
        return false;
    }

    if (audio_format != 1 && audio_format != 3) {
        set_error("暂不支持该 WAV 编码格式");
        return false;
    }

    if (bits_per_sample != 16 && bits_per_sample != 32) {
        set_error("仅支持 16-bit PCM 或 32-bit float WAV");
        return false;
    }

    const int bytes_per_sample = bits_per_sample / 8;
    const size_t frame_count = data_size / (bytes_per_sample * channels);
    if (frame_count == 0) {
        set_error("WAV 文件无有效音频数据");
        return false;
    }

    std::vector<float> mono;
    mono.resize(frame_count);

    if (bits_per_sample == 16) {
        const int16_t *samples_in = reinterpret_cast<const int16_t *>(data.data());
        for (size_t i = 0; i < frame_count; ++i) {
            int64_t sum = 0;
            for (int c = 0; c < channels; ++c) {
                sum += samples_in[i * channels + c];
            }
            mono[i] = static_cast<float>(sum) / static_cast<float>(channels * 32768.0f);
        }
    } else {
        const float *samples_in = reinterpret_cast<const float *>(data.data());
        for (size_t i = 0; i < frame_count; ++i) {
            float sum = 0.0f;
            for (int c = 0; c < channels; ++c) {
                sum += samples_in[i * channels + c];
            }
            mono[i] = sum / static_cast<float>(channels);
        }
    }

    samples = std::move(mono);
    return true;
}

std::vector<float> resample_linear(const std::vector<float> &input, int in_rate, int out_rate) {
    if (input.empty() || in_rate <= 0 || out_rate <= 0 || in_rate == out_rate) {
        return input;
    }

    const double ratio = static_cast<double>(out_rate) / static_cast<double>(in_rate);
    const size_t out_len = static_cast<size_t>(input.size() * ratio);
    std::vector<float> output;
    output.resize(out_len > 0 ? out_len : 1);

    for (size_t i = 0; i < output.size(); ++i) {
        const double src_index = static_cast<double>(i) / ratio;
        const size_t idx = static_cast<size_t>(src_index);
        const double frac = src_index - static_cast<double>(idx);
        const float v0 = input[std::min(idx, input.size() - 1)];
        const float v1 = input[std::min(idx + 1, input.size() - 1)];
        output[i] = v0 + static_cast<float>((v1 - v0) * frac);
    }

    return output;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_voiceassistant_audio_WhisperBridge_nativeInit(JNIEnv *env, jobject /* this */, jstring model_path, jboolean use_gpu) {
    clear_error();
    if (!model_path) {
        set_error("模型路径为空");
        return 0;
    }

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) {
        set_error("无法读取模型路径");
        return 0;
    }

    whisper_context_params ctx_params = whisper_context_default_params();
    ctx_params.use_gpu = (use_gpu == JNI_TRUE);
    whisper_context *ctx = whisper_init_from_file_with_params(path, ctx_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!ctx) {
        set_error("模型加载失败");
        return 0;
    }

    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_voiceassistant_audio_WhisperBridge_nativeFree(JNIEnv *env, jobject /* this */, jlong handle) {
    clear_error();
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx) {
        whisper_free(ctx);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voiceassistant_audio_WhisperBridge_nativeTranscribe(
        JNIEnv *env,
        jobject /* this */,
        jlong handle,
        jstring wav_path,
        jstring language,
        jint mode,
        jint beam_size,
        jint best_of,
        jboolean enable_timestamps,
        jboolean use_multithread,
        jint thread_count) {
    clear_error();
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (!ctx) {
        set_error("模型未初始化");
        return nullptr;
    }

    if (!wav_path) {
        set_error("音频路径为空");
        return nullptr;
    }

    const char *path = env->GetStringUTFChars(wav_path, nullptr);
    if (!path) {
        set_error("无法读取音频路径");
        return nullptr;
    }

    int sample_rate = 0;
    int channels = 0;
    int bits = 0;
    std::vector<float> samples;
    const bool ok = read_wav_file(path, sample_rate, channels, bits, samples);
    env->ReleaseStringUTFChars(wav_path, path);

    if (!ok) {
        return nullptr;
    }

    if (sample_rate != kTargetSampleRate) {
        samples = resample_linear(samples, sample_rate, kTargetSampleRate);
    }

    if (samples.empty()) {
        set_error("音频数据为空");
        return nullptr;
    }

    std::string language_code = "auto";
    if (language) {
        const char *lang = env->GetStringUTFChars(language, nullptr);
        if (lang && std::strlen(lang) > 0) {
            language_code = lang;
        }
        if (lang) {
            env->ReleaseStringUTFChars(language, lang);
        }
    }

    const int sampling_mode = (mode == 1) ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY;
    whisper_full_params params = whisper_full_default_params(static_cast<whisper_sampling_strategy>(sampling_mode));
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_timestamps = (enable_timestamps == JNI_FALSE);
    params.no_context = true;
    params.language = language_code.c_str();
    const int safe_best_of = std::max(1, static_cast<int>(best_of));
    const int safe_beam_size = std::max(1, static_cast<int>(beam_size));
    if (sampling_mode == WHISPER_SAMPLING_BEAM_SEARCH) {
        params.beam_search.beam_size = safe_beam_size;
    } else {
        params.greedy.best_of = safe_best_of;
    }

    const bool enable_multithreading = (use_multithread == JNI_TRUE);
    const int requested_threads = std::max(0, static_cast<int>(thread_count));
    int total_threads = 1;
    if (enable_multithreading) {
        if (requested_threads > 0) {
            total_threads = std::max(1, std::min(kMaxThreads, requested_threads));
        } else {
            const int hw_threads = std::max(1, static_cast<int>(std::thread::hardware_concurrency()));
            total_threads = std::max(1, std::min(kMaxThreads, hw_threads));
        }
    }
    const float duration_sec = static_cast<float>(samples.size()) / static_cast<float>(kTargetSampleRate);
    int n_processors = 1;
    if (enable_multithreading &&
        duration_sec >= static_cast<float>(kParallelMinSeconds) &&
        total_threads >= 4) {
        n_processors = 2;
    }
    params.n_threads = std::max(1, total_threads / n_processors);

    int ret = 0;
    if (enable_multithreading && n_processors > 1) {
        ret = whisper_full_parallel(ctx, params, samples.data(), static_cast<int>(samples.size()), n_processors);
    } else {
        ret = whisper_full(ctx, params, samples.data(), static_cast<int>(samples.size()));
    }
    if (ret != 0) {
        set_error("whisper 转写失败");
        return nullptr;
    }

    const int segments = whisper_full_n_segments(ctx);
    std::string result;
    for (int i = 0; i < segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) {
            result += text;
        }
    }

    if (result.empty()) {
        set_error("未识别到文本");
        return nullptr;
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_voiceassistant_audio_WhisperBridge_nativeGetLastError(JNIEnv *env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_last_error.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(g_last_error.c_str());
}
