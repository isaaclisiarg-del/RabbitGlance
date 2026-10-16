package com.isaaclisi.classicclock

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

/** Shares one lens model between the settings preview and the actual wallpaper. */
internal class CosmicWallpaperRenderer(private val context: Context) {
    private var source = WallpaperImageStore.load(context)
    private var fitted: Bitmap? = null
    private var fittedWidth = 0; private var fittedHeight = 0
    private var sceneKey: List<Any>? = null
    private var scenePixels = 0
    private var backScene: Bitmap? = null
    private var frontScene: Bitmap? = null
    private var revision = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mesh = FloatArray((32+1)*(56+1)*2)
    private var runtime: RuntimeShader? = null
    private var shaderAttempted = false
    var lastUsedGpu = false; private set

    fun reload(prefs: ClockPreferences) {
        if (revision == prefs.wallpaperRevision) return
        revision = prefs.wallpaperRevision
        source.recycle(); source=WallpaperImageStore.load(context)
        fitted?.recycle(); fitted=null; fittedWidth=0
    }
    private fun fit(width: Int, height: Int): Bitmap {
        if (fittedWidth==width && fittedHeight==height && fitted!=null) return fitted!!
        fitted?.recycle()
        val bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val scale=max(width/source.width.toFloat(),height/source.height.toFloat())
        Canvas(bmp).drawBitmap(source,null,RectF((width-source.width*scale)/2,(height-source.height*scale)/2,
            (width+source.width*scale)/2,(height+source.height*scale)/2),paint)
        fitted=bmp; fittedWidth=width; fittedHeight=height;return bmp
    }
    fun draw(canvas: Canvas, width: Int, height: Int, preferences: ClockPreferences, seconds: Float) {
        val prefs=CosmicColors.image(context,preferences)
        if(width<=0 || height<=0)return
        canvas.drawColor(Color.BLACK)
        reload(prefs)
        val sceneTime=if(prefs.orbitalIntensity==OrbitalIntensity.OFF)0f else seconds*(if(prefs.orbitalIntensity==OrbitalIntensity.COSMIC)1.65f else 1f)
        // Bound background texture memory; Canvas scales the rendered scene to the surface.
        val ratio=min(1f,1440f/max(width,height))
        val w=(width*ratio).toInt().coerceAtLeast(1); val h=(height*ratio).toInt().coerceAtLeast(1)
        val bitmap=fit(w,h)
        val cx=w*prefs.wallpaperCenterX; val cy=h*prefs.wallpaperCenterY
        val radius=min(w,h)*prefs.wallpaperRadius
        canvas.save(); canvas.scale(width/w.toFloat(),height/h.toFloat())
        lastUsedGpu=false
        if(prefs.wallpaperLensEnabled && Build.VERSION.SDK_INT>=33 && canvas.isHardwareAccelerated) {
            if(!shaderAttempted) { shaderAttempted=true;runtime=runCatching { RuntimeShader(context.assets.open("gravitational_lens.agsl").bufferedReader().use { it.readText() }) }.getOrNull() }
            runtime?.let { shader ->
                runCatching {
                    shader.setInputShader("wallpaper",BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP))
                    shader.setFloatUniform("center",cx,cy);shader.setFloatUniform("radius",radius)
                    shader.setFloatUniform("strength",prefs.lensStrength);shader.setFloatUniform("time",sceneTime)
                    paint.shader=shader; canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),paint);paint.shader=null;lastUsedGpu=true
                }.onFailure { paint.shader=null;runtime=null }
            }
        }
        if(!lastUsedGpu) {
            if(prefs.wallpaperLensEnabled && prefs.lensStrength>0f) {
                var index=0
                for(y in 0..56)for(x in 0..32) {
                    val point=GravitationalLens.destination(x*w/32f,y*h/56f,cx,cy,radius,prefs.lensStrength,sceneTime)
                    mesh[index++]=point.x;mesh[index++]=point.y
                }
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmapMesh(bitmap,32,56,mesh,0,null,0,paint)
            } else canvas.drawBitmap(bitmap,0f,0f,paint)
        }
        if(prefs.wallpaperScene) {
            val scene=prefs.copy(geometryTheme=ClockTheme.BLACK_HOLE,wallpaperClockOnly=false)
            val unit=radius*2f
            // The same inclination, horizon and accretion controls drive widget and wallpaper.
            val imageSize=(unit*1.5f).toInt().coerceIn(128,512)
            val key=listOf(scene.blackHoleMass,scene.diskInclination,scene.diskTilt,scene.accretionGlow,
                if(scene.photonRing)1f else 0f,if(scene.dopplerBeaming)1f else 0f,
                scene.cosmicPrimary,scene.cosmicSecondary,if(scene.adaptiveColors)1f else 0f)
            if (sceneKey != key || scenePixels != imageSize) {
                backScene?.recycle(); frontScene?.recycle()
                backScene=CosmicSceneRenderer.layer(scene,imageSize,false)
                frontScene=CosmicSceneRenderer.layer(scene,imageSize,true)
                sceneKey=key; scenePixels=imageSize
            }
            paint.alpha=(235+20*sin(sceneTime*.4f)).toInt()
            canvas.drawBitmap(backScene!!,null,RectF(cx-unit*.5f,cy-unit*.5f,cx+unit*.5f,cy+unit*.5f),paint)
            BlackHoleArt.drawFlow(canvas,scene,cx,cy,unit,false,sceneTime)
            paint.alpha=255
            paint.color=0xff010104.toInt();paint.shader=null
            canvas.drawCircle(cx,cy,unit*scene.coreRadius(),paint)
            canvas.drawBitmap(frontScene!!,null,RectF(cx-unit*.5f,cy-unit*.5f,cx+unit*.5f,cy+unit*.5f),paint)
            BlackHoleArt.drawFlow(canvas,scene,cx,cy,unit,true,sceneTime)
        }
        if(prefs.starsEnabled) CosmicStarField.drawStatic(canvas,prefs,cx,cy,radius*2f,seconds)
        canvas.restore()
    }
    fun release() { fitted?.recycle();fitted=null;source.recycle();backScene?.recycle();frontScene?.recycle();backScene=null;frontScene=null }
}
