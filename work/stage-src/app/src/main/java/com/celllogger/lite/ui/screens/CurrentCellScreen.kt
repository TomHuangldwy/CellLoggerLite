package com.celllogger.lite.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celllogger.lite.data.local.CellSampleEntity
import com.celllogger.lite.data.model.ExperimentId
import com.celllogger.lite.ui.CellLoggerViewModel
import com.celllogger.lite.ui.InfoCard
import com.celllogger.lite.ui.InfoRow
import com.celllogger.lite.ui.PageScaffold
import com.celllogger.lite.ui.formatUtc

@Composable
fun CurrentCellScreen(
    viewModel: CellLoggerViewModel,
    openDeviceCheck: () -> Unit
) {
    val context = LocalContext.current
    val running by viewModel.running.collectAsStateWithLifecycle()
    val intervalMs by viewModel.intervalMs.collectAsStateWithLifecycle()
    val experiment by viewModel.experiment.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val managerError by viewModel.managerError.collectAsStateWithLifecycle()
    val samples by viewModel.recentSamples.collectAsStateWithLifecycle()
    val latest = samples.firstOrNull()
    val manualBusy by viewModel.manualBusy.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val required = telephonyPermissions(context)
        if (required.all { result[it] == true }) {
            viewModel.startCollection()
        }
    }

    PageScaffold(title = "当前小区与采集控制") {
        InfoCard(title = "实验设置") {
            Text(
                text = "采样间隔 ${intervalMs / 1000} s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = (intervalMs / 1000).toFloat(),
                onValueChange = { viewModel.setIntervalMs((it * 1000).toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                enabled = !running
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExperimentId.entries.forEach { exp ->
                    FilterChip(
                        selected = experiment == exp,
                        onClick = { if (!running) viewModel.setExperiment(exp) },
                        label = { Text(exp.label.removePrefix("实验 ").substringBefore("：")) }
                    )
                }
            }
            if (running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "会话：${session?.sessionId ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!running) {
                Button(
                    onClick = {
                        val missing = telephonyPermissions(context)
                        if (missing.isEmpty()) {
                            viewModel.startCollection()
                        } else {
                            permissionLauncher.launch(missing.toTypedArray())
                        }
                    }
                ) {
                    Text("开始采集")
                }
            } else {
                OutlinedButton(onClick = { viewModel.stopCollection() }) {
                    Text("停止采集")
                }
                Button(
                    onClick = { viewModel.requestManualSample() },
                    enabled = !manualBusy
                ) {
                    Text(if (manualBusy) "请求中…" else "手动触发一次")
                }
            }
        }

        if (managerError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = managerError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = openDeviceCheck) { Text("去检查权限/设备") }
        }

        Spacer(Modifier.height(12.dp))
        ServingCellCard(latest = latest)

        Spacer(Modifier.height(12.dp))
        InfoCard(title = "字段说明") {
            Text(
                text = "Android 未上报的字段一律显示为 — 并写入 NULL，" +
                    "不使用 0 / -160 等占位值。详见“更多 → 公开 API 限制”。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ServingCellCard(latest: CellSampleEntity?) {
    InfoCard(title = "服务小区 / 最近一次采样") {
        if (latest == null) {
            Text("尚未采样。开始采集后这里会显示最近一条记录。")
            return@InfoCard
        }
        InfoRow("UTC 时间", formatUtc(latest.utcTimestampMs))
        InfoRow("会话", latest.sessionId)
        InfoRow("实验", latest.experimentId)
        InfoRow("网络类型", latest.dataNetworkType)
        InfoRow("NR 状态", latest.nrState)
        InfoRow("注册状态", latest.registrationState)
        InfoRow("接入技术", latest.accessTechnology)
        InfoRow("连接状态", latest.connectionState)
        InfoRow("MCC/MNC", listOfNotNull(latest.mcc, latest.mnc).joinToString("/").ifEmpty { null })
        InfoRow("频点 ARFCN", latest.arfcn?.toString())
        InfoRow("带宽 kHz", latest.bandwidthKhz?.toString())
        InfoRow("PCI", latest.pci?.toString())
        InfoRow("TAC", latest.tac?.toString())
        InfoRow("Cell ID", latest.cellId?.toString())
        InfoRow("RSRP", latest.rsrpDbm?.let { "$it dBm" })
        InfoRow("RSRQ", latest.rsrqDb?.let { "$it dB" })
        InfoRow("SINR", latest.sinrDb?.let { "$it dB" })
        InfoRow("RSSI", latest.rssiDbm?.let { "$it dBm" })
        InfoRow("数据新鲜度", latest.dataFreshnessMs?.let { "$it ms" })
        InfoRow("回调时刻", latest.callbackElapsedRealtimeMs?.toString())
        InfoRow("定位", formatLatLng(latest.latitude, latest.longitude))
        InfoRow("精度/速度/方向", formatLocationDetail(latest))
        InfoRow("姿态 yaw/pitch/roll", formatAttitude(latest))
    }
}

private fun formatLatLng(lat: Double?, lng: Double?): String? =
    if (lat == null || lng == null) null else "%.6f, %.6f".format(lat, lng)

private fun formatLocationDetail(s: CellSampleEntity): String? {
    val parts = mutableListOf<String>()
    s.accuracyMeters?.let { parts.add("±%.1f m".format(it)) }
    s.speedMetersPerSecond?.let { parts.add("%.1f m/s".format(it)) }
    s.bearingDegrees?.let { parts.add("%.1f°".format(it)) }
    return parts.joinToString(" ").ifEmpty { null }
}

private fun formatAttitude(s: CellSampleEntity): String? {
    val yaw = s.yawDegrees
    val pitch = s.pitchDegrees
    val roll = s.rollDegrees
    if (yaw == null || pitch == null || roll == null) return null
    return "%.1f° %.1f° %.1f°".format(yaw, pitch, roll)
}

private fun telephonyPermissions(context: Context): List<String> {
    val required = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    )
    return required.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}
