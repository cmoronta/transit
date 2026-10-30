package com.transitbike.shared.nav

import com.transitbike.shared.model.LatLng
import kotlin.math.*

/**
 * Result of snapping a GPS coordinate to a polyline segment.
 */
data class SnapResult(
    val snappedPoint: LatLng,
    val segmentIndex: Int,
    val distanceMeters: Double,
    val segmentFraction: Double
)

/**
 * High-performance polyline decoding and geometric utilities for bike navigation.
 */
object PolylineDecoder {

    private const val EARTH_RADIUS_METERS = 6371008.8

    /**
     * Decodes a Google encoded polyline string into a list of LatLng points.
     */
    fun decode(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(LatLng(lat / 1e5, lng / 1e5))
        }

        return poly
    }

    /**
     * Encodes a list of LatLng points into a Google encoded polyline string.
     */
    fun encode(points: List<LatLng>): String {
        val result = StringBuilder()
        var lastLat = 0
        var lastLng = 0

        for (point in points) {
            val lat = (point.latitude * 1e5).roundToInt()
            val lng = (point.longitude * 1e5).roundToInt()

            encodeSignedNumber(lat - lastLat, result)
            encodeSignedNumber(lng - lastLng, result)

            lastLat = lat
            lastLng = lng
        }

        return result.toString()
    }

    private fun encodeSignedNumber(num: Int, out: StringBuilder) {
        var sgnNum = if (num < 0) (num shl 1).inv() else (num shl 1)
        while (sgnNum >= 0x20) {
            val nextVal = (0x20 or (sgnNum and 0x1f)) + 63
            out.append(nextVal.toChar())
            sgnNum = sgnNum shr 5
        }
        out.append((sgnNum + 63).toChar())
    }

    /**
     * Calculates the great-circle distance between two coordinates in meters using Haversine formula.
     */
    fun haversineDistanceM(p1: LatLng, p2: LatLng): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLatRad = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLonRad = Math.toRadians(p2.longitude - p1.longitude)

        val sinDeltaLat = sin(deltaLatRad / 2.0)
        val sinDeltaLon = sin(deltaLonRad / 2.0)

        val a = sinDeltaLat * sinDeltaLat +
                cos(lat1Rad) * cos(lat2Rad) * sinDeltaLon * sinDeltaLon
        val c = 2.0 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates initial bearing (azimuth) from p1 to p2 in degrees [0, 360).
     */
    fun bearingDeg(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /**
     * Projects a point onto a line segment between start and end.
     */
    fun projectOntoSegment(point: LatLng, start: LatLng, end: LatLng): SnapResult {
        val midLatRad = Math.toRadians((start.latitude + end.latitude) / 2.0)
        val cosMid = cos(midLatRad)

        // Equirectangular projection local flat coordinates in meters
        val startX = 0.0
        val startY = 0.0
        val endX = Math.toRadians(end.longitude - start.longitude) * EARTH_RADIUS_METERS * cosMid
        val endY = Math.toRadians(end.latitude - start.latitude) * EARTH_RADIUS_METERS
        val ptX = Math.toRadians(point.longitude - start.longitude) * EARTH_RADIUS_METERS * cosMid
        val ptY = Math.toRadians(point.latitude - start.latitude) * EARTH_RADIUS_METERS

        val dx = endX - startX
        val dy = endY - startY
        val lenSq = dx * dx + dy * dy

        val fraction = if (lenSq < 1e-6) {
            0.0
        } else {
            val dot = (ptX - startX) * dx + (ptY - startY) * dy
            (dot / lenSq).coerceIn(0.0, 1.0)
        }

        val snappedLat = start.latitude + fraction * (end.latitude - start.latitude)
        val snappedLon = start.longitude + fraction * (end.longitude - start.longitude)
        val snapped = LatLng(snappedLat, snappedLon)
        val distance = haversineDistanceM(point, snapped)

        return SnapResult(snapped, 0, distance, fraction)
    }

    /**
     * Finds the closest point on a polyline to the given point.
     */
    fun findClosestPointOnPolyline(point: LatLng, polyline: List<LatLng>): SnapResult {
        if (polyline.isEmpty()) {
            return SnapResult(point, 0, Double.MAX_VALUE, 0.0)
        }
        if (polyline.size == 1) {
            return SnapResult(polyline[0], 0, haversineDistanceM(point, polyline[0]), 0.0)
        }

        var minDistance = Double.MAX_VALUE
        var bestSnap = SnapResult(polyline[0], 0, Double.MAX_VALUE, 0.0)

        for (i in 0 until polyline.size - 1) {
            val start = polyline[i]
            val end = polyline[i + 1]
            val snap = projectOntoSegment(point, start, end)
            if (snap.distanceMeters < minDistance) {
                minDistance = snap.distanceMeters
                bestSnap = snap.copy(segmentIndex = i)
            }
        }

        return bestSnap
    }

    /**
     * Computes the remaining distance along the polyline starting from the snapped point on segmentIndex.
     */
    fun remainingDistanceM(polyline: List<LatLng>, fromSegmentIndex: Int, snappedPoint: LatLng): Double {
        if (polyline.size < 2 || fromSegmentIndex >= polyline.size - 1) {
            return 0.0
        }

        var total = haversineDistanceM(snappedPoint, polyline[fromSegmentIndex + 1])
        for (i in (fromSegmentIndex + 1) until polyline.size - 1) {
            total += haversineDistanceM(polyline[i], polyline[i + 1])
        }
        return total
    }

    /**
     * Computes the total cumulative length of a polyline in meters.
     */
    fun totalLengthM(polyline: List<LatLng>): Double {
        if (polyline.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until polyline.size - 1) {
            sum += haversineDistanceM(polyline[i], polyline[i + 1])
        }
        return sum
    }
}
