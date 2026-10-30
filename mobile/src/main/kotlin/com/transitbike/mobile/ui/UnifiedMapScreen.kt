package com.transitbike.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transitbike.mobile.ui.theme.*
import com.transitbike.shared.model.*
import com.transitbike.shared.nav.UnitFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val navState by viewModel.activeNavState.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = when (uiState.mode) {
            ScreenMode.ACTIVE_RIDING -> 140.dp
            ScreenMode.ROUTE_PREVIEW -> 240.dp
            ScreenMode.EXPLORE -> 180.dp
        },
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            when (uiState.mode) {
                ScreenMode.EXPLORE -> ExploreSheetContent(
                    favorites = uiState.favorites,
                    recents = uiState.recents,
                    onSelectFavorite = { viewModel.selectDestination(it) },
                    onSelectRecent = { viewModel.selectRecent(it) },
                    onOpenAdmin = { viewModel.openAdminDiagnostics() }
                )
                ScreenMode.ROUTE_PREVIEW -> RoutePreviewSheetContent(
                    routes = uiState.availableRoutes,
                    selectedRoute = uiState.selectedRoute,
                    onSelectRoute = { viewModel.selectRoute(it) },
                    onStartRide = { viewModel.startRide(context) }
                )
                ScreenMode.ACTIVE_RIDING -> ActiveRidingSheetContent(
                    navState = navState,
                    onEndRide = { viewModel.stopRide(context) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Interactive Map Background (Vector Route Visualization)
            VectorMapCanvas(
                selectedRoute = uiState.selectedRoute,
                currentLocation = uiState.origin,
                modifier = Modifier.fillMaxSize()
            )

            // Top Overlays by Mode
            when (uiState.mode) {
                ScreenMode.EXPLORE -> {
                    ExploreTopBar(
                        favorites = uiState.favorites,
                        onSelectFavorite = { viewModel.selectDestination(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                    )
                }
                ScreenMode.ROUTE_PREVIEW -> {
                    RoutePreviewTopHeader(
                        originName = uiState.originName,
                        destinationName = uiState.destinationName,
                        onBack = { viewModel.cancelRoutePreview() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                    )
                }
                ScreenMode.ACTIVE_RIDING -> {
                    ActiveRidingTurnBanner(
                        navState = navState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                    )

                    // Speedometer floating badge
                    SpeedometerBadge(
                        speedMps = navState?.currentSpeedMps ?: 0.0,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    if (uiState.showAdminSheet) {
        AdminDiagnosticsSheet(
            isAdmin = uiState.isAdmin,
            quotaSnapshot = uiState.adminQuotaSnapshot,
            onRefresh = { viewModel.refreshAdminQuota() },
            onDismiss = { viewModel.dismissAdminDiagnostics() }
        )
    }
}

// ----------------------------------------------------
// Top Overlay Components
// ----------------------------------------------------

@Composable
private fun ExploreTopBar(
    favorites: List<FavoritePlace>,
    onSelectFavorite: (FavoritePlace) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Where to ride?",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick destination chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites) { place ->
                SuggestionChip(
                    onClick = { onSelectFavorite(place) },
                    label = { Text(place.name) },
                    icon = {
                        val icon = when (place.iconType) {
                            "home" -> Icons.Default.Home
                            "work" -> Icons.Default.Work
                            else -> Icons.Default.DirectionsBike
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun RoutePreviewTopHeader(
    originName: String,
    destinationName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = originName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = destinationName.ifBlank { "Destination" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActiveRidingTurnBanner(
    navState: NavState?,
    modifier: Modifier = Modifier
) {
    val maneuver = navState?.currentManeuver
    val distanceM = navState?.distanceToNextManeuverMeters ?: 0.0
    val distanceStr = UnitFormatter.formatDistance(distanceM, UnitSystem.IMPERIAL)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ColorProtectedBikePath),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (maneuver?.type) {
                    ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnLeft
                    ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnRight
                    ManeuverType.U_TURN -> Icons.Default.UturnLeft
                    ManeuverType.ARRIVE -> Icons.Default.Place
                    else -> Icons.Default.Straight
                }
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "In $distanceStr",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = maneuver?.instruction ?: "Proceed along route",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpeedometerBadge(
    speedMps: Double,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = UnitFormatter.formatSpeed(speedMps, UnitSystem.IMPERIAL),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Speed",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ----------------------------------------------------
// Drawer / Sheet Content Components
// ----------------------------------------------------

@Composable
private fun ExploreSheetContent(
    favorites: List<FavoritePlace>,
    recents: List<RecentRide>,
    onSelectFavorite: (FavoritePlace) -> Unit,
    onSelectRecent: (RecentRide) -> Unit,
    onOpenAdmin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Rides", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenAdmin) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Diagnostics", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        recents.forEach { ride ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectRecent(ride) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ride.destinationName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "${UnitFormatter.formatDistance(ride.distanceMeters, UnitSystem.IMPERIAL)} · ${UnitFormatter.formatDuration(ride.durationSeconds)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePreviewSheetContent(
    routes: List<RouteModel>,
    selectedRoute: RouteModel?,
    onSelectRoute: (RouteModel) -> Unit,
    onStartRide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text("Bike Routes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        routes.forEach { route ->
            val isSelected = route.id == selectedRoute?.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onSelectRoute(route) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${UnitFormatter.formatDuration(route.durationSeconds)} · ${UnitFormatter.formatDistance(route.distanceMeters, UnitSystem.IMPERIAL)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        // Safety Badge
                        if (route.isSafestDefault) {
                            Badge(containerColor = ColorProtectedBikePath) {
                                Text(
                                    text = "${route.protectedLanesPercentage.toInt()}% Protected · Safest ★",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "${route.protectedLanesPercentage.toInt()}% Protected",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = route.summary,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartRide,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Navigation, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Ride", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveRidingSheetContent(
    navState: NavState?,
    onEndRide: () -> Unit
) {
    val remainingDist = navState?.remainingDistanceMeters ?: 0.0
    val remainingSeconds = navState?.remainingDurationSeconds ?: 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${UnitFormatter.formatDuration(remainingSeconds)} · ${UnitFormatter.formatDistance(remainingDist, UnitSystem.IMPERIAL)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Remaining Trip",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onEndRide,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("End Ride", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ----------------------------------------------------
// Vector Route Map Canvas (High performance 60fps fallback)
// ----------------------------------------------------

@Composable
private fun VectorMapCanvas(
    selectedRoute: RouteModel?,
    currentLocation: LatLng,
    modifier: Modifier = Modifier
) {
    val polylineCoords = selectedRoute?.coordinates ?: emptyList()

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        if (polylineCoords.size >= 2) {
            // Find coordinate bounding box
            val minLat = polylineCoords.minOf { it.latitude }
            val maxLat = polylineCoords.maxOf { it.latitude }
            val minLon = polylineCoords.minOf { it.longitude }
            val maxLon = polylineCoords.maxOf { it.longitude }

            val latSpan = maxOf(0.005, maxLat - minLat)
            val lonSpan = maxOf(0.005, maxLon - minLon)

            val padding = 80f
            val drawWidth = size.width - padding * 2
            val drawHeight = size.height - padding * 2

            fun project(point: LatLng): Offset {
                val x = padding + ((point.longitude - minLon) / lonSpan).toFloat() * drawWidth
                val y = size.height - (padding + ((point.latitude - minLat) / latSpan).toFloat() * drawHeight)
                return Offset(x, y)
            }

            // Draw route shadow/casing
            for (i in 0 until polylineCoords.size - 1) {
                val start = project(polylineCoords[i])
                val end = project(polylineCoords[i + 1])
                drawLine(
                    color = Color.Black.copy(alpha = 0.25f),
                    start = start,
                    end = end,
                    strokeWidth = 22f,
                    cap = StrokeCap.Round
                )
            }

            // Draw safety-colored route segments
            val segments = selectedRoute?.safetySegments ?: emptyList()
            if (segments.isNotEmpty()) {
                for (seg in segments) {
                    val segCoords = seg.coordinates
                    val segColor = getProtectionColor(seg.protection)
                    for (i in 0 until segCoords.size - 1) {
                        drawLine(
                            color = segColor,
                            start = project(segCoords[i]),
                            end = project(segCoords[i + 1]),
                            strokeWidth = 14f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            } else {
                for (i in 0 until polylineCoords.size - 1) {
                    drawLine(
                        color = ColorProtectedBikePath,
                        start = project(polylineCoords[i]),
                        end = project(polylineCoords[i + 1]),
                        strokeWidth = 14f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw start / end markers
            drawCircle(Color.White, radius = 14f, center = project(polylineCoords.first()))
            drawCircle(ColorProtectedBikePath, radius = 9f, center = project(polylineCoords.first()))

            drawCircle(Color.White, radius = 16f, center = project(polylineCoords.last()))
            drawCircle(Color.Red, radius = 11f, center = project(polylineCoords.last()))
        }
    }
}
