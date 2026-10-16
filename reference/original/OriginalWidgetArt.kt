package com.isaaclisi.classicclock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Reuses UbuntuWelcomeClock's geometry, clipping, palette and gradients at rest.
 * Never samples seconds: an unmoving light must not masquerade as a second hand.
 */
internal object OriginalWidgetArt {
    fun render(prefs: ClockPreferences, pixels: Int, today: LocalDate = LocalDate.now(),
        chrome: UnityChrome = UnityChrome(), petals: Boolean = true, markers: Boolean = true,
        ambient: Boolean = true, core: Boolean = true): Bitmap {
        val size=pixels.coerceIn(64,if(prefs.geometryTheme.isTeam)768 else 512)
        if(prefs.geometryTheme.isTeam) return TeamThemeRenderer.render(prefs,size,today,
            body=core,figures=petals,plate=core && prefs.showClock).also {
            if(petals)CosmicStarField.drawStatic(Canvas(it),prefs,size*.5f,size*.5f,size.toFloat())
        }
        val bmp=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        if (prefs.wallpaperClockOnly && prefs.geometryTheme == ClockTheme.BLACK_HOLE) return bmp
        val canvas=Canvas(bmp)
        val unit=size.toFloat()
        val center=unit*.5f
        val coreRadius=unit*prefs.coreRadius()
        val orbits=CosmicOrbitGeometry.specs(today.dayOfMonth,today.lengthOfMonth())
        val dots=OriginalWelcomeGeometry.dotMarkers(today.dayOfMonth,today.lengthOfMonth())
        val palette=if(prefs.adaptiveColors)CosmicColors.palette(prefs).toList() else CosmicClockPalette.colors(prefs.petalPalette, chrome).map { it.toArgb() }
        fun drawCircle(x:Float,y:Float,r:Float,color:Int,alpha:Float) {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color=color
                this.alpha=(alpha.coerceIn(0f,1f)*255).toInt()
            }
            canvas.drawCircle(x,y,r,paint)
        }
        fun gradientCircle(
            x:Float,y:Float,r:Float,shaderX:Float,shaderY:Float,shaderRadius:Float,
            colors:IntArray,stops:FloatArray,alpha:Float
        ) {
            val brush=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader=RadialGradient(shaderX,shaderY,shaderRadius,colors,stops,Shader.TileMode.CLAMP)
                this.alpha=(alpha.coerceIn(0f,1f)*255).toInt()
            }
            canvas.drawCircle(x,y,r,brush)
        }
        if (ambient) gradientCircle(center,center,unit*.52f,center,center,unit*.52f,
            intArrayOf(if(prefs.adaptiveColors)(prefs.cosmicPrimary and 0xffffff) or 0x18000000 else if(prefs.geometryTheme==ClockTheme.BLACK_HOLE)0x0cff8a21 else 0x338F4770,Color.TRANSPARENT),floatArrayOf(0f,1f),1f)
        // Original painting clips every orbit outside the central info sphere.
        val clip=Path().apply { addCircle(center,center,coreRadius,Path.Direction.CW) }
        canvas.save()
        canvas.clipOutPath(clip)
        if (petals && prefs.geometryTheme.isScene) {
            CosmicSceneRenderer.layer(prefs,size,false,today=today).also { canvas.drawBitmap(it,0f,0f,null);it.recycle() }
        }
        if (petals && prefs.geometryTheme != ClockTheme.UBUNTU_ORIGINAL && !prefs.geometryTheme.isScene) {
            ClockThemeRenderer.drawStatic(canvas, prefs.geometryTheme, center, center, unit,
                prefs.petalCount(today), prefs.cosmicColorMix,if(prefs.adaptiveColors)CosmicColors.palette(prefs) else null)
        }
        if (petals && prefs.geometryTheme == ClockTheme.UBUNTU_ORIGINAL) orbits.forEachIndexed { i,o ->
            val a=CosmicOrbitGeometry.angle(o,0f,prefs.orbitalIntensity)
            val d=CosmicOrbitGeometry.distance(o,0f,prefs.orbitalIntensity)
            val x=center+unit*cos(a)*d
            val y=center+unit*sin(a)*d
            val color=palette[if(!prefs.adaptiveColors && prefs.petalPalette==PetalPalette.ORIGINAL && o.previousMonth)
                5 else i%palette.size]
            val luminosity=if(prefs.orbitalIntensity==OrbitalIntensity.OFF)1f else .94f+.06f*sin(o.radialPhase)
            drawCircle(x,y,unit*o.radius,color,(o.opacity*luminosity).coerceAtMost(.46f))
        }
        canvas.restore()
        // Exactly the same central disc gradient and coreOpacity adjustment.
        if (core) gradientCircle(center,center,coreRadius,
            center+coreRadius*.16f,center-coreRadius*.21f,coreRadius*1.6f,
            if (prefs.geometryTheme == ClockTheme.BLACK_HOLE) intArrayOf(0xFF030306.toInt(),0xFF020204.toInt(),0xFF000001.toInt())
            else if(prefs.geometryTheme.isTeam)intArrayOf(0xff17171c.toInt(),0xff101014.toInt(),0xff08080c.toInt())
            else intArrayOf(0xFF292331.toInt(),0xFF231B2C.toInt(),0xF51C1524.toInt()),
            floatArrayOf(0f,.67f,1f),if (prefs.geometryTheme == ClockTheme.BLACK_HOLE) 1f else prefs.coreOpacity.coerceIn(.45f,1f))
        val rim=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE
            strokeWidth=unit*.0019f
            color=0x33BEB0C8
        }
        if (core) canvas.drawCircle(center,center,coreRadius,rim)
        if (petals && prefs.geometryTheme == ClockTheme.BLACK_HOLE)
            CosmicSceneRenderer.layer(prefs,size,true,today=today).also { canvas.drawBitmap(it,0f,0f,null);it.recycle() }
        // Original idle marker positions, states and radii; brightness is a static adjustment.
        val glow=CosmicClockPalette.glow(prefs.luminousIntensity)/.82f
        if (markers) dots.forEachIndexed { i,d ->
            val color=when(d.state) {
                0 -> 0xFFEFC0D6.toInt(); 1 -> 0xFFF8F5FA.toInt(); else -> 0xFFC9A7BC.toInt()
            }
            val r=unit*if(d.state==1).011f else .0058f
            val idle=when(d.state){0->.18f;1->.27f;else->.14f}
            drawCircle(unit*d.x,unit*d.y,r,color,idle*glow)
        }
        if (petals) CosmicStarField.drawStatic(canvas, prefs, center, center, unit)
        return bmp
    }
}

