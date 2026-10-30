package com.transitbike.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import com.transitbike.shared.model.BikeProtectionType

// Safety segment highlight colors
val ColorProtectedBikePath = Color(0xFF10B981) // Emerald Green
val ColorBikeLane = Color(0xFF0284C7)          // Ocean Blue
val ColorCaution = Color(0xFFF59E0B)           // Amber Warning
val ColorWalkDismount = Color(0xFF64748B)      // Slate Grey
val ColorRegularStreet = Color(0xFF94A3B8)     // Light Slate

fun getProtectionColor(type: BikeProtectionType): Color {
    return when (type) {
        BikeProtectionType.PROTECTED_LANE -> ColorProtectedBikePath
        BikeProtectionType.BIKE_LANE -> ColorBikeLane
        BikeProtectionType.CAUTION -> ColorCaution
        BikeProtectionType.WALK -> ColorWalkDismount
        BikeProtectionType.REGULAR -> ColorRegularStreet
    }
}

// Dark theme defaults
val PrimaryDark = Color(0xFF6EE7B7)
val OnPrimaryDark = Color(0xFF003822)
val SecondaryDark = Color(0xFF7DD3FC)
val BackgroundDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val OnSurfaceDark = Color(0xFFF8FAFC)

// Light theme defaults
val PrimaryLight = Color(0xFF059669)
val OnPrimaryLight = Color(0xFFFFFFFF)
val SecondaryLight = Color(0xFF0284C7)
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
