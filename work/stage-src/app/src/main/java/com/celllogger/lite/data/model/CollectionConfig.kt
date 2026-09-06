package com.celllogger.lite.data.model

import com.celllogger.lite.data.source.DataSourceType

/**
 * A user-initiated logging session. Samples are grouped by [sessionId] so the
 * history/CSV can later be split per run.
 */
data class CollectionConfig(
    val sessionId: String,
    val experimentId: ExperimentId,
    val sampleIntervalMs: Int,
    val sourceType: DataSourceType = DataSourceType.TELEPHONY,
    val subscriptionId: Int? = null,
    val note: String = "",
    val minLocationIntervalMs: Long = 1_000L
) {
    init {
        require(sampleIntervalMs in 1_000..5_000) {
            "Sampling interval must be between 1 s and 5 s."
        }
    }

    companion object {
        fun create(
            experimentId: ExperimentId,
            sampleIntervalMs: Int,
            subscriptionId: Int? = null,
            note: String = ""
        ): CollectionConfig {
            val safeInterval = sampleIntervalMs.coerceIn(1_000, 5_000)
            val stamp = java.text.SimpleDateFormat(
                "yyyyMMdd-HHmmss", java.util.Locale.US
            ).format(java.util.Date())
            val unique = java.util.UUID.randomUUID().toString().take(8)
            val sessionId = "${experimentId.name}-$stamp-$unique"
            return CollectionConfig(
                sessionId = sessionId,
                experimentId = experimentId,
                sampleIntervalMs = safeInterval,
                subscriptionId = subscriptionId,
                note = note
            )
        }
    }
}

enum class ExperimentId(val label: String, val description: String) {
    STATIC(
        "实验 1：静止",
        "手机固定不动（桌面/支架），记录 3–10 分钟，观察同一位置的 RSRP/RSRQ 波动。"
    ),
    WALKING(
        "实验 2：步行",
        "沿固定路线步行 500 m 以上，手持或放口袋，对比移动中的重选/切换与信号变化。"
    ),
    STATE_CHANGE(
        "实验 3：屏幕/业务状态变化",
        "保持位置不动，依次执行屏幕亮/灭、语音/视频通话、后台大流量下载，用 1 s 采样对比状态影响。"
    ),
    MULTI_DEVICE(
        "实验 4：多设备对比",
        "多台设备同一路线、同一时段各自导出 CSV，按 UTC 时间戳对齐后对比。"
    ),
    CUSTOM("自定义", "不限实验场景的自由记录。")
}

/**
 * Latest available GPS/network location. Nullable, never synthesized.
 */
data class LocationReading(
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val altitudeMeters: Double?,
    val provider: String?,
    val elapsedRealtimeMs: Long?,
    val utcMs: Long?
)

/**
 * Latest orientation computed from the rotation-vector sensor.
 * Degrees are already converted from radians.
 */
data class AttitudeReading(
    val yawDegrees: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val elapsedRealtimeMs: Long?,
    val sensorName: String?
)
