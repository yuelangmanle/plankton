package com.plankton.one102.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {
    @Test
    fun largePhotoUsesPowerOfTwoSamplingBeforeDecode() {
        assertEquals(4, visionDecodeSampleSize(width = 8000, height = 6000, targetMaxSize = 2000))
    }

    @Test
    fun nearTargetPhotoKeepsEnoughDetailForOcr() {
        assertEquals(1, visionDecodeSampleSize(width = 3000, height = 2000, targetMaxSize = 2000))
    }

    @Test
    fun invalidDimensionsNeverProduceZeroSample() {
        assertEquals(1, visionDecodeSampleSize(width = 0, height = 0, targetMaxSize = 2000))
    }
}
