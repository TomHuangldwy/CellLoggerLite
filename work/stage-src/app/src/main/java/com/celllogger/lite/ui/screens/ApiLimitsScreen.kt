package com.celllogger.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.celllogger.lite.ui.InfoCard
import com.celllogger.lite.ui.PageScaffold

@Composable
fun ApiLimitsScreen(onBack: () -> Unit) {
    PageScaffold(title = "公开 API 限制", onBack = onBack) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp)
            ) {
                Text(
                    text = "Data source: Android Telephony API\n" +
                        "Granularity: cell/connection-level exposed by Android\n" +
                        "Not DIAG / Not ML1 / Not per-SSB vector",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        InfoCard(title = "为什么有这个页面") {
            Text(
                text = "Cell Logger Lite 明确把自己定位成 Android 公开 Telephony API 的" +
                    "实验工具，而不是基站/射频仪表。它不会伪装成 DIAG/ML1 工具，也不会暗示" +
                    "RSRP/SINR 能定位到某个 SSB 波束。",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(12.dp))

        InfoCard(title = "公开 API 能提供 / 不能提供") {
            Text(
                text = "能提供：\n" +
                    "· CellInfoNr/CellInfoLte 列表（服务小区、相邻可测小区）；\n" +
                    "· 注册/连接状态、MCC/MNC、PCI、TAC、Cell ID、NR-ARFCN/EARFCN；\n" +
                    "· NR SS-RSRP/RSRQ/SINR、LTE RSRP/RSRQ/RSSNR（调制解调器聚合值）；\n" +
                    "· ServiceState 注册状态与 NR 状态（连接/受限/无）。\n" +
                    "\n不能提供：\n" +
                    "· 每个 SSB 波束的同步信号/波束质量向量；\n" +
                    "· ML1/ML2 内部测量、随机接入/调度原始日志；\n" +
                    "· DIAG 端口或 vendor 私有诊断数据；\n" +
                    "· 部分 OEM 在非 carrier-privilege 应用上屏蔽的字段。",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))

        InfoCard(title = "对实验结论的影响") {
            Text(
                text = "请把结论表述为“在 Android 公开 API 提供的 cell/connection 粒度上观察到……”，" +
                    "不要表述为“该基站某波束信号为……”。多设备数据比较前还应检查 Android 版本、OEM、" +
                    "双卡状态，因为这些都会影响字段是否上报。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
