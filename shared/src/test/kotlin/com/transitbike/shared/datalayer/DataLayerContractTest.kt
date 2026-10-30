package com.transitbike.shared.datalayer

import com.transitbike.shared.model.*
import org.junit.Assert.*
import org.junit.Test

class DataLayerContractTest {

    @Test
    fun testRouteByteRoundtrip() {
        val route = RouteModel(
            id = "route_contract_1",
            summary = "via Atlanta BeltLine",
            distanceMeters = 3360.0,
            durationSeconds = 615,
            protectedLanesPercentage = 100.0,
            polyline = "k~`nE~mmaOlC_I",
            coordinates = listOf(LatLng(33.7700, -84.3800), LatLng(33.7725, -84.3656)),
            isSafestDefault = true
        )

        val bytes = DataLayerContract.encodeRoute(route)
        assertTrue(bytes.isNotEmpty())

        val decoded = DataLayerContract.decodeRoute(bytes)
        assertEquals(route.id, decoded.id)
        assertEquals(route.summary, decoded.summary)
        assertEquals(route.distanceMeters, decoded.distanceMeters, 0.01)
        assertTrue(decoded.isSafestDefault)
    }

    @Test
    fun testNavStateByteRoundtrip() {
        val state = NavState(
            status = NavStatus.NAVIGATING,
            routeId = "route_xyz",
            currentManeuverIndex = 1,
            distanceToNextManeuverMeters = 35.0,
            remainingDistanceMeters = 1500.0,
            remainingDurationSeconds = 300L,
            currentSpeedMps = 4.8,
            isOffRoute = false,
            rerouteCount = 0
        )

        val bytes = DataLayerContract.encodeNavState(state)
        val decoded = DataLayerContract.decodeNavState(bytes)

        assertEquals(NavStatus.NAVIGATING, decoded.status)
        assertEquals(1, decoded.currentManeuverIndex)
        assertEquals(35.0, decoded.distanceToNextManeuverMeters, 0.01)
        assertEquals(4.8, decoded.currentSpeedMps, 0.01)
    }

    @Test
    fun testCueEventByteRoundtrip() {
        val cue = CueEvent(
            threshold = CueThreshold.NOW_15M,
            maneuver = Maneuver(
                id = "m_turn",
                instruction = "Turn left onto BeltLine",
                wayName = "BeltLine",
                distanceMeters = 14.0,
                durationSeconds = 3.0,
                angle = -90.0,
                type = ManeuverType.TURN_LEFT,
                location = LatLng(33.7725, -84.3656)
            ),
            distanceMeters = 14.0
        )

        val bytes = DataLayerContract.encodeCueEvent(cue)
        val decoded = DataLayerContract.decodeCueEvent(bytes)

        assertEquals(CueThreshold.NOW_15M, decoded.threshold)
        assertEquals(ManeuverType.TURN_LEFT, decoded.maneuver.type)
        assertEquals(14.0, decoded.distanceMeters, 0.01)
    }

    @Test
    fun testFavoritesAndRecentsByteRoundtrip() {
        val favorites = listOf(
            FavoritePlace("fav_1", "Home", "Midtown Atlanta", LatLng(33.78, -84.38), "home"),
            FavoritePlace("fav_2", "Work", "Ponce City Market", LatLng(33.77, -84.36), "work")
        )
        val favBytes = DataLayerContract.encodeFavorites(favorites)
        val decodedFavs = DataLayerContract.decodeFavorites(favBytes)
        assertEquals(2, decodedFavs.size)
        assertEquals("Home", decodedFavs[0].name)
        assertEquals("work", decodedFavs[1].iconType)

        val recents = listOf(
            RecentRide("ride_1", "Piedmont Park", LatLng(33.785, -84.37), 1761753600L, 2400.0, 480L)
        )
        val recentBytes = DataLayerContract.encodeRecents(recents)
        val decodedRecents = DataLayerContract.decodeRecents(recentBytes)
        assertEquals(1, decodedRecents.size)
        assertEquals("Piedmont Park", decodedRecents[0].destinationName)
    }
}
