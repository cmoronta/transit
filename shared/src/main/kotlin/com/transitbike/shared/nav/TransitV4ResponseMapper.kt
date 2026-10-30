package com.transitbike.shared.nav

import com.transitbike.shared.model.*
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs

/**
 * Maps Transit API v4 (/v4/public/plan) JSON responses to typed RouteModels,
 * sorting by protected bike path percentage descending so safest route is default.
 */
object TransitV4ResponseMapper {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Parses a raw Transit API v4 JSON response string and returns routes sorted safest first.
     */
    fun parseAndMap(jsonString: String): List<RouteModel> {
        val planResponse = jsonParser.decodeFromString<TransitV4PlanResponse>(jsonString)
        return mapToRoutes(planResponse)
    }

    /**
     * Maps a deserialized TransitV4PlanResponse to domain RouteModel list.
     * Guaranteed: Safest route is at index 0 with isSafestDefault = true.
     */
    fun mapToRoutes(response: TransitV4PlanResponse): List<RouteModel> {
        val routes = mutableListOf<RouteModel>()

        for ((resultIndex, result) in response.results.withIndex()) {
            val bikeLeg = result.legs.firstOrNull { it.legMode == "personal_bike" }
                ?: result.legs.firstOrNull()
                ?: continue

            val coordinates = if (bikeLeg.polyline.isNotBlank()) {
                PolylineDecoder.decode(bikeLeg.polyline)
            } else {
                emptyList()
            }

            val safetyAnalysis = bikeLeg.safetyAnalysis
            val protectedPct = safetyAnalysis?.protectedLanesPercentage ?: 0.0

            val safetySegments = safetyAnalysis?.segments?.map { seg ->
                val segCoords = if (seg.polyline.isNotBlank()) {
                    PolylineDecoder.decode(seg.polyline)
                } else {
                    emptyList()
                }
                SafetySegment(
                    wayName = seg.wayName.ifBlank { "Bike Way" },
                    protection = mapProtection(seg.protection),
                    lengthMeters = seg.length,
                    polyline = seg.polyline,
                    coordinates = segCoords
                )
            } ?: emptyList()

            val maneuvers = mapDirectionsToManeuvers(bikeLeg.directions, coordinates)

            // Create a readable summary (e.g. "via 10th St NE Cycletrack & BeltLine")
            val summary = buildRouteSummary(safetySegments, bikeLeg.directions)

            val route = RouteModel(
                id = "route_${resultIndex + 1}_${UUID.randomUUID().toString().take(8)}",
                summary = summary,
                distanceMeters = bikeLeg.distance,
                durationSeconds = if (result.duration > 0) result.duration else bikeLeg.duration.toLong(),
                protectedLanesPercentage = protectedPct,
                polyline = bikeLeg.polyline,
                coordinates = coordinates,
                safetySegments = safetySegments,
                maneuvers = maneuvers,
                isSafestDefault = false
            )
            routes.add(route)
        }

        if (routes.isEmpty()) {
            return emptyList()
        }

        // Sort by protected_lanes_percentage descending (Safest first!)
        // Tie-break: faster duration, then shorter distance
        val sortedRoutes = routes.sortedWith(
            compareByDescending<RouteModel> { it.protectedLanesPercentage }
                .thenBy { it.durationSeconds }
                .thenBy { it.distanceMeters }
        )

        // Mark the very first route as Safest Default
        return sortedRoutes.mapIndexed { index, route ->
            if (index == 0) route.copy(isSafestDefault = true) else route
        }
    }

    private fun mapProtection(raw: String): BikeProtectionType {
        return when (raw.lowercase().trim()) {
            "protected_lane", "protected_bike_lane", "cycletrack", "trail" -> BikeProtectionType.PROTECTED_LANE
            "lane", "bike_lane", "buffered_lane" -> BikeProtectionType.BIKE_LANE
            "warning", "caution", "sharrow", "shared_lane" -> BikeProtectionType.CAUTION
            "walk", "dismount", "pedestrian" -> BikeProtectionType.WALK
            else -> BikeProtectionType.REGULAR
        }
    }

