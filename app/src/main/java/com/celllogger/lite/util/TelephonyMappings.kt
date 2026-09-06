package com.celllogger.lite.util

import android.telephony.NetworkRegistrationInfo
import android.telephony.ServiceState
import android.telephony.TelephonyManager

/**
 * Human-readable labels for Android integer enums. Unknown values are rendered
 * as null by the caller where persistence is concerned.
 */
fun dataNetworkTypeLabel(type: Int): String? = when (type) {
    TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
    TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
    TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
    TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
    TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
    TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
    TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
    TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
    TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
    TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
    TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
    TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
    TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
    TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
    TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
    TelephonyManager.NETWORK_TYPE_NR -> "NR"
    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
    TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
    else -> null
}

/**
 * Labels an NR_STATE_* integer. Those constants live on
 * [NetworkRegistrationInfo] (Android 11+), NOT on [ServiceState].
 *
 * Note for the public-API implementation: Android's public SDK exposes the
 * NR_STATE_* integer constants, but the value itself is read through
 * NetworkRegistrationInfo#getNrState(), which is a hidden/system API and is
 * absent from the public android.jar. Cell Logger Lite therefore keeps the
 * persisted nrState column NULL for the TelephonyDataSource and never guesses a
 * state. This mapper is kept for future native/diag sources and for unit
 * helpers that may receive the raw integer from a vendor extension.
 */
fun nrStateLabel(state: Int): String? = when (state) {
    NetworkRegistrationInfo.NR_STATE_NONE -> "NR_NONE"
    NetworkRegistrationInfo.NR_STATE_RESTRICTED -> "NR_RESTRICTED"
    NetworkRegistrationInfo.NR_STATE_CONNECTED -> "NR_CONNECTED"
    NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED -> "NR_NOT_RESTRICTED"
    else -> null
}

fun serviceStateLabel(state: Int): String? = when (state) {
    ServiceState.STATE_IN_SERVICE -> "IN_SERVICE"
    ServiceState.STATE_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
    ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY"
    ServiceState.STATE_POWER_OFF -> "POWER_OFF"
    else -> null
}

/**
 * Coarse registration label derived from ServiceState. A more precise
 * "registered / searching / denied" split is available through
 * NetworkRegistrationInfo from Android 11+; this method keeps API 29 parity.
 */
fun registrationStateLabel(serviceState: ServiceState?): String? {
    if (serviceState == null) return null
    return when (serviceState.state) {
        ServiceState.STATE_IN_SERVICE -> {
            if (serviceState.roaming) "REGISTERED_ROAMING" else "REGISTERED_HOME"
        }
        ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY"
        ServiceState.STATE_POWER_OFF -> "POWER_OFF"
        ServiceState.STATE_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
        else -> null
    }
}

/**
 * Convert a platform "unknown" sentinel into null. Only Integer.MAX_VALUE is
 * treated as missing; a real -140 dBm or 0 dB stays untouched.
 */
fun Int?.asKnownIntOrNull(): Int? {
    if (this == null || this == Integer.MAX_VALUE) return null
    return this
}

fun Int?.asCqiOrNull(): Int? {
    if (this == null || this == Integer.MAX_VALUE || this == 255) return null
    return this
}

fun Long?.asKnownLongOrNull(): Long? {
    if (this == null || this == Long.MAX_VALUE) return null
    return this
}
