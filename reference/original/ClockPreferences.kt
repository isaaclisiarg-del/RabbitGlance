package com.isaaclisi.classicclock

import android.content.Context
import android.content.SharedPreferences
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class ClockStyle { MINIMAL, UBUNTU_TOUCH_CLASSIC, UBUNTU_TOUCH_ORIGINAL }
/** The theme changes actual outline geometry. The existing petalPalette remains independent. */
enum class ClockTheme { UBUNTU_ORIGINAL, STAR, NEBULA, BLACK_HOLE, STELLAR_NURSERY, ESTUDIANTES, G2_ESPORTS, RIVER_PLATE, BOCA_JUNIORS }
/** Multicolor compositions for non-Ubuntu shapes; AUTO follows the selected geometry. */
enum class CosmicColorMix { AUTO, SUPERNOVA, AURORA, DEEP_SPACE }
enum class ClockFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }
enum class ClockFace { DIGITAL, ANALOG }
enum class ClockSize { COMPACT, NORMAL, LARGE }
enum class StarDensity(val count: Int) { SPARSE(18), BALANCED(36), RICH(60) }
enum class StarShape { CROSS, WEBB }
enum class TeamAnimation { SOLID, WAVE }
enum class TeamEventFrequency(val seconds: Float) { CALM(72f), NORMAL(44f), LIVELY(30f) }
enum class TeamRotation { STILL, BALANCE, ORBIT }
enum class TeamColorMode { CLUB, WALLPAPER, PRISM }
enum class RenderQuality(val fps: Int) { ECONOMY(15), BALANCED(30), FLUID(60) }

val ClockTheme.isTeam: Boolean get() = this == ClockTheme.ESTUDIANTES || this == ClockTheme.G2_ESPORTS || isFootball
val ClockTheme.isFootball: Boolean get() = this == ClockTheme.RIVER_PLATE || this == ClockTheme.BOCA_JUNIORS
val ClockTheme.hasPetals: Boolean get() = this != ClockTheme.BLACK_HOLE && !isTeam
val ClockTheme.isScene: Boolean get() = this == ClockTheme.BLACK_HOLE || this == ClockTheme.STELLAR_NURSERY || isTeam
fun ClockTheme.title(): String = when (this) {
    ClockTheme.UBUNTU_ORIGINAL -> "Ubuntu Original"
    ClockTheme.STAR -> "Estrella de plasma"
    ClockTheme.NEBULA -> "Nebulosa"
    ClockTheme.BLACK_HOLE -> "Agujero negro"
    ClockTheme.STELLAR_NURSERY -> "Velo estelar"
    ClockTheme.ESTUDIANTES -> "Estudiantes de La Plata"
    ClockTheme.G2_ESPORTS -> "G2 Esports"
    ClockTheme.RIVER_PLATE -> "River Plate"
    ClockTheme.BOCA_JUNIORS -> "Boca Juniors"
}

