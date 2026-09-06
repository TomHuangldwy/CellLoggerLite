package com.celllogger.lite.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.celllogger.lite.ui.InfoCard
import com.celllogger.lite.ui.InfoRow
import com.celllogger.lite.ui.PageScaffold

@Composable
fun DeviceCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh++
    }

    val requiredPermissions = buildRequiredPermissionList()
    val grantState = remember(refresh) {
        requiredPermissions.map { permission ->
            permission to ContextCompat.checkSelfPermission(context, permission)
        }
    }
    val missing = grantState.filter { it.second != PackageManager.PERMISSION_GRANTED }

    PageScaffold(title = "权限 / 设备检查", onBack = onBack) {
        InfoCard(title = "运行时权限") {
            grantState.forEach { (permission, result) ->
                val granted = result == PackageManager.PERMISSION_GRANTED
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = if (granted) "✓" else "!",
                        color = if (granted) Color(0xFF1B873B) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${permissionLabel(permission)}  ·  $permission",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (missing.isNotEmpty()) {
                Button(onClick = {
                    permissionLauncher.launch(missing.map { it.first }.toTypedArray())
                }) {
                    Text("请求缺失权限")
                }
            } else {
                Text(
                    "所有必需权限均已授予。",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        InfoCard(title = "手机能力") {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val subManager = context.getSystemService(SubscriptionManager::class.java)
            val activeSubs = runCatching {
                subManager.activeSubscriptionInfoList.orEmpty().size
            }.getOrDefault(0)
            val locationManager = context.getSystemService(LocationManager::class.java)
            InfoRow("设备型号", "${Build.MANUFACTURER} ${Build.MODEL}")
            InfoRow("Android 版本", Build.VERSION.RELEASE)
            InfoRow("SDK", Build.VERSION.SDK_INT.toString())
            InfoRow(
                "蜂窝能力",
                if (tm.phoneType != TelephonyManager.PHONE_TYPE_NONE) "可用" else "不可用"
            )
            InfoRow("活动订阅", if (activeSubs > 0) "$activeSubs 张" else "无")
            InfoRow(
                "位置服务",
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) "GPS 开启"
                else "GPS 关闭（无定位修复）"
            )
        }

        Spacer(Modifier.height(12.dp))

        InfoCard(title = "说明") {
            Text(
                text = "Cell Logger Lite 需要：\n" +
                    "· READ_PHONE_STATE —— 读取注册状态/网络类型/订阅；\n" +
                    "· ACCESS_FINE_LOCATION —— Android 对小区信息与信号强度也要求精确定位权限；\n" +
                    "· POST_NOTIFICATIONS (Android 13+) —— 后台采集时显示前台服务通知。\n" +
                    "本应用不请求 Root、不访问 DIAG 端口。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }) {
                Text("打开系统设置")
            }
        }
    }
}

private fun buildRequiredPermissionList(): List<String> {
    val list = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list += Manifest.permission.POST_NOTIFICATIONS
    }
    return list
}

private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.READ_PHONE_STATE -> "读取电话状态"
    Manifest.permission.ACCESS_FINE_LOCATION -> "精确定位"
    Manifest.permission.POST_NOTIFICATIONS -> "通知"
    else -> permission
}
