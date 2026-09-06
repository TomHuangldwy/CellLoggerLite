# Cell Logger Lite

Cell Logger Lite 是一个 **不 root、仅使用 Android 公开 Telephony API** 的
NR/LTE 信号采集实验工具，最低支持 Android 10（API 29）。它同时记录定位
（LocationManager，不依赖 GMS）与姿态（rotation-vector 传感器），数据落
Room 数据库，并支持按原始列导出 CSV。

> Data source: Android Telephony API  
> Granularity: cell/connection-level exposed by Android  
> Not DIAG / Not ML1 / Not per-SSB vector

## 快速开始

1. 用 Android Studio（Ladybug 或更新）打开本目录，等待 Gradle Sync。
2. 把手机插到电脑，打开开发者选项中的 USB 调试。
3. 运行 `app`，在“更多 → 权限/设备检查”中授予
   `READ_PHONE_STATE`、`ACCESS_FINE_LOCATION`（Android 13+ 还会请求通知权限）。
4. 回到“采集”页，选择实验与 1–5 s 采样间隔，点击“开始采集”。
5. 结束会话后到“历史”页导出 CSV。

前置条件：手机装有有效 SIM 卡且能注册蜂窝网络；没有蜂窝能力的平板只能记录
位置/姿态，小区字段全部为 `null`。

## 目录

```text
android-cell-logger-lite-1-root/
├── app/src/main/java/com/celllogger/lite/
│   ├── CellLoggerApplication.kt        # Application 入口
│   ├── di/ServiceLocator.kt            # 手动依赖容器
│   ├── data/
│   │   ├── model/                      # CellReading、实验/采样枚举
│   │   ├── source/                     # CellDataSource + Telephony/NativeDiag
│   │   ├── local/                      # Room Entity / DAO / Database
│   │   ├── mapper/                     # 读数 → Room 行
│   │   └── repository/
│   ├── collection/                     # 采样控制器、定位、姿态
│   ├── export/CsvExporter.kt
│   ├── service/CellLoggerService.kt    # 前台服务，锁屏后继续
│   └── ui/                             # Compose 页面
├── docs/IMPLEMENTATION.md              # 设计说明与字段口径
└── app/build.gradle.kts
```

## 为什么不做“高频轮询”

`requestCellInfoUpdate()` 是主动向调制解调器要数据，Android 也会限频。因此本项目：

1. 通过 `TelephonyCallback` / `PhoneStateListener` 接收系统主动推送并缓存；
2. 定时采样（1–5 s，可配置）只读缓存；
3. 只在会话开始、用户手动触发、或缓存超过 10 s 无系统更新时
   才调用一次 `requestCellInfoUpdate()`。

每行记录都带 `trigger`、`requestElapsedRealtimeMs`、`callbackElapsedRealtimeMs`
和 `dataFreshnessMs`，保证实验数据可审计。

## 字段空值约定

凡平台没有给出的字段一律为 `null`（Room 中为 SQL NULL，CSV 中为空列）。
程序不做“0 / -160 / Integer.MAX_VALUE”填充；平台自身的
`Integer.MAX_VALUE` sentinel 在边界处统一转成 `null`。

详细设计见 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)。
