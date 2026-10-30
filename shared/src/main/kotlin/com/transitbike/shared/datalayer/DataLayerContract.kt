package com.transitbike.shared.datalayer

import com.transitbike.shared.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Message and Data Client paths and serialization for Phone <-> Wear OS synchronization.
 */
object DataLayerContract {

    // MessageClient Paths (Low-latency commands & high-priority triggers)
    const val PATH_TRIP_START = "/trip/start"
    const val PATH_TRIP_STOP = "/trip/stop"
    const val PATH_NAV_CUE = "/nav/cue"
    const val PATH_NAV_REROUTE = "/nav/reroute"
    const val PATH_PING = "/diagnostics/ping"

    // DataClient Paths (Persistent state, route sync, tile & complication telemetry)
    const val PATH_ACTIVE_ROUTE = "/nav/route"
    const val PATH_NAV_STATE = "/nav/state"
    const val PATH_FAVORITES = "/favorites"
    const val PATH_RECENTS = "/recents"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // --- Typed Helper Functions ---

    fun encodeRoute(route: RouteModel): ByteArray =
        json.encodeToString(route).encodeToByteArray()

    fun decodeRoute(bytes: ByteArray): RouteModel =
        json.decodeFromString(bytes.decodeToString())

    fun encodeNavState(state: NavState): ByteArray =
        json.encodeToString(state).encodeToByteArray()

    fun decodeNavState(bytes: ByteArray): NavState =
        json.decodeFromString(bytes.decodeToString())

    fun encodeCueEvent(cue: CueEvent): ByteArray =
        json.encodeToString(cue).encodeToByteArray()

    fun decodeCueEvent(bytes: ByteArray): CueEvent =
        json.decodeFromString(bytes.decodeToString())

    fun encodeFavorites(favorites: List<FavoritePlace>): ByteArray =
        json.encodeToString(favorites).encodeToByteArray()

    fun decodeFavorites(bytes: ByteArray): List<FavoritePlace> =
        json.decodeFromString(bytes.decodeToString())

    fun encodeRecents(recents: List<RecentRide>): ByteArray =
        json.encodeToString(recents).encodeToByteArray()

    fun decodeRecents(bytes: ByteArray): List<RecentRide> =
        json.decodeFromString(bytes.decodeToString())
}