data class ClockPreferences(
    val nativeRendering: Boolean = true,
    val showClock: Boolean = true,
    val teamAnimation: TeamAnimation = TeamAnimation.SOLID,
    val teamCelebration: Boolean = true,
    /** Per-widget setting: animate the G2 mask over Estudiantes when power is present. */
    val chargingBlackFire: Boolean = true,
    /** Battery state belongs to the rendered document, never persisted. */
    val chargingNow: Boolean = false,
    val g2MontageEnabled: Boolean = true,
    val teamEventsEnabled: Boolean = true,
    val teamEventMask: Int = 31,
    val teamEventFrequency: TeamEventFrequency = TeamEventFrequency.NORMAL,
    /** Host identity and one-shot preview are document inputs, never shared preferences. */
    val teamEventSeed: Int = 0,
    val teamEventPreview: Int = -1,
    val teamCrestScale: Float = 1f,
    val teamGlow: Float = 1f,
    val teamWave: Float = 1f,
    val teamRotation: TeamRotation = TeamRotation.BALANCE,
    val teamColorMode: TeamColorMode = TeamColorMode.WALLPAPER,
    val adaptiveColors: Boolean = true,
    val cosmicPrimary: Int = 0xffefa940.toInt(),
    val cosmicSecondary: Int = 0xffb5653d.toInt(),
    val starsEnabled: Boolean = false,
    val starDensity: StarDensity = StarDensity.BALANCED,
    val starShape: StarShape = StarShape.WEBB,
    val starBrightness: Float = 0.8f,
    val starSize: Float = 1f,
    val starTwinkle: Boolean = true,
    val motionSpeed: Float = 1f,
    val blackHoleMass: Float = 1f,
    val diskInclination: Float = 0.3f,
    val diskTilt: Float = 25f,
    val accretionSpeed: Float = 1f,
    val accretionGlow: Float = 1f,
    val photonRing: Boolean = true,
    val dopplerBeaming: Boolean = true,
    val lensStrength: Float = 0.8f,
    val wallpaperLensEnabled: Boolean = true,
    val wallpaperCenterX: Float = 0.5f,
    val wallpaperCenterY: Float = 0.43f,
    val wallpaperRadius: Float = 0.32f,
    val wallpaperScene: Boolean = true,
    val wallpaperClockOnly: Boolean = false,
    val wallpaperRevision: Int = 0,
    val renderQuality: RenderQuality = RenderQuality.BALANCED,

    val style: ClockStyle = ClockStyle.MINIMAL,
    val geometryTheme: ClockTheme = ClockTheme.UBUNTU_ORIGINAL,
    val cosmicColorMix: CosmicColorMix = CosmicColorMix.AUTO,
    val cosmicPetals: Int = 20,
    val matchOriginalDensity: Boolean = true,
    val showDate: Boolean = true,
    val face: ClockFace = ClockFace.DIGITAL,
    val showSeconds: Boolean = false,
    val format: ClockFormat = ClockFormat.SYSTEM,
    val size: ClockSize = ClockSize.NORMAL,
    val trembleOnCharge: Boolean = true,
    val ringAnimation: Boolean = true,
    val orbitalIntensity: OrbitalIntensity = OrbitalIntensity.SUBTLE,
    val petalPalette: PetalPalette = PetalPalette.COSMIC,
    val luminousIntensity: ClockGlow = ClockGlow.NORMAL,
    val minutePulseDuration: MinutePulseDuration = MinutePulseDuration.NORMAL,
    /** Opacity of the central sphere only; legacy data retains its original opaque appearance. */
    val coreOpacity: Float = 1f
) {
    val dateVisible: Boolean get() = showDate && showClock
    val animatesArtwork: Boolean get() = orbitalIntensity != OrbitalIntensity.OFF ||
        (starsEnabled && starTwinkle) || (ringAnimation && !geometryTheme.isTeam)

    fun coreRadius(): Float = if(geometryTheme==ClockTheme.BLACK_HOLE)
        .255f+.04f*blackHoleMass else OriginalWelcomeGeometry.CORE_RADIUS

    fun petalCount(date: java.time.LocalDate): Int = if (matchOriginalDensity)
        CosmicOrbitGeometry.specs(date.dayOfMonth, date.lengthOfMonth()).size
        else cosmicPetals.coerceIn(12, 20)

    fun uses24Hours(system24Hours: Boolean): Boolean = when (format) {
        ClockFormat.SYSTEM -> system24Hours
        ClockFormat.TWELVE_HOUR -> false
        ClockFormat.TWENTY_FOUR_HOUR -> true
    }
}

