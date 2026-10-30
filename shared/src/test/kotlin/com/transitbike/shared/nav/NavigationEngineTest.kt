package com.transitbike.shared.nav

import com.transitbike.shared.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NavigationEngineTest {

    private lateinit var testRoute: RouteModel
    private var simulatedTimeMs: Long = 1_000_000L

    @Before
    fun setup() {
        simulatedTimeMs = 1_000_000L

        // Route: Straight line east from (33.7700, -84.3900) to (33.7700, -84.3700) ~ 1.85 km
        val pStart = LatLng(33.7700, -84.3900)
        val pMid = LatLng(33.7700, -84.3800)
        val pEnd = LatLng(33.7700, -84.3700)

        val m1 = Maneuver(
            id = "m1",
            instruction = "Head east on 10th St",
            wayName = "10th St",
            distanceMeters = 925.0,
            durationSeconds = 180.0,
            angle = 90.0,
            type = ManeuverType.DEPART,
            location = pMid // maneuver point at halfway mark
        )
        val m2 = Maneuver(
            id = "m2",
            instruction = "Arrive at BeltLine",
            wayName = "BeltLine",
            distanceMeters = 925.0,
            durationSeconds = 180.0,
            angle = null,
            type = ManeuverType.ARRIVE,
            location = pEnd
        )

        testRoute = RouteModel(
            id = "test_route_1",
            summary = "via 10th St",
            distanceMeters = 1850.0,
            durationSeconds = 360,
            protectedLanesPercentage = 100.0,
            polyline = "",
            coordinates = listOf(pStart, pMid, pEnd),
            maneuvers = listOf(m1, m2),
            isSafestDefault = true
        )
    }

    @Test
    fun testStartNavigationInitialState() {
        val engine = NavigationEngine(testRoute, clockMs = { simulatedTimeMs })
        val state = engine.navState.value

        assertEquals(NavStatus.NAVIGATING, state.status)
        assertEquals("test_route_1", state.routeId)
        assertEquals(0, state.currentManeuverIndex)
        assertEquals("m1", state.currentManeuver?.id)
        assertEquals("m2", state.nextManeuver?.id)
        assertFalse(state.isOffRoute)
        assertEquals(0, state.rerouteCount)
    }

    @Test
    fun testCueTriggerProgressionAndDeduplication() {
        val engine = NavigationEngine(testRoute, clockMs = { simulatedTimeMs })
        val pMid = testRoute.coordinates[1] // Target of maneuver m1

        // Rider is 250m west of pMid -> No cue yet
        val loc250m = LatLng(33.7700, pMid.longitude - 0.0027)
        val (_, cue1) = engine.updateLocation(loc250m, speedMps = 5.0, nowMs = simulatedTimeMs)
        assertNull("No cue should fire at 250m", cue1)

        // Rider approaches within 180m -> APPROACH_200M fires
        val loc180m = LatLng(33.7700, pMid.longitude - 0.0019)
        val (_, cue2) = engine.updateLocation(loc180m, speedMps = 5.0, nowMs = simulatedTimeMs + 5000)
        assertNotNull("APPROACH_200M cue should fire", cue2)
        assertEquals(CueThreshold.APPROACH_200M, cue2?.threshold)

        // Rider moves to 120m -> Should NOT re-fire APPROACH_200M
        val loc120m = LatLng(33.7700, pMid.longitude - 0.0013)
        val (_, cue3) = engine.updateLocation(loc120m, speedMps = 5.0, nowMs = simulatedTimeMs + 10000)
        assertNull("Should not duplicate APPROACH_200M", cue3)

        // Rider moves to 40m -> PREPARE_50M fires
        val loc40m = LatLng(33.7700, pMid.longitude - 0.00043)
        val (_, cue4) = engine.updateLocation(loc40m, speedMps = 5.0, nowMs = simulatedTimeMs + 15000)
        assertNotNull("PREPARE_50M cue should fire", cue4)
        assertEquals(CueThreshold.PREPARE_50M, cue4?.threshold)

        // Rider moves to 10m -> NOW_15M fires
        val loc10m = LatLng(33.7700, pMid.longitude - 0.00010)
        val (_, cue5) = engine.updateLocation(loc10m, speedMps = 5.0, nowMs = simulatedTimeMs + 20000)
        assertNotNull("NOW_15M cue should fire", cue5)
        assertEquals(CueThreshold.NOW_15M, cue5?.threshold)
    }

    @Test
    fun testOffRouteTimerAndRerouteCooldown() {
        val engine = NavigationEngine(testRoute, clockMs = { simulatedTimeMs })

        // Rider is on route
        val onRoute = LatLng(33.7700, -84.3850)
        val (s1, _) = engine.updateLocation(onRoute, 5.0, simulatedTimeMs)
        assertFalse(s1.isOffRoute)

        // Rider deviates 60m north of route (threshold is 40m)
        val offRoute = LatLng(33.7706, -84.3850)

        // Tick 1: off-route detected, but 0s elapsed
        val (s2, _) = engine.updateLocation(offRoute, 5.0, simulatedTimeMs)
        assertTrue(s2.isOffRoute)
        assertEquals(NavStatus.NAVIGATING, s2.status)

        // Tick 2: 10s elapsed off-route (threshold is 15s) -> still NAVIGATING
        simulatedTimeMs += 10_000L
        val (s3, _) = engine.updateLocation(offRoute, 5.0, simulatedTimeMs)
        assertTrue(s3.isOffRoute)
        assertEquals(NavStatus.NAVIGATING, s3.status)

        // Tick 3: 16s elapsed off-route -> transitions to REROUTING!
        simulatedTimeMs += 6_000L // total 16s
        val (s4, _) = engine.updateLocation(offRoute, 5.0, simulatedTimeMs)
        assertTrue(s4.isOffRoute)
        assertEquals(NavStatus.REROUTING, s4.status)
    }

    @Test
    fun testArrivalDetectionWithin25Meters() {
        val engine = NavigationEngine(testRoute, clockMs = { simulatedTimeMs })
        val destination = testRoute.coordinates.last()

        // 10m from destination
        val nearDest = LatLng(destination.latitude, destination.longitude + 0.0001)
        val (state, _) = engine.updateLocation(nearDest, speedMps = 1.0, nowMs = simulatedTimeMs)

        assertEquals(NavStatus.ARRIVED, state.status)
        assertEquals(0.0, state.remainingDistanceMeters, 0.01)
        assertEquals(0L, state.remainingDurationSeconds)
    }
}
