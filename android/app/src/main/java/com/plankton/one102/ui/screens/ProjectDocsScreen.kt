package com.plankton.one102.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.plankton.one102.ui.DocAccessCache
import com.plankton.one102.ui.ProjectDocEntry
import com.plankton.one102.ui.findProjectDocTitle
import com.plankton.one102.ui.isDocPasswordValid
import com.plankton.one102.ui.listProjectDocs
import com.plankton.one102.ui.readProjectDoc
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.AiRichText
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.components.LocalGlassPrefs
import com.plankton.one102.ui.theme.GlassWhite

@Composable
fun ProjectDocsScreen(
    padding: PaddingValues,
    docId: String? = null,
    onOpenDoc: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val docs = remember { listProjectDocs() }

    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var locked by rememberSaveable { mutableStateOf(DocAccessCache.password == null) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(true) }

    val glassPrefs = LocalGlassPrefs.current
    val dialogColor = if (glassPrefs.enabled) GlassWhite else MaterialTheme.colorScheme.surface
    val dialogShape = RoundedCornerShape(24.dp)

    LaunchedEffect(docId, locked) {
        if (locked) {
            content = null
            error = null
            loading = false
            return@LaunchedEffect
        }
        if (docId.isNullOrBlank()) {
            content = null
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        content = readProjectDoc(context, docId)
        if (content == null) {
            error = "未找到文档内容"
        }
        loading = false
    }

    if (locked) {
        GlassBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("项目文档已锁定", style = MaterialTheme.typography.titleLarge)
                Text(
                    "请输入密码后查看文档内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                OutlinedButton(onClick = { showPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("输入密码")
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("返回")
                }
            }
        }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                confirmButton = {
                    Button(onClick = {
                        if (isDocPasswordValid(passwordInput)) {
                            DocAccessCache.unlock(passwordInput)
                            locked = false
                            passwordError = null
                            passwordInput = ""
                            showPasswordDialog = false
                        } else {
                            passwordError = "密码错误"
                        }
                    }) { Text("解锁") }
                },
                dismissButton = {
                    TextButton(onClick = { onClose() }) { Text("返回") }
                },
                title = { Text("输入文档密码") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        if (!passwordError.isNullOrBlank()) {
                            Text(passwordError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                containerColor = dialogColor,
                shape = dialogShape,
            )
        }
        return
    } else if (docId.isNullOrBlank()) {
        ProjectDocList(
            padding = padding,
            docs = docs,
            onOpenDoc = onOpenDoc,
        )
        return
    }

    val title = findProjectDocTitle(docId) ?: "项目文档"
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (loading) {
                Text("加载中…", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            if (error != null) {
                Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                return@Column
            }
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AiRichText(
                        text = content.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

}

@Composable
private fun ProjectDocList(
    padding: PaddingValues,
    docs: List<ProjectDocEntry>,
    onOpenDoc: (String) -> Unit,
) {
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("项目文档", style = MaterialTheme.typography.titleLarge)
            Text(
                "请选择要查看的文档。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            for (doc in docs) {
                DocEntryCard(entry = doc, onOpen = { onOpenDoc(doc.id) })
            }
        }
    }
}

@Composable
private fun DocEntryCard(entry: ProjectDocEntry, onOpen: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text("查看")
            }
        }
    }
}
