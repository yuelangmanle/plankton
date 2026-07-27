package com.plankton.one102.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.plankton.one102.data.api.ChatCompletionClient
import com.plankton.one102.domain.ApiCapability
import com.plankton.one102.domain.ApiConnection
import com.plankton.one102.domain.ApiRoute
import com.plankton.one102.domain.ApiRouteMode
import com.plankton.one102.domain.ApiTaskType
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.apiConnectionsCompat
import com.plankton.one102.domain.apiRoutesCompat
import com.plankton.one102.domain.label
import com.plankton.one102.domain.migratedApiCenter
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.toConfig
import com.plankton.one102.domain.ApiProviderPresets
import com.plankton.one102.domain.ApiProviderGroup
import com.plankton.one102.domain.isMimoTokenPlanUrl
import com.plankton.one102.domain.recommendedModel
import com.plankton.one102.domain.stableModelIds
import com.plankton.one102.domain.suggestedCapabilities
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassCard
import kotlinx.coroutines.launch

@Composable
fun ApiCenterSection(
    settings: Settings,
    viewModel: MainViewModel,
) {
    val canonical = settings.migratedApiCenter()
    val connections = canonical.apiConnectionsCompat()
    val routes = canonical.apiRoutesCompat()
    var editingConnection by remember { mutableStateOf<ApiConnection?>(null) }
    var creatingConnection by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<ApiRoute?>(null) }
    var deleteTarget by remember { mutableStateOf<ApiConnection?>(null) }

    LaunchedEffect(settings.apiConnections, settings.apiRoutes) {
        viewModel.ensureApiCenterMigration(settings)
    }

    fun save(next: Settings) = viewModel.saveSettings(next.migratedApiCenter())
    fun replaceConnection(next: ApiConnection) {
        val updated = canonical.apiConnections.toMutableList()
        val index = updated.indexOfFirst { it.id == next.id }
        if (index >= 0) updated[index] = next else updated += next
        val routesWithDefault = canonical.apiRoutesCompat().map { route ->
            val supportsTask = route.task != ApiTaskType.ImageRecognition || ApiCapability.Vision in next.capabilities
            when {
                !supportsTask -> route
                route.primaryConnectionId == null -> route.copy(primaryConnectionId = next.id)
                index < 0 && route.mode == ApiRouteMode.Automatic && route.fallbackConnectionId == null && route.primaryConnectionId != next.id -> {
                    route.copy(fallbackConnectionId = next.id)
                }
                else -> route
            }
        }
        save(canonical.copy(apiConnections = updated, apiRoutes = routesWithDefault))
    }
    fun replaceRoute(next: ApiRoute) {
        val updated = canonical.apiRoutes.filterNot { it.task == next.task } + next
        save(canonical.copy(apiRoutes = updated))
    }

    fun importApilotConnection(next: ApiConnection) {
        val imported = next.copy(
            id = newId(),
            name = next.name.trim().ifBlank { "Apilot 导入服务" },
        )
        val updated = canonical.apiConnections + imported
        val routesWithDefault = canonical.apiRoutesCompat().map { route ->
            when {
                route.primaryConnectionId == null -> route.copy(primaryConnectionId = imported.id)
                route.mode == ApiRouteMode.Automatic && route.fallbackConnectionId == null && route.primaryConnectionId != imported.id -> route.copy(fallbackConnectionId = imported.id)
                else -> route
            }
        }
        save(canonical.copy(apiConnections = updated, apiRoutes = routesWithDefault))
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("API 中心", style = MaterialTheme.typography.titleLarge)
            Text(
                "服务连接、任务路由和调用记录分开管理。普通任务默认只调用一个服务；双 API 只用于你明确开启的核对任务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { creatingConnection = true }, modifier = Modifier.weight(1f)) { Text("添加服务") }
                OutlinedButton(
                    onClick = { connections.forEach { viewModel.checkSavedApiConnection(it.id) } },
                    enabled = connections.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("检测全部") }
            }
        }
    }

    ApilotInteropCard(settings = canonical, onImport = ::importApilotConnection)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("当前任务路由", style = MaterialTheme.typography.titleMedium)
            ApiTaskType.entries.forEachIndexed { index, task ->
                if (index > 0) HorizontalDivider()
                val route = routes.firstOrNull { it.task == task } ?: ApiRoute(task = task)
                val primary = connections.firstOrNull { it.id == route.primaryConnectionId }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.label(), style = MaterialTheme.typography.titleSmall)
                            val source = primary?.let { "${it.name.ifBlank { "未命名服务" }} / ${it.selectedModel.ifBlank { "未选模型" }}" } ?: "尚未分配服务"
                            Text(
                                "${route.mode.label()} · $source",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { editingRoute = route }) { Text("配置") }
                    }
                }
            }
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("服务连接", style = MaterialTheme.typography.titleMedium)
            if (connections.isEmpty()) {
                Text("添加一个服务后即可为普通问答、批量解析、图片识别和报告分配路由。", style = MaterialTheme.typography.bodySmall)
            }
            connections.forEachIndexed { index, connection ->
                if (index > 0) HorizontalDivider()
                ConnectionRow(
                    connection = connection,
                    onTest = { viewModel.checkSavedApiConnection(connection.id) },
                    onEdit = { editingConnection = connection },
                    onDelete = { deleteTarget = connection },
                )
            }
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("最近调用", style = MaterialTheme.typography.titleMedium)
            if (canonical.apiInvocationRecords.isEmpty()) {
                Text("调用后会显示实际服务、模型、耗时和是否切换备用服务。", style = MaterialTheme.typography.bodySmall)
            } else {
                canonical.apiInvocationRecords.takeLast(8).asReversed().forEach { record ->
                    Text(
                        "${record.task.label()} · ${record.connectionName} / ${record.model} · ${if (record.ok) "成功" else "失败"}${record.latencyMs?.let { " · ${it}ms" }.orEmpty()}${if (record.fallbackUsed) " · 已切换备用" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (record.ok) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = { save(canonical.copy(apiInvocationRecords = emptyList())) }) { Text("清除记录") }
            }
        }
    }

    if (creatingConnection || editingConnection != null) {
        ApiConnectionEditorDialog(
            existing = editingConnection,
            onDismiss = {
                creatingConnection = false
                editingConnection = null
            },
            onSave = {
                replaceConnection(it)
                creatingConnection = false
                editingConnection = null
            },
        )
    }

    editingRoute?.let { route ->
        ApiRouteEditorDialog(
            route = route,
            connections = connections,
            onDismiss = { editingRoute = null },
            onSave = {
                replaceRoute(it)
                editingRoute = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除服务连接") },
            text = { Text("删除“${target.name.ifBlank { target.baseUrl }}”后，引用它的任务会变为未分配。不会删除历史调用记录。") },
            confirmButton = {
                TextButton(onClick = {
                    val routesWithoutTarget = canonical.apiRoutes.map { route ->
                        route.copy(
                            primaryConnectionId = route.primaryConnectionId.takeUnless { it == target.id },
                            fallbackConnectionId = route.fallbackConnectionId.takeUnless { it == target.id },
                            secondaryConnectionId = route.secondaryConnectionId.takeUnless { it == target.id },
                        )
                    }
                    save(canonical.copy(apiConnections = canonical.apiConnections.filterNot { it.id == target.id }, apiRoutes = routesWithoutTarget))
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ConnectionRow(
    connection: ApiConnection,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(connection.name.ifBlank { "未命名服务" }, style = MaterialTheme.typography.titleSmall)
        Text(
            "${connection.selectedModel.ifBlank { "未选择模型" }} · ${connection.baseUrl.ifBlank { "未填写地址" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val health = when (connection.lastHealthOk) {
            true -> "可用${connection.lastLatencyMs?.let { " · ${it}ms" }.orEmpty()}"
            false -> "失败 · ${connection.lastHealthMessage.orEmpty()}"
            null -> "尚未检测"
        }
        Text(
            health,
            style = MaterialTheme.typography.bodySmall,
            color = if (connection.lastHealthOk == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTest) { Text("检测") }
            OutlinedButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun ApiConnectionEditorDialog(
    existing: ApiConnection?,
    onDismiss: () -> Unit,
    onSave: (ApiConnection) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember { ChatCompletionClient() }
    var step by remember(existing?.id) { mutableStateOf(if (existing == null) 1 else 2) }
    val connectionId = remember(existing?.id) { existing?.id ?: newId() }
    var providerId by remember(existing?.id) { mutableStateOf(existing?.providerId ?: "custom") }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember(existing?.id) { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKey by remember(existing?.id) { mutableStateOf(existing?.apiKey.orEmpty()) }
    var model by remember(existing?.id) { mutableStateOf(existing?.selectedModel.orEmpty()) }
    // The provider list is a one-edit-session snapshot.  Keeping it in Settings would allow a
    // retired model to reappear as a selectable candidate after the provider removed it.
    var models by remember(existing?.id) { mutableStateOf(emptyList<String>()) }
    var loading by remember(existing?.id) { mutableStateOf(false) }
    var status by remember(existing?.id) { mutableStateOf<String?>(existing?.lastHealthMessage) }
    var healthOk by remember(existing?.id) { mutableStateOf(existing?.lastHealthOk) }
    var healthLatency by remember(existing?.id) { mutableStateOf(existing?.lastLatencyMs) }
    var checkedAt by remember(existing?.id) { mutableStateOf(existing?.lastCheckedAt) }
    var revealKey by remember(existing?.id) { mutableStateOf(false) }
    var capabilities by remember(existing?.id) { mutableStateOf(existing?.capabilities.orEmpty()) }
    var capabilityVerifiedAt by remember(existing?.id) { mutableStateOf(existing?.capabilityVerifiedAt) }
    var providerQuery by remember(existing?.id) { mutableStateOf("") }
    var modelPickerOpen by remember(existing?.id) { mutableStateOf(false) }

    val preset = ApiProviderPresets.entries.firstOrNull { it.id == providerId }
    fun draft(): ApiConnection = ApiConnection(
        id = connectionId,
        name = name.trim().ifBlank { preset?.name ?: "自定义服务" },
        providerId = providerId,
        baseUrl = baseUrl.trim(),
        selectedModel = model.trim(),
        modelIds = emptyList(),
        apiKeyRef = existing?.apiKeyRef.orEmpty(),
        apiKey = apiKey,
        lastModelsAt = null,
        capabilities = capabilities,
        capabilityVerifiedAt = capabilityVerifiedAt,
        lastHealthOk = healthOk,
        lastHealthMessage = status,
        lastLatencyMs = healthLatency,
        lastCheckedAt = checkedAt,
        riskAcknowledged = existing?.riskAcknowledged == true || !isMimoTokenPlanUrl(baseUrl),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加服务（$step / 3）" else "编辑服务") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (step == 1) {
                    Text("选择连接方案", style = MaterialTheme.typography.titleSmall)
                    Text("模板只填入厂商和兼容地址，不固化模型名；模型始终从你的账号实时获取。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = providerQuery,
                        onValueChange = { providerQuery = it },
                        singleLine = true,
                        label = { Text("搜索厂商") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val query = providerQuery.trim()
                    val visible = ApiProviderPresets.entries.filter { candidate ->
                        query.isBlank() || candidate.name.contains(query, ignoreCase = true) || candidate.description.contains(query, ignoreCase = true)
                    }
                    ApiProviderGroup.entries.forEach { group ->
                        val candidates = visible.filter { it.group == group }
                        if (candidates.isNotEmpty()) {
                            Text(group.label(), style = MaterialTheme.typography.labelLarge)
                            candidates.forEach { candidate ->
                                OutlinedButton(
                                    onClick = {
                                        providerId = candidate.id
                                        name = candidate.name
                                        baseUrl = candidate.baseUrl
                                        step = 2
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(candidate.name) }
                                Text(candidate.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("服务名称") })
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, singleLine = true, label = { Text("Base URL（OpenAI 兼容）") })
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        singleLine = true,
                        label = { Text("API Key") },
                        visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { revealKey = !revealKey }) { Text(if (revealKey) "隐藏" else "显示") } },
                    )
                    if (isMimoTokenPlanUrl(baseUrl)) {
                        Text(
                            "MiMo Token Plan 可能不适用于非编程自定义应用，存在停服或 Key 风险。继续保存即表示已了解风险。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    val result = client.listModels(draft().toConfig())
                                    val fetched = stableModelIds(result.models)
                                    models = fetched
                                    val selectedFromList = fetched.firstOrNull { it.equals(model.trim(), ignoreCase = true) }
                                    when {
                                        fetched.isEmpty() -> status = result.message
                                        selectedFromList != null -> {
                                            model = selectedFromList
                                            status = "${result.message}；保留当前模型：$selectedFromList"
                                        }
                                        model.isBlank() -> {
                                            val suggestion = recommendedModel(fetched, "")
                                            model = suggestion.orEmpty()
                                            status = "${result.message}；已智能推荐：${suggestion ?: "无"}。可随时手动切换。"
                                        }
                                        else -> status = "${result.message}；当前手动模型不在返回列表中，已保留且未自动替换。"
                                    }
                                    loading = false
                                }
                            },
                            enabled = baseUrl.isNotBlank() && !loading,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (loading) "获取中…" else "获取并推荐") }
                        OutlinedButton(
                            onClick = { modelPickerOpen = true },
                            enabled = models.isNotEmpty() && !loading,
                            modifier = Modifier.weight(1f),
                        ) { Text("切换模型") }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                val start = System.currentTimeMillis()
                                val result = client.check(draft().toConfig(), "请只回复：OK", maxTokens = 8)
                                healthOk = result.ok
                                if (result.ok) {
                                    capabilities = capabilities + ApiCapability.Text
                                    capabilityVerifiedAt = nowIso()
                                }
                                healthLatency = (System.currentTimeMillis() - start).coerceAtLeast(0L)
                                checkedAt = nowIso()
                                status = result.message
                                loading = false
                            }
                        },
                        enabled = baseUrl.isNotBlank() && model.isNotBlank() && !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("测试连接") }
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        singleLine = true,
                        label = { Text("模型") },
                        placeholder = { Text("先获取并智能推荐；也可手动填写") },
                    )
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (step == 2) {
                        Text("模型能力", style = MaterialTheme.typography.titleSmall)
                        Text("连接测试成功后会自动确认“文本”。其余能力可采用智能建议；图片路由仍建议先以真实图片任务复核。", style = MaterialTheme.typography.bodySmall)
                        val hints = suggestedCapabilities(model)
                        if (hints.isNotEmpty()) {
                            Text(
                                "智能建议：${hints.joinToString("、") { it.label() }}（来自当前模型标识）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedButton(
                                onClick = {
                                    capabilities = capabilities + hints
                                    capabilityVerifiedAt = nowIso()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("一键采用智能建议") }
                        }
                        ApiCapability.entries.forEach { capability ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(capability.label())
                                Switch(
                                    checked = capability in capabilities,
                                    onCheckedChange = { checked ->
                                        capabilities = if (checked) capabilities + capability else capabilities - capability
                                        capabilityVerifiedAt = nowIso()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (step == 1) TextButton(onClick = onDismiss) { Text("取消") }
                if (step == 2 && existing == null) TextButton(onClick = { step = 1 }) { Text("上一步") }
                TextButton(onClick = { onSave(draft()) }, enabled = baseUrl.isNotBlank() && model.isNotBlank()) { Text("保存") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (modelPickerOpen) {
        ModelPickerDialog(
            models = models,
            selected = model,
            onDismiss = { modelPickerOpen = false },
            onSelect = {
                model = it
                status = "已手动选择模型：$it"
                modelPickerOpen = false
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelPickerDialog(
    models: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var fullModelName by remember { mutableStateOf<String?>(null) }
    val visible = stableModelIds(models).filter { it.contains(query.trim(), ignoreCase = true) }
    val recommendation = recommendedModel(models, "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "候选来自本次从当前账号获取的结果，已稳定排序且不会保存为本地预设。智能推荐：${recommendation ?: "无"}；不会因重新获取而覆盖手动选择。长按候选项可查看完整名称。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("搜索已获取的模型") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    visible.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onSelect(item) },
                                    onLongClick = { fullModelName = item },
                                ),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                if (item.equals(selected.trim(), ignoreCase = true)) "$item（当前）" else item,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (visible.isEmpty()) Text("没有匹配的模型", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    fullModelName?.let { name ->
        AlertDialog(
            onDismissRequest = { fullModelName = null },
            title = { Text("模型完整名称") },
            text = { SelectionContainer { Text(name) } },
            confirmButton = { TextButton(onClick = { fullModelName = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun ApiRouteEditorDialog(
    route: ApiRoute,
    connections: List<ApiConnection>,
    onDismiss: () -> Unit,
    onSave: (ApiRoute) -> Unit,
) {
    var mode by remember(route.task) { mutableStateOf(route.mode) }
    var primary by remember(route.task) { mutableStateOf(route.primaryConnectionId) }
    var fallback by remember(route.task) { mutableStateOf(route.fallbackConnectionId) }
    var secondary by remember(route.task) { mutableStateOf(route.secondaryConnectionId) }
    val eligible = if (route.task == ApiTaskType.ImageRecognition) {
        connections.filter { ApiCapability.Vision in it.capabilities }
    } else {
        connections
    }
    fun name(id: String?): String = connections.firstOrNull { it.id == id }?.name?.ifBlank { "未命名服务" } ?: "未分配"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${route.task.label()} 路由") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自动切换只在超时、限流或 5xx 时使用备用服务；鉴权、地址、模型和能力错误不会静默切换。", style = MaterialTheme.typography.bodySmall)
                RoutePicker("调用方式：${mode.label()}", ApiRouteMode.entries.map { it.label() to { mode = it } })
                if (route.task == ApiTaskType.ImageRecognition && eligible.isEmpty()) {
                    Text("图片识别只可分配给已验证图片能力的服务。请先在服务编辑页确认“图片”能力。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                RoutePicker("主服务：${name(primary)}", eligible.map { it.name.ifBlank { it.baseUrl } to { primary = it.id } })
                if (mode == ApiRouteMode.Automatic || mode == ApiRouteMode.Dual) {
                    RoutePicker("备用服务：${name(fallback)}", eligible.filter { it.id != primary }.map { it.name.ifBlank { it.baseUrl } to { fallback = it.id } })
                }
                if (mode == ApiRouteMode.Dual) {
                    Text("双 API 会并行调用两个服务，可能增加费用和耗时。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    RoutePicker("第二服务：${name(secondary)}", eligible.filter { it.id != primary }.map { it.name.ifBlank { it.baseUrl } to { secondary = it.id } })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(route.copy(mode = mode, primaryConnectionId = primary, fallbackConnectionId = fallback, secondaryConnectionId = secondary)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RoutePicker(
    label: String,
    entries: List<Pair<String, () -> Unit>>,
) {
    var pickerOpen by remember(label) { mutableStateOf(false) }
    OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (pickerOpen) {
        OptionPickerDialog(
            title = label.substringBefore('：'),
            entries = entries,
            onDismiss = { pickerOpen = false },
            onSelect = { action ->
                action()
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    entries: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    onSelect: (() -> Unit) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entries.forEach { (name, action) ->
                    OutlinedButton(onClick = { onSelect(action) }, modifier = Modifier.fillMaxWidth()) {
                        Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (entries.isEmpty()) Text("没有可选项", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun ApiCapability.label(): String = when (this) {
    ApiCapability.Text -> "文本"
    ApiCapability.Vision -> "图片"
    ApiCapability.StructuredJson -> "结构化 JSON"
    ApiCapability.LongContext -> "长上下文"
}
