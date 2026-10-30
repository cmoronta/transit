package com.transitbike.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RouteModelSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testRouteModelJsonRoundtrip() {
        val original = RouteModel(
            id = "route_1",
            summary = "via 10th St NE Cycletrack",
            distanceMeters = 4510.0,
            durationSeconds = 840,
            protectedLanesPercentage = 92.0,
            polyline = "g~_mE~fnaO_@mB_@mB",
            coordinates = listOf(LatLng(33.7795, -84.3834), LatLng(33.7725, -84.3656)),
            safetySegments = listOf(
                SafetySegment(
                    wayName = "10th St NE Cycletrack",
                    protection = BikeProtectionType.PROTECTED_LANE,
                    lengthMeters = 965.0,
                    polyline = "g~_mE~fnaO_@mB"
                )
            ),
            maneuvers = listOf(
                Maneuver(
                    id = "step_1",
                    instruction = "Head east on 10th St NE Cycletrack",
                    wayName = "10th St NE Cycletrack",
                    distanceMeters = 965.0,
                    durationSeconds = 180.0,
                    angle = 90.0,
                    type = ManeuverType.DEPART,
                    location = LatLng(33.7795, -84.3834)
                )
            ),
            isSafestDefault = true
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<RouteModel>(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.summary, decoded.summary)
        assertEquals(original.protectedLanesPercentage, decoded.protectedLanesPercentage, 0.001)
        assertTrue(decoded.isSafestDefault)
        assertEquals(1, decoded.safetySegments.size)
        assertEquals(BikeProtectionType.PROTECTED_LANE, decoded.safetySegments[0].protection)
        assertEquals(ManeuverType.DEPART, decoded.maneuvers[0].type)
    }

    @Test
    fun testNavStateSerialization() {
        val state = NavState(
            status = NavStatus.NAVIGATING,
            routeId = "route_test_123",
            currentManeuverIndex = 2,
            distanceToNextManeuverMeters = 48.5,
            remainingDistanceMeters = 1200.0,
            remainingDurationSeconds = 240,
            currentSpeedMps = 5.2,
            isOffRoute = false,
            rerouteCount = 1
        )

        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<NavState>(encoded)

        assertEquals(NavStatus.NAVIGATING, decoded.status)
        assertEquals("route_test_123", decoded.routeId)
        assertEquals(2, decoded.currentManeuverIndex)
        assertEquals(48.5, decoded.distanceToNextManeuverMeters, 0.01)
        assertEquals(5.2, decoded.currentSpeedMps, 0.01)
        assertEquals(1, decoded.rerouteCount)
    }
}
