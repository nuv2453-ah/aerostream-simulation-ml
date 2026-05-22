package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.example.simulation.FluidSolver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun FluidCanvas(
    solver: FluidSolver,
    heatmapMode: String,
    showParticles: Boolean,
    renderTrigger: Long, // Drives redraw cycles
    isDrawingMode: Boolean,
    onSketchGrid: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isDrawingMode) {
                if (isDrawingMode) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val gridX = ((change.position.x / canvasW) * solver.Nx).toInt()
                        val gridY = ((change.position.y / canvasH) * solver.Ny).toInt()
                        onSketchGrid(gridX, gridY)
                    }
                }
            }
            .pointerInput(isDrawingMode) {
                if (isDrawingMode) {
                    detectTapGestures { offset ->
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val gridX = ((offset.x / canvasW) * solver.Nx).toInt()
                        val gridY = ((offset.y / canvasH) * solver.Ny).toInt()
                        onSketchGrid(gridX, gridY)
                    }
                }
            }
    ) {
        // Redraw on renderTrigger updates
        val tagDummy = renderTrigger

        val canvasW = size.width
        val canvasH = size.height
        val cellW = canvasW / solver.Nx
        val cellH = canvasH / solver.Ny

        // 1. Draw scientific fluid heatmap fields
        for (y in 0 until solver.Ny) {
            val yOffset = y * cellH
            for (x in 0 until solver.Nx) {
                val xOffset = x * cellW
                val idx = solver.index(x, y)

                if (solver.solid[idx]) {
                    // Render solid aerodynamic obstacle
                    drawRect(
                        color = Color(0xFF1E293B), // Dark slate/solid barrier
                        topLeft = Offset(xOffset, yOffset),
                        size = Size(cellW + 0.5f, cellH + 0.5f)
                    )
                    // Structural edge border to make shapes look detailed
                    if (x > 0 && !solver.solid[solver.index(x - 1, y)]) {
                        drawLine(
                            color = Color(0xFF38BDF8), // Cyan outer boundaries glow
                            start = Offset(xOffset, yOffset),
                            end = Offset(xOffset, yOffset + cellH),
                            strokeWidth = 1.0f
                        )
                    }
                } else {
                    // Match visual styling palette dynamically
                    val color = when (heatmapMode) {
                        "Velocity" -> {
                            val uVal = solver.u[idx]
                            val vVal = solver.v[idx]
                            val speed = sqrt(uVal * uVal + vVal * vVal)
                            getVelocityColor(speed)
                        }
                        "Pressure" -> getPressureColor(solver.p[idx])
                        "Vorticity" -> getVorticityColor(solver.vort[idx])
                        else -> Color(0xFF0F172A)
                    }

                    drawRect(
                        color = color,
                        topLeft = Offset(xOffset, yOffset),
                        size = Size(cellW + 0.5f, cellH + 0.5f)
                    )
                }
            }
        }

        // 2. Overlay dynamic flow tracer particles
        if (showParticles) {
            for (p in solver.particles) {
                val px = (p.x / solver.Nx) * canvasW
                val py = (p.y / solver.Ny) * canvasH

                drawCircle(
                    color = Color(0xFFE2E8F0).copy(alpha = 0.55f), // Slate white glowing tracers
                    radius = 2.5f,
                    center = Offset(px, py)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Continuous Scientific Color Heatmap Mappings
// -------------------------------------------------------------

// Blue (slow) -> Green -> Yellow -> Red (fast)
private fun getVelocityColor(valMag: Float): Color {
    val maxLimit = 18.0f
    val norm = (valMag / maxLimit).coerceIn(0.0f, 1.0f)

    return when {
        norm < 0.33f -> {
            val t = norm / 0.33f
            Color(
                red = (0.05f + t * 0.1f),
                green = (0.2f + t * 0.5f),
                blue = (0.4f + t * 0.4f)
            )
        }
        norm < 0.66f -> {
            val t = (norm - 0.33f) / 0.33f
            Color(
                red = (0.15f + t * 0.7f),
                green = (0.7f + t * 0.2f),
                blue = (0.8f - t * 0.6f)
            )
        }
        else -> {
            val t = (norm - 0.66f) / 0.34f
            Color(
                red = (0.85f + t * 0.15f),
                green = (0.9f - t * 0.8f),
                blue = (0.2f - t * 0.2f)
            )
        }
    }
}

// Low pressure suction (Blue) -> Neutral (Dark slate) -> High stagnation pressure (Orange/Red)
private fun getPressureColor(valMag: Float): Color {
    // Normal pressure ranges are -12.0 to 12.0
    val limit = 8.0f
    val norm = (valMag / limit).coerceIn(-1.0f, 1.0f)

    return if (norm < 0.0f) {
        // Suction vacuum (Upper lift generating zone)
        val t = -norm
        Color(
            red = (15 / 255.0f * (1.0f - t)),
            green = (23 / 255.0f * (1.0f - t) + t * 0.4f),
            blue = (42 / 255.0f * (1.0f - t) + t * 0.9f)
        )
    } else {
        // Compression / Stagnation (Nose block zone)
        val t = norm
        Color(
            red = (15 / 255.0f * (1.0f - t) + t * 0.95f),
            green = (23 / 255.0f * (1.0f - t) + t * 0.35f),
            blue = (42 / 255.0f * (1.0f - t))
        )
    }
}

// Negative vorticity clockwise eddy (Purple/Violet) -> Neutral (Dark Slate) -> Positive CCW curl eddy (Teal/Emerald)
private fun getVorticityColor(valMag: Float): Color {
    val limit = 3.5f
    val norm = (valMag / limit).coerceIn(-1.0f, 1.0f)

    return if (norm < 0.0f) {
        val t = -norm
        Color(
            red = (15 / 255.0f * (1.0f - t) + t * 0.55f),
            green = (23 / 255.0f * (1.0f - t)),
            blue = (42 / 255.0f * (1.0f - t) + t * 0.85f)
        )
    } else {
        val t = norm
        Color(
            red = (15 / 255.0f * (1.0f - t)),
            green = (23 / 255.0f * (1.0f - t) + t * 0.8f),
            blue = (42 / 255.0f * (1.0f - t) + t * 0.75f)
        )
    }
}
