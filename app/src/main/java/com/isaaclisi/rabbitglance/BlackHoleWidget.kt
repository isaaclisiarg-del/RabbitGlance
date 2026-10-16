package com.isaaclisi.rabbitglance

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.RemoteViews
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Event-only document publication. No service, frame ticker, wake lock or exact alarm. */
class BlackHoleWidget : AppWidgetProvider() {
 override fun onReceive(context: Context,intent: Intent) {
  if(intent.action==PINNED) {
   val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
   if(id>0)SettingsStore.save(context,id,SettingsStore.read(context))
  }
  super.onReceive(context,intent)
  if(intent.action in setOf(Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_DATE_CHANGED,
    Intent.ACTION_LOCALE_CHANGED,PINNED)) publish(context,ids(context))
 }
 override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) { publish(context,ids) }
 override fun onAppWidgetOptionsChanged(context: Context,manager: AppWidgetManager,id: Int,options: Bundle) { publish(context,intArrayOf(id)) }
 override fun onDeleted(context: Context,ids: IntArray) { ids.forEach { SettingsStore.delete(context,it) } }
 override fun onRestored(context: Context,oldIds: IntArray,newIds: IntArray) {
  oldIds.zip(newIds.toTypedArray()).forEach { (old,new)->SettingsStore.save(context,new,SettingsStore.read(context,old));SettingsStore.delete(context,old) }
  publish(context,newIds)
 }
 private fun publish(c: Context,ids: IntArray) {
  if(ids.isEmpty())return
  val pending=goAsync();val context=c.applicationContext
  worker.execute { try { ids.forEach { update(context,it) } } finally { pending.finish() } }
 }
 companion object {
  const val PINNED="com.isaaclisi.rabbitglance.PINNED"
  internal val worker=Executors.newSingleThreadExecutor()
  fun ids(c: Context)=AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,BlackHoleWidget::class.java))
  fun refresh(c: Context,id: Int,onDone: (() -> Unit)? = null) { worker.execute { try { update(c.applicationContext,id) } finally { onDone?.invoke() } } }
  fun motionScale(c: Context)=Settings.Global.getFloat(c.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f).let { if(it.isFinite())it.coerceAtLeast(0f)else 1f }
  private fun update(c: Context,id: Int) {
   val manager=AppWidgetManager.getInstance(c);val options=manager.getAppWidgetOptions(id)
   val portrait=c.resources.configuration.orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE
   val w=options.getInt(if(portrait)AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,280).coerceIn(120,640)
   val h=options.getInt(if(portrait)AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,300).coerceIn(120,640)
   val intent=PendingIntent.getActivity(c,id,Intent(c,MainActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   try {
    val document=BlackHoleScene.create(c,SettingsStore.read(c,id),w,h,motionScale(c))
    document.views.setOnClickPendingIntent(R.id.widget_root,intent)
    manager.updateAppWidget(id,document.views)
    diagnostics(c).edit().putString("publication_$id","Aceptado por AppWidgetManager · ${document.bytes.size} bytes · ${w}×${h}. Reproducción en launcher pendiente.").remove("error_$id").apply()
   } catch(e: Exception) {
    val error="${e.javaClass.simpleName}: ${e.message}"
    diagnostics(c).edit().putString("error_$id",error).apply()
    val failure=RemoteViews(c.packageName,R.layout.widget_error)
    failure.setTextViewText(R.id.widget_error,"Black Hole\nRemoteCompose no aceptado\nToca para ver diagnóstico")
    failure.setOnClickPendingIntent(R.id.widget_root,intent)
    manager.updateAppWidget(id,failure)
   }
  }
  fun diagnostics(c: Context)=c.getSharedPreferences("diagnostics",0)
 }
}
