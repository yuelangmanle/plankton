package com.plankton.one102.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.data.log.AppLogger
import com.plankton.one102.data.AppJson
import com.plankton.one102.data.api.CalcOutput
import com.plankton.one102.data.api.buildDatasetCalcFromOutput
import com.plankton.one102.domain.LibraryMeta
import com.plankton.one102.domain.nowIso
import kotlinx.coroutines.CancellationException
import java.io.File

class ExcelExportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val datasetId = inputData.getString(KEY_DATASET_ID) ?: return failure("缺少数据集 ID")
        val outputUriRaw = inputData.getString(KEY_OUTPUT_URI) ?: return failure("缺少输出路径")
        val table = inputData.getInt(KEY_TABLE, 0)
        if (table !in 1..4) return failure("导出类型不合法：$table")

        val app = applicationContext as PlanktonApplication
        val targetUri = Uri.parse(outputUriRaw)

        setProgress(workDataOf(KEY_PROGRESS to "读取数据…"))
        val dataset = app.datasetRepository.getById(datasetId) ?: return failure("数据集不存在")
        AppLogger.logInfo(
            applicationContext,
            "ExcelExportWorker",
            "开始导出表$table：dataset=$datasetId，点位=${dataset.points.size}，物种=${dataset.species.size}",
        )

        val latinOptions = ExportLatinOptions(
            lvl1 = inputData.getBoolean(KEY_LATIN_LVL1, false),
            lvl2 = inputData.getBoolean(KEY_LATIN_LVL2, false),
            lvl3 = inputData.getBoolean(KEY_LATIN_LVL3, false),
            lvl4 = inputData.getBoolean(KEY_LATIN_LVL4, false),
            lvl5 = inputData.getBoolean(KEY_LATIN_LVL5, false),
            species = inputData.getBoolean(KEY_LATIN_SPECIES, false),
        )

        val calcOverride = inputData.getString(KEY_CALC_OUTPUT_JSON)?.let { raw ->
            val output = AppJson.decodeFromString(CalcOutput.serializer(), raw)
            buildDatasetCalcFromOutput(dataset, output).calc
        }

        val libraryMeta = runCatching {
            val taxonomyVersion = app.taxonomyRepository.getBuiltinVersion()
            val taxonomyCustom = app.taxonomyOverrideRepository.getCustomMeta()
            val wetWeightVersion = app.wetWeightRepository.getBuiltinVersion()
            val wetWeightCustom = app.wetWeightRepository.getCustomMeta()
            val aliasMeta = app.aliasRepository.getMeta()
            LibraryMeta(
                taxonomyVersion = taxonomyVersion,
                taxonomyCustomCount = taxonomyCustom.count,
                taxonomyUpdatedAt = taxonomyCustom.updatedAt,
                wetWeightVersion = wetWeightVersion,
                wetWeightCustomCount = wetWeightCustom.count,
                wetWeightUpdatedAt = wetWeightCustom.updatedAt,
                aliasCount = aliasMeta.count,
                aliasUpdatedAt = aliasMeta.updatedAt,
            )
        }.getOrNull()

        val latinNameMap = runCatching {
            buildTaxonomyLatinOverrides(app.taxonomyOverrideRepository.getCustomEntries())
        }.getOrElse { emptyMap() }

        val tempDir = File(applicationContext.cacheDir, "exports").apply { mkdirs() }
        val tempFile = File.createTempFile("export_table$table-", ".xlsx", tempDir)

        try {
            setProgress(workDataOf(KEY_PROGRESS to "生成 Excel…"))
            val exporter = ExcelTemplateExporter(applicationContext)
            when (table) {
                1 -> exporter.exportTable1ToFile(dataset, latinOptions, calcOverride, libraryMeta, latinNameMap, tempFile)
                2 -> exporter.exportTable2ToFile(dataset, latinOptions, calcOverride, libraryMeta, latinNameMap, tempFile)
                3 -> exporter.exportSimpleCountTableToFile(dataset, libraryMeta, tempFile)
                4 -> exporter.exportSimpleTable2ToFile(dataset, libraryMeta, tempFile)
            }
            AppLogger.logInfo(
                applicationContext,
                "ExcelExportWorker",
                "Excel 生成完成：size=${tempFile.length()} bytes",
            )

            setProgress(workDataOf(KEY_PROGRESS to "写入文件…"))
            writeFileToSafAtomic(applicationContext, targetUri, tempFile)

            app.preferences.setLastExport(outputUriRaw, nowIso())
            AppLogger.logInfo(applicationContext, "ExcelExportWorker", "导出完成：$outputUriRaw")
            return Result.success(workDataOf(KEY_OUTPUT_URI to outputUriRaw))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppLogger.logError(applicationContext, "ExcelExportWorker", "导出表${table}失败", t)
            runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, targetUri) }
            return failure(t.message ?: t.toString())
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun failure(message: String): Result {
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    companion object {
        const val KEY_DATASET_ID = "dataset_id"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_TABLE = "table"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_CALC_OUTPUT_JSON = "calc_output_json"
        const val KEY_LATIN_LVL1 = "latin_lvl1"
        const val KEY_LATIN_LVL2 = "latin_lvl2"
        const val KEY_LATIN_LVL3 = "latin_lvl3"
        const val KEY_LATIN_LVL4 = "latin_lvl4"
        const val KEY_LATIN_LVL5 = "latin_lvl5"
        const val KEY_LATIN_SPECIES = "latin_species"
    }
}
