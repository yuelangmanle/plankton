package com.plankton.one102.domain

import kotlinx.serialization.Serializable

@Serializable
data class DepthRange(
    val minM: Double = 0.0,
    val maxM: Double = 0.0,
)

@Serializable
data class StratificationConfig(
    val enabled: Boolean = false,
    // 默认分层：上层 0–10（含 10）、中层 10–30、下层 >30（通过“区间重叠按上→中→下优先”实现）
    val upper: DepthRange = DepthRange(minM = 0.0, maxM = 10.0),
    val middle: DepthRange = DepthRange(minM = 10.0, maxM = 30.0),
    val lower: DepthRange = DepthRange(minM = 30.0, maxM = 9999.0),
)
