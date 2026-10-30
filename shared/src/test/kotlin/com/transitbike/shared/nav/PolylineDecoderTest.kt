package com.transitbike.shared.nav

import com.transitbike.shared.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun testEncodeDecodeRoundtrip() {
        val original = listOf(
            LatLng(33.77950, -84.38340),
            LatLng(33.77960, -84.38000),
            LatLng(33.77250, -84.36560)
        )

        val encoded = PolylineDecoder.encode(original)
        assertTrue(encoded.isNotEmpty())

        val decoded = PolylineDecoder.decode(encoded)
        assertEquals(original.size, decoded.size)

        for (i in original.indices) {
            assertEquals(original[i].latitude, decoded[i].latitude, 0.0001)
            assertEquals(original[i].longitude, decoded[i].longitude, 0.0001)
        }
    }

    @Test
    fun testHaversineDistanceKnownAtlantaLocations() {
        // Midtown 10th St NE (33.7816, -84.3831) to Ponce City Market (33.7725, -84.3656)
        val p1 = LatLng(33.7816, -84.3831)
        val p2 = LatLng(33.7725, -84.3656)

        val distanceM = PolylineDecoder.haversineDistanceM(p1, p2)
        // Straight line distance is ~1.9 km
        assertTrue("Distance should be between 1800m and 2100m, was $distanceM", distanceM in 1800.0..2100.0)
    }

    @Test
    fun testHaversineDistanceZeroForSamePoint() {
        val p = LatLng(33.7795, -84.3834)
        assertEquals(0.0, PolylineDecoder.haversineDistanceM(p, p), 0.001)
    }

    @Test
    fun testBearingCalculation() {
        val origin = LatLng(33.7700, -84.3800)
        val north = LatLng(33.7800, -84.3800)
        val east = LatLng(33.7700, -84.3700)

        val bearingNorth = PolylineDecoder.bearingDeg(origin, north)
        assertEquals(0.0, bearingNorth, 1.0)

        val bearingEast = PolylineDecoder.bearingDeg(origin, east)
        assertEquals(90.0, bearingEast, 1.0)
    }

    @Test
    fun testProjectOntoSegmentAndClosestPoint() {
        // Horizontal segment from (33.7700, -84.3900) to (33.7700, -84.3700)
        val start = LatLng(33.7700, -84.3900)
        val end = LatLng(33.7700, -84.3700)
        val polyline = listOf(start, end)

        // Point slightly north of midpoint
        val rider = LatLng(33.7702, -84.3800)
        val snap = PolylineDecoder.findClosestPointOnPolyline(rider, polyline)

        assertEquals(0, snap.segmentIndex)
        assertEquals(33.7700, snap.snappedPoint.latitude, 0.0001)
        assertEquals(-84.3800, snap.snappedPoint.longitude, 0.0005)
        assertTrue("Snap distance should be ~22m", snap.distanceMeters in 15.0..30.0)
    }

    @Test
    fun testRemainingDistance() {
        val p1 = LatLng(33.7700, -84.3900)
        val p2 = LatLng(33.7700, -84.3800)
        val p3 = LatLng(33.7700, -84.3700)
        val polyline = listOf(p1, p2, p3)

        val totalLen = PolylineDecoder.totalLengthM(polyline)
        assertTrue(totalLen > 0.0)

        val remainingFromP2 = PolylineDecoder.remainingDistanceM(polyline, 1, p2)
        val segment2Len = PolylineDecoder.haversineDistanceM(p2, p3)
        assertEquals(segment2Len, remainingFromP2, 0.01)
    }
}
