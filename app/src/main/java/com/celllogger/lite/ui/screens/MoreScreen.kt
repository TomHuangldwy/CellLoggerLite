package com.celllogger.lite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.celllogger.lite.ui.PageScaffold

@Composable
fun MoreScreen(
    openDeviceCheck: () -> Unit,
    openApiLimits: () -> Unit
) {
    PageScaffold(title = "更多") {
        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("权限 / 设备检查", style = MaterialTheme.typography.titleMedium)
                Text(
                    "检查 READ_PHONE_STATE、ACCESS_FINE_LOCATION、通知权限，以及电话能力。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = openDeviceCheck) { Text("打开检查页") }
            }
        }
        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("公开 API 限制说明", style = MaterialTheme.typography.titleMedium)
                Text(
                    "解释本工具能拿到什么、拿不到什么，以及为什么实验结论必须限定在公开 API 粒度。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = openApiLimits) { Text("打开说明页") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "App version / SDK / 型号",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
