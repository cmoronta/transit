package com.transitbike.shared.nav

import com.transitbike.shared.model.UnitSystem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure Kotlin unit and time formatting for bike rider UI on phone and watch.
 */
object UnitFormatter {

    private const val METERS_TO_FEET = 3.28084
    private const val METERS_TO_MILES = 0.000621371
    private const val MPS_TO_MPH = 2.23694
    private const val MPS_TO_KMH = 3.6

    /**
     * Determines whether to use Imperial or Metric based on a country code.
     * US, LR, MM default to IMPERIAL; all others to METRIC.
     */
    fun getUnitSystemForLocale(locale: Locale = Locale.getDefault()): UnitSystem {
        val country = locale.country.uppercase()
        return if (country == "US" || country == "LR" || country == "MM") {
            UnitSystem.IMPERIAL
        } else {
            UnitSystem.METRIC
        }
    }

    /**
     * Formats distance in meters into human-readable string (e.g. "500 ft", "1.4 mi", "250 m", "3.2 km").
     */
    fun formatDistance(meters: Double, unitSystem: UnitSystem): String {
        if (meters <= 0.0) {
            return if (unitSystem == UnitSystem.IMPERIAL) "0 ft" else "0 m"
        }

        return when (unitSystem) {
            UnitSystem.IMPERIAL -> {
                val miles = meters * METERS_TO_MILES
                if (miles < 0.1) {
                    val feet = (meters * METERS_TO_FEET).roundToInt()
                    val roundedFeet = when {
                        feet < 50 -> ((feet + 5) / 10) * 10
                        feet < 200 -> ((feet + 12) / 25) * 25
                        else -> ((feet + 25) / 50) * 50
                    }
                    "${maxOf(10, roundedFeet)} ft"
                } else if (miles < 10.0) {
                    String.format(Locale.US, "%.1f mi", miles)
                } else {
                    "${miles.roundToInt()} mi"
                }
            }
            UnitSystem.METRIC -> {
                if (meters < 1000.0) {
                    val roundedM = when {
                        meters < 50 -> ((meters + 5) / 10).toInt() * 10
                        meters < 200 -> ((meters + 12) / 25).toInt() * 25
                        else -> ((meters + 25) / 50).toInt() * 50
                    }
                    "${maxOf(10, roundedM)} m"
                } else if (meters < 10000.0) {
                    val km = meters / 1000.0
                    String.format(Locale.US, "%.1f km", km)
                } else {
                    "${(meters / 1000.0).roundToInt()} km"
                }
            }
        }
    }

    /**
     * Formats duration in seconds into human-readable string (e.g. "14 min", "1 hr 12 min").
     */
    fun formatDuration(seconds: Long): String {
        if (seconds < 60) {
            return "< 1 min"
        }
        val totalMinutes = (seconds + 30) / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return if (hours == 0L) {
            "$minutes min"
        } else if (minutes == 0L) {
            "$hours hr"
        } else {
            "$hours hr $minutes min"
        }
    }

    /**
     * Formats riding speed in meters/second into mph or km/h.
     */
    fun formatSpeed(speedMps: Double, unitSystem: UnitSystem): String {
        if (speedMps <= 0.1) {
            return if (unitSystem == UnitSystem.IMPERIAL) "0 mph" else "0 km/h"
        }
        return when (unitSystem) {
            UnitSystem.IMPERIAL -> {
                val mph = (speedMps * MPS_TO_MPH).roundToInt()
                "$mph mph"
            }
            UnitSystem.METRIC -> {
                val kmh = (speedMps * MPS_TO_KMH).roundToInt()
                "$kmh km/h"
            }
        }
    }

    /**
     * Formats arrival timestamp (epoch seconds) as clock time (e.g. "5:24 PM" or "17:24").
     */
    fun formatEta(
        epochSeconds: Long,
        is24Hour: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val instant = Instant.ofEpochSecond(epochSeconds)
        val zonedDateTime = instant.atZone(zoneId)
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
        return zonedDateTime.format(formatter)
    }
}
