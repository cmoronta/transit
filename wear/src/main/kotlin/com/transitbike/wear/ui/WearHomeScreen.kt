package com.transitbike.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.transitbike.shared.model.FavoritePlace
import com.transitbike.shared.model.RecentRide
import com.transitbike.shared.model.UnitSystem
import com.transitbike.shared.nav.UnitFormatter

@Composable
fun WearHomeScreen(
    favorites: List<FavoritePlace>,
    recents: List<RecentRide>,
    onSelectDestination: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader {
                Text(
                    text = "Transit Bike",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Quick destination chips
        items(favorites) { place ->
            FilledTonalButton(
                onClick = { onSelectDestination(place.name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Text(
                    text = place.name,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            ListHeader {
                Text("Recent Trips", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(recents) { ride ->
            AppCard(
                onClick = { onSelectDestination(ride.destinationName) },
                appName = { Text("Recent") },
                title = { Text(ride.destinationName, maxLines = 1) },
                time = {
                    Text(
                        "${UnitFormatter.formatDistance(ride.distanceMeters, UnitSystem.IMPERIAL)} · ${UnitFormatter.formatDuration(ride.durationSeconds)}",
                        fontSize = 11.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}
