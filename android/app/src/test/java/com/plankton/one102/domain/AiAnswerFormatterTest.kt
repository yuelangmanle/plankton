package com.plankton.one102.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAnswerFormatterTest {
    @Test
    fun thinkBlockIsSeparatedFromUserVisibleAnswer() {
        val raw = """
            <think>
            先比较两个候选分类，再核对数据库。
            </think>

            结论：该物种应归入轮虫类。
            FINAL_TAXONOMY_JSON: {"lvl1":"轮虫类","lvl2":"轮虫纲","lvl3":"","lvl4":"","lvl5":""}
        """.trimIndent()

        val parts = splitAiAnswer(raw)

        assertTrue(parts.reasoningText.contains("比较两个候选"))
        assertTrue(parts.answerText.contains("结论：该物种应归入轮虫类"))
        assertTrue(parts.answerText.contains("FINAL_TAXONOMY_JSON"))
        assertFalse(parts.answerText.contains("比较两个候选"))
    }

    @Test
    fun chineseReasoningSectionIsFoldedUntilConclusion() {
        val raw = """
            思考过程：
            需要先确认中文俗名对应的拉丁名。
            再比较两个数据库的分类差异。
            结论：建议暂按桡足类处理。
        """.trimIndent()

        val parts = splitAiAnswer(raw)

        assertEquals("结论：建议暂按桡足类处理。", parts.answerText)
        assertTrue(parts.reasoningText.contains("确认中文俗名"))
        assertTrue(parts.reasoningText.contains("分类差异"))
    }

    @Test
    fun displayAnswerHidesFinalMarkerButKeepsReasoningAvailable() {
        val raw = "<think>计算换算系数</think>\n推荐平均湿重：0.001 mg/个\nFINAL_MG_PER_INDIVIDUAL: 0.001"

        val display = buildAiDisplayAnswer(raw)

        assertTrue(display.hasReasoning)
        assertTrue(display.reasoningText.contains("换算系数"))
        assertTrue(display.visibleText.contains("推荐平均湿重"))
        assertFalse(display.visibleText.contains("FINAL_MG_PER_INDIVIDUAL"))
    }
}
