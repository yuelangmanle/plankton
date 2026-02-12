package com.plankton.one102.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plankton.one102.domain.Taxonomy

@Composable
fun TaxonomyDialog(
    initialLatin: String,
    initialTaxonomy: Taxonomy,
    initialWriteToDb: Boolean,
    onClose: () -> Unit,
    onApply: (nameLatin: String, taxonomy: Taxonomy, writeToDb: Boolean) -> Unit,
) {
    var latin by remember(initialLatin) { mutableStateOf(initialLatin) }
    var taxonomy by remember(initialTaxonomy) { mutableStateOf(initialTaxonomy) }
    var writeToDb by remember(initialWriteToDb) { mutableStateOf(initialWriteToDb) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("编辑分类/拉丁名", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "提示：可上下滑动；只保存你修改的内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("本次写入本机库", modifier = Modifier.weight(1f))
                        Switch(checked = writeToDb, onCheckedChange = { writeToDb = it })
                    }
                    Text(
                        if (writeToDb) "已开启：保存时分类/拉丁名会同步写入自定义库。" else "默认关闭：仅更新当前数据集，不写入自定义库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    OutlinedTextField(
                        value = latin,
                        onValueChange = { latin = it },
                        label = { Text("拉丁名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = taxonomy.lvl1,
                        onValueChange = { taxonomy = taxonomy.copy(lvl1 = it) },
                        label = { Text("大类（原生动物/轮虫类/枝角类/桡足类）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = taxonomy.lvl2,
                        onValueChange = { taxonomy = taxonomy.copy(lvl2 = it) },
                        label = { Text("纲") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = taxonomy.lvl3,
                        onValueChange = { taxonomy = taxonomy.copy(lvl3 = it) },
                        label = { Text("目") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = taxonomy.lvl4,
                        onValueChange = { taxonomy = taxonomy.copy(lvl4 = it) },
                        label = { Text("科") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = taxonomy.lvl5,
                        onValueChange = { taxonomy = taxonomy.copy(lvl5 = it) },
                        label = { Text("属") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { onApply(latin, taxonomy, writeToDb) }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                }
            }
        }
    }
}
