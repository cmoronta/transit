package com.transitbike.shared.nav

import com.transitbike.shared.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Pure Kotlin Navigation Engine responsible for:
 * 1. Snapping GPS coordinates to route polyline
 * 2. Emitting 200m / 50m / 15m maneuver cues
 * 3. Detecting off-route state (>= 15s > 40m)
 * 4. Enforcing reroute cooldown (20s) and trip cap (max 10)
 * 5. Detecting arrival (< 25m from destination)
 */
class NavigationEngine(
    private var activeRoute: RouteModel? = null,
    private val clockMs: () => Long = { System.currentTimeMillis() }
) {

    companion object {
        const val OFF_ROUTE_DISTANCE_THRESHOLD_M = 40.0
        const val OFF_ROUTE_DURATION_THRESHOLD_MS = 15_000L
        const val REROUTE_COOLDOWN_MS = 20_000L
        const val MAX_REROUTES_PER_TRIP = 10
        const val ARRIVAL_DISTANCE_THRESHOLD_M = 25.0
        const val MANEUVER_COMPLETION_DISTANCE_M = 20.0
    }

    private val _navState = MutableStateFlow(NavState())
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    private val _cueEvents = MutableSharedFlow<CueEvent>(extraBufferCapacity = 16)
    val cueEvents: SharedFlow<CueEvent> = _cueEvents.asSharedFlow()

    private var currentManeuverIndex = 0
    private val triggeredCues = mutableSetOf<String>()

    private var firstOffRouteTimeMs: Long? = null
    private var lastRerouteTimeMs: Long = 0L
    private var rerouteCount: Int = 0

    init {
        activeRoute?.let { startRoute(it) }
    }

    /**
     * Initializes or resets navigation with a new route.
     */
    fun startRoute(route: RouteModel) {
        activeRoute = route
        currentManeuverIndex = 0
        triggeredCues.clear()
        firstOffRouteTimeMs = null
        rerouteCount = 0

        val firstManeuver = route.maneuvers.firstOrNull()
        val secondManeuver = route.maneuvers.getOrNull(1)

        _navState.value = NavState(
            status = NavStatus.NAVIGATING,
            routeId = route.id,
            currentManeuverIndex = 0,
            currentManeuver = firstManeuver,
            nextManeuver = secondManeuver,
            distanceToNextManeuverMeters = firstManeuver?.distanceMeters ?: 0.0,
            remainingDistanceMeters = route.distanceMeters,
            remainingDurationSeconds = route.durationSeconds,
            currentSpeedMps = 0.0,
            isOffRoute = false,
            offRouteDurationSeconds = 0L,
            rerouteCount = 0
        )
    }

    /**
     * Stops navigation and resets engine to IDLE state.
     */
    fun stopNavigation() {
        activeRoute = null
        firstOffRouteTimeMs = null
        _navState.value = NavState(status = NavStatus.IDLE)
    }

    /**
     * Applies a newly recalculated reroute.
     */
    fun applyReroute(newRoute: RouteModel) {
        val currentRerouteCount = rerouteCount + 1
        startRoute(newRoute)
        this.rerouteCount = currentRerouteCount
        this.lastRerouteTimeMs = clockMs()
        _navState.value = _navState.value.copy(rerouteCount = currentRerouteCount)
    }

    /**
     * Ingests a new GPS fix and updates state, maneuvers, cues, and off-route metrics.
     * @return Pair of updated NavState and optional CueEvent triggered on this tick.
     */
    fun updateLocation(
        location: LatLng,
        speedMps: Double,
        nowMs: Long = clockMs()
    ): Pair<NavState, CueEvent?> {
        val route = activeRoute ?: return Pair(_navState.value, null)
        val coordinates = route.coordinates

        if (coordinates.isEmpty()) {
            return Pair(_navState.value, null)
        }

        val destination = coordinates.last()
        val distToDestination = PolylineDecoder.haversineDistanceM(location, destination)

        // 1. Arrival Check
        if (distToDestination <= ARRIVAL_DISTANCE_THRESHOLD_M) {
            val arrivedState = _navState.value.copy(
                status = NavStatus.ARRIVED,
                remainingDistanceMeters = 0.0,
                remainingDurationSeconds = 0L,
                distanceToNextManeuverMeters = 0.0,
                isOffRoute = false,
                currentSpeedMps = speedMps
            )
            _navState.value = arrivedState
            return Pair(arrivedState, null)
        }

        // 2. Snap to Polyline
        val snap = PolylineDecoder.findClosestPointOnPolyline(location, coordinates)
        val isCurrentlyOffTrack = snap.distanceMeters > OFF_ROUTE_DISTANCE_THRESHOLD_M

        var offRouteDurationMs = 0L
        var shouldTriggerReroute = false

        if (isCurrentlyOffTrack) {
            if (firstOffRouteTimeMs == null) {
                firstOffRouteTimeMs = nowMs
            }
            offRouteDurationMs = nowMs - firstOffRouteTimeMs!!

            // Generous off-route trigger: 15s off-route + 20s cooldown + <= 10 reroutes
            if (offRouteDurationMs >= OFF_ROUTE_DURATION_THRESHOLD_MS) {
                val timeSinceLastReroute = nowMs - lastRerouteTimeMs
                if (timeSinceLastReroute >= REROUTE_COOLDOWN_MS && rerouteCount < MAX_REROUTES_PER_TRIP) {
                    shouldTriggerReroute = true
                }
            }
        } else {
            // Rider is back on route - reset timer
            firstOffRouteTimeMs = null
        }

        // 3. Maneuver Step Progression
        advanceManeuversIfNeeded(location, snap.segmentIndex, route.maneuvers)

        val currentManeuver = route.maneuvers.getOrNull(currentManeuverIndex)
        val nextManeuver = route.maneuvers.getOrNull(currentManeuverIndex + 1)

        val distanceToNextManeuver = if (currentManeuver != null) {
            PolylineDecoder.haversineDistanceM(location, currentManeuver.location)
        } else {
            0.0
        }

        val remainingDistM = PolylineDecoder.remainingDistanceM(
            coordinates,
            snap.segmentIndex,
            snap.snappedPoint
        )

        // Estimated remaining time based on current speed or average bike speed (15 km/h ~ 4.16 m/s)
        val effectiveSpeed = if (speedMps > 1.5) speedMps else 4.16
        val remainingSeconds = (remainingDistM / effectiveSpeed).toLong()

        // 4. Cue Trigger Evaluation
        val triggeredCue = evaluateCue(currentManeuver, distanceToNextManeuver)

        val newStatus = if (shouldTriggerReroute) {
            NavStatus.REROUTING
        } else {
            NavStatus.NAVIGATING
        }

        val newState = NavState(
            status = newStatus,
            routeId = route.id,
            currentManeuverIndex = currentManeuverIndex,
            currentManeuver = currentManeuver,
            nextManeuver = nextManeuver,
            distanceToNextManeuverMeters = distanceToNextManeuver,
            remainingDistanceMeters = remainingDistM,
            remainingDurationSeconds = max(0L, remainingSeconds),
            currentSpeedMps = speedMps,
            isOffRoute = isCurrentlyOffTrack,
            offRouteDurationSeconds = offRouteDurationMs / 1000L,
            rerouteCount = rerouteCount
        )

        _navState.value = newState
        triggeredCue?.let { _cueEvents.tryEmit(it) }

        return Pair(newState, triggeredCue)
    }

    private fun advanceManeuversIfNeeded(
        riderLocation: LatLng,
        currentSegmentIndex: Int,
        maneuvers: List<Maneuver>
    ) {
        if (currentManeuverIndex >= maneuvers.size - 1) return

        val targetManeuver = maneuvers.getOrNull(currentManeuverIndex) ?: return
        val distToManeuver = PolylineDecoder.haversineDistanceM(riderLocation, targetManeuver.location)

        // If rider is very close to or past the maneuver point, advance
        if (distToManeuver <= MANEUVER_COMPLETION_DISTANCE_M) {
            currentManeuverIndex++
        }
    }

    private fun evaluateCue(maneuver: Maneuver?, distanceM: Double): CueEvent? {
        if (maneuver == null) return null

        val threshold = when {
            distanceM <= 15.0 -> CueThreshold.NOW_15M
            distanceM <= 50.0 -> CueThreshold.PREPARE_50M
            distanceM <= 200.0 -> CueThreshold.APPROACH_200M
            else -> null
        } ?: return null

        val cueKey = "${maneuver.id}_${threshold.name}"
        if (triggeredCues.contains(cueKey)) {
            return null
        }

        triggeredCues.add(cueKey)
        return CueEvent(threshold, maneuver, distanceM)
    }
}
