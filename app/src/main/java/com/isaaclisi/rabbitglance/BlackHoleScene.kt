package com.isaaclisi.rabbitglance

import android.content.Context
import android.graphics.*
import android.widget.RemoteViews
import java.time.LocalDate
import java.util.Locale
import kotlin.math.*
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.ADD
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.SUB
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.MUL
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.DIV
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.MOD
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.MIN
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.MAX
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.FLOOR
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.SIGN
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.SIN
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.COS
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.IFELSE
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.CONTINUOUS_SECONDS
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.MINUTE
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.HOUR
import com.isaaclisi.rabbitglance.ClockDrawDocument.Companion.ANIMATION_SECONDS

/** One scene document shared by preview and AppWidget. No frame production in the app. */
internal object BlackHoleScene {
 data class Document(val bytes: ByteArray, val views: RemoteViews, val materialBytes: Int)
 fun create(context: Context,prefs: ClockPreferences,w: Int,h: Int, motionScale: Float = 1f,
  clickable: Boolean = true,today: LocalDate = LocalDate.now()): Document {
  val doc=ClockDrawDocument(w,h)
  val width=w.toFloat();val height=h.toFloat()
  val dateFont=min(16f,width*.048f).coerceAtLeast(1f)
  val dateHeight=if(prefs.dateVisible && prefs.showClock)min(height*.24f,dateFont*2.8f+4f)else 0f
  val diameter=min((width-4).coerceAtLeast(1f),(height-dateHeight-4).coerceAtLeast(1f))*prefs.size.scale
  val left=(w-diameter)*.5f;val top=(h-diameter-dateHeight-if(dateHeight>0f)3f else 0f)*.5f
  val cx=w*.5f;val cy=top+diameter*.5f
  val dots=OriginalWelcomeGeometry.dotMarkers(today.dayOfMonth,today.lengthOfMonth())
  val palette=listOf(0xffc0ace8.toInt(),0xffa9d7eb.toInt(),0xffeab3d0.toInt(),0xffefae98.toInt(),0xff8f71b4.toInt())
  val moving=motionScale>0f
  // Continuous seconds registers a frame listener in the platform even though animation time drives phase.
  val time=if(moving && (prefs.orbitalIntensity!=OrbitalIntensity.OFF || (prefs.starsEnabled && prefs.starTwinkle)))doc.expression(ANIMATION_SECONDS,CONTINUOUS_SECONDS,0f,MUL,ADD,
   motionScale,DIV,prefs.motionSpeed,MUL)else 0f
  val pixels=512
  fun image(bitmap: Bitmap) { doc.image(bitmap,left.roundToInt(),top.roundToInt(),diameter.roundToInt());bitmap.recycle() }
  image(material(prefs,pixels,false))
  image(BlackHoleArt.layer(prefs,pixels,false))
  BlackHoleArt.nativeFlow(doc,prefs,time,cx,cy,diameter,false)
  image(material(prefs,pixels,true))
  image(BlackHoleArt.layer(prefs,pixels,true))
  BlackHoleArt.nativeFlow(doc,prefs,time,cx,cy,diameter,true)
  CosmicStarField.drawNative(doc,prefs,time,cx,cy,diameter)
            if(moving && prefs.ringAnimation) {
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
                    .82f, MUL, 1f, MIN) else 0f
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

            } else dots.forEach { marker ->
                val radius=diameter*if(marker.state==1).011f else .0058f
                val idle=when(marker.state){0->.18f;1->.27f;else->.14f}
                val color=when(marker.state){0->0xffefc0d6.toInt();1->0xfff8f5fa.toInt();else->0xffc9a7bc.toInt()}
                doc.circle(left+diameter*marker.x,top+diameter*marker.y,radius,color,idle)
            }

  if(prefs.showClock) {
   if(prefs.face==ClockFace.ANALOG) {
    ClockFaceRenderer.analog(doc,cx,cy,diameter,prefs.showSeconds)
   } else {
   val use24=android.text.format.DateFormat.is24HourFormat(context)
   val hour=if(use24)HOUR else doc.expression(HOUR,11f,ADD,12f,MOD,1f,ADD)
   val colon=doc.text(":")
   var clock=doc.merge(doc.number(hour,2,use24),colon,doc.number(doc.expression(MINUTE,60f,MOD),2,true))
   if(prefs.showSeconds)clock=doc.merge(clock,colon,doc.number(doc.expression(CONTINUOUS_SECONDS,60f,MOD,FLOOR),2,true))
   if(!use24)clock=doc.merge(clock,doc.text(" "),doc.lookup(java.text.DateFormatSymbols(context.resources.configuration.locales[0]).amPmStrings.toList(),doc.expression(HOUR,12f,DIV,FLOOR)))
   doc.textPaint(100f);val measured=doc.textWidth(clock)
   val font=doc.expression(diameter*.56f*100f,measured,DIV,44f,MIN)
   doc.textPaint(font,0xa9000000.toInt());doc.drawText(clock,cx,cy+diameter*.013f+2f)
   doc.textPaint(font);doc.drawText(clock,cx,cy+diameter*.013f)
   }
   if(dateHeight>0f) {
    val date=dateText(doc,context.resources.configuration.locales[0]);doc.textPaint(100f,weight=400)
    val fitted=doc.expression((w-8f)*100f,doc.textWidth(date),DIV,dateFont,MIN)
    doc.textPaint(fitted,weight=400);doc.drawText(date,cx,top+diameter+dateHeight*.5f)
   }
  }
  if(clickable)doc.click(R.id.widget_root)
  return Document(doc.byteArray(),doc.build(),4*pixels*pixels*4)
 }
 /** Exact ambient and central gradient/rim from OriginalWidgetArt, generated only at document construction. */
 private fun material(p: ClockPreferences,size: Int,core: Boolean): Bitmap {
  val b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);val c=Canvas(b);val u=size.toFloat();val center=u*.5f
  val r=u*p.coreRadius();val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  if(core) {
   paint.shader=RadialGradient(center+r*.16f,center-r*.21f,r*1.6f,
    intArrayOf(0xff030306.toInt(),0xff020204.toInt(),0xff000001.toInt()),floatArrayOf(0f,.67f,1f),Shader.TileMode.CLAMP)
   c.drawCircle(center,center,r,paint);paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=u*.0019f
   paint.color=0x33beb0c8;c.drawCircle(center,center,r,paint)
  } else {
   paint.shader=RadialGradient(center,center,u*.52f,intArrayOf(0x0cff8a21,Color.TRANSPARENT),floatArrayOf(0f,1f),Shader.TileMode.CLAMP)
   c.drawCircle(center,center,u*.52f,paint)
  }
  return b
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
        // in the historical source. RabbitGlance has not yet been verified on Motorola.
        // The provider refreshes the document daily, so
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
