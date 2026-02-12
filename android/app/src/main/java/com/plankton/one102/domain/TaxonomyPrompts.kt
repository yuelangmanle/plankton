package com.plankton.one102.domain

private val LVL1_ALLOWED = listOf("原生动物", "轮虫类", "枝角类", "桡足类")

fun buildTaxonomyPrompt(nameCn: String, nameLatin: String?): String {
    val latin = nameLatin?.trim().takeIf { !it.isNullOrBlank() } ?: "（未提供）"
    val allowed = LVL1_ALLOWED.joinToString(" / ")
    return """
        请精确考证并给出以下浮游动物在“内置分类库”下的分类信息。

        物种中文名：$nameCn
        拉丁名：$latin

        分类字段说明（对应分类库 A-E 列）：
        - lvl1：四大类之一（必须从：$allowed 中选择；不确定请输出 UNKNOWN）
        - lvl2：纲
        - lvl3：目
        - lvl4：科
        - lvl5：属

        输出要求（必须满足）：
        1) 给出分类结论，并说明依据（使用何种权威分类体系/数据库/文献，如 WoRMS、GBIF、专著、论文等）。
        2) 至少提供 1 条可核对来源（DOI/ISBN/URL 或完整参考文献条目）。如果无法提供可靠来源，请明确说明“不确定/未找到可靠数据”，不要编造来源。
        3) 最后一行严格输出（只输出一行，不要加代码块）：
           FINAL_TAXONOMY_JSON: <{"lvl1":"...","lvl2":"...","lvl3":"...","lvl4":"...","lvl5":"..."} 或 UNKNOWN>
    """.trimIndent()
}
