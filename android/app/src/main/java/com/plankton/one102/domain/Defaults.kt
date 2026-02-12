package com.plankton.one102.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

typealias Id = String

fun newId(): Id = UUID.randomUUID().toString()

fun nowIso(): String = Instant.now().toString()

@Serializable
data class ApiConfig(
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

@Serializable
data class ApiProfile(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

@Serializable
data class VoiceAssistantConfig(
    val engine: String = "sherpa_streaming",
    val modelId: String = "small-q8_0",
    val sherpaStreamingModel: String = "zipformer_zh",
    val sherpaOfflineModel: String = "sense_voice",
    val decodeMode: String = "fast",
    val useGpu: Boolean = true,
    val autoStrategy: Boolean = true,
    val useMultithread: Boolean = true,
    val threadCount: Int = 0,
    val sherpaProvider: String = "nnapi",
)

@Serializable
enum class UiDensityMode {
    Standard,
    Compact,
}

@Serializable
enum class DisplayRefreshMode {
    Adaptive,
    Hz60,
    Hz90,
    Hz120,
}

@Serializable
enum class ImportTemplateType {
    SpeciesLibrary,
}

@Serializable
data class ImportTemplatePreset(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val mapping: String = "",
    val type: ImportTemplateType = ImportTemplateType.SpeciesLibrary,
    val sheetIndex: Int = 0,
    val hasHeader: Boolean = true,
    val headerRow: Int = 1,
    val startRow: Int = 2,
    val nameCnColumn: String = "A",
    val latinColumn: String = "B",
    val wetWeightColumn: String = "C",
    val lvl1Column: String = "D",
    val lvl2Column: String = "E",
    val lvl3Column: String = "F",
    val lvl4Column: String = "G",
    val lvl5Column: String = "H",
    val aliasColumns: String = "I",
)

@Serializable
data class VoiceCommandMacro(
    val id: String = "",
    val name: String = "",
    val command: String = "",
    val runAsAsk: Boolean = false,
)

@Serializable
data class Settings(
    val defaultVOrigL: Double = 20.0,
    val api1: ApiConfig = ApiConfig(name = "API 1"),
    val api2: ApiConfig = ApiConfig(name = "API 2"),
    val imageApi: ApiConfig = ApiConfig(name = "图片 API"),
    val apiProfiles: List<ApiProfile> = emptyList(),
    val voiceAssistant: VoiceAssistantConfig = VoiceAssistantConfig(),
    val voiceAssistantOverrideEnabled: Boolean = true,
    val glassOpacity: Float = 1f,
    val aiAssistantEnabled: Boolean = false,
    val aiUiHidden: Boolean = false,
    val aiUseDualApi: Boolean = true,
    val autoMatchWriteToDb: Boolean = false,
    val speciesEditWriteToDb: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val hapticsStrength: Float = 0.6f,
    val exportLatinLvl1: Boolean = false,
    val exportLatinLvl2: Boolean = false,
    val exportLatinLvl3: Boolean = false,
    val exportLatinLvl4: Boolean = false,
    val exportLatinLvl5: Boolean = false,
    val exportLatinSpecies: Boolean = false,
    val uiMode: UiMode = UiMode.Auto,
    val uiDensityMode: UiDensityMode = UiDensityMode.Standard,
    val displayRefreshMode: DisplayRefreshMode = DisplayRefreshMode.Adaptive,
    val glassEffectEnabled: Boolean = true,
    val blurEnabled: Boolean = true,
    val activeWetWeightLibraryId: String = DEFAULT_WET_WEIGHT_LIBRARY_ID,
    val fieldModeAutoContinue: Boolean = true,
    val fieldModeAccidentalTouchGuard: Boolean = true,
    val activeImportTemplateId: String = "table1-default",
    val importTemplates: List<ImportTemplatePreset> = listOf(
        ImportTemplatePreset(
            id = "table1-default",
            name = "表1规范模板",
            description = "用于“物种库规范导入”（中文名/拉丁名/湿重/五级分类/别名）",
            mapping = "A=中文名, B=拉丁名, C=平均湿重, D=大类, E=纲, F=目, G=科, H=属, I=别名",
            type = ImportTemplateType.SpeciesLibrary,
            sheetIndex = 0,
            hasHeader = true,
            headerRow = 1,
            startRow = 2,
            nameCnColumn = "A",
            latinColumn = "B",
            wetWeightColumn = "C",
            lvl1Column = "D",
            lvl2Column = "E",
            lvl3Column = "F",
            lvl4Column = "G",
            lvl5Column = "H",
            aliasColumns = "I",
        ),
        ImportTemplatePreset(
            id = "simple-default",
            name = "简表模板（四大类+物种）",
            description = "用于简表导入，四大类 + 物种名称",
            mapping = "A=大类, B=物种名, C=平均湿重(可空), I=别名(可空)",
            type = ImportTemplateType.SpeciesLibrary,
            sheetIndex = 0,
            hasHeader = true,
            headerRow = 1,
            startRow = 2,
            nameCnColumn = "B",
            latinColumn = "",
            wetWeightColumn = "C",
            lvl1Column = "A",
            lvl2Column = "",
            lvl3Column = "",
            lvl4Column = "",
            lvl5Column = "",
            aliasColumns = "I",
        ),
    ),
    val voiceCommandMacros: List<VoiceCommandMacro> = listOf(
        VoiceCommandMacro(
            id = "macro-export-table1",
            name = "清零当前点位并导出表1",
            command = "清空当前点位计数，然后导出表1",
            runAsAsk = false,
        ),
        VoiceCommandMacro(
            id = "macro-check-missing",
            name = "检查缺失项",
            command = "检查当前数据集缺失湿重、缺失分类和异常点位",
            runAsAsk = true,
        ),
    ),
)

val DEFAULT_SETTINGS = Settings()

@Serializable
enum class UiMode {
    Auto,
    Phone,
    Tablet,
}

@Serializable
data class Taxonomy(
    val lvl1: String = "",
    val lvl2: String = "",
    val lvl3: String = "",
    val lvl4: String = "",
    val lvl5: String = "",
)

val DEFAULT_TAXONOMY = Taxonomy()
