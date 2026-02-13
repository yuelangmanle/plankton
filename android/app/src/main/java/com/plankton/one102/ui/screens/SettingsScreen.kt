package com.plankton.one102.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.BuildConfig
import com.plankton.one102.data.log.AppLogger
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.ApiProfile
import com.plankton.one102.domain.DisplayRefreshMode
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.UiDensityMode
import com.plankton.one102.domain.UiMode
import com.plankton.one102.ui.AppInfo
import com.plankton.one102.ui.HapticKind
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.components.GlassPrefs
import com.plankton.one102.ui.components.LocalGlassPrefs
import com.plankton.one102.ui.performHaptic
import kotlinx.coroutines.launch
import java.util.UUID

private enum class AdvancedSettingsTab {
    ApiAi,
    DataOps,
    UiDisplay,
    About,
}

private enum class QuickApiTarget {
    Api1,
    Api2,
    ImageApi,
}

private const val PROJECT_REPOSITORY_URL = "https://github.com/yuelangmanle/plankton"

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    padding: PaddingValues,
    onOpenAliases: () -> Unit = {},
    onOpenAiCache: () -> Unit = {},
    onOpenDocs: () -> Unit = {},
    onOpenOps: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf(settings) }
    var defaultVOrigText by remember { mutableStateOf(settings.defaultVOrigL.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var profileEditorOpen by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf(ApiProfileDraft()) }
    var profileDeleteTarget by remember { mutableStateOf<ApiProfile?>(null) }
    var changelogExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var advancedTab by remember { mutableStateOf(AdvancedSettingsTab.ApiAi) }
    var quickTarget by remember { mutableStateOf(QuickApiTarget.Api1) }
    var quickMenuExpanded by remember { mutableStateOf(false) }
    var quickProfileId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val logExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = runCatching { AppLogger.exportLogs(context, uri) }
            message = res.fold(
                onSuccess = { "已导出日志包" },
                onFailure = { "导出日志失败：${it.message}" },
            )
        }
    }

    LaunchedEffect(settings) {
        if (!dirty) {
            draft = settings
            defaultVOrigText = settings.defaultVOrigL.toString()
        } else {
            // Avoid wiping unsaved edits when settings update (e.g. uiMode applied immediately).
                draft = draft.copy(
                    uiMode = settings.uiMode,
                    uiDensityMode = settings.uiDensityMode,
                    displayRefreshMode = settings.displayRefreshMode,
                    glassEffectEnabled = settings.glassEffectEnabled,
                    blurEnabled = settings.blurEnabled,
                    glassOpacity = settings.glassOpacity,
                speciesEditWriteToDb = settings.speciesEditWriteToDb,
            )
        }
    }
    LaunchedEffect(draft.apiProfiles) {
        if (draft.apiProfiles.isEmpty()) {
            quickProfileId = ""
        } else if (draft.apiProfiles.none { it.id == quickProfileId }) {
            quickProfileId = draft.apiProfiles.first().id
        }
    }
    val scrollState = rememberScrollState()

    CompositionLocalProvider(
        LocalGlassPrefs provides GlassPrefs(
            enabled = settings.glassEffectEnabled,
            blur = settings.blurEnabled && !scrollState.isScrollInProgress,
            opacity = settings.glassOpacity,
        ),
    ) {
        GlassBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text("设置", style = MaterialTheme.typography.titleLarge)

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("设置分层", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (advancedExpanded) "当前：显示全部设置项" else "当前：仅显示常用设置（减少滚动）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                    OutlinedButton(onClick = { advancedExpanded = !advancedExpanded }) {
                        Text(if (advancedExpanded) "切到常用" else "展开高级")
                    }
                }
            }
            
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("视觉效果", style = MaterialTheme.typography.titleMedium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("启用毛玻璃效果", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.glassEffectEnabled,
                            onCheckedChange = { v ->
                                val next = settings.copy(glassEffectEnabled = v)
                                viewModel.saveSettings(next)
                                draft = draft.copy(glassEffectEnabled = v)
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("启用高斯模糊", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            enabled = settings.glassEffectEnabled,
                            checked = settings.blurEnabled,
                            onCheckedChange = { v ->
                                val next = settings.copy(blurEnabled = v)
                                viewModel.saveSettings(next)
                                draft = draft.copy(blurEnabled = v)
                            },
                        )
                    }

                    Text(
                        "透明度：${(draft.glassOpacity.coerceIn(0.5f, 1.5f) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Slider(
                        enabled = settings.glassEffectEnabled,
                        value = draft.glassOpacity.coerceIn(0.5f, 1.5f),
                        onValueChange = { v -> draft = draft.copy(glassOpacity = v) },
                        onValueChangeFinished = {
                            viewModel.saveSettings(
                                settings.copy(glassOpacity = draft.glassOpacity.coerceIn(0.5f, 1.5f)),
                            )
                        },
                        valueRange = 0.5f..1.5f,
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("默认原水体积（L）", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = defaultVOrigText,
                        onValueChange = {
                            defaultVOrigText = it
                            dirty = true
                        },
                        singleLine = true,
                        placeholder = { Text("例如：20") },
                    )
                    Text(
                        "说明：新建采样点时默认填入该值；已有点位仍可单独修改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("常用 API 快速切换", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "从已保存配置里一键切到 API1 / API2 / 图片 API（不进入完整编辑）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    if (draft.apiProfiles.isEmpty()) {
                        Text(
                            "还没有已保存配置。可在“展开高级 → API/AI”里先保存一个配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            val targets = listOf(
                                QuickApiTarget.Api1 to "API1",
                                QuickApiTarget.Api2 to "API2",
                                QuickApiTarget.ImageApi to "图片API",
                            )
                            targets.forEach { (target, label) ->
                                val selected = quickTarget == target
                                if (selected) {
                                    Button(onClick = { quickTarget = target }, modifier = Modifier.weight(1f)) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { quickTarget = target }, modifier = Modifier.weight(1f)) { Text(label) }
                                }
                            }
                        }

                        val selectedProfile = draft.apiProfiles.firstOrNull { it.id == quickProfileId }
                        OutlinedButton(
                            onClick = { quickMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = selectedProfile?.name?.ifBlank { selectedProfile.model.ifBlank { "未命名配置" } }
                                    ?: "选择配置",
                            )
                        }
                        DropdownMenu(expanded = quickMenuExpanded, onDismissRequest = { quickMenuExpanded = false }) {
                            draft.apiProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name.ifBlank { profile.model.ifBlank { "未命名配置" } }) },
                                    onClick = {
                                        quickProfileId = profile.id
                                        quickMenuExpanded = false
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val profile = draft.apiProfiles.firstOrNull { it.id == quickProfileId } ?: return@Button
                                val nextConfig = profile.toConfig(
                                    nameFallback = when (quickTarget) {
                                        QuickApiTarget.Api1 -> "API 1"
                                        QuickApiTarget.Api2 -> "API 2"
                                        QuickApiTarget.ImageApi -> "图片识别 API"
                                    },
                                )
                                val nextSettings = when (quickTarget) {
                                    QuickApiTarget.Api1 -> settings.copy(api1 = nextConfig)
                                    QuickApiTarget.Api2 -> settings.copy(api2 = nextConfig)
                                    QuickApiTarget.ImageApi -> settings.copy(imageApi = nextConfig)
                                }
                                viewModel.saveSettings(nextSettings)
                                draft = when (quickTarget) {
                                    QuickApiTarget.Api1 -> draft.copy(api1 = nextConfig)
                                    QuickApiTarget.Api2 -> draft.copy(api2 = nextConfig)
                                    QuickApiTarget.ImageApi -> draft.copy(imageApi = nextConfig)
                                }
                                message = "已切换到 ${profile.name.ifBlank { profile.model.ifBlank { "所选配置" } }}"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("应用到当前目标") }
                    }
                }
            }

            if (advancedExpanded) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("高级设置分组", style = MaterialTheme.typography.titleMedium)
                    val tabs = listOf(
                        AdvancedSettingsTab.ApiAi to "API / AI",
                        AdvancedSettingsTab.DataOps to "数据 / 任务",
                        AdvancedSettingsTab.UiDisplay to "交互 / 显示",
                        AdvancedSettingsTab.About to "文档 / 关于",
                    )
                    tabs.chunked(2).forEach { rowTabs ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            rowTabs.forEach { (tab, label) ->
                                val selected = advancedTab == tab
                                if (selected) {
                                    Button(
                                        onClick = { advancedTab = tab },
                                        modifier = Modifier.weight(1f),
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { advancedTab = tab },
                                        modifier = Modifier.weight(1f),
                                    ) { Text(label) }
                                }
                            }
                            if (rowTabs.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text(
                        "当前分组：${
                            when (advancedTab) {
                                AdvancedSettingsTab.ApiAi -> "API 与 AI"
                                AdvancedSettingsTab.DataOps -> "数据与任务"
                                AdvancedSettingsTab.UiDisplay -> "交互与显示"
                                AdvancedSettingsTab.About -> "文档与关于"
                            }
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
            if (advancedTab == AdvancedSettingsTab.ApiAi) {
            ApiBlock(
                title = "API 1",
                hint = "用于湿重辅助查询（需“有理有据”，展示依据与可核对来源）。",
                value = draft.api1,
                onChange = {
                    dirty = true
                    draft = draft.copy(api1 = it)
                },
            )
            ApiBlock(
                title = "API 2",
                hint = "用于交叉验证（同样要求提供依据与来源）。",
                value = draft.api2,
                onChange = {
                    dirty = true
                    draft = draft.copy(api2 = it)
                },
            )
            ApiBlock(
                title = "图片识别 API",
                hint = "用于“图片识别导入（AI）”，可配置更大参数的视觉模型。",
                value = draft.imageApi,
                onChange = {
                    dirty = true
                    draft = draft.copy(imageApi = it)
                },
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("API 管理与快速切换", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "保存多个 API 配置，快速切换到 API1/2/图片 API。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                profileDraft = ApiProfileDraft.fromConfig(draft.api1)
                                profileEditorOpen = true
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("保存 API1 为配置") }
                        OutlinedButton(
                            onClick = {
                                profileDraft = ApiProfileDraft.fromConfig(draft.api2)
                                profileEditorOpen = true
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("保存 API2 为配置") }
                    }
                    OutlinedButton(
                        onClick = {
                            profileDraft = ApiProfileDraft.fromConfig(draft.imageApi)
                            profileEditorOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存图片 API 为配置") }

                    Button(
                        onClick = {
                            profileDraft = ApiProfileDraft()
                            profileEditorOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("新增配置") }

                    if (draft.apiProfiles.isEmpty()) {
                        Text(
                            "暂无已保存的配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (profile in draft.apiProfiles) {
                                val usedApi1 = draft.api1.matchesProfile(profile)
                                val usedApi2 = draft.api2.matchesProfile(profile)
                                val usedImage = draft.imageApi.matchesProfile(profile)
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(profile.name.ifBlank { "未命名配置" }, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Model：${profile.model.ifBlank { "（空）" }} · Base URL：${profile.baseUrl.ifBlank { "（空）" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        )
                                        if (usedApi1 || usedApi2 || usedImage) {
                                            val tags = buildList {
                                                if (usedApi1) add("API1")
                                                if (usedApi2) add("API2")
                                                if (usedImage) add("图片 API")
                                            }
                                            Text("已用于：${tags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = {
                                                    dirty = true
                                                    draft = draft.copy(api1 = profile.toConfig(nameFallback = "API 1"))
                                                    message = "已应用到 API1"
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) { Text("应用到 API1") }
                                            OutlinedButton(
                                                onClick = {
                                                    dirty = true
                                                    draft = draft.copy(api2 = profile.toConfig(nameFallback = "API 2"))
                                                    message = "已应用到 API2"
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) { Text("应用到 API2") }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                dirty = true
                                                draft = draft.copy(imageApi = profile.toConfig(nameFallback = "图片 API"))
                                                message = "已应用到 图片API"
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("应用到 图片API") }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            TextButton(
                                                onClick = {
                                                    profileDraft = ApiProfileDraft.fromProfile(profile)
                                                    profileEditorOpen = true
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) { Text("编辑") }
                                            TextButton(
                                                onClick = { profileDeleteTarget = profile },
                                                modifier = Modifier.weight(1f),
                                            ) { Text("删除") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI 功能入口", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "用于离线使用或演示场景。开启后会隐藏 AI 相关入口，并临时关闭 AI 辅助。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("隐藏 AI 功能入口", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.aiUiHidden,
                            onCheckedChange = { v ->
                                val next = settings.copy(
                                    aiUiHidden = v,
                                    aiAssistantEnabled = if (v) false else settings.aiAssistantEnabled,
                                )
                                viewModel.saveSettings(next)
                                draft = draft.copy(aiUiHidden = v, aiAssistantEnabled = next.aiAssistantEnabled)
                                message = if (v) "已隐藏 AI 功能入口" else "已显示 AI 功能入口"
                            },
                        )
                    }
                }
            }

            }

            if (advancedTab == AdvancedSettingsTab.DataOps) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("一键补齐/缺失项处理", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "控制“物种页一键补齐”和“助手页补齐缺失项”的写库行为。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("补齐结果写入本机库", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.autoMatchWriteToDb,
                            onCheckedChange = { v ->
                                viewModel.saveSettings(settings.copy(autoMatchWriteToDb = v))
                                draft = draft.copy(autoMatchWriteToDb = v)
                                message = if (v) "已开启：补齐结果会写入本机库" else "已关闭：补齐结果仅用于当前数据集"
                            },
                        )
                    }
                    Text(
                        if (settings.autoMatchWriteToDb) {
                            "开启后：AI 补齐到的分类/湿重会写入“自定义分类/湿重库”，方便下次直接从本机库补齐；清空本次匹配不会回滚写库。"
                        } else {
                            "关闭后：补齐结果只写入当前数据集；不影响本机库。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("物种编辑写入本机库", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.speciesEditWriteToDb,
                            onCheckedChange = { v ->
                                viewModel.saveSettings(settings.copy(speciesEditWriteToDb = v))
                                draft = draft.copy(speciesEditWriteToDb = v)
                                message = if (v) "已开启：物种编辑会同步写入本机库" else "已关闭：物种编辑仅更新当前数据集"
                            },
                        )
                    }
                    Text(
                        if (settings.speciesEditWriteToDb) {
                            "开启后：在物种页/编辑页手动修改分类或湿重会写入自定义库。"
                        } else {
                            "关闭后：手动编辑仅影响当前数据集，不会改动本机库。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
            }

            if (advancedTab == AdvancedSettingsTab.UiDisplay) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("震动反馈", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "用于加减计数、添加物种等操作的触感反馈。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("开启震动", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.hapticsEnabled,
                            onCheckedChange = { v ->
                                viewModel.saveSettings(settings.copy(hapticsEnabled = v))
                                draft = draft.copy(hapticsEnabled = v)
                                message = if (v) "已开启震动反馈" else "已关闭震动反馈"
                            },
                        )
                    }

                    Text(
                        "强度：${(draft.hapticsStrength.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Slider(
                        enabled = settings.hapticsEnabled,
                        value = draft.hapticsStrength.coerceIn(0f, 1f),
                        onValueChange = { v -> draft = draft.copy(hapticsStrength = v) },
                        onValueChangeFinished = {
                            viewModel.saveSettings(settings.copy(hapticsStrength = draft.hapticsStrength.coerceIn(0f, 1f)))
                            if (settings.hapticsEnabled) {
                                performHaptic(context, settings.copy(hapticsStrength = draft.hapticsStrength.coerceIn(0f, 1f)), HapticKind.Success)
                            }
                        },
                        valueRange = 0f..1f,
                    )

                    OutlinedButton(
                        enabled = settings.hapticsEnabled,
                        onClick = { performHaptic(context, settings.copy(hapticsStrength = draft.hapticsStrength.coerceIn(0f, 1f)), HapticKind.Success) },
                        modifier = Modifier.fillMaxWidth(),
                ) { Text("测试震动") }
                }
            }
            }

            if (advancedTab == AdvancedSettingsTab.UiDisplay) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("界面模式（平板适配）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "自动：根据屏幕宽度自动切换；平板会使用侧边栏导航。若自动识别不准确，可手动强制切换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            UiMode.Auto to "自动",
                            UiMode.Phone to "手机",
                            UiMode.Tablet to "平板",
                        )
                        for ((mode, label) in modes) {
                            val selected = settings.uiMode == mode
                            if (selected) {
                                Button(onClick = { viewModel.saveSettings(settings.copy(uiMode = mode)) }, modifier = Modifier.weight(1f)) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.saveSettings(settings.copy(uiMode = mode))
                                        // Keep draft in sync for the final "保存" button, without marking dirty.
                                        draft = draft.copy(uiMode = mode)
                                        message = "界面模式已切换为：$label"
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
            }

            if (advancedTab == AdvancedSettingsTab.UiDisplay) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("显示密度（标准/紧凑）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "标准：更大的点击区域；紧凑：更高信息密度，适合现场高频录入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            UiDensityMode.Standard to "标准",
                            UiDensityMode.Compact to "紧凑",
                        )
                        for ((mode, label) in modes) {
                            val selected = settings.uiDensityMode == mode
                            if (selected) {
                                Button(
                                    onClick = { viewModel.saveSettings(settings.copy(uiDensityMode = mode)) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.saveSettings(settings.copy(uiDensityMode = mode))
                                        draft = draft.copy(uiDensityMode = mode)
                                        message = "显示密度已切换为：$label"
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
            }

            if (advancedTab == AdvancedSettingsTab.UiDisplay) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("屏幕刷新率", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "自适应由系统按场景调度；高刷优先建议选择 120Hz。若设备不支持某档位，会自动回退到最接近模式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            DisplayRefreshMode.Adaptive to "自适应",
                            DisplayRefreshMode.Hz60 to "60Hz",
                            DisplayRefreshMode.Hz90 to "90Hz",
                            DisplayRefreshMode.Hz120 to "120Hz",
                        )
                        for ((mode, label) in modes) {
                            val selected = settings.displayRefreshMode == mode
                            if (selected) {
                                Button(
                                    onClick = { viewModel.saveSettings(settings.copy(displayRefreshMode = mode)) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.saveSettings(settings.copy(displayRefreshMode = mode))
                                        draft = draft.copy(displayRefreshMode = mode)
                                        message = "刷新率模式已切换为：$label"
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
            }

            if (advancedTab == AdvancedSettingsTab.DataOps) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("数据与缓存", style = MaterialTheme.typography.titleMedium)
                    val showAiCache = !settings.aiUiHidden
                    Text(
                        if (showAiCache) {
                            "别名用于把常用简写/旧名映射到标准名；AI 缓存用于复用结构化结果并支持追溯。"
                        } else {
                            "别名用于把常用简写/旧名映射到标准名。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onOpenAliases, modifier = Modifier.weight(1f)) { Text("别名管理") }
                        if (showAiCache) {
                            OutlinedButton(onClick = onOpenAiCache, modifier = Modifier.weight(1f)) { Text("AI 缓存") }
                        }
                    }
                }
            }
            }

            if (advancedTab == AdvancedSettingsTab.DataOps) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("日志与诊断", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "用于收集导出/解析等关键路径错误日志，便于排查问题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { logExportLauncher.launch("plankton-logs.zip") },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出日志包") }
                        OutlinedButton(
                            onClick = {
                                AppLogger.clear(context)
                                message = "已清空日志"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("清空日志") }
                    }
                }
            }
            }

            if (advancedTab == AdvancedSettingsTab.About) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("软件信息", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "版本：${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("制作人：${AppInfo.PRODUCER}", style = MaterialTheme.typography.bodyMedium)
                    Text("单位：${AppInfo.ORG}", style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer {
                        Text(
                            "GitHub：$PROJECT_REPOSITORY_URL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        onClick = {
                            runCatching { uriHandler.openUri(PROJECT_REPOSITORY_URL) }
                                .onFailure { message = "Open link failed: ${it.message}" }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open GitHub Project")
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("使用流程与核心算法", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "内置说明：从录入到导出，及核心公式/指标含义（离线可用）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    OutlinedButton(onClick = { showGuide = true }, modifier = Modifier.fillMaxWidth()) { Text("查看说明") }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("项目文档", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "包含项目书、当前程序情况、语音助手项目书与提示词（离线可用）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    OutlinedButton(onClick = onOpenDocs, modifier = Modifier.fillMaxWidth()) { Text("查看项目书") }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("效率/架构优化中心", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "集中入口：导入模板中心、异常数据工作台、现场录入模式、语音指令宏、导出任务中心、数据质量趋势。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    OutlinedButton(onClick = onOpenOps, modifier = Modifier.fillMaxWidth()) { Text("打开运营中心") }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("更新日志", style = MaterialTheme.typography.titleMedium)
                    if (changelogExpanded) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(AppInfo.changeLog) { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        for (line in AppInfo.changeLog.take(10)) {
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { changelogExpanded = !changelogExpanded }) {
                        Text(if (changelogExpanded) "收起" else "展开全部")
                    }
                }
            }
            }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val v = defaultVOrigText.trim().toDoubleOrNull()
                        if (v == null || !v.isFinite() || v <= 0) {
                            message = "默认原水体积需为大于 0 的数值。"
                            return@Button
                        }
                        val next = draft.copy(defaultVOrigL = v)
                        viewModel.saveSettings(next)
                        dirty = false
                        message = "已保存"
                    },
                ) { Text("保存") }
            }

            if (message != null) {
                Text(message!!, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    }

    if (showGuide) {
        GuideDialog(onClose = { showGuide = false })
    }

    if (profileEditorOpen) {
        ApiProfileEditorDialog(
            draft = profileDraft,
            onDismiss = { profileEditorOpen = false },
            onConfirm = { next ->
                val updated = draft.apiProfiles.toMutableList()
                val idx = updated.indexOfFirst { it.id == next.id }
                if (idx >= 0) {
                    updated[idx] = next
                } else {
                    updated.add(next)
                }
                draft = draft.copy(apiProfiles = updated)
                dirty = true
                message = "已保存配置"
                profileEditorOpen = false
            },
        )
    }

    if (profileDeleteTarget != null) {
        val target = profileDeleteTarget
        AlertDialog(
            onDismissRequest = { profileDeleteTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toRemove = target ?: return@TextButton
                        draft = draft.copy(apiProfiles = draft.apiProfiles.filterNot { it.id == toRemove.id })
                        dirty = true
                        message = "已删除配置"
                        profileDeleteTarget = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { profileDeleteTarget = null }) { Text("取消") } },
            title = { Text("删除配置") },
            text = { Text("确定删除该配置吗？") },
        )
    }
}

@Composable
private fun ApiBlock(
    title: String,
    hint: String,
    value: ApiConfig,
    onChange: (ApiConfig) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

            OutlinedTextField(
                value = value.name,
                onValueChange = { onChange(value.copy(name = it)) },
                singleLine = true,
                label = { Text("名称") },
            )
            OutlinedTextField(
                value = value.model,
                onValueChange = { onChange(value.copy(model = it)) },
                singleLine = true,
                label = { Text("Model") },
            )
            OutlinedTextField(
                value = value.baseUrl,
                onValueChange = { onChange(value.copy(baseUrl = it)) },
                singleLine = true,
                label = { Text("Base URL（OpenAI 兼容）") },
                placeholder = { Text("例如：https://api.openai.com/v1（或直接填完整 /v1/chat/completions）") },
            )
            OutlinedTextField(
                value = value.apiKey,
                onValueChange = { onChange(value.copy(apiKey = it)) },
                singleLine = true,
                label = { Text("API Key") },
            )
        }
    }
}

private data class ApiProfileDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    fun toProfile(): ApiProfile = ApiProfile(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
    )

    companion object {
        fun fromProfile(profile: ApiProfile): ApiProfileDraft = ApiProfileDraft(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            apiKey = profile.apiKey,
            model = profile.model,
        )

        fun fromConfig(config: ApiConfig): ApiProfileDraft = ApiProfileDraft(
            id = UUID.randomUUID().toString(),
            name = config.name,
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
        )
    }
}

private fun ApiProfile.toConfig(nameFallback: String): ApiConfig = ApiConfig(
    name = name.ifBlank { nameFallback },
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
)

private fun ApiConfig.matchesProfile(profile: ApiProfile): Boolean {
    return baseUrl.trim() == profile.baseUrl.trim() &&
        model.trim() == profile.model.trim() &&
        apiKey.trim() == profile.apiKey.trim()
}

@Composable
private fun ApiProfileEditorDialog(
    draft: ApiProfileDraft,
    onDismiss: () -> Unit,
    onConfirm: (ApiProfile) -> Unit,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var baseUrl by remember(draft.id) { mutableStateOf(draft.baseUrl) }
    var apiKey by remember(draft.id) { mutableStateOf(draft.apiKey) }
    var model by remember(draft.id) { mutableStateOf(draft.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = name.trim().ifBlank { model.trim().ifBlank { "API 配置" } }
                    onConfirm(
                        ApiProfile(
                            id = draft.id,
                            name = finalName,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("API 配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("名称") },
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    singleLine = true,
                    label = { Text("Model") },
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    singleLine = true,
                    label = { Text("Base URL（OpenAI 兼容）") },
                    placeholder = { Text("例如：https://api.openai.com/v1") },
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    singleLine = true,
                    label = { Text("API Key") },
                )
            }
        },
    )
}

@Composable
private fun GuideDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("使用流程与核心算法", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("关闭") }
                }

                GlassCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Text("一、推荐使用流程（从录入到导出）", style = MaterialTheme.typography.titleMedium) }
                        item {
                            Text(
                                "1) 采样点：录入名称、浓缩体积 Vc（mL）、原水体积 Vo（L）。支持 Excel 导入与批量复制参数；若有垂向分层样品，可开启“分层计算”并用 1-0.3 这类格式表示站位-水深(m)。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        item { Text("2) 物种与计数：按采样点录入每个物种计数（+/- 或点计数框手动输入）。物种信息建议用“编辑信息”进入全屏编辑。", style = MaterialTheme.typography.bodySmall) }
                        item { Text("3) 一键补齐：对缺失分类/湿重的物种，先查本机库（内置分类/湿重库 + 自定义覆盖），仍缺失再调用 API；会记录本次写入，可一键撤销。", style = MaterialTheme.typography.bodySmall) }
                        item { Text("4) 助手：先做规则检查与点位追溯（可复制计算过程）；需要时一键修复缺失项。", style = MaterialTheme.typography.bodySmall) }
                        item { Text("5) 导出：生成多 Sheet 的 .xlsx（表1/表2），可预览与分享；图表用于快速核对。", style = MaterialTheme.typography.bodySmall) }

                        item { Text("二、全局助手与询问 AI", style = MaterialTheme.typography.titleMedium) }
                        item {
                            Text(
                                "全局助手悬浮窗：支持自然语言指令操作（打开设置/检测 API/AI 分析/点位追溯/打开表1表2预览/导出表格/打开数据库树与思维导图/切换手机或平板模式等）。未识别的指令会转入“批量录入”解析。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        item {
                            Text(
                                "询问 AI：可查询当前数据集、内置数据库、项目文档与更新记录；点击询问会直接调用 API，失败后自动检测。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        item { Text("三、数据结构与流转", style = MaterialTheme.typography.titleMedium) }
                        item { Text("数据集 Dataset = points（采样点）+ species（物种：中文名/拉丁名/分类/湿重/按点位计数映射）。", style = MaterialTheme.typography.bodySmall) }
                        item { Text("补齐/查询优先级：分类=自定义覆盖 > 内置库；湿重=内置库优先，缺失再查当前自定义库 > API；并支持别名映射与 AI 结构化结果缓存（可追溯）。", style = MaterialTheme.typography.bodySmall) }

                        item { Text("四、核心公式（与指南一致）", style = MaterialTheme.typography.titleMedium) }
                        item {
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("密度：density(ind/L) = (n / 1.3) * (Vc / Vo)", style = MaterialTheme.typography.bodySmall)
                                    Text("生物量：biomass(mg/L) = density * 湿重(mg/个)", style = MaterialTheme.typography.bodySmall)
                                    Text("Shannon-Wiener：H' = - Σ (p_i * ln p_i)，p_i = n_i / N", style = MaterialTheme.typography.bodySmall)
                                    Text("Pielou：J = H' / ln S（S 为该点位物种数）", style = MaterialTheme.typography.bodySmall)
                                    Text("Margalef：D = (S - 1) / ln N", style = MaterialTheme.typography.bodySmall)
                                    Text("优势度：Y_i = (n_i / N) * f_i，f_i = 出现点数 / 总点数；Y > 0.02 视为优势种", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "分层汇总：按水深范围将样品归类到上/中/下层；同一站位同一水层会先合并原始个体数，再重算 H'/D/J 与优势种（多样性指数不做平均）。",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        item { Text("五、备份与迁移（概念）", style = MaterialTheme.typography.titleMedium) }
                        item { Text("历史数据集页支持导出/导入与一键分享备份；备份包含：数据集 + 设置 + 自定义分类/湿重 + 别名 + AI 缓存。", style = MaterialTheme.typography.bodySmall) }
                        item {
                            Text(
                                "提示：想看某个点位的完整计算过程，可到“助手 → 点位追溯”生成可复制的逐步说明。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            )
                        }
                        item { Text("五、全局助手与询问 AI", style = MaterialTheme.typography.titleMedium) }
                        item {
                            Text(
                                "全局助手支持“功能操作 + 询问”：可打开设置/导出/助手等页面，检测 API、触发 AI 分析当前数据、生成点位追溯，并可一键导出表1/表2。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        item {
                            Text(
                                "询问功能会结合当前数据集、内置数据库以及项目书/更新日志，回答“怎么用/做了什么/迭代情况”等问题；与助手页的“询问 AI”共享能力与结果。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
