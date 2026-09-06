# Cell Logger Lite 实现说明

## 1. 目标与边界

本项目面向“用手机做公开 API 粒度信号实验”的场景，不是 RF 仪表替代品：

```text
Data source: Android Telephony API
Granularity: cell/connection-level exposed by Android
Not DIAG / Not ML1 / Not per-SSB vector
```

## 2. 架构

```text
          ┌─────────────────────────────────────────────────────┐
          │ UI (Compose)  ViewModel                              │
          └──────────────┬──────────────────────────────────────┘
                         │ start/stop / manual request
          ┌──────────────▼──────────────────────────────────────┐
          │ CellLoggerService (foreground, specialUse)          │
          └──────────────┬──────────────────────────────────────┘
                         │
          ┌──────────────▼──────────────────────────────────────┐
          │ CollectionManager                                   │
          │  · 定时 1–5 s 读缓存并落库                            │
          │  · 主动请求按需/限频 (≥10 s fallback)                │
          └───────┬──────────────────────┬──────────────────────┘
                  │                      │
   ┌──────────────▼───────────┐  ┌───────▼──────────────┐
   │ CellDataSource           │  │ Location/Attitude    │
   │  TelephonyDataSource     │  │  LocationProvider     │
   │  NativeDiagDataSource(*) │  │  AttitudeProvider     │
   └──────────────┬───────────┘  └──────────────────────┘
                  │
   ┌──────────────▼──────────────────────────────────────┐
   │ Room CellDatabase → CellLogRepository → CsvExporter  │
   └─────────────────────────────────────────────────────┘
```

`(*) NativeDiagDataSource` 是占位实现，不参与运行，用于证明“未来接入 DIAG /
ML1 时不需要改 Room、CSV 或 UI 采集逻辑”的扩展点。

## 3. DataSource 抽象

`data/source/CellDataSource.kt`：

```kotlin
interface CellDataSource {
    val type: DataSourceType
    val availability: StateFlow<DataSourceAvailability>
    val current: StateFlow<CellReading?>

    suspend fun start(config: CollectionConfig)
    suspend fun stop()
    suspend fun requestCellInfoUpdate(): CellReading
    suspend fun readCached(nowElapsedRealtimeMs: Long): CellReading
}
```

`CellReading` 内含一帧内所有被解码的小区（可见小区数、注册状态、连接状态），
并挑选一个“服务小区”填到 CSV 单小区列中。

## 4. TelephonyDataSource 实现策略

### 4.1 双版本兼容

- **Android 12+**：`TelephonyCallback.CellInfoListener` +
  `ServiceStateListener`，手动刷新用
  `requestCellInfoUpdate(Executor, CellInfoCallback)`。
- **Android 10–11（API 29/30）**：使用弃用的 `PhoneStateListener.listen()`
  接收系统推送；手动刷新同样可用
  `requestCellInfoUpdate(Executor, CellInfoCallback)`（该 API 从 API 29 公开）。

两者都缓存最近的小区列表与服务状态；定时采样不直接碰调制解调器。

### 4.2 数据新鲜度

| 触发方式 | requestUtcMs / requestElapsedRealtimeMs | callbackUtcMs / callbackElapsedRealtimeMs | dataFreshnessMs |
| --- | --- | --- | --- |
| 手动 requestCellInfoUpdate | 请求发出时刻 | 回调收到时刻 | callback − request |
| 系统 CellInfo 推送 | null | 收到时刻 | 无（由下一周期采样时按源时间计算） |
| 周期读缓存 | null | 该缓存收到时刻 | 采样时刻 − cellInfoElapsedRealtimeMs |

`cellInfoElapsedRealtimeMs` 优先取 `CellInfo.getTimestampMillis()`（API 34+
新增，单位为毫秒）；Android 10–13 使用已废弃但公开的 `getTimeStamp()`
（纳秒换算为毫秒）。`getTimestampNanos()` 未出现在 public SDK
android.jar 中，因此不使用。若平台给 0/未知则取“收到该列表的
elapsedRealtime”。

### 4.3 空值策略

所有解析函数先去掉 Android 的未知 sentinel，例如：

```kotlin
fun Int?.asKnownIntOrNull(): Int? {
    if (this == null || this == Integer.MAX_VALUE) return null
    return this
}
```

