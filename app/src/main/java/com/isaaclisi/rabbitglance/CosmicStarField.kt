package com.isaaclisi.rabbitglance

import android.graphics.*
import kotlin.math.*

/** Stable star positions: changing the clock never reshuffles the user's sky. */
internal object CosmicStarField {
    data class Star(val x: Float, val y: Float, val size: Float, val phase: Float, val color: Int)
    fun stars(prefs: ClockPreferences): List<Star> {
        if (!prefs.starsEnabled) return emptyList()
        val random = java.util.Random(0x524142424954L)
        return buildList {
            while (size < prefs.starDensity.count) {
                val x = .075f + random.nextFloat() * .85f
                val y = .075f + random.nextFloat() * .85f
                val big = size % 11 == 0
                if (hypot(x - .5f, y - .5f) < if (big) .405f else .355f) continue
                add(Star(x, y, (if (big) .0065f else .0015f + random.nextFloat() * .002f) * prefs.starSize,
                    random.nextFloat() * 6.28f,
                    if (prefs.adaptiveColors) CosmicColors.blend(if(size%2==0)prefs.cosmicPrimary else prefs.cosmicSecondary,Color.WHITE,.55f) else if (size % 3 == 0) 0xffffe0be.toInt() else if (size % 3 == 1) 0xffc7eaff.toInt() else 0xffedceff.toInt()))
            }
        }
    }
    fun drawStatic(canvas: Canvas, prefs: ClockPreferences, cx: Float, cy: Float, unit: Float, seconds: Float = 0f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (star in stars(prefs)) {
            val x = cx + (star.x - .5f) * unit; val y = cy + (star.y - .5f) * unit
            val r = star.size * unit
            val alpha = (prefs.starBrightness * if (prefs.starTwinkle) .65f + .35f * sin(seconds * .7f + star.phase).pow(2) else 1f).coerceIn(0f, 1f)
            paint.shader = RadialGradient(x, y, r * 5f,
                intArrayOf(star.color, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            paint.alpha = (alpha * 90).toInt(); canvas.drawCircle(x, y, r * 5f, paint); paint.shader = null
            paint.color = star.color; paint.alpha = (alpha * 210).toInt(); paint.strokeWidth = maxOf(.5f, r * .24f)
            if (star.size > .004f * prefs.starSize) {
                val arms = if (prefs.starShape == StarShape.WEBB) 3 else 2
                repeat(arms) { i ->
                    val a = PI.toFloat() * i / arms
                    val length = r * if (i == 0) 7f else 5f
                    canvas.drawLine(x - cos(a) * length, y - sin(a) * length,
                        x + cos(a) * length, y + sin(a) * length, paint)
                }
            }
            paint.color = Color.WHITE; paint.alpha = (alpha * 255).toInt(); canvas.drawCircle(x, y, r, paint)
        }
    }
    fun drawNative(doc: ClockDrawDocument, prefs: ClockPreferences, time: Float, cx: Float, cy: Float, unit: Float) {
        if(!prefs.starsEnabled)return
        // Eight independent phase groups keep rich star fields within old V6 resource limits.
        val light=List(8) { i -> if(prefs.starTwinkle)doc.expression(time,.7f,ClockDrawDocument.MUL,
            i*.785398f,ClockDrawDocument.ADD,ClockDrawDocument.SIN,.24f*prefs.starBrightness,
            ClockDrawDocument.MUL,.68f*prefs.starBrightness,ClockDrawDocument.ADD,1f,ClockDrawDocument.MIN)
            else prefs.starBrightness.coerceAtMost(1f) }
        val halo=light.map { doc.expression(it,.09f,ClockDrawDocument.MUL) }
        for (star in stars(prefs)) {
            val x = cx + (star.x - .5f) * unit; val y = cy + (star.y - .5f) * unit; val r = star.size * unit
            val group=(star.phase/.785398f).toInt().coerceIn(0,7)
            val alpha=light[group]
            doc.circle(x,y,r*4f,star.color,halo[group])
            if (star.size > .004f * prefs.starSize) {
                val arms = if (prefs.starShape == StarShape.WEBB) 3 else 2
                repeat(arms) { i ->
                    val a = PI.toFloat() * i / arms; val length = r * if (i == 0) 7f else 5f
                    doc.line(x - cos(a) * length, y - sin(a) * length,
                        x + cos(a) * length, y + sin(a) * length, maxOf(.45f, r * .24f), star.color, alpha)
                }
            }
            doc.circle(x, y, r, 0xfffffcf7.toInt(), alpha)
        }
    }
}

