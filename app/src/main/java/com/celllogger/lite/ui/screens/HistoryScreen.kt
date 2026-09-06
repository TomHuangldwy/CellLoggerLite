package com.celllogger.lite.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celllogger.lite.data.local.CellSampleEntity
import com.celllogger.lite.ui.CellLoggerViewModel
import com.celllogger.lite.ui.EmptyHint
import com.celllogger.lite.ui.ScreenHeader
import com.celllogger.lite.ui.formatUtc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: CellLoggerViewModel) {
    val samples by viewModel.recentSamples.collectAsStateWithLifecycle()
    var exportedMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            exportedMessage = "CSV 导出中…"
            viewModel.exportCsv(uri) {
                exportedMessage = "CSV 已导出。"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "历史记录")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                val name = "cell_logger_" +
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv"
                exportLauncher.launch(name)
            }) {
                Text("导出全部 CSV")
            }
            OutlinedButton(onClick = { viewModel.clearHistory() }) {
                Text("清空")
            }
        }
        exportedMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))

        if (samples.isEmpty()) {
            EmptyHint("还没有数据。回到“采集”页开始一个会话。")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(samples, key = { it.id }) { sample ->
                    SampleRow(sample)
                }
            }
        }
    }
}

@Composable
private fun SampleRow(sample: CellSampleEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = formatUtc(sample.utcTimestampMs) ?: "",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = listOfNotNull(
                    sample.experimentId,
                    sample.dataNetworkType,
                    sample.accessTechnology,
                    sample.connectionState,
                    sample.registrationState
                ).joinToString(" · ").ifEmpty { null } ?: "无小区信息",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = listOfNotNull(
                    sample.mcc?.let { "MCC $it" },
                    sample.mnc?.let { "MNC $it" },
                    sample.pci?.let { "PCI $it" },
                    sample.arfcn?.let { "ARFCN $it" },
                    sample.cellId?.let { "CID $it" }
                ).joinToString("  "),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = sample.rsrpDbm?.let { "RSRP $it dBm" }
                    ?: sample.rsrqDb?.let { "RSRQ $it dB" }
                    ?: "RSRP/RSRQ 不可用",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
