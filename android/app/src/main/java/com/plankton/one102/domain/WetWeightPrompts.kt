package com.plankton.one102.domain

fun buildWetWeightPrompt(nameCn: String, nameLatin: String?): String {
    val latin = nameLatin?.trim().takeIf { !it.isNullOrBlank() } ?: "（未提供）"
    return """
        请精确考证并给出以下浮游动物的平均湿重（单位：mg/个）。
        
        物种中文名：$nameCn
        拉丁名：$latin
        
        输出要求（必须满足）：
        1) 最终答案只写给用户看的短结论，不要把思考过程、推理链或内部分析混入正文。
        2) 给出“推荐平均湿重（mg/个）”，如有不同文献/体长范围导致差异，请给出“合理范围”。
        3) 用 1-3 行说明依据：样本来源或研究对象、体长/发育阶段、湿重定义、干湿重换算（如有）、单位换算等。
        4) 至少提供 1 条可核对来源（DOI/ISBN/URL 或完整参考文献条目）。如果无法提供可靠来源，请明确说明“不确定/未找到可靠数据”，不要编造来源。
        5) 不要只给一个数字就结束。
        
        最后一行请严格输出：
        FINAL_MG_PER_INDIVIDUAL: <number 或 UNKNOWN>
    """.trimIndent()
}