/** Each placed widget owns a frozen snapshot; the legacy store supplies new-widget defaults. */
class ClockPreferenceStore(private val context: Context,
    private val widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID) {
    private val prefs = if(widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
        context.getSharedPreferences("unity_clock", Context.MODE_PRIVATE) else instance(context,widgetId)

    companion object {
        private const val INSTANCE = "_instanceInitialized"
        private fun name(id: Int) = "unity_clock_widget_$id"
        @Synchronized private fun instance(context: Context,id: Int): SharedPreferences {
            require(id > 0)
            val target=context.getSharedPreferences(name(id),Context.MODE_PRIVATE)
            if(!target.getBoolean(INSTANCE,false)) {
                val source=context.getSharedPreferences("unity_clock",Context.MODE_PRIVATE)
                copy(source.all,target)
            }
            return target
        }
        private fun copy(values: Map<String,*>,target: SharedPreferences) {
            val edit=target.edit().clear()
            for((key,value) in values)when(value) {
                is String -> edit.putString(key,value)
                is Boolean -> edit.putBoolean(key,value)
                is Float -> edit.putFloat(key,value)
                is Int -> edit.putInt(key,value)
                is Long -> edit.putLong(key,value)
            }
            edit.putBoolean(INSTANCE,true).apply()
        }
        fun initializePlaced(context: Context) {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,ClassicClockWidget::class.java))
                .forEach { instance(context,it) }
        }
        fun delete(context: Context,id: Int) { if(id>0)context.deleteSharedPreferences(name(id)) }
        fun restore(context: Context,oldIds: IntArray,newIds: IntArray) {
            // Read every source before writing: host remapping can overlap old IDs.
            val snapshots=oldIds.map { instance(context,it).all.toMap() }
            oldIds.indices.take(newIds.size).forEach { i ->
                copy(snapshots[i],context.getSharedPreferences(name(newIds[i]),Context.MODE_PRIVATE))
            }
            oldIds.filterNot { it in newIds }.forEach { delete(context,it) }
        }
    }
    /** Small immutable-by-value pin payload, captured before Android allocates a widget ID. */
    fun snapshot(): Bundle = Bundle().apply {
        for((key,value) in prefs.all)when(value) {
            is String -> putString(key,value)
            is Boolean -> putBoolean(key,value)
            is Float -> putFloat(key,value)
            is Int -> putInt(key,value)
            is Long -> putLong(key,value)
        }
    }
    @Suppress("DEPRECATION")
    fun saveSnapshot(snapshot: Bundle) {
        require(widgetId>0)
        copy(snapshot.keySet().associateWith { snapshot.get(it) },prefs)
    }
    fun read() = ClockPreferences(
        nativeRendering = prefs.getBoolean("nativeRendering", true),
        showClock = prefs.getBoolean("showClock", true),
        teamAnimation = readEnum("teamAnimation",TeamAnimation.SOLID),
        teamCelebration = prefs.getBoolean("teamCelebration",true),
        chargingBlackFire = prefs.getBoolean("chargingBlackFire",true),
        g2MontageEnabled = prefs.getBoolean("g2MontageEnabled",true),
        teamEventsEnabled = prefs.getBoolean("teamEventsEnabled",true),
        teamEventMask = prefs.getInt("teamEventMask",31) and 31,
        teamEventFrequency = readEnum("teamEventFrequency",TeamEventFrequency.NORMAL),
        teamCrestScale = readFloat("teamCrestScale",1f,.75f,1.15f),
        teamGlow = readFloat("teamGlow",1f,.3f,1.5f),
        teamWave = readFloat("teamWave",1f,0f,2f),
        teamRotation = readEnum("teamRotation",TeamRotation.BALANCE),
        teamColorMode = readEnum("teamColorMode",TeamColorMode.WALLPAPER),
        adaptiveColors = prefs.getBoolean("adaptiveColors", true),
        starsEnabled = prefs.getBoolean("starsEnabled", false),
        starDensity = readEnum("starDensity", StarDensity.BALANCED),
        starShape = readEnum("starShape", StarShape.WEBB),
        starBrightness = readFloat("starBrightness", 0.8f, 0.15f, 1.5f),
        starSize = readFloat("starSize", 1f, 0.5f, 1.8f),
        starTwinkle = prefs.getBoolean("starTwinkle", true),
        motionSpeed = readFloat("motionSpeed", 1f, 0.2f, 2.0f),
        blackHoleMass = readFloat("blackHoleMass", 1f, 0.65f, 1.25f),
        diskInclination = readFloat("diskInclination", 0.3f, 0.15f, 0.7f),
        diskTilt = readFloat("diskTilt", 25f, -55.0f, 55.0f),
        accretionSpeed = readFloat("accretionSpeed", 1f, 0.2f, 2.0f),
        accretionGlow = readFloat("accretionGlow", 1f, 0.3f, 1.6f),
        photonRing = prefs.getBoolean("photonRing", true),
        dopplerBeaming = prefs.getBoolean("dopplerBeaming", true),
        lensStrength = readFloat("lensStrength", 0.8f, 0.0f, 1.5f),
        wallpaperLensEnabled = prefs.getBoolean("wallpaperLensEnabled", true),
        wallpaperCenterX = readFloat("wallpaperCenterX", 0.5f, 0.1f, 0.9f),
        wallpaperCenterY = readFloat("wallpaperCenterY", 0.43f, 0.1f, 0.9f),
        wallpaperRadius = readFloat("wallpaperRadius", 0.32f, 0.12f, 0.48f),
        wallpaperScene = prefs.getBoolean("wallpaperScene", true),
        wallpaperClockOnly = prefs.getBoolean("wallpaperClockOnly", false),
        wallpaperRevision = prefs.getInt("wallpaperRevision", 0).coerceAtLeast(0),
        renderQuality = readEnum("renderQuality", RenderQuality.BALANCED),
        style = readEnum("style", ClockStyle.MINIMAL),
        geometryTheme = readEnum("geometryTheme", ClockTheme.UBUNTU_ORIGINAL),
        cosmicColorMix = readEnum("cosmicColorMix", CosmicColorMix.AUTO),
        cosmicPetals = prefs.getInt("cosmicPetals", 20).coerceIn(12, 20),
        matchOriginalDensity = prefs.getBoolean("matchOriginalDensity", true),
        showDate = prefs.getBoolean("showDate", true),
        face = readEnum("face", ClockFace.DIGITAL),
        showSeconds = prefs.getBoolean("seconds", false),
        format = readEnum("format", ClockFormat.SYSTEM),
        size = readEnum("size", ClockSize.NORMAL),
        trembleOnCharge = prefs.getBoolean("trembleOnCharge", true),
        ringAnimation = prefs.getBoolean("cosmicRing", true),
        orbitalIntensity = readEnum("cosmicOrbit", OrbitalIntensity.SUBTLE),
        petalPalette = readEnum("cosmicPalette", PetalPalette.COSMIC),
        luminousIntensity = readEnum("cosmicGlow", ClockGlow.NORMAL),
        minutePulseDuration = readEnum("cosmicPulseDuration", MinutePulseDuration.NORMAL),
        coreOpacity = prefs.getFloat("cosmicCoreOpacity", 1f).let { if (it.isFinite()) it.coerceIn(.45f, 1f) else 1f }
    )
    private fun readFloat(key: String, default: Float, low: Float, high: Float): Float =
        prefs.getFloat(key, default).let { if (it.isFinite()) it.coerceIn(low, high) else default }

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == prefs.getString(key, null) } ?: fallback

    fun save(value: ClockPreferences) {
        // Upgrade placed legacy widgets before changing defaults or the shared wallpaper.
        if(widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)initializePlaced(context)
        prefs.edit().putBoolean("teamEventsEnabled",value.teamEventsEnabled)
            .putInt("teamEventMask",value.teamEventMask and 31).putString("teamEventFrequency",value.teamEventFrequency.name)
            .putBoolean("showClock",value.showClock).putString("teamAnimation",value.teamAnimation.name)
            .putBoolean("teamCelebration",value.teamCelebration).putBoolean("chargingBlackFire",value.chargingBlackFire).putBoolean("g2MontageEnabled",value.g2MontageEnabled).putFloat("teamCrestScale",value.teamCrestScale.let { if(it.isFinite())it.coerceIn(.75f,1.15f) else 1f })
            .putFloat("teamGlow",value.teamGlow.let { if(it.isFinite())it.coerceIn(.3f,1.5f) else 1f })
            .putFloat("teamWave",value.teamWave.let { if(it.isFinite())it.coerceIn(0f,2f) else 1f })
            .putString("teamRotation",value.teamRotation.name).putString("teamColorMode",value.teamColorMode.name)
            .putBoolean("adaptiveColors",value.adaptiveColors).putBoolean("nativeRendering", value.nativeRendering).putString("style", value.style.name).putString("geometryTheme", value.geometryTheme.name)
            .putBoolean("starsEnabled", value.starsEnabled)
            .putString("starDensity", value.starDensity.name)
            .putString("starShape", value.starShape.name)
            .putFloat("starBrightness", value.starBrightness.let { if (it.isFinite()) it.coerceIn(0.15f, 1.5f) else 0.8f })
            .putFloat("starSize", value.starSize.let { if (it.isFinite()) it.coerceIn(0.5f, 1.8f) else 1f })
            .putBoolean("starTwinkle", value.starTwinkle)
            .putFloat("motionSpeed", value.motionSpeed.let { if (it.isFinite()) it.coerceIn(0.2f, 2.0f) else 1f })
            .putFloat("blackHoleMass", value.blackHoleMass.let { if (it.isFinite()) it.coerceIn(0.65f, 1.25f) else 1f })
            .putFloat("diskInclination", value.diskInclination.let { if (it.isFinite()) it.coerceIn(0.15f, 0.7f) else 0.3f })
            .putFloat("diskTilt", value.diskTilt.let { if (it.isFinite()) it.coerceIn(-55.0f, 55.0f) else 25f })
            .putFloat("accretionSpeed", value.accretionSpeed.let { if (it.isFinite()) it.coerceIn(0.2f, 2.0f) else 1f })
            .putFloat("accretionGlow", value.accretionGlow.let { if (it.isFinite()) it.coerceIn(0.3f, 1.6f) else 1f })
            .putBoolean("photonRing", value.photonRing)
            .putBoolean("dopplerBeaming", value.dopplerBeaming)
            .putFloat("lensStrength", value.lensStrength.let { if (it.isFinite()) it.coerceIn(0.0f, 1.5f) else 0.8f })
            .putBoolean("wallpaperLensEnabled", value.wallpaperLensEnabled)
            .putFloat("wallpaperCenterX", value.wallpaperCenterX.let { if (it.isFinite()) it.coerceIn(0.1f, 0.9f) else 0.5f })
            .putFloat("wallpaperCenterY", value.wallpaperCenterY.let { if (it.isFinite()) it.coerceIn(0.1f, 0.9f) else 0.43f })
            .putFloat("wallpaperRadius", value.wallpaperRadius.let { if (it.isFinite()) it.coerceIn(0.12f, 0.48f) else 0.32f })
            .putBoolean("wallpaperScene", value.wallpaperScene)
            .putBoolean("wallpaperClockOnly", value.wallpaperClockOnly)
            .putInt("wallpaperRevision", value.wallpaperRevision.coerceAtLeast(0))
            .putString("renderQuality", value.renderQuality.name)
            .putString("cosmicColorMix", value.cosmicColorMix.name)
            .putInt("cosmicPetals", value.cosmicPetals.coerceIn(12, 20))
            .putBoolean("matchOriginalDensity", value.matchOriginalDensity)
            .putBoolean("showDate", value.showDate).putString("face", value.face.name)
            .putBoolean("seconds", value.showSeconds)
            .putString("format", value.format.name).putString("size", value.size.name)
            .putBoolean("trembleOnCharge",value.trembleOnCharge)
            .putBoolean("cosmicRing", value.ringAnimation)
            .putString("cosmicOrbit", value.orbitalIntensity.name)
            .putString("cosmicPalette", value.petalPalette.name)
            .putString("cosmicGlow", value.luminousIntensity.name)
            .putString("cosmicPulseDuration", value.minutePulseDuration.name)
            .putFloat("cosmicCoreOpacity", value.coreOpacity.let { if (it.isFinite()) it.coerceIn(.45f, 1f) else 1f }).apply()
    }
}

object ClockTime {
    fun nextUpdateDelay(epochMillis: Long, showSeconds: Boolean): Long {
        val interval = if (showSeconds) 1_000L else 60_000L
        return interval - Math.floorMod(epochMillis, interval)
    }

    fun time(instant: Instant, zone: ZoneId, locale: Locale, use24Hours: Boolean, seconds: Boolean): String {
        val pattern = if (use24Hours) {
            if (seconds) "HH:mm:ss" else "HH:mm"
        } else {
            if (seconds) "h:mm:ss a" else "h:mm a"
        }
        return DateTimeFormatter.ofPattern(pattern, locale).format(instant.atZone(zone))
    }

    fun date(instant: Instant, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
            .format(instant.atZone(zone)).replaceFirstChar { it.titlecase(locale) }
}

