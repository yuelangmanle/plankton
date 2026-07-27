package com.plankton.one102.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.plankton.one102.domain.ApiConnection
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.migratedApiCenter
import com.plankton.one102.data.interop.APILOT_EXTRA_REQUESTED_SCOPES
import com.plankton.one102.data.interop.APILOT_EXTRA_REQUEST_ID
import com.plankton.one102.data.interop.APILOT_EXTRA_RETURN_TRANSPORT
import com.plankton.one102.data.interop.APILOT_EXTRA_SCHEMA_VERSION
import com.plankton.one102.data.interop.APILOT_EXTRA_SOURCE_NAME
import com.plankton.one102.data.interop.APILOT_GITHUB_URL
import com.plankton.one102.data.interop.APILOT_IMPORT_ACTION
import com.plankton.one102.data.interop.APILOT_IMPORT_MIME
import com.plankton.one102.data.interop.APILOT_PACKAGE
import com.plankton.one102.data.interop.APILOT_PICK_ACTION
import com.plankton.one102.data.interop.buildApilotImportPayload
import com.plankton.one102.data.interop.parseApilotPickedProfile
import com.plankton.one102.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

@Composable
fun ApilotInteropCard(
    settings: Settings,
    onImport: (ApiConnection) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connections = remember(settings) {
        settings.migratedApiCenter().apiConnections.filter {
            it.baseUrl.isNotBlank() && it.selectedModel.isNotBlank()
        }
    }
    var includeApiKeys by remember { mutableStateOf(false) }
    var shareConfirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedConnectionIds by remember { mutableStateOf(connections.map { it.id }.toSet()) }
    var showInstallGuide by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            status = "已取消 Apilot 授权，未修改本机 API。"
            return@rememberLauncherForActivityResult
        }
        val data = result.data
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                data?.getStringExtra("com.apilot.extra.API_CONFIG_JSON")
                    ?: data?.data?.let { uri -> context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            }
            val picked = raw?.let(::parseApilotPickedProfile)
            when {
                picked == null -> status = "Apilot 返回内容无效，未修改本机 API。"
                picked.protocolId != "openai_compatible" -> status = "Apilot 返回的协议为 ${picked.protocolId}，主 App 暂不自动导入。"
                else -> {
                    onImport(picked.connection)
                    status = "已导入 Apilot 服务：${picked.connection.name}；请在 API 中心确认任务路由。"
                }
            }
        }
    }

    fun openApilotPicker() {
        val intent = Intent(APILOT_PICK_ACTION).setPackage(APILOT_PACKAGE).apply {
            putExtra(APILOT_EXTRA_SOURCE_NAME, "浮游动物一体化")
            putExtra(APILOT_EXTRA_REQUEST_ID, UUID.randomUUID().toString())
            putExtra(APILOT_EXTRA_SCHEMA_VERSION, 2)
            putStringArrayListExtra(
                APILOT_EXTRA_REQUESTED_SCOPES,
                arrayListOf("connection", "models.default", "models.all", "secret.api_key"),
            )
            putExtra(APILOT_EXTRA_RETURN_TRANSPORT, "auto")
        }
        runCatching { pickLauncher.launch(intent) }
            .onFailure {
                status = "无法打开 Apilot：${it.message ?: "请先安装 Apilot"}"
                showInstallGuide = true
            }
    }

    fun shareToApilot(selectedConnections: List<ApiConnection>) {
        if (selectedConnections.isEmpty()) {
            status = "请至少选择一个要发送的服务。"
            return
        }
        val directory = context.cacheDir.resolve("apilot-interop").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = directory.resolve("api-profiles-${UUID.randomUUID()}.json")
        runCatching {
            file.writeText(
                buildApilotImportPayload(
                    connections = selectedConnections,
                    includeApiKeys = includeApiKeys,
                    sourceSignatureSha256 = signingCertificateSha256(context),
                ),
            )
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(APILOT_IMPORT_ACTION).setPackage(APILOT_PACKAGE).apply {
                setDataAndType(uri, APILOT_IMPORT_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(APILOT_EXTRA_SOURCE_NAME, "浮游动物一体化")
                putExtra(APILOT_EXTRA_REQUEST_ID, UUID.randomUUID().toString())
            }
            context.startActivity(intent)
            status = if (includeApiKeys) {
                "已请求 Apilot 导入 ${selectedConnections.size} 个服务（包含 Key，仍需在 Apilot 确认）。"
            } else {
                "已请求 Apilot 导入 ${selectedConnections.size} 个服务（未包含 Key）。"
            }
            scope.launch {
                kotlinx.coroutines.delay(70_000)
                file.delete()
            }
        }.onFailure {
            status = "发送到 Apilot 失败：${it.message ?: "请先安装 Apilot"}"
            showInstallGuide = true
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Apilot 双向接入", style = MaterialTheme.typography.titleMedium)
            Text(
                "从 Apilot 选择已保存方案，或把本机服务导入 Apilot。所有导入/授权都由对方应用再次确认。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = ::openApilotPicker, modifier = Modifier.weight(1f)) { Text("从 Apilot 选择") }
                OutlinedButton(
                    onClick = {
                        selectedConnectionIds = connections.map { it.id }.toSet()
                        shareConfirm = true
                    },
                    enabled = connections.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("发送到 Apilot")
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }

    if (shareConfirm) {
        val selectedConnections = connections.filter { it.id in selectedConnectionIds }
        AlertDialog(
            onDismissRequest = { shareConfirm = false },
            title = { Text("发送 API 到 Apilot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将发送 " + selectedConnections.size + "/" + connections.size + " 个已配置服务。Apilot 仍会展示导入确认页。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedConnectionIds = connections.map { it.id }.toSet() }) { Text("全选") }
                        TextButton(onClick = { selectedConnectionIds = emptySet() }) { Text("全不选") }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        connections.forEach { connection ->
                            val checked = connection.id in selectedConnectionIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { selected ->
                                        selectedConnectionIds = if (selected) {
                                            selectedConnectionIds + connection.id
                                        } else {
                                            selectedConnectionIds - connection.id
                                        }
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        connection.name.ifBlank { "未命名服务" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        connection.selectedModel + " · " + connection.baseUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeApiKeys, onCheckedChange = { includeApiKeys = it })
                        Text("同时授权 API Key（敏感信息）")
                    }
                    Text(
                        if (includeApiKeys) "已勾选：Key 会通过一次性 content:// 文件交给 Apilot，请只对可信设备使用。"
                        else "默认不发送 Key；Apilot 中导入后可手动补填。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { shareConfirm = false; shareToApilot(selectedConnections) },
                    enabled = selectedConnections.isNotEmpty(),
                ) { Text("发送") }
            },
            dismissButton = { TextButton(onClick = { shareConfirm = false }) { Text("取消") } },
        )
    }

    if (showInstallGuide) {
        AlertDialog(
            onDismissRequest = { showInstallGuide = false },
            title = { Text("需要安装 Apilot") },
            text = { Text("本机未检测到支持 API 互操作的 Apilot。可前往 Apilot GitHub 项目下载并安装后，再返回此处重试。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APILOT_GITHUB_URL))) }
                        showInstallGuide = false
                    },
                ) { Text("前往 GitHub") }
            },
            dismissButton = { TextButton(onClick = { showInstallGuide = false }) { Text("取消") } },
        )
    }
}

private fun signingCertificateSha256(context: android.content.Context): String? = runCatching {
    val packageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
    )
    val certificate = packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return@runCatching null
    MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
}.getOrNull()
