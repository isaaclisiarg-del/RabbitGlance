package com.isaaclisi.rabbitglance

import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class OrbitalIntensity { OFF, SUBTLE, COSMIC }
enum class PetalPalette { ORIGINAL, ADAPTIVE, COSMIC }
enum class ClockGlow { SOFT, NORMAL, INTENSE }
enum class MinutePulseDuration(val millis: Int) { SHORT(800), NORMAL(1200), LONG(1800) }

/** Pure one-revolution-per-minute clock geometry; no 1.2-second animation gate. */
object CosmicMinutePolicy {
    fun minute(epochMillis: Long): Long = Math.floorDiv(epochMillis, 60_000L)
    fun elapsed(epochMillis: Long): Long = Math.floorMod(epochMillis, 60_000L)
    fun progress(epochMillis: Long): Float = elapsed(epochMillis) / 60_000f

    fun clockwise(epochMillis: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(epochMillis).atZone(zone).minute % 2 == 0

    /** Old persisted duration choice now controls the illuminated tail, not rotation speed. */
    fun trailDots(choice: MinutePulseDuration): Float = when (choice) {
        MinutePulseDuration.SHORT -> 1.1f
        MinutePulseDuration.NORMAL -> 2.0f
        MinutePulseDuration.LONG -> 3.0f
    }

    /**
     * A light belongs to EACH existing calendar dot; there is no separate rotating overlay.
     * The peak advances smoothly across all [count] dots over 60 wall-clock seconds.
     *
     * 'ahead' blends into the next light; 'behind' fades 1-3 dots of the tail.
     * Both calculations wrap at midnight/minute boundaries without a visible seam.
     */
    fun dotLight(
        index: Int, count: Int, minuteProgress: Float, clockwise: Boolean,
        trail: Float = 2f
    ): Float {
        if (count <= 0 || index !in 0 until count || !minuteProgress.isFinite() ||
            minuteProgress !in 0f..1f) return 0f

        val head = (minuteProgress % 1f) * count
        val position = if (clockwise) index.toFloat() else
            Math.floorMod(-index, count).toFloat()
        val behind = ((head - position + count) % count)
        val ahead = ((position - head + count) % count)

        val tailLength = trail.coerceIn(.6f, 4f)
        val fade = if (behind <= tailLength) {
            val t = (1f - behind / tailLength).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        } else 0f
        val arrival = if (ahead < .9f) {
            val t = (1f - ahead / .9f).coerceIn(0f, 1f)
            .72f * t * t
        } else 0f
        return maxOf(fade, arrival).coerceIn(0f, 1f)
    }
}

data class CosmicOrbit(
    val angle: Float, val distance: Float, val radius: Float, val opacity: Float,
    val speed: Float, val radialPhase: Float, val previousMonth: Boolean
)

/** Deterministic geometry, generated once per calendar change, never in a frame. */
object CosmicOrbitGeometry {
    fun specs(day: Int, days: Int): List<CosmicOrbit> {
        val source = OriginalWelcomeGeometry.circleLayers(day, days).filter { it.progress > 0f }
        val out = ArrayList<CosmicOrbit>(source.size + 8)
        source.forEachIndexed { i, disc ->
            out += CosmicOrbit(
                atan2(disc.y - .5f, disc.x - .5f),
                hypot(disc.x - .5f, disc.y - .5f), disc.radius, disc.opacity,
                ((2.0 * PI / (115.0 + (i % 9) * 17.0)) *
                    if (i % 4 == 0) -1 else 1).toFloat(),
                i * 1.618f, disc.previousMonth
            )
        }
        repeat(8) { i ->
            out += CosmicOrbit(
                (i * 2.399963f - PI / 2).toFloat(), .31f + (i % 3) * .012f,
                .068f + (i % 4) * .019f, .10f + (i % 3) * .026f,
                ((2.0 * PI / (143.0 + i * 19)) * if (i % 2 == 0) -1 else 1).toFloat(),
                i * 2.13f, i % 3 == 0
            )
        }
        return out
    }
    fun angle(spec: CosmicOrbit, seconds: Float, intensity: OrbitalIntensity): Float =
        spec.angle + seconds * spec.speed * when (intensity) {
            OrbitalIntensity.OFF -> 0f; OrbitalIntensity.SUBTLE -> 1f; OrbitalIntensity.COSMIC -> 1.65f
        }
    fun distance(spec: CosmicOrbit, seconds: Float, intensity: OrbitalIntensity): Float =
        spec.distance + sin(seconds * .13f + spec.radialPhase) * when (intensity) {
            OrbitalIntensity.OFF -> 0f; OrbitalIntensity.SUBTLE -> .005f; OrbitalIntensity.COSMIC -> .012f
        }
    fun x(spec: CosmicOrbit, seconds: Float, intensity: OrbitalIntensity): Float =
        .5f + cos(angle(spec, seconds, intensity)) * distance(spec, seconds, intensity)
    fun y(spec: CosmicOrbit, seconds: Float, intensity: OrbitalIntensity): Float =
        .5f + sin(angle(spec, seconds, intensity)) * distance(spec, seconds, intensity)
}
