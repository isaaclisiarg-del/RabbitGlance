package com.isaaclisi.rabbitglance

import android.graphics.*
import android.util.LruCache
import kotlin.math.*

/** Emissive turbulent disk + lensed far-side arc. No uniform concentric wire rings. */
internal object BlackHoleArt {
    private val cache=object:LruCache<String,Bitmap>(8*1024*1024) {
        override fun sizeOf(key: String,value: Bitmap)=value.allocationByteCount
    }
    /** A bounded D^3 beaming profile across the material, rather than a separate light spot. */
    fun beaming(cosine: Float,enabled: Boolean): Float {
        if(!enabled)return 1f
        val d=sqrt(1f-.42f*.42f)/(1f+.42f*cosine.coerceIn(-1f,1f))
        return (d*d*d*.72f).coerceIn(.2f,2.8f)
    }
    fun layer(p: ClockPreferences,size: Int,front: Boolean): Bitmap {
        val palette=CosmicColors.palette(p)
        val key=listOf(size,front,p.blackHoleMass,p.diskTilt,p.diskInclination,p.accretionGlow,p.photonRing,
            p.dopplerBeaming,p.adaptiveColors,palette[0],palette[1]).joinToString("/")
        synchronized(cache) {
            cache.get(key)?.let { return it.copy(Bitmap.Config.ARGB_8888,false) }
        }
        val pixels=IntArray(size*size)
        val tilt=Math.toRadians(p.diskTilt.toDouble()).toFloat();val ct=cos(tilt);val st=sin(tilt)
        val horizon=p.coreRadius();val inner=horizon+.019f;val outer=.505f
        val primary=palette[0];val secondary=palette[1]
        val hot=CosmicColors.blend(primary,Color.WHITE,.86f)
        for(py in 0 until size)for(px in 0 until size) {
            val dx=(px+.5f)/size-.5f;val dy=(py+.5f)/size-.5f
            val x=ct*dx+st*dy;val y=-st*dx+ct*dy
            val rr=hypot(x,y);val diskR=hypot(x,y/p.diskInclination)
            var emission=0f;var thermal=0f;var secondaryMix=0f
            if((y>=0f)==front && diskR>inner-.014f && diskR<outer+.025f) {
                val a=atan2(y/p.diskInclination,x)
                // Sheared spiral turbulence with broad eddies and fine, irregular filaments.
                val spiral=a*5f-diskR*84f
                val eddy=sin(a*9f+diskR*31f)*.50f+sin(a*17f-diskR*62f)*.25f
                val filament=(.5f+.5f*sin(spiral+eddy*2.4f+sin(diskR*117f+a*3f))).pow(5)
                val grain=.79f+.21f*sin(px*1.77f+py*2.63f+sin(px*.31f)*5f)
                val edge=((diskR-inner+.012f)/.024f).coerceIn(0f,1f)
                val fade=((outer+.018f-diskR)/.095f).coerceIn(0f,1f).pow(2)
                val heat=exp(-max(0f,diskR-inner)*13f)
                val beam=beaming(x/max(diskR,.01f),p.dopplerBeaming)
                emission=(.34f+filament*1.05f+eddy*.09f)*heat*edge*fade*grain*beam*(if(front)5.8f else 2.8f)
                thermal=heat*(.55f+filament*.34f)*beam*.95f
                secondaryMix=((diskR-inner)/.17f).coerceIn(0f,1f)
            }
            if(!front && rr>horizon-.002f) {
                val a=atan2(y,x)
                // The far side is lifted into an asymmetric arch by the lens.
                val top=(.38f-.62f*y/max(rr,.01f)).coerceIn(.08f,1f)
                val warp=.003f*sin(a*13f+sin(a*5f)*2f)
                val q=(rr-horizon-.025f-warp)/.020f
                val threads=.55f+.45f*(.5f+.5f*sin((rr-horizon)*570f+sin(a*11f)*1.9f)).pow(3)
                val beam=beaming(x/max(rr,.01f),p.dopplerBeaming)
                val arch=exp(-q*q)*top*threads*beam*1.6f
                val haze=exp(-((rr-horizon-.035f)/.064f).pow(2))*top*.19f*beam
                emission+=arch+haze
                if(p.photonRing) {
                    val photon=exp(-((rr-horizon-.006f)/.0026f).pow(2))*(.60f+.28f*sin(a*2f))
                    emission+=photon*1.6f;thermal=max(thermal,photon)
                }
                thermal=max(thermal,arch*.67f)
            }
            emission*=p.accretionGlow
            val a=((1f-exp(-emission*1.18f))*255f).roundToInt().coerceIn(0,255)
            if(a>0) {
                val base=CosmicColors.blend(primary,secondary,secondaryMix*.60f)
                val color=CosmicColors.blend(base,hot,(thermal*.72f+max(0f,emission-1.15f)*.22f).coerceIn(0f,.92f))
                pixels[py*size+px]=(a shl 24) or (color and 0xffffff)
            }
        }
        val bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
        synchronized(cache) { cache.put(key,bitmap) }
        return bitmap.copy(Bitmap.Config.ARGB_8888,false)
    }
    fun drawFlow(canvas: Canvas,p: ClockPreferences,cx: Float,cy: Float,u: Float,front: Boolean,time: Float) {
        if(p.orbitalIntensity==OrbitalIntensity.OFF)return
        val palette=CosmicColors.palette(p)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND }
        canvas.save();canvas.rotate(p.diskTilt,cx,cy);canvas.scale(1f,p.diskInclination,cx,cy)
        repeat(18) { i ->
            val r=u*(p.coreRadius()+.025f+(i%6)*.025f)
            val a=time*p.accretionSpeed*(.65f-(i%6)*.055f)+i*2.399963f
            val mask=max(0f,sin(a)*(if(front)1f else -1f))
            val beam=beaming(cos(a),p.dopplerBeaming)
            paint.color=CosmicColors.blend(palette[i%2],Color.WHITE,.65f)
            paint.alpha=(mask*beam*.35f*p.accretionGlow*255).toInt().coerceIn(0,220)
            paint.strokeWidth=u*.0022f
            canvas.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),Math.toDegrees(a.toDouble()).toFloat()-16f,16f,false,paint)
        }
        canvas.restore()
    }
    fun nativeFlow(doc: ClockDrawDocument,p: ClockPreferences,time: Float,cx: Float,cy: Float,u: Float,front: Boolean) {
        if(p.orbitalIntensity==OrbitalIntensity.OFF)return
        val palette=CosmicColors.palette(p)
        doc.save();doc.rotate(p.diskTilt,cx,cy);doc.scale(1f,p.diskInclination,cx,cy)
        repeat(18) { i ->
            val r=u*(p.coreRadius()+.025f+(i%6)*.025f)
            val angle=doc.expression(time,p.accretionSpeed*(if(p.orbitalIntensity==OrbitalIntensity.COSMIC)1.65f else 1f)*(.65f-(i%6)*.055f),ClockDrawDocument.MUL,i*2.399963f,ClockDrawDocument.ADD)
            val mask=doc.expression(angle,ClockDrawDocument.SIN,if(front)1f else -1f,ClockDrawDocument.MUL,0f,ClockDrawDocument.MAX,.34f*p.accretionGlow,ClockDrawDocument.MUL)
            val inverse=if(p.dopplerBeaming)doc.expression(.906f,angle,ClockDrawDocument.COS,.42f,ClockDrawDocument.MUL,1f,ClockDrawDocument.ADD,ClockDrawDocument.DIV) else 1f
            val alpha=if(p.dopplerBeaming)doc.expression(inverse,inverse,ClockDrawDocument.MUL,inverse,ClockDrawDocument.MUL,.72f,ClockDrawDocument.MUL,mask,ClockDrawDocument.MUL,.86f,ClockDrawDocument.MIN) else mask
            val degrees=doc.expression(angle,57.29578f,ClockDrawDocument.MUL)
            doc.save();doc.rotate(degrees,cx,cy)
            val color=CosmicColors.blend(palette[i%2],Color.WHITE,.65f)
            // Short emissive streaks advect at different speeds, not a ring of orbiting dots.
            doc.line(cx+r*.967f,cy-r*.254f,cx+r*.992f,cy-r*.127f,u*.0022f,color,alpha)
            doc.line(cx+r*.992f,cy-r*.127f,cx+r,cy,u*.0022f,color,alpha)
            doc.restore()
        }
        doc.restore()
    }
}
