package com.plankton.one102.ui

data class AssistantAiState(
    val question: String = "",
    val answer1: String = "",
    val answer2: String = "",
    val answer1Label: String = "",
    val answer2Label: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val lastUpdatedAt: String? = null,
    val taskLabel: String = "",
)

data class AssistantTraceState(
    val byPointId: Map<String, String> = emptyMap(),
    val busyPointId: String? = null,
    val error: String? = null,
    val includeTrace: Boolean = true,
)

data class AssistantFixState(
    val busy: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val apiTrace: String = "",
)

data class AssistantUiState(
    val datasetId: String? = null,
    val ai: AssistantAiState = AssistantAiState(),
    val trace: AssistantTraceState = AssistantTraceState(),
    val fix: AssistantFixState = AssistantFixState(),
    val apiCheckBusy: Boolean = false,
)
