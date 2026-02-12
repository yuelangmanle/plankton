package com.voiceassistant.data

enum class TranscriptionEngine(val id: String, val label: String) {
    WHISPER("whisper", "Whisper (离线)"),
    SHERPA_STREAMING("sherpa_streaming", "Sherpa 流式"),
    SHERPA_SENSEVOICE("sherpa_offline", "Sherpa 高精度");

    companion object {
        fun fromId(raw: String?): TranscriptionEngine {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.id == normalized } ?: WHISPER
        }
    }
}

enum class SherpaProvider(val id: String, val label: String, val providerName: String) {
    CPU("cpu", "CPU", "cpu"),
    NNAPI("nnapi", "NNAPI", "nnapi");

    companion object {
        fun fromId(raw: String?): SherpaProvider {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.id == normalized } ?: CPU
        }
    }
}

enum class SherpaStreamingModel(val id: String, val label: String) {
    ZIPFORMER_ZH("zipformer_zh", "zipformer（中文）"),
    ZIPFORMER_BILINGUAL("zipformer_bilingual", "zipformer bilingual（中英）");

    companion object {
        fun fromId(raw: String?): SherpaStreamingModel {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.id == normalized } ?: ZIPFORMER_ZH
        }
    }
}

enum class SherpaOfflineModel(val id: String, val label: String) {
    SENSE_VOICE("sense_voice", "sense-voice（高精度）"),
    PARAFORMER_ZH("paraformer_zh", "paraformer（中文）");

    companion object {
        fun fromId(raw: String?): SherpaOfflineModel {
            val normalized = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.id == normalized } ?: SENSE_VOICE
        }
    }
}
