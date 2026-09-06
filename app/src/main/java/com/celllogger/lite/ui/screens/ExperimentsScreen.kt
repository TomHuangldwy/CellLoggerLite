package com.celllogger.lite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.celllogger.lite.data.model.ExperimentId
import com.celllogger.lite.ui.CellLoggerViewModel
import com.celllogger.lite.ui.PageScaffold

@Composable
fun ExperimentsScreen(
    viewModel: CellLoggerViewModel,
    onExperimentSelected: (ExperimentId) -> Unit
) {
    PageScaffold(title = "实验方案") {
        Text(
            text = "所有实验都是实验性测量：请记录手机型号、SIM 状态、位置与时间，" +
                "并在报告中标明“仅 Android 公开 API 可提供的信息”。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        ExperimentId.entries
            .filter { it != ExperimentId.CUSTOM }
            .forEach { experiment ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = experiment.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = experiment.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onExperimentSelected(experiment) }) {
                            Text("使用此实验并去采集")
                        }
                    }
                }
            }

        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "实验质量控制",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "1. 每次实验使用独立会话，导出前记录 SIM 卡、手机壳/遮挡、基站距离等。\n" +
                        "2. 静止实验建议放在同一位置 3 分钟以上；步行实验路线要固定。\n" +
                        "3. 多设备对比时，先对表（UTC），再按小区/位置聚类，不要跨设备直接平均 RSRP。\n" +
                        "4. 记住：RSRP 是调制解调器通过公开 API 给出的聚合值，不是逐 SSB 波束图。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
