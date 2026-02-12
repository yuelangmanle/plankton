package com.plankton.one102.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard

@Composable
fun ToolsScreen(
    padding: PaddingValues,
    onOpenFocus: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenBatch: () -> Unit,
    onOpenAliases: () -> Unit,
    onOpenAiCache: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("工具箱", style = MaterialTheme.typography.titleLarge)

        ToolItem(
            title = "专注录入（全屏）",
            subtitle = "最大化计数列表显示空间，适合现场快速录入。",
            onClick = onOpenFocus,
        )
        ToolItem(
            title = "结果图表",
            subtitle = "查看密度/生物量/多样性等的图表与 TopN 排名。",
            onClick = onOpenCharts,
        )
        ToolItem(
            title = "批量操作",
            subtitle = "批量设置体积、清空某点位计数、复制参数等。",
            onClick = onOpenBatch,
        )
        ToolItem(
            title = "别名 / 同名合并",
            subtitle = "管理物种别名，扫描并合并重复物种。",
            onClick = onOpenAliases,
        )
        ToolItem(
            title = "AI 缓存",
            subtitle = "查看/清空 AI 查询缓存（含提示词与原始回答）。",
            onClick = onOpenAiCache,
        )
        ToolItem(
            title = "全量备份/恢复",
            subtitle = "一键导出/导入：数据集 + 自定义数据库 + 设置（用于迁移设备）。",
            onClick = onOpenBackup,
        )
        }
    }
}

@Composable
private fun ToolItem(title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}
