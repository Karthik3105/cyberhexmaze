package com.example.honeycombmaze.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

class HexagonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(
            path = drawCustomHexagonPath(size)
        )
    }
}

fun drawCustomHexagonPath(size: Size): Path {
    return Path().apply {
        val radius = Math.min(size.width / 2f, size.height / 2f)
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        
        // Draw a pointy-topped hexagon (standard) or flat-topped depending on angle.
        // In the image, hexagons are pointy-topped (point facing up).
        // For flat topped, angles are 0, 60, 120, 180, 240, 300 degrees.
        // Looking closely at the image, the point is not up, it's flat on top!
        // Actually, the left side of the "classic" icon is pointed left/right. So it is flat topped.
        // Wait, looking at the main honeycomb logo at the top left, the points are facing left and right.
        // So the top and bottom are flat!
        // Flat topped angles:
        for (i in 0 until 6) {
            val angleDeg = 60 * i
            val angleRad = Math.PI / 180 * angleDeg
            val point = androidx.compose.ui.geometry.Offset(
                center.x + radius * cos(angleRad).toFloat(),
                center.y + radius * sin(angleRad).toFloat()
            )
            if (i == 0) {
                moveTo(point.x, point.y)
            } else {
                lineTo(point.x, point.y)
            }
        }
        close()
    }
}
