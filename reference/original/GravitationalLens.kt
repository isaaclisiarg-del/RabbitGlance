package com.isaaclisi.classicclock

import kotlin.math.*

/** A bounded artistic thin-lens approximation, not a ray-traced GR simulation. */
internal object GravitationalLens {
    data class Point(val x: Float, val y: Float)
    fun destination(x: Float, y: Float, cx: Float, cy: Float, radius: Float, strength: Float, seconds: Float): Point {
        if (radius <= 0 || strength <= 0) return Point(x, y)
        val dx = (x-cx)/radius; val dy = (y-cy)/radius
        val r = hypot(dx,dy)
        if (r < .001f || r > 4f) return Point(x,y)
        val field = strength * .32f * exp(-r*r*.5f)
        val observed = (r + sqrt(r*r + 4f*field)) * .5f
        val spin = .035f * strength * sin(seconds * .18f) * exp(-r*r)
        val angle = atan2(dy,dx)+spin
        return Point(cx+cos(angle)*observed*radius, cy+sin(angle)*observed*radius)
    }
    fun frameDelay(quality: RenderQuality, powerSave: Boolean, reducedMotion: Boolean): Long =
        if (reducedMotion) 0L else kotlin.math.ceil(1000.0 / if (powerSave) 15 else quality.fps).toLong()
}