    private fun mapDirectionsToManeuvers(
        directions: List<TransitV4DirectionStep>,
        routeCoordinates: List<LatLng>
    ): List<Maneuver> {
        val maneuvers = mutableListOf<Maneuver>()
        val totalSteps = directions.size

        for ((index, step) in directions.withIndex()) {
            val stepCoords = if (step.polyline.isNotBlank()) {
                PolylineDecoder.decode(step.polyline)
            } else {
                emptyList()
            }

            val location = stepCoords.firstOrNull()
                ?: routeCoordinates.getOrNull(index)
                ?: LatLng(0.0, 0.0)

            val type = determineManeuverType(
                index = index,
                totalSteps = totalSteps,
                angle = step.angle,
                instruction = step.instruction
            )

            maneuvers.add(
                Maneuver(
                    id = "step_${index + 1}",
                    instruction = step.instruction.ifBlank { "Follow route" },
                    wayName = step.wayName.ifBlank { "Bike path" },
                    distanceMeters = step.length,
                    durationSeconds = step.duration,
                    angle = step.angle,
                    type = type,
                    location = location,
                    polyline = step.polyline
                )
            )
        }

        return maneuvers
    }

    /**
     * Translates Transit API angle (-180 to 180) and instruction text into a ManeuverType.
     */
    fun determineManeuverType(
        index: Int,
        totalSteps: Int,
        angle: Double?,
        instruction: String
    ): ManeuverType {
        val text = instruction.lowercase()

        // Arrival check (last step or mentions arrive/destination)
        if (index == totalSteps - 1 || text.contains("arrive") || text.contains("destination")) {
            return ManeuverType.ARRIVE
        }

        // Departure check (first step or mentions head/start/depart)
        if (index == 0 && (text.contains("head") || text.contains("depart") || text.contains("start"))) {
            return ManeuverType.DEPART
        }

        // Text heuristics take priority for explicit turns
        if (text.contains("u-turn")) return ManeuverType.U_TURN
        if (text.contains("sharp right")) return ManeuverType.SHARP_RIGHT
        if (text.contains("sharp left")) return ManeuverType.SHARP_LEFT
        if (text.contains("slight right") || text.contains("bear right")) return ManeuverType.SLIGHT_RIGHT
        if (text.contains("slight left") || text.contains("bear left")) return ManeuverType.SLIGHT_LEFT
        if (text.contains("turn right")) return ManeuverType.TURN_RIGHT
        if (text.contains("turn left")) return ManeuverType.TURN_LEFT
        if (text.contains("continue") || text.contains("straight")) return ManeuverType.STRAIGHT

        // Angle-based classification
        if (angle != null) {
            val absAngle = abs(angle)
            if (absAngle > 160.0) return ManeuverType.U_TURN
            if (angle in 25.0..65.0) return ManeuverType.SLIGHT_RIGHT
            if (angle in 65.0..120.0) return ManeuverType.TURN_RIGHT
            if (angle in 120.0..160.0) return ManeuverType.SHARP_RIGHT
            if (angle in -65.0..-25.0) return ManeuverType.SLIGHT_LEFT
            if (angle in -120.0..-65.0) return ManeuverType.TURN_LEFT
            if (angle in -160.0..-120.0) return ManeuverType.SHARP_LEFT
        }

        return if (index == 0) ManeuverType.DEPART else ManeuverType.STRAIGHT
    }

    private fun buildRouteSummary(
        segments: List<SafetySegment>,
        directions: List<TransitV4DirectionStep>
    ): String {
        // Find longest protected segment
        val longestProtected = segments
            .filter { it.protection == BikeProtectionType.PROTECTED_LANE }
            .maxByOrNull { it.lengthMeters }

        if (longestProtected != null && longestProtected.wayName.isNotBlank()) {
            val secondLongest = segments
                .filter { it.wayName != longestProtected.wayName && it.lengthMeters > 200 }
                .maxByOrNull { it.lengthMeters }

            return if (secondLongest != null && secondLongest.wayName.isNotBlank()) {
                "via ${longestProtected.wayName} & ${secondLongest.wayName}"
            } else {
                "via ${longestProtected.wayName}"
            }
        }

        val primaryDirection = directions.maxByOrNull { it.length }
        return if (primaryDirection != null && primaryDirection.wayName.isNotBlank()) {
            "via ${primaryDirection.wayName}"
        } else {
            "Personal Bike Route"
        }
    }
}
