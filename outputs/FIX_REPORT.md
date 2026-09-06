# Cell Logger Lite 编译错误修复报告

验证方式：使用 compileSdk 35 的 `android.jar`（E 盘可读 SDK）对修复后的
`TelephonyDataSource` / `CellDataSource` / `NativeDiagDataSource` /
`CellReading` / `CollectionConfig` / `TelephonyMappings` 做 Kotlin 2.0.20
类型检查，编译退出码 0（仅余弃用警告）。Room/KSP 未改动，实体与 DAO 原样。

## 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `app/.../ui/screens/CurrentCellScreen.kt` | 实体导入从 `data.model.CellSampleEntity` 改为正确的 `data.local.CellSampleEntity` |
| `app/.../util/TelephonyMappings.kt` | 删除不存在的 `ServiceState.NR_STATE_*`，改为 `NetworkRegistrationInfo.NR_STATE_*`（公开常量），并补充说明 |
| `app/.../data/source/TelephonyDataSource.kt` | 修复 NR 转型、时间戳 API、`@RequiresApi` 来源、缺失 import 等 |
| `docs/IMPLEMENTATION.md` | 更新 CellInfo 时间戳与 NR 状态说明 |

## 真正的根因（不是缓存）

用 `javap` 检查 compileSdk 35 的 `android.jar` 后确认：

1. `CellInfoNr#getCellIdentity()` / `getCellSignalStrength()` 在公开 SDK 桩中
   返回基类 `CellIdentity` / `CellSignalStrength`，因此必须先向下转型到
   `CellIdentityNr` / `CellSignalStrengthNr` 才能访问 `mccString`、`tac`、
   `nci`、`pci`、`nrarfcn`、`ssRsrp` 等。这解释了“找不到 mccString/nci/ssRsrp”
   的真实原因。
2. `CellInfo#getTimestampNanos()` 不存在于公开 `android.jar`；公开 API 为
   `getTimestampMillis()`（API 34+）与已废弃的 `getTimeStamp()`。代码据此改为
   API 34+ 用 `timestampMillis`，Android 10–13 用 `timeStamp / 1_000_000`。
3. `android.annotation.RequiresApi` 不在 `android.jar`，应使用
   `androidx.annotation.RequiresApi`（随 `androidx.core` 传递提供）。
4. `ServiceState` 没有 `NR_STATE_*` 常量，也没有可公开访问的
   `getNrState()`；`NetworkRegistrationInfo#getNrState()` 是隐藏 API。
   因此 TelephonyDataSource 的 `nrState` 显式为 `null`（宁缺毋猜）。
5. `CurrentCellScreen.kt` 导入错误导致后续所有字段 unresolved 与
   “String vs String / Int? vs Int?” 同型 mismatch 级联报错。

以上任一 unresolved 都会让 Kotlin 对同一个构造调用产生大量
“actual type is X, but X was expected” 的自相同类型不匹配，因此并非缓存损坏。

## 构建步骤

```powershell
$env:JAVA_HOME = "E:\projects\diag_learning\项目移植\AS\jbr"
.\gradlew.bat :app:assembleDebug --console=plain
```

如果增量缓存异常，可先执行 `.\gradlew.bat clean :app:assembleDebug`。
