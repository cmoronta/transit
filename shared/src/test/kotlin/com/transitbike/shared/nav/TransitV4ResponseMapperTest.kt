package com.transitbike.shared.nav

import com.transitbike.shared.model.BikeProtectionType
import com.transitbike.shared.model.ManeuverType
import org.junit.Assert.*
import org.junit.Test

class TransitV4ResponseMapperTest {

    private fun loadFixture(): String {
        val stream = javaClass.classLoader.getResourceAsStream("plan_v4_atlanta_bike.json")
            ?: error("Missing test fixture plan_v4_atlanta_bike.json")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun testParseAndMapSortsByProtectedBikePercentage() {
        val json = loadFixture()
        val routes = TransitV4ResponseMapper.parseAndMap(json)

        assertEquals(2, routes.size)

        // Route 0 must be the 92% protected bike route (10th St Cycletrack + BeltLine)
        val safestRoute = routes[0]
        assertEquals(92.0, safestRoute.protectedLanesPercentage, 0.01)
        assertTrue("Safest route should be marked default", safestRoute.isSafestDefault)
        assertTrue(safestRoute.summary.contains("Cycletrack") || safestRoute.summary.contains("BeltLine"))
        assertEquals(4, safestRoute.safetySegments.size)
        assertEquals(4, safestRoute.maneuvers.size)

        // Route 1 must be the 18% protected route (Peachtree St with caution warning)
        val fasterRoute = routes[1]
        assertEquals(18.0, fasterRoute.protectedLanesPercentage, 0.01)
        assertFalse(fasterRoute.isSafestDefault)
        assertEquals(2, fasterRoute.safetySegments.size)
    }

    @Test
    fun testSafetySegmentProtectionTypes() {
        val json = loadFixture()
        val routes = TransitV4ResponseMapper.parseAndMap(json)
        val safest = routes[0]

        // 10th St NE Cycletrack -> PROTECTED_LANE
        assertEquals(BikeProtectionType.PROTECTED_LANE, safest.safetySegments[0].protection)
        assertEquals("10th St NE Cycletrack", safest.safetySegments[0].wayName)

        // Monroe Dr NE -> BIKE_LANE
        assertEquals(BikeProtectionType.BIKE_LANE, safest.safetySegments[1].protection)

        // Atlanta BeltLine Eastside Trail -> PROTECTED_LANE
        assertEquals(BikeProtectionType.PROTECTED_LANE, safest.safetySegments[2].protection)
    }

    @Test
    fun testManeuverTypeTranslations() {
        val json = loadFixture()
        val routes = TransitV4ResponseMapper.parseAndMap(json)
        val safest = routes[0]

        // Step 1: Head east on 10th St NE Cycletrack -> DEPART
        assertEquals(ManeuverType.DEPART, safest.maneuvers[0].type)

        // Step 2: Turn right onto Monroe Dr NE -> TURN_RIGHT
        assertEquals(ManeuverType.TURN_RIGHT, safest.maneuvers[1].type)

        // Step 3: Turn left onto Atlanta BeltLine Eastside Trail -> TURN_LEFT
        assertEquals(ManeuverType.TURN_LEFT, safest.maneuvers[2].type)

        // Step 4: Turn right toward Ponce City Market (last step) -> ARRIVE
        assertEquals(ManeuverType.ARRIVE, safest.maneuvers[3].type)
    }

    @Test
    fun testDetermineManeuverTypeAnglesAndKeywords() {
        assertEquals(
            ManeuverType.DEPART,
            TransitV4ResponseMapper.determineManeuverType(0, 5, 0.0, "Head north on Piedmont Ave")
        )
        assertEquals(
            ManeuverType.TURN_RIGHT,
            TransitV4ResponseMapper.determineManeuverType(1, 5, 90.0, "Turn right onto 10th St")
        )
        assertEquals(
            ManeuverType.SLIGHT_RIGHT,
            TransitV4ResponseMapper.determineManeuverType(2, 5, 45.0, "Bear right onto path")
        )
        assertEquals(
            ManeuverType.TURN_LEFT,
            TransitV4ResponseMapper.determineManeuverType(3, 5, -90.0, "Turn left onto BeltLine")
        )
        assertEquals(
            ManeuverType.U_TURN,
            TransitV4ResponseMapper.determineManeuverType(2, 5, 175.0, "Make a U-turn")
        )
        assertEquals(
            ManeuverType.ARRIVE,
            TransitV4ResponseMapper.determineManeuverType(4, 5, 0.0, "Arrive at Ponce City Market")
        )
    }
}
