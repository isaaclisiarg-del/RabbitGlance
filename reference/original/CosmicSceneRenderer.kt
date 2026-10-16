package com.isaaclisi.classicclock

import android.graphics.*
import kotlin.math.*

/** Procedural scenes; the black hole's controls describe a disk and horizon, never petals. */
internal object CosmicSceneRenderer {
    fun layer(prefs: ClockPreferences, pixels: Int, front: Boolean, seconds: Float = 0f,today: java.time.LocalDate=java.time.LocalDate.now()): Bitmap {
        val size = pixels.coerceIn(64, 512)
        if(prefs.geometryTheme.isTeam)return if(front)Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888) else TeamThemeRenderer.layer(prefs,size,today)
        if(prefs.geometryTheme==ClockTheme.BLACK_HOLE)return BlackHoleArt.layer(prefs,size,front)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it)
            if (!front) {
                c.clipOutPath(Path().apply { addCircle(size*.5f,size*.5f,size*prefs.coreRadius(),Path.Direction.CW) })
                nursery(c, prefs, size * .5f, size * .5f, size.toFloat(), seconds)
            }
        }
    }
    private fun nursery(c: Canvas, p: ClockPreferences, cx: Float, cy: Float, u: Float, time: Float) {
        val count = p.petalCount(java.time.LocalDate.now())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val palette = if(p.adaptiveColors)CosmicColors.palette(p) else when(p.cosmicColorMix) {
            CosmicColorMix.AURORA -> intArrayOf(0xff39ae9c.toInt(),0xffa6e8f1.toInt(),0xff9c8ae6.toInt(),0xff4b8c92.toInt())
            CosmicColorMix.SUPERNOVA -> intArrayOf(0xffda567d.toInt(),0xffa588ed.toInt(),0xfff0b77a.toInt(),0xffa34d7c.toInt())
            else -> intArrayOf(0xff6f78d4.toInt(), 0xff81c9f0.toInt(), 0xffb7a1f1.toInt(), 0xffab794e.toInt())
        }
        repeat(count) { i ->
            val phase = i * 2.399963f
            val angle = 2 * PI.toFloat() * i / count + .12f * sin(phase + time * .12f)
            val r = .395f + .045f * sin(phase * 1.3f)
            val x = cx + cos(angle) * u * r; val y = cy + sin(angle) * u * r
            val tx = -sin(angle); val ty = cos(angle)
            val path = Path().apply {
                moveTo(x - tx * u * .11f, y - ty * u * .11f)
                cubicTo(cx + cos(angle - .25f) * u * .50f, cy + sin(angle - .25f) * u * .50f,
                    cx + cos(angle + .22f) * u * .31f, cy + sin(angle + .22f) * u * .31f,
                    x + tx * u * .12f, y + ty * u * .12f)
            }
            paint.color = palette[i % palette.size]; paint.alpha = 18; paint.strokeWidth = u * .019f; c.drawPath(path, paint)
            paint.alpha = 105; paint.strokeWidth = u * .0018f; c.drawPath(path, paint)
            paint.color = 0xffddedff.toInt(); paint.alpha = 75; paint.strokeWidth = u * .0007f; c.drawPath(path, paint)
        }
    }
    private fun nativeNursery(doc: ClockDrawDocument,p: ClockPreferences,time: Float,cx: Float,cy: Float,u: Float) {
        val count=p.petalCount(java.time.LocalDate.now())
        val palette=if(p.adaptiveColors)CosmicColors.palette(p) else when(p.cosmicColorMix) {
            CosmicColorMix.AURORA -> intArrayOf(0xff39ae9c.toInt(),0xffa6e8f1.toInt(),0xff9c8ae6.toInt(),0xff4b8c92.toInt())
            CosmicColorMix.SUPERNOVA -> intArrayOf(0xffda567d.toInt(),0xffa588ed.toInt(),0xfff0b77a.toInt(),0xffa34d7c.toInt())
            else -> intArrayOf(0xff6f78d4.toInt(),0xff81c9f0.toInt(),0xffb7a1f1.toInt(),0xffab794e.toInt())
        }
        doc.save();doc.clipOutsideCircle(cx,cy,u*p.coreRadius())
        repeat(count) { i ->
            val phase=i*2.399963f;val angle=2*PI.toFloat()*i/count
            val r=.395f+.045f*sin(phase*1.3f)
            val x=cx+cos(angle)*u*r;val y=cy+sin(angle)*u*r
            val tx=-sin(angle);val ty=cos(angle)
            val wave=if(p.orbitalIntensity==OrbitalIntensity.OFF)0f else doc.expression(time,.35f+(i%5)*.037f,
                ClockDrawDocument.MUL,phase,ClockDrawDocument.ADD,ClockDrawDocument.SIN,
                u*(if(p.orbitalIntensity==OrbitalIntensity.COSMIC).045f else .028f),ClockDrawDocument.MUL)
            fun bend(base: Float,direction: Float)=doc.expression(wave,direction,ClockDrawDocument.MUL,base,ClockDrawDocument.ADD)
            val points=floatArrayOf(ClockDrawDocument.variable(10),x-tx*u*.11f,y-ty*u*.11f,
                ClockDrawDocument.variable(14),
                bend(cx+cos(angle-.25f)*u*.50f,tx),bend(cy+sin(angle-.25f)*u*.50f,ty),
                bend(cx+cos(angle+.22f)*u*.31f,-tx),bend(cy+sin(angle+.22f)*u*.31f,-ty),
                x+tx*u*.12f,y+ty*u*.12f,ClockDrawDocument.variable(16))
            doc.strokeLayers(points,listOf(Triple(palette[i%4],u*.014f,.055f),
                Triple(palette[i%4],u*.0018f,.45f),Triple(0xffd7edff.toInt(),u*.0007f,.27f)))
        }
        doc.restore()
    }

    fun drawNative(doc: ClockDrawDocument, p: ClockPreferences, time: Float, cx: Float, cy: Float,
        u: Float, pixels: Int, front: Boolean, vectors: Boolean = false,today: java.time.LocalDate=java.time.LocalDate.now()) {
        if (!p.geometryTheme.isScene) return
        if(p.geometryTheme.isTeam) { if(!front)TeamThemeRenderer.native(doc,p,time,cx,cy,u,pixels,vectors,today);return }
        if (p.geometryTheme == ClockTheme.STELLAR_NURSERY && front) return
        if(p.geometryTheme==ClockTheme.STELLAR_NURSERY && vectors) {
            nativeNursery(doc,p,time,cx,cy,u);return
        }
        doc.save()
        if (p.geometryTheme == ClockTheme.STELLAR_NURSERY && p.orbitalIntensity != OrbitalIntensity.OFF) {
            val angle = doc.expression(time, .18f, ClockDrawDocument.MUL, ClockDrawDocument.SIN, 4f, ClockDrawDocument.MUL)
            val breath = doc.expression(time, .27f, ClockDrawDocument.MUL, ClockDrawDocument.SIN, if(p.orbitalIntensity==OrbitalIntensity.COSMIC).055f else .035f, ClockDrawDocument.MUL, 1f, ClockDrawDocument.ADD)
            doc.rotate(angle, cx, cy); doc.scale(breath, breath, cx, cy)
        }
        val art = layer(p, pixels, front)
        doc.image(art, (cx-u*.5f).roundToInt(), (cy-u*.5f).roundToInt(), u.roundToInt())
        art.recycle(); doc.restore()
        if (p.geometryTheme != ClockTheme.BLACK_HOLE || p.orbitalIntensity == OrbitalIntensity.OFF) return
        BlackHoleArt.nativeFlow(doc,p,time,cx,cy,u,front)
    }
}

