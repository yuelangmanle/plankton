package com.plankton.one102.domain

private val LVL1_NORMALIZE: Map<String, String> = mapOf(
    "轮虫" to "轮虫类",
    "轮虫类" to "轮虫类",
    "桡足" to "桡足类",
    "桡足类" to "桡足类",
    "枝角" to "枝角类",
    "枝角类" to "枝角类",
    "原生动物" to "原生动物",
    "原生动物类" to "原生动物",
)

val LVL1_ORDER: List<String> = listOf("原生动物", "轮虫类", "枝角类", "桡足类")

fun normalizeLvl1Name(v: String): String {
    val key = v.trim()
    if (key.isEmpty()) return ""
    return LVL1_NORMALIZE[key] ?: key
}

