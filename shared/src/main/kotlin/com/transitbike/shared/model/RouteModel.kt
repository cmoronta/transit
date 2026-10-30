package com.transitbike.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Geographic coordinate pair (WGS 84).
 */
@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * Transit API v4 Plan response structure (/v4/public/plan).
 */
@Serializable
data class TransitV4PlanResponse(
    val results: List<TransitV4Result> = emptyList()
)

@Serializable
data class TransitV4Result(
    val accessibility: String? = null,
    @SerialName("start_time") val startTime: Long = 0L,
    @SerialName("end_time") val endTime: Long = 0L,
    val duration: Long = 0L,
    val fare: Double? = null,
    val legs: List<TransitV4SimpleLeg> = emptyList()
)

@Serializable
data class TransitV4SimpleLeg(
    @SerialName("leg_mode") val legMode: String = "",
    @SerialName("start_time") val startTime: Long = 0L,
    @SerialName("end_time") val endTime: Long = 0L,
    val duration: Double = 0.0,
    val distance: Double = 0.0,
    val polyline: String = "",
    @SerialName("safety_analysis") val safetyAnalysis: TransitV4SafetyAnalysis? = null,
    val directions: List<TransitV4DirectionStep> = emptyList()
)

@Serializable
data class TransitV4SafetyAnalysis(
    @SerialName("protected_lanes_percentage") val protectedLanesPercentage: Double = 0.0,
    @SerialName("lanes_percentage") val lanesPercentage: Double = 0.0,
    @SerialName("safe_percentage") val safePercentage: Double = 0.0,
    val segments: List<TransitV4SafetySegment> = emptyList()
)

@Serializable
data class TransitV4SafetySegment(
    @SerialName("way_name") val wayName: String = "",
    val protection: String = "default",
    val length: Double = 0.0,
    val polyline: String = ""
)

@Serializable
data class TransitV4DirectionStep(
    val length: Double = 0.0,
    @SerialName("way_name") val wayName: String = "",
    val duration: Double = 0.0,
    val angle: Double? = null,
    val instruction: String = "",
    val polyline: String = ""
)

// ==========================================
// Domain Models for Phone & Wear Navigation
// ==========================================

@Serializable
enum class BikeProtectionType {
    PROTECTED_LANE,
    BIKE_LANE,
    CAUTION,
    WALK,
    REGULAR
}

@Serializable
data class SafetySegment(
    val wayName: String,
    val protection: BikeProtectionType,
    val lengthMeters: Double,
    val polyline: String,
    val coordinates: List<LatLng> = emptyList()
)

@Serializable
enum class ManeuverType {
    DEPART,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    STRAIGHT,
    U_TURN,
    ARRIVE
}

@Serializable
data class Maneuver(
    val id: String,
    val instruction: String,
    val wayName: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val angle: Double?,
    val type: ManeuverType,
    val location: LatLng,
    val polyline: String = ""
)

@Serializable
data class RouteModel(
    val id: String,
    val summary: String,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val protectedLanesPercentage: Double,
    val polyline: String,
    val coordinates: List<LatLng> = emptyList(),
    val safetySegments: List<SafetySegment> = emptyList(),
    val maneuvers: List<Maneuver> = emptyList(),
    val isSafestDefault: Boolean = false
)

@Serializable
enum class NavStatus {
    IDLE,
    PLANNING,
    NAVIGATING,
    ARRIVED,
    REROUTING
}

@Serializable
data class NavState(
    val status: NavStatus = NavStatus.IDLE,
    val routeId: String = "",
    val currentManeuverIndex: Int = 0,
    val currentManeuver: Maneuver? = null,
    val nextManeuver: Maneuver? = null,
    val distanceToNextManeuverMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingDurationSeconds: Long = 0L,
    val currentSpeedMps: Double = 0.0,
    val isOffRoute: Boolean = false,
    val offRouteDurationSeconds: Long = 0L,
    val rerouteCount: Int = 0
)

@Serializable
enum class CueThreshold {
    APPROACH_200M,
    PREPARE_50M,
    NOW_15M
}

@Serializable
data class CueEvent(
    val threshold: CueThreshold,
    val maneuver: Maneuver,
    val distanceMeters: Double
)

@Serializable
data class FavoritePlace(
    val id: String,
    val name: String,
    val address: String,
    val location: LatLng,
    val iconType: String = "star" // "home", "work", "gym", "star"
)

@Serializable
data class RecentRide(
    val id: String,
    val destinationName: String,
    val destinationLocation: LatLng,
    val timestamp: Long,
    val distanceMeters: Double,
    val durationSeconds: Long
)

@Serializable
enum class UnitSystem {
    METRIC,
    IMPERIAL
}
