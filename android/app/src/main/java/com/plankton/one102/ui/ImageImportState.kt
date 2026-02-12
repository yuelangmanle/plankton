package com.plankton.one102.ui

import android.net.Uri

enum class ImageImportSource { Api1, Api2, ImageApi }

enum class ImageImportMode { Append, NewDataset }

enum class NameMatchKind { Exact, Alias, Fuzzy, Raw }

data class ImageImportSpecies(
    val nameRaw: String,
    val nameResolved: String,
    val count: Int,
    val countExpr: String? = null,
    val matchKind: NameMatchKind,
    val matchScore: Double? = null,
    val confidence: Double? = null,
)

data class ImageImportPoint(
    val label: String,
    val species: List<ImageImportSpecies>,
)

data class ImageImportResult(
    val points: List<ImageImportPoint>,
    val warnings: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val droppedCount: Int = 0,
)

data class ImageImportUiState(
    val datasetId: String? = null,
    val useApi1: Boolean = true,
    val useApi2: Boolean = false,
    val useImageApi: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val api1: ImageImportResult? = null,
    val api2: ImageImportResult? = null,
    val apiImage: ImageImportResult? = null,
    val source: ImageImportSource = ImageImportSource.Api1,
    val mode: ImageImportMode = ImageImportMode.Append,
    val images: List<Uri> = emptyList(),
    val api1Unsupported: Boolean = false,
    val api2Unsupported: Boolean = false,
    val apiImageUnsupported: Boolean = false,
    val overwriteExisting: Boolean = true,
)
