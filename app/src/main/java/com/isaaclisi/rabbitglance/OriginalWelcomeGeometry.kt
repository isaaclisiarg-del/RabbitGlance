package com.isaaclisi.rabbitglance

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Independent geometric recreation of Unity 8's Welcome infographic.
 * References: qml/Greeter/{Infographics,ObjectPositioner,Circle}.qml in
 * UBports' Canonical-era Unity 8 tree. No GPL source or images copied.
 *
 * This renderer intentionally uses calendar progress, NOT fictitious device
 * usage statistics. The historical greeter used a dedicated activity model.
 */
data class WelcomeDisc(
    val x: Float,
    val y: Float,
    val radius: Float,
    val opacity: Float,
    val progress: Float,
    val previousMonth: Boolean
)

data class WelcomeDot(val x: Float, val y: Float, val state: Int)

object OriginalWelcomeGeometry {
    const val CORE_RADIUS = 0.329f
    const val DOT_ORBIT = 0.278f
    const val DISC_ORBIT = 0.333f

    fun circleLayers(day: Int, daysInMonth: Int): List<WelcomeDisc> {
        val days = daysInMonth.coerceIn(28, 31)
        val today = day.coerceIn(1, days)
        val out = ArrayList<WelcomeDisc>(days * 2)
        for (past in listOf(true, false)) {
            for (i in 0 until days) {
                val theta = (2.0 * PI * i / days).toFloat()
                // Organic variation in radius; no invented telemetry or randomness.
                val radius = 0.185f * (0.64f + .23f * (
                    .5f + .5f * sin(theta * 5f + (if(past) 1.2f else 0f))))
                out += WelcomeDisc(
                    x = .5f + sin(theta) * DISC_ORBIT,
                    y = .5f - cos(theta) * DISC_ORBIT,
                    radius = radius,
                    opacity = if(past) .09f else .29f,
                    progress = if(past || i < today) 1f else 0f,
                    previousMonth = past
                )
            }
        }
        return out
    }

    /** 0 = elapsed day, 1 = current day, 2 = remaining day. */
    fun dotMarkers(day: Int, daysInMonth: Int): List<WelcomeDot> {
        val days = daysInMonth.coerceIn(28,31)
        val today = day.coerceIn(1,days)
        return (0 until days).map { i ->
            val theta = (2.0 * PI * i / days).toFloat()
            WelcomeDot(
                x = .5f + sin(theta) * DOT_ORBIT,
                y = .5f - cos(theta) * DOT_ORBIT,
                state = when {
                    i < today - 1 -> 0
                    i == today - 1 -> 1
                    else -> 2
                }
            )
        }
    }
}
