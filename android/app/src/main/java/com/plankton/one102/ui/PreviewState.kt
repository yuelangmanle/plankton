package com.plankton.one102.ui

import com.plankton.one102.domain.CalcDiffReport
import com.plankton.one102.domain.DatasetCalc

data class PreviewCalcCheckState(
    val useApi1: Boolean = true,
    val useApi2: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val apiCalc1: DatasetCalc? = null,
    val apiCalc2: DatasetCalc? = null,
    val apiWarn1: List<String> = emptyList(),
    val apiWarn2: List<String> = emptyList(),
    val diffReport1: CalcDiffReport? = null,
    val diffReport2: CalcDiffReport? = null,
    val calcSource: CalcSource = CalcSource.Internal,
    val lastUpdatedAt: String? = null,
)

data class PreviewReportState(
    val useApi1: Boolean = true,
    val useApi2: Boolean = false,
    val samplingSite: String = "",
    val geoInfo: String = "",
    val climateSeason: String = "",
    val otherInfo: String = "",
    val busy: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val text1: String? = null,
    val text2: String? = null,
    val lastUpdatedAt: String? = null,
)

data class PreviewUiState(
    val datasetId: String? = null,
    val calcCheck: PreviewCalcCheckState = PreviewCalcCheckState(),
    val report: PreviewReportState = PreviewReportState(),
)
