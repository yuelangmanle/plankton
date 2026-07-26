package com.plankton.one102.ui

import android.net.Uri
import com.plankton.one102.domain.ImageConflictStrategy
import com.plankton.one102.domain.ImageCountConflict
import com.plankton.one102.domain.ImageCountKey
import com.plankton.one102.domain.resolveImageConflict

enum class ImageImportSource { Api1, Api2, ImageApi }

enum class ImageImportMode { Append, NewDataset }

enum class ImageTaskPhase { Queued, Compressing, Recognizing, Parsing, Ready, Failed, Canceled }

data class ImageImportTask(
    val imageIndex: Int,
    val phase: ImageTaskPhase = ImageTaskPhase.Queued,
    val detail: String = "等待处理",
)

enum class NameMatchKind { Exact, Alias, Fuzzy, Raw }

data class ImageImportSpecies(
    val nameRaw: String,
    val nameResolved: String,
    val count: Int,
    val countExpr: String? = null,
    val matchKind: NameMatchKind,
    val matchScore: Double? = null,
    val confidence: Double? = null,
    val sourceImageIndex: Int? = null,
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
    val conflicts: List<ImageCountConflict> = emptyList(),
)

/** Applies only the choices the person has already made; unresolved collisions remain blocking. */
fun ImageImportResult.applyConflictChoices(
    choices: Map<ImageCountKey, ImageConflictStrategy>,
): ImageImportResult {
    if (conflicts.isEmpty()) return this
    val points = linkedMapOf<String, MutableList<ImageImportSpecies>>()
    for (point in this.points) {
        points.getOrPut(point.label) { mutableListOf() }.addAll(point.species)
    }
    val unresolved = mutableListOf<ImageCountConflict>()
    for (conflict in conflicts) {
        val strategy = choices[conflict.key]
        if (strategy == null) {
            unresolved += conflict
            continue
        }
        val resolved = runCatching { resolveImageConflict(conflict, strategy) }
        if (resolved.isFailure) {
            unresolved += conflict
            continue
        }
        val count = resolved.getOrThrow()
        val representative = conflict.candidates.firstOrNull()
        if (representative != null) {
            points.getOrPut(conflict.key.pointLabel) { mutableListOf() } += ImageImportSpecies(
                nameRaw = representative.speciesName,
                nameResolved = representative.speciesName,
                count = count,
                matchKind = NameMatchKind.Raw,
                sourceImageIndex = representative.sourceImageIndex,
            )
        }
    }
    return copy(
        points = points.map { (label, rows) -> ImageImportPoint(label, rows) },
        conflicts = unresolved,
    )
}

data class ImageImportUiState(
    val datasetId: String? = null,
    val useApi1: Boolean = false,
    val useApi2: Boolean = false,
    val useImageApi: Boolean = true,
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
    val conflictChoices: Map<ImageCountKey, ImageConflictStrategy> = emptyMap(),
    val tasks: List<ImageImportTask> = emptyList(),
)