NR 读取 `CellSignalStrengthNr.ssRsrp/ssRsrq/ssSinr`（SS-RSRP 族）；LTE 读取
`CellSignalStrengthLte.rsrp/rsrq/rssnr`。均不把合法负 dBm 或 0 dB 当成缺失。

`CellInfoNr` 在 SDK 桩中把 `getCellIdentity()`/`getCellSignalStrength()`
声明为基类类型，需要先向下转型到 `CellIdentityNr`/`CellSignalStrengthNr`
（两者均自 API 29 公开）再读取 MCC/MNC/TAC/NCI/PCI/NR-ARFCN/SS-RSRP 等。

`nrState`（`NetworkRegistrationInfo.NR_STATE_*`）的读取方法
`getNrState()` 属于隐藏/系统 API，public SDK 不暴露；TelephonyDataSource
将该列保持 NULL，不做猜测填充。

## 5. 定位与姿态

- **定位**：`LocationManager`（GPS/网络），避免引入 Google Play Services。
  每次保存样本时读取最新 fix；超过 15 s 的旧 fix 不写入该行（返回 null）。
- **姿态**：优先 `TYPE_GAME_ROTATION_VECTOR`，回退
  `TYPE_ROTATION_VECTOR`；`getRotationMatrixFromVector` +
  `getOrientation` 后转角度，得到 yaw/pitch/roll。

两者的“物理真值 0”与“API 未给出”用不同机制区分：定位用
`Location.hasSpeed()/hasBearing()/hasAccuracy()`，姿态在传感器没有上报前整组为
null；不把传感器角度 0 当作缺失。

## 6. Room 与 CSV

- Entity：`CellSampleEntity`，一行一采样。
- 列：UTC/elapsed、请求/回调/新鲜度、设备、订阅、网络/注册/连接状态、
  MCC/MNC/ARFCN/PCI/TAC/CID、RSRP/RSRQ/SINR/RSSI/CQI、定位、姿态、
  trigger、session/experiment、note。
- CSV：表头为 `utc_epoch_ms, utc_iso8601, ...`；缺值为空；带 UTF-8 BOM；
  通过系统“创建文档”保存，便于多设备对表。

多设备对齐步骤：

1. 每台设备执行同一实验会话；
2. 导出 CSV；
3. 以 `utc_iso8601` 或 `utc_epoch_ms` 对齐；
4. 按 `device_model`、`subscription_id` 分组后再比较。

## 7. 采样策略（CollectionManager）

```kotlin
val primeReading = source.requestCellInfoUpdate()   // 会话开始请求一次
persist(primeReading)

while (running) {
    delay(config.sampleIntervalMs)
    persist(source.readCached(SystemClock.elapsedRealtime()))

    val stale = now - cached.cellInfoElapsedRealtimeMs >= 10_000
    if (stale && now - lastRequestElapsed >= 10_000) {
        persist(source.requestCellInfoUpdate())
    }
}
```

系统推送只负责更新 `current` 与内部缓存；落库节奏由 1–5 s 采样间隔控制。

## 8. 前台服务

`CellLoggerService` 使用 Android 14 的 `specialUse` 类型并声明 subtype，采集不
会因灭屏/切后台而停止。Service 持有 `CollectionManager`，UI 只负责启动/停止。

## 9. 四个实验的协议提示

1. **静止**：固定位置 3–10 分钟，建议 2 s 采样，观察聚合 RSRP 分布与方差。
2. **步行**：固定路线，2 s 采样 + GPS 1 s，同时记录位置速度与重选/连接状态。
3. **屏幕/业务状态变化**：同一位置分阶段执行，1 s 采样，保留阶段切换时间戳。
4. **多设备对比**：UTC 对表，固定路线与时段，比较前先确认 Android 版本/双卡。

所有实验都要在论文/报告里注明：
Android Telephony API 提供的是 cell/connection 级信息，不是 DIAG/ML1/SSB 矢量。

## 10. 已知限制

- 某些 OEM/运营商要求 carrier privileges 才返回部分字段；
- `CellInfoNr` 的 CSI-RSRP/CSI-SINR 字段未单独落列（SS 族已落列，需要时可扩表）；
- `requestCellInfoUpdate()`/cell info 推送的实际时效由厂商 modem 决定；
- 目前“服务小区”为按注册状态与连接状态排序后的主小区；相邻小区数量已保存。
