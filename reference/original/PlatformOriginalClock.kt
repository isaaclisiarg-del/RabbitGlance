package com.isaaclisi.classicclock

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.ADD
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.SUB
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.MUL
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.DIV
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.MOD
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.MIN
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.MAX
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.FLOOR
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.SIGN
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.SIN
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.COS
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.IFELSE
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.CONTINUOUS_SECONDS
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.MINUTE
import com.isaaclisi.classicclock.ClockDrawDocument.Companion.HOUR

/** All changing variables below are evaluated by the platform player in the widget's host. */
@android.annotation.TargetApi(35)
internal object PlatformOriginalClock {
    @Volatile private var support: Boolean? = null
    @Volatile private var vectorSupport: Boolean? = null
    @Volatile private var matrixSupport: Boolean? = null

    /** Tiny View probes stay on the UI thread, before artwork is sent to a worker. */
    fun prepare(context: Context) { supported(context);pathsSupported(context);matrixSupported(context) }

    /**
     * Matrix rotation is probed separately from PathData. An animated image
     * can orbit even on firmwares unable to draw a V6 complex vector path.
     * A failure is never used to disable the already-working animated circles.
     */
    fun matrixSupported(context: Context): Boolean {
        if (!supported(context)) return false
        matrixSupport?.let { return it }
        val result = runCatching {
            val doc = ClockDrawDocument(32, 32)
            doc.save()
            doc.rotate(180f, 16f, 16f)
            doc.circle(8f, 16f, 5f, 0xff52d6e5.toInt())
            doc.restore()
            val view = doc.build().apply(context, FrameLayout(context))
            val spec = View.MeasureSpec.makeMeasureSpec(32, View.MeasureSpec.EXACTLY)
            view.measure(spec, spec); view.layout(0, 0, 32, 32)
            val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val works = android.graphics.Color.alpha(image.getPixel(24, 16)) > 0 &&
                android.graphics.Color.alpha(image.getPixel(8, 16)) == 0
            image.recycle()
            works
        }.getOrDefault(false)
        matrixSupport = result
        return result
    }

    /**
     * A firmware can support animated circles but fail to inflate vector PathData.
     * Keep the native engine and use independently animated petal textures in that case.
     */
    fun pathsSupported(context: Context): Boolean {
        if (!supported(context)) return false
        vectorSupport?.let { return it }
        val ok = runCatching {
            val probe = ClockDrawDocument(32, 32)
            probe.path(floatArrayOf(
                ClockDrawDocument.variable(10), 3f, 3f,
                ClockDrawDocument.variable(11), 29f, 3f,
                ClockDrawDocument.variable(11), 16f, 29f,
                ClockDrawDocument.variable(15), ClockDrawDocument.variable(16)
            ), 0xfff17420.toInt(), 1f)
            val view = probe.build().apply(context, FrameLayout(context))
            val measure = View.MeasureSpec.makeMeasureSpec(32, View.MeasureSpec.EXACTLY)
            view.measure(measure, measure)
            view.layout(0, 0, 32, 32)
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val visible = android.graphics.Color.alpha(bitmap.getPixel(16, 12)) > 0
            bitmap.recycle()
            visible
        }.getOrDefault(false)
        vectorSupport = ok
        return ok
    }

