package com.plankton.one102.domain

import kotlin.math.abs

fun parseSiteAndDepthFromLabel(label: String): Pair<String?, Double?> {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return null to null
    val parts = trimmed.split("-", limit = 2)
    val site = parts.firstOrNull()?.trim().takeIf { !it.isNullOrBlank() }
    val depth = parts.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    return site to depth
}

fun formatDepthForLabel(depthM: Double): String {
    if (!depthM.isFinite()) return "0"
    val absV = abs(depthM)
    val s = if (absV in 0.001..1000.0) {
        String.format("%.6f", depthM).trimEnd('0').trimEnd('.')
    } else {
        String.format("%.6g", depthM)
    }
    return if (s.isBlank()) "0" else s
}

fun buildStratifiedLabel(site: String, depthM: Double): String {
    val s = site.trim().ifBlank { "未命名" }
    return "$s-${formatDepthForLabel(depthM)}"
}

fun resolveSiteAndDepthForPoint(label: String, site: String?, depthM: Double?): Pair<String?, Double?> {
    val fixedSite = site?.trim().takeIf { !it.isNullOrBlank() }
    val fixedDepth = depthM?.takeIf { it.isFinite() }
    if (fixedSite != null && fixedDepth != null) return fixedSite to fixedDepth

    val (parsedSite, parsedDepth) = parseSiteAndDepthFromLabel(label)
    return (fixedSite ?: parsedSite) to (fixedDepth ?: parsedDepth)
}

