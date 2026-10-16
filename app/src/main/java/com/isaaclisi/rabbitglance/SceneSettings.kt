package com.isaaclisi.rabbitglance

import android.content.Context
import android.graphics.Color
import kotlin.math.roundToInt

enum class ClockFace { DIGITAL, ANALOG }
enum class StarDensity(val count: Int) { SPARSE(18), BALANCED(36), RICH(60) }
enum class StarShape { CROSS, WEBB }
enum class SceneSize(val scale: Float) { COMPACT(.78f), NORMAL(.90f), LARGE(1f) }

/** Only BLACK_HOLE inputs. Kept names map directly to the recovered source. */
internal data class ClockPreferences(
 val size: SceneSize = SceneSize.NORMAL,
 val orbitalIntensity: OrbitalIntensity = OrbitalIntensity.SUBTLE,
 val motionSpeed: Float = 1f,
 val blackHoleMass: Float = 1f,
 val diskInclination: Float = .3f,
 val diskTilt: Float = 25f,
 val accretionSpeed: Float = 1f,
 val accretionGlow: Float = 1f,
 val photonRing: Boolean = true,
 val dopplerBeaming: Boolean = true,
 val adaptiveColors: Boolean = false,
 val cosmicPrimary: Int = 0xffefa940.toInt(),
 val cosmicSecondary: Int = 0xffb5653d.toInt(),
 val starsEnabled: Boolean = false,
 val starDensity: StarDensity = StarDensity.BALANCED,
 val starShape: StarShape = StarShape.WEBB,
 val starBrightness: Float = .8f,
 val starSize: Float = 1f,
 val starTwinkle: Boolean = true,
 val showClock: Boolean = true,
 val face: ClockFace = ClockFace.DIGITAL,
 val showSeconds: Boolean = false,
 val ringAnimation: Boolean = true,
 val minutePulseDuration: MinutePulseDuration = MinutePulseDuration.NORMAL,
 val dateVisible: Boolean = true
) {
 fun coreRadius() = .255f + .04f * blackHoleMass
}

internal object SettingsStore {
 fun read(c: Context, id: Int = 0): ClockPreferences {
  val s=c.getSharedPreferences("scene_$id",Context.MODE_PRIVATE)
  if(id!=0 && !s.contains("size"))return read(c)
  return ClockPreferences(size=SceneSize.entries.getOrElse(s.getInt("size",1)){SceneSize.NORMAL},
   orbitalIntensity=OrbitalIntensity.entries.getOrElse(s.getInt("intensity",1)){OrbitalIntensity.SUBTLE},
   motionSpeed=s.getFloat("speed",1f).coerceIn(.2f,2f), accretionGlow=s.getFloat("glow",1f).coerceIn(.3f,1.6f),
   starsEnabled=s.getBoolean("stars",false), showClock=s.getBoolean("clock",true),showSeconds=s.getBoolean("seconds",false),face=ClockFace.entries.getOrElse(s.getInt("face",0)){ClockFace.DIGITAL})
 }
 fun save(c: Context,id: Int,p: ClockPreferences) { c.getSharedPreferences("scene_$id",0).edit()
  .putInt("size",p.size.ordinal).putInt("intensity",p.orbitalIntensity.ordinal).putFloat("speed",p.motionSpeed)
  .putFloat("glow",p.accretionGlow).putBoolean("stars",p.starsEnabled).putBoolean("clock",p.showClock)
  .putBoolean("seconds",p.showSeconds).putInt("face",p.face.ordinal).apply() }
 fun delete(c: Context,id: Int) { c.deleteSharedPreferences("scene_$id") }
}

/** Exact blend and original non-adaptive thermal palette; no wallpaper dependency. */
internal object CosmicColors {
 fun blend(a: Int,b: Int,t: Float): Int {
  val f=t.coerceIn(0f,1f)
  fun c(x: Int,y: Int)=(x+(y-x)*f).roundToInt().coerceIn(0,255)
  return Color.rgb(c(Color.red(a),Color.red(b)),c(Color.green(a),Color.green(b)),c(Color.blue(a),Color.blue(b)))
 }
 fun palette(p: ClockPreferences): IntArray = if(p.adaptiveColors)intArrayOf(p.cosmicPrimary,p.cosmicSecondary,
  blend(p.cosmicPrimary,p.cosmicSecondary,.45f),blend(p.cosmicSecondary,Color.WHITE,.45f))
  else intArrayOf(0xffffa431.toInt(),0xffe06a24.toInt(),0xffffcc73.toInt(),0xffffe7b0.toInt())
}
