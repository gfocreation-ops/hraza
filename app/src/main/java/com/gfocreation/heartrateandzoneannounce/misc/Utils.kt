package com.gfocreation.heartrateandzoneannounce.misc

import android.app.Activity
import android.view.WindowManager
import com.gfocreation.heartrateandzoneannounce.model.UserPhysioParams
import com.gfocreation.heartrateandzoneannounce.model.Zone
import kotlin.math.roundToInt


// Lon en Int
fun long2Int(value : Long): Int {
    return (value / 1000).toInt()
}

/** Convertit 00:05:30 → millisecondes */
fun convertHms2Ms(text: String): Long {
    return try {
        val parts = text.split(":").map { it.toIntOrNull() ?: 0 }
        val h = parts.getOrNull(0) ?: 0
        val m = parts.getOrNull(1) ?: 0
        val s = parts.getOrNull(2) ?: 0
        ((h * 3600 + m * 60 + s) * 1000).toLong()
    } catch (e: Exception) {
        0L
    }
}

/** Convertit millisecondes → 00:00:00 */
fun formatMsToHms(ms: Long, modeCourt: Boolean = false): String {
    val totalSec = (ms / 1000).toInt()
    if (modeCourt && ms < 60_000L)
        return if (ms<3_000L) String.format("%.1f sec", ms / 1000f) else "${totalSec} sec"
    return formatSecToHms(totalSec)
}

fun formatSecToHms(totalSec: Int): String {
    if (totalSec<60)
        return "${totalSec} sec"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    if (h>0)
        return "%02d:%02d:%02d".format(h, m, s)
    else
        return "%02dm%02ds".format(m, s)

}

// Zone à partir du bpm

fun bpm2ZoneId(bpm: Int, zones: List<Zone>): Zone {
    if (bpm <= zones[0].maxBpm)
        return zones[0]
    for (z in zones.subList(1, zones.size - 1)) {
        if (bpm <= z.maxBpm) {
            return z
        }
    }
    return zones.last()
}


// Luminosité

enum class TrainingBrightnessMode {
    NORMAL,
    LOW
}

fun Activity.applyTrainingBrightness(mode: TrainingBrightnessMode) {
    val params = window.attributes
    params.screenBrightness =
        if (mode == TrainingBrightnessMode.LOW) 0.05f
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = params
}

fun Activity.resetScreenBrightness() {
    val params = window.attributes
    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    window.attributes = params
}

// Valeur avec/sans décimale
fun energieValeurAff(value: Double): String {
    return if (value < 5) String.format("%.1f", value) else value.roundToInt().toString()
}

// Energie des stats

fun energieToDisplay(kcal: Double, unit: UserPhysioParams.EnergyUnit): Int {
    return when (unit) {
        UserPhysioParams.EnergyUnit.KCAL -> kcal.roundToInt()
        UserPhysioParams.EnergyUnit.KJ -> (kcal * 4.184).roundToInt()
    }
}


// Pourcent total

fun bpmToPercentFcZonesAff(bpm: Int, zones: List<Zone>): String {
    val prc = bpmToPercentFcZones(bpm, zones).roundToInt()
    return " ${prc}%"
}

private fun bpmToPercentFcZones(bpm: Int, zones: List<Zone>): Float {
    val z = bpm2ZoneId(bpm, zones)

    val minP = z.minPercent
    val maxP = z.maxPercent
    val minB = z.minBpm
    val maxB = z.maxBpm

    if (maxB <= minB) return minP.toFloat()

    val p = (bpm - minB).toFloat() / (maxB - minB)
    return minP + p * (maxP - minP)
}


// Pourcent dans la zone

data class ZonePosition(
    val zone: Zone,
    val percentInZone: Float   // 0.0f → 1.0f
)

fun bpm2ZoneIdPrc(bpm: Int, zones: List<Zone>): ZonePosition {

    val zone = bpm2ZoneId(bpm, zones)

    val min = zone.minBpm
    val max = zone.maxBpm

    val percent = if (max > min) {
        ((bpm - min).toFloat() / (max - min).toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    return ZonePosition(zone, percent)
}
