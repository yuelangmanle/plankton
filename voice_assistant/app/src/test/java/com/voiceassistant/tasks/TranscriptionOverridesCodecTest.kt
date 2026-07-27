package com.voiceassistant.tasks

import com.voiceassistant.data.DecodeMode
import com.voiceassistant.data.SherpaProvider
import com.voiceassistant.data.TranscriptionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptionOverridesCodecTest {
    @Test
    fun explicitOverridesSurviveEncodingWithoutChangingModelName() {
        val overrides = TranscriptionOverrides(
            engine = TranscriptionEngine.SHERPA_STREAMING,
            modelId = "custom model / 2026?alpha",
            decodeMode = DecodeMode.ACCURATE,
            useGpu = true,
            useMultithread = false,
            threadCount = 8,
            sherpaProvider = SherpaProvider.NNAPI,
        )

        val decoded = TranscriptionOverridesCodec.decode(TranscriptionOverridesCodec.encode(overrides))

        assertEquals(overrides, decoded)
    }

    @Test
    fun emptyAndCorruptPayloadFallBackToNoOverrides() {
        assertEquals(TranscriptionOverrides(), TranscriptionOverridesCodec.decode(null))
        val decoded = TranscriptionOverridesCodec.decode("engine=not-base64&threads=MA")
        assertNull(decoded.engine)
        assertNull(decoded.threadCount)
    }
}
