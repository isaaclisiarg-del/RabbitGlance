package com.isaaclisi.rabbitglance

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews

/** Local framework probes. They cannot certify the policy of a different launcher process. */
internal object Diagnostics {
 data class Probe(val name: String,val passed: Boolean,val error: String?)
 fun capture(c: Context,doc: ClockDrawDocument): Bitmap {
  val v=doc.build().apply(c,FrameLayout(c))
  val spec=View.MeasureSpec.makeMeasureSpec(64,View.MeasureSpec.EXACTLY)
  v.measure(spec,spec);v.layout(0,0,64,64)
  return Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).also { val canvas=Canvas(it);v.draw(canvas);it.eraseColor(0);v.draw(canvas) }
 }
 fun probes(c: Context): List<Probe> {
  fun test(name: String,make: (ClockDrawDocument)->Unit,verify: (Bitmap)->Boolean): Probe {
   return try {
    val d=ClockDrawDocument(64,64);make(d);val b=capture(c,d);val valid=verify(b);b.recycle()
    Probe(name,valid,if(valid)null else "La muestra de píxeles no coincide")
   } catch(e: Exception) { Probe(name,false,"${e.javaClass.simpleName}: ${e.message}") }
  }
  val white=Color.WHITE
  return listOf(
   test("Círculos / pintura",{it.circle(32f,32f,12f,white)},{Color.alpha(it.getPixel(32,32))>240}),
   test("Líneas y alfa",{it.line(8f,32f,56f,32f,4f,white,.5f)},{Color.alpha(it.getPixel(32,32)) in 100..155}),
   test("Expresión float / NaN",{d->val x=d.expression(16f,16f,ClockDrawDocument.ADD);d.circle(x,32f,8f,white)},{Color.alpha(it.getPixel(32,32))>240}),
   test("Rotación + escala / save restore",{d->d.save();d.rotate(180f,32f,32f);d.scale(1f,.5f,32f,32f);d.circle(12f,32f,5f,white);d.restore()},{Color.alpha(it.getPixel(52,32))>200 && Color.alpha(it.getPixel(12,32))==0}),
   test("Textura PNG",{d->val b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);b.eraseColor(white);d.image(b,20,20,24);b.recycle()},{Color.alpha(it.getPixel(30,30))>240}),
   test("ColorExpression",{d->val color=d.color(Color.RED,Color.BLUE,.5f);d.circleColorId(32f,32f,10f,color,1f)},{val color=it.getPixel(32,32);Color.red(color)>60 && Color.blue(color)>60}),
   test("Texto / medida / número / merge",{d->d.textPaint(24f);val t=d.merge(d.number(12f,2,true),d.text(":"),d.number(34f,2,true));d.textWidth(t);d.drawText(t,32f,32f)},{b->(15..49).any {y->(0..63).any {x->Color.alpha(b.getPixel(x,y))>60}}})
  )
 }
 fun report(c: Context,probes: List<Probe>,payload: Int?,extra: String): String {
  val launcher=runCatching { c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)?.activityInfo?.packageName }.getOrNull()
  val version=runCatching { RemoteViews.DrawInstructions.getSupportedVersion().toString() }.getOrElse { "ERROR: ${it.message}" }
  val prefs=BlackHoleWidget.diagnostics(c)
  return buildString {
   appendLine("RabbitGlance 0.1.0 · diagnóstico")
   appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
   appendLine("Build: ${Build.DISPLAY}")
   appendLine("Launcher: ${launcher ?: "no resuelto"}")
   appendLine("DrawInstructions soportado: $version · documento heredado V6")
   appendLine("Escala de animación: ${BlackHoleWidget.motionScale(c)}")
   appendLine("Documento: ${payload ?: "pendiente"} bytes · 4 texturas 512² ≈ 4 MiB decodificadas")
   appendLine("Operaciones comprobadas LOCALMENTE:")
   probes.forEach { appendLine("${if(it.passed)"OK" else "FALLO"} · ${it.name}${it.error?.let {e->" · $e"} ?: ""}") }
   appendLine(extra)
   BlackHoleWidget.ids(c).forEach { id->appendLine("Widget $id: ${prefs.getString("publication_$id","sin publicación registrada")}");prefs.getString("error_$id",null)?.let {appendLine("ERROR $it")};prefs.getString("observation_$id",null)?.let {appendLine("Observación manual: $it")} }
   appendLine("La vista previa usa RemoteViews.apply y el reproductor del framework. No certifica la reproducción de Moto App Launcher.")
   appendLine("El host evalúa flujos, atenuación Doppler, estrellas opcionales, puntos y hora. La app prepara materiales sólo por configuración, redimensionado y eventos.")
   appendLine("Sin servicios, overlays, wallpapers, permisos especiales ni generación de fotogramas en segundo plano.")
   appendLine("Lente del fondo: fuera del alcance del widget. Se preserva el arco óptico original; no se captura la pantalla ajena.")
   appendLine("Shaders AGSL: no se envían. AOSP Android 16 los rechaza por defecto; política del host Motorola pendiente.")
   appendLine("FPS, batería y pausa al ocultar/bloquear: pendientes de medir en Motorola.")
  }
 }
}
