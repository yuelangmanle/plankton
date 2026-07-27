package com.voiceassistant.text

internal data class UncertainSpan(
    val text: String,
    val reason: String,
)

internal data class ProposedCommandAction(
    val type: String,
    val pointId: String,
    val species: String,
    val value: Int,
)

/** A suggestion only. The receiving application must preview and confirm before writing data. */
internal data class CommandReviewModel(
    val originalText: String,
    val normalizedText: String,
    val uncertainSpans: List<UncertainSpan>,
    val proposedActions: List<ProposedCommandAction>,
    val unparsed: List<String>,
)
