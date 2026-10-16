package com.isaaclisi.classicclock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calendar-matched cosmic petals (up to 70); each is drawn with two
 * differently tinted vector layers. This is geometry, not recolored Ubuntu discs.
 *
 * Host player: V6 matrix rotation and stretching of curved petals.
 * Static fallback: the identical petal outlines without pretending to animate.
 * No ticker, overlay, service, video, bitmap frame loop or app-side redraw.
 */
internal object ClockThemeRenderer {
    const val MAX_PETALS = 70
    const val DEFAULT_PETALS = 20

    internal data class Shape(
        val petal: Int,
        val inner: Boolean,
        val angle: Float,
        val root: Float,
        val reach: Float,
        val width: Float,
        val bend: Float,
        val color: Int,
        val highlight: Int,
        val opacity: Float,
        val phase: Float,
        val speed: Float,
        val wobble: Float
    )

    private data class Palette(val outer: IntArray, val inner: IntArray, val shimmer: IntArray)

    private fun colors(theme: ClockTheme, choice: CosmicColorMix, adaptive: IntArray? = null): Palette {
        if(adaptive!=null)return Palette(adaptive,
            adaptive.map { CosmicColors.blend(it,android.graphics.Color.WHITE,.34f) }.toIntArray(),
            adaptive.map { CosmicColors.blend(it,android.graphics.Color.WHITE,.75f) }.toIntArray())
        val mix = if (choice == CosmicColorMix.AUTO) {
            if (theme == ClockTheme.STAR) CosmicColorMix.SUPERNOVA else CosmicColorMix.DEEP_SPACE
        } else choice
        return when (mix) {
            CosmicColorMix.SUPERNOVA -> Palette(
                intArrayOf(0xffee386b.toInt(), 0xffd64ac3.toInt(), 0xff6946cc.toInt(),
                    0xfffc622c.toInt(), 0xffb01c6f.toInt()),
                intArrayOf(0xffffd47a.toInt(), 0xffff87b4.toInt(), 0xffa6a5ff.toInt(),
                    0xffffa147.toInt(), 0xffff64cb.toInt()),
                intArrayOf(0xfffff4c5.toInt(), 0xffe5aaff.toInt(), 0xff92d7ff.toInt(),
                    0xffffffb4.toInt(), 0xffffc8fa.toInt())
            )
            CosmicColorMix.AURORA -> Palette(
                intArrayOf(0xff118d9f.toInt(), 0xff4058d1.toInt(), 0xff2cbb94.toInt(),
                    0xff774de1.toInt(), 0xff217eb4.toInt()),
                intArrayOf(0xff6fffea.toInt(), 0xffa5a9ff.toInt(), 0xffb8ffaf.toInt(),
                    0xffd09aff.toInt(), 0xff77e8ff.toInt()),
                intArrayOf(0xffecfff6.toInt(), 0xffd4deff.toInt(), 0xffecffc4.toInt(),
                    0xfff2dcff.toInt(), 0xffd3fcff.toInt())
            )
            CosmicColorMix.DEEP_SPACE -> Palette(
                intArrayOf(0xff5743bb.toInt(), 0xff255ca5.toInt(), 0xff9f3caa.toInt(),
                    0xff27649e.toInt(), 0xff9a487d.toInt()),
                intArrayOf(0xff9da9ff.toInt(), 0xff58d9ff.toInt(), 0xffd995fb.toInt(),
                    0xff7ce9ed.toInt(), 0xffffa3da.toInt()),
                intArrayOf(0xffe5dfff.toInt(), 0xffe0ffff.toInt(), 0xffffd5ff.toInt(),
                    0xffcbffff.toInt(), 0xffffe6f9.toInt())
            )
            CosmicColorMix.AUTO -> error("AUTO resolved above")
        }
    }

