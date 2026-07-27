package com.plankton.one102.data.api

import org.junit.Assert.assertTrue
import org.junit.Test

class AiAnswerUtilsTest {
    @Test
    fun reasoningOnlyResponseRequestsContinuationEvenWhenItEndsWithPunctuation() {
        assertTrue(looksTruncatedAnswer("<think>先核对分类，再给出结论。</think>"))
    }
}