    fun supported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        support?.let { return it }
        val result = runCatching {
            if (RemoteViews.DrawInstructions.getSupportedVersion() < 6) return@runCatching false
            val probe = ClockDrawDocument(32, 32)
            probe.circle(16f, 16f, 10f, 0xffe95420.toInt())
            val view = probe.build().apply(context, FrameLayout(context))
            val spec = View.MeasureSpec.makeMeasureSpec(32, View.MeasureSpec.EXACTLY)
            view.measure(spec, spec); view.layout(0, 0, 32, 32)
            val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val valid = view.javaClass.name.contains("remotecompose") &&
                android.graphics.Color.alpha(image.getPixel(16, 16)) > 0
            image.recycle()
            valid
        }.getOrDefault(false)
        support = result
        return result
    }

    fun motionScale(context: Context): Float = Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f).let { if (it.isFinite()) it.coerceAtLeast(0f) else 1f }

    fun create(context: Context, prefs: ClockPreferences, width: Float, height: Float,
        id: Int, clickable: Boolean, artPixels: Int = 512,
        today: LocalDate = LocalDate.now()): RemoteViews {
        val w = width.roundToInt().coerceAtLeast(1)
        val h = height.roundToInt().coerceAtLeast(1)
        val doc = ClockDrawDocument(w, h)
        val dateFont = min(16f, width * .048f).coerceAtLeast(1f)
        val dateHeight = if (prefs.dateVisible) min(height * .24f, dateFont * 2.8f + 4f) else 0f
        val diameter = min((width - 4).coerceAtLeast(1f), (height - dateHeight - 4).coerceAtLeast(1f)) *
            when (prefs.size) { ClockSize.COMPACT -> .78f; ClockSize.NORMAL -> .90f; ClockSize.LARGE -> 1f }
        val left = (w - diameter) * .5f
        val top = (h - diameter - dateHeight - if (prefs.dateVisible) 3f else 0f) * .5f
        val cx = w * .5f
        val cy = top + diameter * .5f
        val date = today
        val chrome = CosmicClockPalette.chrome(context)
        val palette = if(prefs.adaptiveColors)CosmicColors.palette(prefs).toList() else CosmicClockPalette.colors(prefs.petalPalette, chrome).map { it.toArgb() }
        val orbits = CosmicOrbitGeometry.specs(date.dayOfMonth, date.lengthOfMonth())
        val dots = OriginalWelcomeGeometry.dotMarkers(date.dayOfMonth, date.lengthOfMonth())
        val scale = motionScale(context)
        val moving = scale > 0f
        // V6 schedules frames from time ids 1/2/3, not ANIMATION_SECONDS (30).
        // Keep that listener in the artwork expression even when no clock is drawn.
        val time = if (moving && (prefs.animatesArtwork || prefs.chargingNow && prefs.chargingBlackFire)) doc.expression(ClockDrawDocument.ANIMATION_SECONDS,
            CONTINUOUS_SECONDS, 0f, MUL, ADD, scale, DIV, prefs.motionSpeed, MUL) else 0f
        val orbitMultiplier = when (prefs.orbitalIntensity) {
            OrbitalIntensity.OFF -> 0f; OrbitalIntensity.SUBTLE -> 1f; OrbitalIntensity.COSMIC -> 1.65f
        }
        val radialAmplitude = when (prefs.orbitalIntensity) {
            OrbitalIntensity.OFF -> 0f; OrbitalIntensity.SUBTLE -> .005f; OrbitalIntensity.COSMIC -> .012f
        }
        if(prefs.geometryTheme.isTeam) {
            if(prefs.geometryTheme==ClockTheme.G2_ESPORTS && prefs.g2MontageEnabled && G2Montage.available(context)) {
                G2Montage.native(context,doc,time,cx,cy,diameter)
            } else {
                TeamThemeRenderer.native(doc,prefs,time,cx,cy,diameter,artPixels,pathsSupported(context),today)
                CosmicStarField.drawNative(doc,prefs,time,cx,cy,diameter)
            }
        } else if (!(prefs.wallpaperClockOnly && prefs.geometryTheme == ClockTheme.BLACK_HOLE)) {
            doc.image(OriginalWidgetArt.render(prefs, artPixels, date, chrome, petals = false,
                markers = false, core = false), left.roundToInt(), top.roundToInt(), diameter.roundToInt())
            val simpleCosmic = prefs.geometryTheme != ClockTheme.UBUNTU_ORIGINAL && !prefs.geometryTheme.isScene &&
                !pathsSupported(context)
            doc.save()
            if (!simpleCosmic && !prefs.geometryTheme.isScene) doc.clipOutsideCircle(cx, cy,
                diameter * prefs.coreRadius())
            if (prefs.geometryTheme.isScene) {
                CosmicSceneRenderer.drawNative(doc, prefs, time, cx, cy, diameter, artPixels, false, (prefs.geometryTheme == ClockTheme.STELLAR_NURSERY || prefs.geometryTheme.isTeam) && pathsSupported(context),today=today)
            } else if (simpleCosmic) {
                ClockThemeRenderer.drawNativePetals(doc, prefs.geometryTheme,
                    time, cx, cy, diameter, prefs.orbitalIntensity,
                    prefs.petalCount(date), prefs.cosmicColorMix, artPixels,if(prefs.adaptiveColors)CosmicColors.palette(prefs) else null)
            } else if (prefs.geometryTheme != ClockTheme.UBUNTU_ORIGINAL) {
                ClockThemeRenderer.drawNative(doc, prefs.geometryTheme, time, cx, cy, diameter,
                    prefs.orbitalIntensity, prefs.petalCount(date), prefs.cosmicColorMix,if(prefs.adaptiveColors)CosmicColors.palette(prefs) else null)
            } else orbits.forEachIndexed { i, orbit ->
                val angle = doc.expression(time, orbit.speed * orbitMultiplier, MUL, orbit.angle, ADD)
                val distance = doc.expression(time, .13f, MUL, orbit.radialPhase, ADD, SIN,
                    radialAmplitude, MUL, orbit.distance, ADD)
                val x = doc.expression(angle, COS, distance, MUL, diameter, MUL, cx, ADD)
                val y = doc.expression(angle, SIN, distance, MUL, diameter, MUL, cy, ADD)
                val alpha = if (prefs.orbitalIntensity == OrbitalIntensity.OFF) orbit.opacity else
                    doc.expression(time, .19f, MUL, orbit.radialPhase, ADD, SIN,
                        .06f, MUL, .94f, ADD, orbit.opacity, MUL)
                val color = palette[if (!prefs.adaptiveColors && prefs.petalPalette == PetalPalette.ORIGINAL && orbit.previousMonth)
                    5 else i % palette.size]
                doc.circle(x, y, diameter * orbit.radius, color, alpha)
            }
            doc.restore()
            doc.image(OriginalWidgetArt.render(prefs, artPixels, date, chrome, petals = false,
                markers = false, ambient = false), left.roundToInt(), top.roundToInt(), diameter.roundToInt())

            CosmicSceneRenderer.drawNative(doc, prefs, time, cx, cy, diameter, artPixels, true,today=today)
            CosmicStarField.drawNative(doc, prefs, time, cx, cy, diameter)

            val head = doc.expression(CONTINUOUS_SECONDS, 60f, MOD, 60f, DIV, dots.size.toFloat(), MUL)
            val clockwise = doc.expression(1f, MINUTE, 2f, MOD, SUB)
            val trail = CosmicMinutePolicy.trailDots(prefs.minutePulseDuration)
            dots.forEachIndexed { i, marker ->
                val position = doc.expression(Math.floorMod(-i, dots.size).toFloat(), i.toFloat(), clockwise, IFELSE)
                val t = doc.expression(1f, head, position, SUB, dots.size.toFloat(), ADD,
                    dots.size.toFloat(), MOD, trail, DIV, SUB, 0f, MAX)
                val a = doc.expression(1f, position, head, SUB, dots.size.toFloat(), ADD,
                    dots.size.toFloat(), MOD, .9f, DIV, SUB, 0f, MAX)
                val energy = if (moving && prefs.ringAnimation) doc.expression(
                    t, t, MUL, 3f, 2f, t, MUL, SUB, MUL,
                    a, a, MUL, .72f, MUL, MAX,
                    CosmicClockPalette.glow(prefs.luminousIntensity), MUL, 1f, MIN) else 0f
                val idle = when (marker.state) { 0 -> .18f; 1 -> .27f; else -> .14f }
                val radius = diameter * if (marker.state == 1) .011f else .0058f
                val x = left + diameter * marker.x
                val y = top + diameter * marker.y
                val haloRadius = doc.expression(1.8f, energy, ADD, radius, MUL)
                val haloAlpha = doc.expression(energy, .24f, MUL)
                doc.circle(x, y, haloRadius, palette[i % palette.size], haloAlpha)
                val color = when (marker.state) { 0 -> 0xffefc0d6.toInt(); 1 -> 0xfff8f5fa.toInt(); else -> 0xffc9a7bc.toInt() }
                val bright = doc.expression(energy, .08f, SUB, SIGN, 0f, MAX)
                val dotColor = doc.color(color, 0xfffff9fd.toInt(), bright)
                val dotRadius = doc.expression(1f, .62f, energy, MUL, ADD, radius, MUL)
                val dotAlpha = doc.expression(1f - idle, energy, MUL, idle, ADD)
                doc.circleColorId(x, y, dotRadius, dotColor, dotAlpha)
            }

        }

        val locale = context.resources.configuration.locales[0]
        val use24 = prefs.uses24Hours(DateFormat.is24HourFormat(context))
        if (prefs.showClock && prefs.face == ClockFace.ANALOG) {
            ClockFaceRenderer.analog(doc, cx, cy+if(prefs.geometryTheme.isTeam)diameter*TeamThemeRenderer.CLOCK_Y else 0f,
                diameter*if(prefs.geometryTheme.isTeam)TeamThemeRenderer.DIAL_SIZE else 1f, prefs.showSeconds)
        } else if(prefs.showClock) {
            val hour = if (use24) HOUR else doc.expression(HOUR, 11f, ADD, 12f, MOD, 1f, ADD)
            val colon = doc.text(":")
            var clock = doc.merge(doc.number(hour, 2, use24), colon,
                doc.number(doc.expression(MINUTE, 60f, MOD), 2, true))
            if (prefs.showSeconds) clock = doc.merge(clock, colon,
                doc.number(doc.expression(CONTINUOUS_SECONDS, 60f, MOD, FLOOR), 2, true))
            if (!use24) clock = doc.merge(clock, doc.text(" "), doc.lookup(
                java.text.DateFormatSymbols(locale).amPmStrings.toList(), doc.expression(HOUR, 12f, DIV, FLOOR)))
            doc.textPaint(100f)
            val measured = doc.textWidth(clock)
            val font = doc.expression(diameter * (if(prefs.geometryTheme.isTeam)TeamThemeRenderer.TIME_WIDTH else .56f) * 100f, measured, DIV, 44f, MIN)
            doc.textPaint(font, 0xa9000000.toInt())
            val clockOffset=if(prefs.geometryTheme.isTeam)TeamThemeRenderer.CLOCK_Y else .013f
            doc.drawText(clock, cx, cy + diameter * clockOffset + 2f)
            doc.textPaint(font)
            doc.drawText(clock, cx, cy + diameter * clockOffset)
        }
        if (prefs.dateVisible) {
            val dateText = dateText(doc, locale)
            doc.textPaint(100f, weight = 400)
            val dateWidth = doc.textWidth(dateText)
            val fittedDate = doc.expression((w - 8f) * 100f, dateWidth, DIV, dateFont, MIN)
            doc.textPaint(fittedDate, weight = 400)
            doc.drawText(dateText, cx, top + diameter + dateHeight * .5f)
        }
        if (clickable) doc.click(R.id.widget_root)
        return doc.build().also { views ->
            if (clickable) views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id,
                Intent(context, ClockWidgetActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                // Explicit Activity target; mutable only so the system can supply sourceBounds for lens alignment.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
        }
    }

    /** Gregorian civil date from the host's integer epoch time, not its buggy V6 day-of-month field. */
    private fun dateText(doc: ClockDrawDocument, locale: Locale): Int {
        fun id(value: Float) = value.toRawBits() and 0x3fffff
        val localSeconds = doc.integerExpression(0b111, 32, 10, 0x10001)
        val negative = doc.expression(0f, localSeconds, SUB, SIGN, 0f, MAX)
        val correction = doc.integerExpression(0b1100, 0, 86399, id(negative), 0x10016)
        val unixDays = doc.integerExpression(0b10111, id(localSeconds), id(correction), 0x10002, 86400, 0x10004)
        val z = doc.expression(unixDays, 719468f, ADD)
        val era = doc.expression(z, 146097f, DIV, FLOOR)
        val doe = doc.expression(z, era, 146097f, MUL, SUB)
        val yoe = doc.expression(doe, doe, 1460f, DIV, FLOOR, SUB,
            doe, 36524f, DIV, FLOOR, ADD, doe, 146096f, DIV, FLOOR, SUB, 365f, DIV, FLOOR)
        val y = doc.expression(yoe, era, 400f, MUL, ADD)
        val doy = doc.expression(doe, 365f, yoe, MUL, yoe, 4f, DIV, FLOOR, ADD,
            yoe, 100f, DIV, FLOOR, SUB, SUB)
        val mp = doc.expression(doy, 5f, MUL, 2f, ADD, 153f, DIV, FLOOR)
        val day = doc.expression(doy, mp, 153f, MUL, 2f, ADD, 5f, DIV, FLOOR, SUB, 1f, ADD)
        val month = doc.expression(mp, -9f, 3f, 10f, mp, SUB, IFELSE, ADD)
        // The V6 TextFromFloat year value can be clipped to two digits ("26")
        // on the Motorola host. The provider refreshes the document daily, so
        // render the full civil year as a localized static text segment instead.
        val civilYear = LocalDate.now().year.toString()
        val symbols = java.text.DateFormatSymbols(locale)
        val pattern = (java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, locale)
            as java.text.SimpleDateFormat).toPattern()
        val parts = mutableListOf<Int>()
        var i = 0
        var quoted = false
        val literal = StringBuilder()
        fun flush() { if (literal.isNotEmpty()) { parts += doc.text(literal.toString()); literal.clear() } }
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                if (i + 1 < pattern.length && pattern[i + 1] == '\'') { literal.append('\''); i += 2; continue }
                quoted = !quoted; i++; continue
            }
            if (!quoted && c in "EdMy") {
                flush()
                var end = i + 1
                while (end < pattern.length && pattern[end] == c) end++
                val count = end - i
                parts += when (c) {
                    'E' -> doc.lookup((1..7).map { d ->
                        val name = if (count >= 4) symbols.weekdays[if (d == 7) 1 else d + 1]
                            else symbols.shortWeekdays[if (d == 7) 1 else d + 1]
                        if (parts.isEmpty()) name.replaceFirstChar { it.titlecase(locale) } else name
                    }, doc.expression(ClockDrawDocument.variable(11), 1f, SUB))
                    'd' -> doc.number(day, 2, count >= 2)
                    'M' -> if (count >= 3) doc.lookup((if (count >= 4) symbols.months else symbols.shortMonths)
                        .take(12), doc.expression(month, 1f, SUB)) else doc.number(month, 2, count >= 2)
                    else -> doc.text(civilYear)
                }
                i = end
            } else { literal.append(c); i++ }
        }
        flush()
        return doc.merge(*parts.toIntArray())
    }
}