    fun shapes(theme: ClockTheme, petals: Int = DEFAULT_PETALS,
        mix: CosmicColorMix = CosmicColorMix.AUTO, adaptive: IntArray? = null): List<Shape> {
        if (theme == ClockTheme.UBUNTU_ORIGINAL || theme.isScene) return emptyList()
        val count = petals.coerceIn(12, MAX_PETALS)
        val palette = colors(theme, mix, adaptive)
        return buildList(count * 2) {
            repeat(count) { i ->
                val a = (2.0 * PI * i / count - PI / 2 +
                    (if (theme == ClockTheme.STAR) .055 * sin(i * 1.71) else .035 * sin(i * 2.2))).toFloat()
                val phase = i * 2.399963f
                val reach = if (theme == ClockTheme.STAR) {
                    .465f + .045f * (.5f + .5f * sin(i * 2.17f))
                } else {
                    .455f + .060f * (.5f + .5f * sin(i * 1.93f))
                }
                val bend = if (theme == ClockTheme.STAR) {
                    .030f * sin(i * 1.7f)
                } else {
                    (if (i % 2 == 0) 1f else -1f) * (.035f + (i % 3) * .018f)
                }
                val j = i % palette.outer.size
                val rate = if (theme == ClockTheme.STAR) .62f else .40f
                val wobble = if (theme == ClockTheme.STAR) .042f else .050f
                // One outer luminous cloud and one tapered core per physical petal.
                add(Shape(i, false, a, .273f, reach,
                    if (theme == ClockTheme.STAR) .074f else .055f, bend,
                    palette.outer[j], palette.inner[j], .48f, phase,
                    rate + (i % 4) * .071f, wobble))
                add(Shape(i, true, a + .018f, .284f, reach - .027f,
                    if (theme == ClockTheme.STAR) .030f else .018f, bend * .92f,
                    palette.inner[j], palette.shimmer[j], .69f, phase + .40f,
                    rate + (i % 4) * .071f, wobble * .78f))
            }
        }
    }

    private fun point(shape: Shape, radius: Float, tangent: Float,
        cx: Float, cy: Float, unit: Float): Pair<Float, Float> {
        val c = cos(shape.angle)
        val s = sin(shape.angle)
        return (cx + unit * (c * radius - s * tangent)) to
            (cy + unit * (s * radius + c * tangent))
    }

    /** M(start); C(left-control, outer-control, tip); C(right-control, inner-control, end); Z. */
    internal fun points(shape: Shape, cx: Float, cy: Float, unit: Float): FloatArray {
        val a = point(shape, shape.root, -shape.width, cx, cy, unit)
        val b = point(shape, shape.root + .073f, -shape.width * 1.10f, cx, cy, unit)
        val c = point(shape, shape.reach - .077f, shape.bend * .56f - shape.width * .72f, cx, cy, unit)
        val d = point(shape, shape.reach, shape.bend, cx, cy, unit)
        val e = point(shape, shape.reach - .076f, shape.bend * .74f + shape.width * .61f, cx, cy, unit)
        val f = point(shape, shape.root + .073f, shape.width * 1.08f, cx, cy, unit)
        val g = point(shape, shape.root, shape.width, cx, cy, unit)
        return floatArrayOf(
            ClockDrawDocument.variable(10), a.first, a.second,
            ClockDrawDocument.variable(14), b.first, b.second, c.first, c.second,
            d.first, d.second,
            ClockDrawDocument.variable(14), e.first, e.second, f.first, f.second,
            g.first, g.second, ClockDrawDocument.variable(15), ClockDrawDocument.variable(16)
        )
    }

