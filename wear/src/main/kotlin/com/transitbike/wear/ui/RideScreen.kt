package com.transitbike.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import com.transitbike.shared.model.*
import com.transitbike.shared.nav.UnitFormatter

enum class WatchRideDisplayMode {
    TURN_VIEW,
    MAP_VIEW
}

@Composable
fun RideScreen(
    navState: NavState?,
    route: RouteModel?,
    isAmbient: Boolean = false,
    onStopRide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayMode by remember { mutableStateOf(WatchRideDisplayMode.TURN_VIEW) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (displayMode) {
            WatchRideDisplayMode.TURN_VIEW -> {
                TurnView(
                    navState = navState,
                    isAmbient = isAmbient,
                    modifier = Modifier.fillMaxSize()
                )
            }
            WatchRideDisplayMode.MAP_VIEW -> {
                WatchVectorMap(
                    route = route,
                    navState = navState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Edge Toggle Button (hidden in ambient mode to conserve battery)
        if (!isAmbient) {
            Button(
                onClick = {
                    displayMode = if (displayMode == WatchRideDisplayMode.TURN_VIEW) {
                        WatchRideDisplayMode.MAP_VIEW
                    } else {
                        WatchRideDisplayMode.TURN_VIEW
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(width = 80.dp, height = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = if (displayMode == WatchRideDisplayMode.TURN_VIEW) "Map" else "Turn",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TurnView(
    navState: NavState?,
    isAmbient: Boolean,
    modifier: Modifier = Modifier
) {
    val maneuver = navState?.currentManeuver
    val distanceM = navState?.distanceToNextManeuverMeters ?: 0.0
    val distanceStr = UnitFormatter.formatDistance(distanceM, UnitSystem.IMPERIAL)
    val remainingDistStr = UnitFormatter.formatDistance(navState?.remainingDistanceMeters ?: 0.0, UnitSystem.IMPERIAL)
    val remainingTimeStr = UnitFormatter.formatDuration(navState?.remainingDurationSeconds ?: 0L)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Maneuver Icon
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isAmbient) Color.DarkGray else Color(0xFF10B981)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (maneuver?.type) {
                    ManeuverType.TURN_LEFT, ManeuverType.SLIGHT_LEFT, ManeuverType.SHARP_LEFT -> "↰"
                    ManeuverType.TURN_RIGHT, ManeuverType.SLIGHT_RIGHT, ManeuverType.SHARP_RIGHT -> "↱"
                    ManeuverType.U_TURN -> "↶"
                    ManeuverType.ARRIVE -> "★"
                    else -> "↑"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Distance to Turn
        Text(
            text = distanceStr,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isAmbient) Color.White else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        // Street Name
        Text(
            text = maneuver?.wayName ?: "Follow route",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Remaining Trip Summary
        Text(
            text = "$remainingTimeStr · $remainingDistStr",
            fontSize = 12.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WatchVectorMap(
    route: RouteModel?,
    navState: NavState?,
    modifier: Modifier = Modifier
) {
    val coords = route?.coordinates ?: emptyList()

    Canvas(modifier = modifier.fillMaxSize()) {
        if (coords.size >= 2) {
            val minLat = coords.minOf { it.latitude }
            val maxLat = coords.maxOf { it.latitude }
            val minLon = coords.minOf { it.longitude }
            val maxLon = coords.maxOf { it.longitude }

            val latSpan = maxOf(0.005, maxLat - minLat)
            val lonSpan = maxOf(0.005, maxLon - minLon)

            val padding = 40f
            val drawWidth = size.width - padding * 2
            val drawHeight = size.height - padding * 2

            fun project(point: LatLng): Offset {
                val x = padding + ((point.longitude - minLon) / lonSpan).toFloat() * drawWidth
                val y = size.height - (padding + ((point.latitude - minLat) / latSpan).toFloat() * drawHeight)
                return Offset(x, y)
            }

            // Draw route line
            for (i in 0 until coords.size - 1) {
                drawLine(
                    color = Color(0xFF10B981),
                    start = project(coords[i]),
                    end = project(coords[i + 1]),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
            }

            // Rider location dot
            val riderLoc = navState?.currentManeuver?.location ?: coords.first()
            drawCircle(Color.White, radius = 10f, center = project(riderLoc))
            drawCircle(Color(0xFF0284C7), radius = 7f, center = project(riderLoc))
        }
    }
}
