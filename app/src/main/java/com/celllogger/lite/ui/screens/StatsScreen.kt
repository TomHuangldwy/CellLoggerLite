package com.celllogger.lite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celllogger.lite.ui.CellLoggerViewModel
import com.celllogger.lite.ui.InfoCard
import com.celllogger.lite.ui.InfoRow
import com.celllogger.lite.ui.PageScaffold
import com.celllogger.lite.ui.formatUtc

@Composable
fun StatsScreen(viewModel: CellLoggerViewModel) {
    val summaries by viewModel.sessionSummaries.collectAsStateWithLifecycle()
    val networkStats by viewModel.networkTypeStats.collectAsStateWithLifecycle()
    val rsrp by viewModel.rsrpSummary.collectAsStateWithLifecycle()

    PageScaffold(title = "统计") {
        val totalSamples = summaries.sumOf { it.sampleCount }
        InfoCard(title = "总量") {
            InfoRow("采样行数", totalSamples.toString())
            InfoRow("会话数", summaries.size.toString())
            InfoRow("平均 RSRP", rsrp?.avgRsrpDbm?.let { "%.1f dBm".format(it) })
        }

        Spacer(Modifier.height(12.dp))

        InfoCard(title = "网络类型分布") {
            if (networkStats.isEmpty()) {
                Text("暂无数据", style = MaterialTheme.typography.bodyMedium)
            } else {
                networkStats.forEach { stat ->
                    InfoRow(stat.networkType ?: "(null)", "${stat.sampleCount} 行")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        InfoCard(title = "会话列表") {
            if (summaries.isEmpty()) {
                Text("暂无会话")
            } else {
                summaries.forEach { s ->
                    InfoRow(
                        s.sessionId,
                        "${s.sampleCount} 行 · ${formatUtc(s.firstUtcMs)}"
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "提示：多设备对比时请把各设备导出的 CSV 按 utc_epoch_ms / " +
                "utc_iso8601 对齐；设备型号、实验类型与会话 ID 都在列中。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