    fun drawStatic(canvas: Canvas, theme: ClockTheme, cx: Float, cy: Float, unit: Float,
        petals: Int = DEFAULT_PETALS, mix: CosmicColorMix = CosmicColorMix.AUTO, adaptive: IntArray? = null) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (shape in shapes(theme, petals, mix, adaptive)) {
            val p = points(shape, cx, cy, unit)
            val path = Path().apply {
                moveTo(p[1], p[2])
                cubicTo(p[4], p[5], p[6], p[7], p[8], p[9])
                cubicTo(p[11], p[12], p[13], p[14], p[15], p[16])
                close()
            }
            paint.color = shape.color
            paint.alpha = (shape.opacity * 255f).toInt().coerceIn(0, 255)
            canvas.drawPath(path, paint)
        }
    }

    /**
     * Universal V6 fallback: use only DrawCircle + FloatExpression, both already
     * proven by Ubuntu Original's native widget. Complex PathData is optional.
     * Luminous beads and soft halos travel across the existing petal silhouettes.
     */
    fun drawNativeLightFallback(doc: ClockDrawDocument, theme: ClockTheme, time: Float,
        cx: Float, cy: Float, unit: Float, intensity: OrbitalIntensity,
        petals: Int = DEFAULT_PETALS, mix: CosmicColorMix = CosmicColorMix.AUTO, adaptive: IntArray? = null) {
        val motion = when (intensity) {
            OrbitalIntensity.OFF -> 0f
            OrbitalIntensity.SUBTLE -> 1f
            OrbitalIntensity.COSMIC -> 1.65f
        }
        val rate = if (theme == ClockTheme.STAR) .028f else .017f
        for (group in shapes(theme, petals, mix, adaptive).chunked(2)) {
            val shape = group.first()
            // Constant radius near each lobe's outer third. Angle advances smoothly
            // in the native clock, and radial/brightness pulses have independent phases.
            val angle = doc.expression(time, rate * motion, ClockDrawDocument.MUL,
                shape.angle, ClockDrawDocument.ADD)
            val wave = doc.expression(time, shape.speed, ClockDrawDocument.MUL,
                shape.phase, ClockDrawDocument.ADD, ClockDrawDocument.SIN,
                unit * .020f * motion, ClockDrawDocument.MUL,
                unit * (shape.reach - .04f), ClockDrawDocument.ADD)
            val x = doc.expression(angle, ClockDrawDocument.COS, wave,
                ClockDrawDocument.MUL, cx, ClockDrawDocument.ADD)
            val y = doc.expression(angle, ClockDrawDocument.SIN, wave,
                ClockDrawDocument.MUL, cy, ClockDrawDocument.ADD)
            val glow = doc.expression(time, shape.speed * .81f, ClockDrawDocument.MUL,
                shape.phase + .45f, ClockDrawDocument.ADD, ClockDrawDocument.SIN,
                .11f, ClockDrawDocument.MUL, .23f, ClockDrawDocument.ADD)
            // Three discs per petal, with independently varying radii, make an
            // unmistakably animated plasma glint without hiding the pointed shape.
            val spread = doc.expression(time, shape.speed * .57f,
                ClockDrawDocument.MUL, shape.phase, ClockDrawDocument.ADD,
                ClockDrawDocument.COS, .006f * unit * motion,
                ClockDrawDocument.MUL, unit * .032f, ClockDrawDocument.ADD)
            doc.circle(x, y, spread, shape.color, glow)
            val core = group.getOrElse(1) { shape }
            doc.circle(x, y, unit * .016f, core.color, .48f)
            val sparkX = doc.expression(x, unit * .014f, ClockDrawDocument.ADD)
            val sparkY = doc.expression(y, unit * .012f, ClockDrawDocument.SUB)
            doc.circle(sparkX, sparkY, unit * .006f, core.highlight, .65f)
        }
    }

    /**
     * One small cropped texture per petal, drawn with dynamic FLOAT bounds.
     * The shape itself stretches and sways independently, even without PathData
     * or matrix rotation. No new bitmaps or RemoteViews are sent per frame.
     */
    fun drawNativePetals(doc: ClockDrawDocument, theme: ClockTheme, time: Float,
        cx: Float, cy: Float, unit: Float, intensity: OrbitalIntensity,
        petals: Int, mix: CosmicColorMix, pixels: Int, adaptive: IntArray? = null) {
        val motion = when (intensity) {
            OrbitalIntensity.OFF -> 0f
            OrbitalIntensity.SUBTLE -> 1f
            OrbitalIntensity.COSMIC -> 1.65f
        }
        val size = pixels.coerceIn(64, 384)
        val scale = size.toFloat()
        for (pair in shapes(theme, petals, mix, adaptive).chunked(2)) {
            val shape = pair.first()
            val full = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(full)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val bounds = android.graphics.RectF()
            // Keep the base just outside the translucent core when stretching.
            for (layer in pair) {
                val p = points(layer.copy(root = .350f), scale * .5f, scale * .5f, scale)
                val path = Path().apply {
                    moveTo(p[1], p[2]); cubicTo(p[4], p[5], p[6], p[7], p[8], p[9])
                    cubicTo(p[11], p[12], p[13], p[14], p[15], p[16]); close()
                }
                val layerBounds = android.graphics.RectF()
                path.computeBounds(layerBounds, true)
                bounds.union(layerBounds)
                paint.color = layer.color; paint.alpha = (layer.opacity * 255f).toInt()
                canvas.drawPath(path, paint)
            }
            val l = (kotlin.math.floor(bounds.left).toInt() - 1).coerceIn(0, size - 1)
            val t = (kotlin.math.floor(bounds.top).toInt() - 1).coerceIn(0, size - 1)
            val r = (kotlin.math.ceil(bounds.right).toInt() + 1).coerceIn(l + 1, size)
            val b = (kotlin.math.ceil(bounds.bottom).toInt() + 1).coerceIn(t + 1, size)
            val bitmap = android.graphics.Bitmap.createBitmap(full, l, t, r - l, b - t)
            // Scale about the root of THIS lobe, keeping the whole corona coherent.
            val anchorX = cx + cos(shape.angle) * unit * .350f
            val anchorY = cy + sin(shape.angle) * unit * .350f
            val stretchX = doc.expression(time, shape.speed, ClockDrawDocument.MUL,
                shape.phase, ClockDrawDocument.ADD, ClockDrawDocument.SIN,
                .12f * motion, ClockDrawDocument.MUL, 1f, ClockDrawDocument.ADD)
            val stretchY = doc.expression(time, shape.speed * .81f, ClockDrawDocument.MUL,
                shape.phase + 1.1f, ClockDrawDocument.ADD, ClockDrawDocument.COS,
                .12f * motion, ClockDrawDocument.MUL, 1f, ClockDrawDocument.ADD)
            fun x(value: Int) = doc.expression(stretchX,
                cx - unit * .5f + value / scale * unit - anchorX,
                ClockDrawDocument.MUL, anchorX, ClockDrawDocument.ADD)
            fun y(value: Int) = doc.expression(stretchY,
                cy - unit * .5f + value / scale * unit - anchorY,
                ClockDrawDocument.MUL, anchorY, ClockDrawDocument.ADD)
            doc.image(bitmap, x(l), y(t), x(r), y(b))
            if (bitmap !== full) bitmap.recycle()
            full.recycle()
        }
    }

    fun drawNative(doc: ClockDrawDocument, theme: ClockTheme, time: Float,
        cx: Float, cy: Float, unit: Float, intensity: OrbitalIntensity,
        petals: Int = DEFAULT_PETALS, mix: CosmicColorMix = CosmicColorMix.AUTO, adaptive: IntArray? = null) {
        val multiplier = when (intensity) {
            OrbitalIntensity.OFF -> 0f
            OrbitalIntensity.SUBTLE -> 1f
            OrbitalIntensity.COSMIC -> 1.65f
        }
        // A shared transform deforms each pair of curved layers independently.
        // Keep the V6 float table below its 1000-slot limit even at 70 petals.
        for (pair in shapes(theme, petals, mix, adaptive).chunked(2)) {
            val lead = pair.first()
            doc.save()
            if (multiplier > 0f) {
                val angular = doc.expression(time, lead.speed * .39f, ClockDrawDocument.MUL,
                    lead.phase, ClockDrawDocument.ADD, ClockDrawDocument.SIN,
                    5f * multiplier, ClockDrawDocument.MUL)
                val stretchX = doc.expression(time, lead.speed, ClockDrawDocument.MUL,
                    lead.phase, ClockDrawDocument.ADD, ClockDrawDocument.SIN,
                    .12f * multiplier, ClockDrawDocument.MUL, 1f, ClockDrawDocument.ADD)
                val stretchY = doc.expression(time, lead.speed * .81f, ClockDrawDocument.MUL,
                    lead.phase + 1.1f, ClockDrawDocument.ADD, ClockDrawDocument.COS,
                    .12f * multiplier, ClockDrawDocument.MUL, 1f, ClockDrawDocument.ADD)
                doc.rotate(angular, cx, cy)
                doc.scale(stretchX, stretchY,
                    cx + cos(lead.angle) * lead.root * unit,
                    cy + sin(lead.angle) * lead.root * unit)
            }
            for (shape in pair) {
                val p = points(shape, cx, cy, unit)
                if (multiplier > 0f && shape.inner) {
                    val pulse = doc.expression(time, shape.speed * .72f, ClockDrawDocument.MUL,
                        shape.phase, ClockDrawDocument.ADD, ClockDrawDocument.SIN,
                        .24f, ClockDrawDocument.MUL, .44f, ClockDrawDocument.ADD)
                    val color = doc.color(shape.color, shape.highlight, pulse)
                    doc.pathColorId(p, color, shape.opacity)
                } else {
                    doc.path(p, shape.color, shape.opacity)
                }
            }
            doc.restore()
        }
    }
}
