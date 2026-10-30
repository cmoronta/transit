package com.transitbike.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.transitbike.shared.model.UnitSystem
import com.transitbike.shared.nav.UnitFormatter
import com.transitbike.wear.sync.WearListenerService

class RideTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val navState = WearListenerService.syncedNavState.value
        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(buildTileLayout(navState))
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTimeline(singleTileTimeline)
            .setFreshnessIntervalMillis(15_000L)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun buildTileLayout(navState: com.transitbike.shared.model.NavState?): LayoutElementBuilders.Layout {
        val rootColumn = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (navState != null) {
            val distStr = UnitFormatter.formatDistance(navState.distanceToNextManeuverMeters, UnitSystem.IMPERIAL)
            val instruction = navState.currentManeuver?.instruction ?: "Following route"
            val remainingTime = UnitFormatter.formatDuration(navState.remainingDurationSeconds)

            rootColumn
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("IN $distStr")
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14)).build())
                        .setColor(ColorBuilders.argb(0xFF10B981.toInt()))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4)).build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText(instruction)
                        .setMaxLines(2)
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(15)).build())
                        .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6)).build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("$remainingTime remaining")
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12)).build())
                        .setColor(ColorBuilders.argb(0xFFA1A1AA.toInt()))
                        .build()
                )
        } else {
            rootColumn
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("Bike Navigation")
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(16)).build())
                        .setColor(ColorBuilders.argb(0xFF10B981.toInt()))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(8)).build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("Tap to start ride")
                        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(13)).build())
                        .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                        .build()
                )
        }

        return LayoutElementBuilders.Layout.Builder().setRoot(rootColumn.build()).build()
    }
}
