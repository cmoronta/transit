package com.transitbike.shared.nav

import com.transitbike.shared.model.UnitSystem
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class UnitFormatterTest {

    @Test
    fun testImperialDistanceFormatting() {
        // Short distances in feet
        assertEquals("50 ft", UnitFormatter.formatDistance(15.0, UnitSystem.IMPERIAL))
        assertEquals("150 ft", UnitFormatter.formatDistance(45.0, UnitSystem.IMPERIAL))
        assertEquals("500 ft", UnitFormatter.formatDistance(152.0, UnitSystem.IMPERIAL))

        // Longer distances in miles
        assertEquals("0.8 mi", UnitFormatter.formatDistance(1287.0, UnitSystem.IMPERIAL))
        assertEquals("2.8 mi", UnitFormatter.formatDistance(4510.0, UnitSystem.IMPERIAL))
        assertEquals("12 mi", UnitFormatter.formatDistance(19312.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun testMetricDistanceFormatting() {
        // Short distances in meters
        assertEquals("20 m", UnitFormatter.formatDistance(18.0, UnitSystem.METRIC))
        assertEquals("150 m", UnitFormatter.formatDistance(152.0, UnitSystem.METRIC))
        assertEquals("500 m", UnitFormatter.formatDistance(500.0, UnitSystem.METRIC))

        // Longer distances in kilometers
        assertEquals("1.3 km", UnitFormatter.formatDistance(1287.0, UnitSystem.METRIC))
        assertEquals("4.5 km", UnitFormatter.formatDistance(4510.0, UnitSystem.METRIC))
        assertEquals("15 km", UnitFormatter.formatDistance(15000.0, UnitSystem.METRIC))
    }

    @Test
    fun testDurationFormatting() {
        assertEquals("< 1 min", UnitFormatter.formatDuration(30L))
        assertEquals("14 min", UnitFormatter.formatDuration(840L))
        assertEquals("1 hr", UnitFormatter.formatDuration(3600L))
        assertEquals("1 hr 12 min", UnitFormatter.formatDuration(4320L))
        assertEquals("2 hr 5 min", UnitFormatter.formatDuration(7500L))
    }

    @Test
    fun testSpeedFormatting() {
        // 5 m/s ~ 18 km/h ~ 11.2 mph
        assertEquals("11 mph", UnitFormatter.formatSpeed(5.0, UnitSystem.IMPERIAL))
        assertEquals("18 km/h", UnitFormatter.formatSpeed(5.0, UnitSystem.METRIC))

        // Stopped / zero speed
        assertEquals("0 mph", UnitFormatter.formatSpeed(0.05, UnitSystem.IMPERIAL))
        assertEquals("0 km/h", UnitFormatter.formatSpeed(0.05, UnitSystem.METRIC))
    }

    @Test
    fun testEtaFormatting() {
        // 2025-10-29 16:30:00 UTC -> in UTC it is 4:30 PM
        val epochSeconds = 1761755400L
        val formatted = UnitFormatter.formatEta(epochSeconds, is24Hour = false, zoneId = ZoneId.of("UTC"))
        assertEquals("4:30 PM", formatted)

        val formatted24 = UnitFormatter.formatEta(epochSeconds, is24Hour = true, zoneId = ZoneId.of("UTC"))
        assertEquals("16:30", formatted24)
    }

    @Test
    fun testLocaleUnitSystemDetection() {
        assertEquals(UnitSystem.IMPERIAL, UnitFormatter.getUnitSystemForLocale(Locale.US))
        assertEquals(UnitSystem.METRIC, UnitFormatter.getUnitSystemForLocale(Locale.CANADA))
        assertEquals(UnitSystem.METRIC, UnitFormatter.getUnitSystemForLocale(Locale.FRANCE))
        assertEquals(UnitSystem.METRIC, UnitFormatter.getUnitSystemForLocale(Locale.GERMANY))
    }
}
