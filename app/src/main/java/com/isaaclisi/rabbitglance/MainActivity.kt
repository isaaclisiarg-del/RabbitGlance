package com.isaaclisi.rabbitglance

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import kotlin.math.roundToInt

class MainActivity : Activity() {
 private var widgetId=0
 private var configuring=false
 private var prefs=ClockPreferences()
 private lateinit var preview: FrameLayout
 private lateinit var state: TextView
 private var nativeView: View?=null
 private var frozen: Bitmap?=null
 private var payload: Int?=null
 private var playing=true
 private var active=false
 private var generation=0
 private var format=0
 private val handler=Handler(Looper.getMainLooper())
 private var pending: Runnable?=null
 private val ink=Color.rgb(238,239,245)
 private val muted=Color.rgb(161,167,186)
 private val accent=Color.rgb(255,179,94)
 private fun dp(n: Int)=(n*resources.displayMetrics.density).roundToInt()
 override fun onCreate(saved: Bundle?) {
  super.onCreate(saved)
  widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,0).coerceAtLeast(0)
  configuring=intent.action==AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
  setResult(RESULT_CANCELED)
  prefs=SettingsStore.read(this,widgetId)
  buildUi()
 }
 override fun onResume() { super.onResume();active=true;requestPreview() }
 override fun onPause() { active=false;generation++;pending?.let(handler::removeCallbacks);clearPreview();super.onPause() }
 override fun onDestroy() { handler.removeCallbacksAndMessages(null);clearPreview();super.onDestroy() }
 private fun text(value: String,size: Float=16f,color: Int=ink)=TextView(this).apply { text=value;textSize=size;setTextColor(color);setPadding(0,dp(7),0,dp(7)) }
 private fun card()=GradientDrawable().apply { setColor(Color.rgb(17,21,30));cornerRadius=dp(22).toFloat() }
 private fun button(label: String,action: ()->Unit)=Button(this).apply { text=label;isAllCaps=false;textSize=16f;setOnClickListener { action() } }
 private fun buildUi() {
  val scroll=ScrollView(this).apply { isFillViewport=true;clipToPadding=false }
  val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(28)) }
  scroll.addView(body);setContentView(scroll)
  scroll.setOnApplyWindowInsetsListener { v,insets->val bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets }
  body.addView(text("RABBITGLANCE",12f,accent).apply { letterSpacing=.2f })
  body.addView(text("Black Hole",32f))
  body.addView(text("Disco turbulento. Luz curvada. Tiempo en órbita.",16f,muted))
  if(!configuring) {
   val ids=BlackHoleWidget.ids(this)
   if(ids.isNotEmpty()) {
    val values=listOf("Diseño para un widget nuevo")+ids.map { "Widget $it" }
    val selector=Spinner(this);selector.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,values)
    selector.setSelection(ids.indexOf(widgetId)+1)
    selector.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
     override fun onNothingSelected(p: AdapterView<*>?) {}
     override fun onItemSelected(p: AdapterView<*>?,v: View?,position: Int,id: Long) {
      val chosen=if(position==0)0 else ids[position-1]
      if(chosen!=widgetId) { widgetId=chosen;prefs=SettingsStore.read(this@MainActivity,widgetId);generation++;clearPreview();buildUi();requestPreview() }
     }
    };body.addView(selector)
   }
  }
  preview=FrameLayout(this).apply { background=card();clipToOutline=true;contentDescription="Vista previa del widget Black Hole" }
  body.addView(preview,LinearLayout.LayoutParams(-1,dp(if(format==1)220 else if(format==2)390 else 330)).apply { topMargin=dp(12) })
  state=text("Preparando la vista previa…",14f,muted);body.addView(state)
  val row=LinearLayout(this)
  val play=button(if(playing)"Pausar" else "Reproducir") {}
  play.setOnClickListener {
   playing=!playing;play.text=if(playing)"Pausar" else "Reproducir"
   if(playing)requestPreview()else freezePreview()
  }
  row.addView(play,LinearLayout.LayoutParams(0,-2,1f))
  row.addView(button("Reiniciar") { playing=true;play.text="Pausar";requestPreview() },LinearLayout.LayoutParams(0,-2,1f));body.addView(row)
  body.addView(text("La reproducción en Moto App Launcher se comprueba después de añadir el widget.",14f,muted))
  body.addView(text("Diseño",23f))
  select(body,"Tamaño de la figura",listOf("Compacto","Normal","Grande"),prefs.size.ordinal) { prefs=prefs.copy(size=SceneSize.entries[it]);requestPreview() }
  select(body,"Intensidad de movimiento",listOf("Quieto","Sutil · original","Cósmico"),prefs.orbitalIntensity.ordinal) { prefs=prefs.copy(orbitalIntensity=OrbitalIntensity.entries[it]);requestPreview() }
  slider(body,"Velocidad",prefs.motionSpeed,.2f,2f,"×") { prefs=prefs.copy(motionSpeed=it);requestPreview() }
  slider(body,"Resplandor del disco",prefs.accretionGlow,.3f,1.6f,"×") { prefs=prefs.copy(accretionGlow=it);requestPreview() }
  select(body,"Esfera del reloj",listOf("Digital · formato del sistema","Analógica"),prefs.face.ordinal) { prefs=prefs.copy(face=ClockFace.entries[it]);requestPreview() }
  toggle(body,"Reloj y fecha",prefs.showClock) { prefs=prefs.copy(showClock=it);requestPreview() }
  toggle(body,"Segundos",prefs.showSeconds) { prefs=prefs.copy(showSeconds=it);requestPreview() }
  toggle(body,"Estrellas titilantes",prefs.starsEnabled) { prefs=prefs.copy(starsEnabled=it);requestPreview() }
  body.addView(button(if(widgetId>0)"Aplicar a este widget" else "Guardar diseño") { save() })
  if(!configuring)body.addView(button("Añadir a la pantalla de inicio") { pin() })
  body.addView(button("Diagnóstico") { showDiagnostics() })
  body.addView(text("0.1.0 · Primera prueba\nMantén pulsado el escritorio → Widgets → RabbitGlance → Black Hole. Puedes redimensionarlo desde el escritorio y tocarlo para configurarlo.",14f,muted))
  body.addView(text("La pausa congela sólo esta vista previa. «Quieto» detiene las estelas; el reloj y los indicadores conservan su tiempo. Las estrellas son opcionales y estaban apagadas en el original.",13f,muted))
 }
 private fun select(body: LinearLayout,label: String,labels: List<String>,initial: Int,change: (Int)->Unit) {
  body.addView(text(label,16f,muted));val s=Spinner(this)
  s.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels);s.setSelection(initial)
  var first=true;s.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(p: AdapterView<*>?) {}
   override fun onItemSelected(p: AdapterView<*>?,v: View?,position: Int,id: Long) { if(first){first=false;return};change(position) }
  };body.addView(s)
 }
 private fun slider(body: LinearLayout,label: String,value: Float,min: Float,max: Float,unit: String,change: (Float)->Unit) {
  val title=text("$label · ${String.format(java.util.Locale.ROOT,"%.1f",value)}$unit",16f,muted);body.addView(title)
  body.addView(SeekBar(this).apply {
   this.max=100;progress=((value-min)/(max-min)*100).roundToInt()
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(s: SeekBar?) {}
    override fun onStopTrackingTouch(s: SeekBar?) {}
    override fun onProgressChanged(s: SeekBar?,p: Int,user: Boolean) { if(user){val f=min+(max-min)*p/100f;title.text="$label · ${String.format(java.util.Locale.ROOT,"%.1f",f)}$unit";change(f)} }
   })
  })
 }
 private fun toggle(body: LinearLayout,label: String,value: Boolean,change: (Boolean)->Unit) {
  body.addView(Switch(this).apply { text=label;textSize=16f;setTextColor(ink);setPadding(0,dp(10),0,dp(10));isChecked=value;setOnCheckedChangeListener { _,checked->change(checked) } })
 }
 private fun requestPreview() {
  if(!active || !::preview.isInitialized)return
  pending?.let(handler::removeCallbacks)
  val ticket=++generation
  pending=Runnable {
   state.text="Preparando materiales…"
   val current=prefs;val dims=when(format){1->360 to 220;2->220 to 420;else->320 to 360}
   BlackHoleWidget.worker.execute {
    val result=runCatching { BlackHoleScene.create(applicationContext,current,dims.first,dims.second,BlackHoleWidget.motionScale(this),false) }
    runOnUiThread {
     if(!active || ticket!=generation)return@runOnUiThread
     clearPreview()
     result.onSuccess { doc->
      payload=doc.bytes.size
      runCatching { doc.views.apply(this,preview) }.onSuccess { view->
       nativeView=view;preview.addView(view,FrameLayout.LayoutParams(-1,-1))
       state.text=if(playing)"Reproductor Android · vista previa en reproducción" else "Vista previa pausada"
       if(!playing)preview.post { if(ticket==generation && active)freezePreview() }
      }.onFailure { e->state.text="El reproductor local rechazó la escena: ${e.javaClass.simpleName}: ${e.message}" }
     }.onFailure { e->state.text="Error al construir: ${e.javaClass.simpleName}: ${e.message}" }
    }
   }
  };handler.postDelayed(pending!!,180)
 }
 private fun clearPreview() {
  if(::preview.isInitialized)preview.removeAllViews();nativeView=null
  frozen?.recycle();frozen=null
 }
 private fun freezePreview() {
  val view=nativeView ?: return
  if(view.width<=0 || view.height<=0)return
  val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(b))
  clearPreview();frozen=b
  preview.addView(ImageView(this).apply { setImageBitmap(b);scaleType=ImageView.ScaleType.FIT_CENTER },FrameLayout.LayoutParams(-1,-1))
  state.text="Vista previa pausada · la reproducción del escritorio no cambia"
 }
 private fun save() {
  SettingsStore.save(this,widgetId,prefs)
  if(widgetId>0)BlackHoleWidget.refresh(this,widgetId) {
   runOnUiThread { Toast.makeText(this,"Diseño enviado al widget",Toast.LENGTH_SHORT).show()
    if(configuring){setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish()} }
  } else Toast.makeText(this,"Diseño guardado",Toast.LENGTH_SHORT).show()
 }
 private fun pin() {
  SettingsStore.save(this,0,prefs)
  val manager=AppWidgetManager.getInstance(this)
  if(manager.isRequestPinAppWidgetSupported) {
   val callback=PendingIntent.getBroadcast(this,0,Intent(this,BlackHoleWidget::class.java).setAction(BlackHoleWidget.PINNED),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
   manager.requestPinAppWidget(ComponentName(this,BlackHoleWidget::class.java),null,callback)
  } else AlertDialog.Builder(this).setMessage("Mantén pulsado el escritorio → Widgets → RabbitGlance → Black Hole.").setPositiveButton("Entendido",null).show()
 }
 private fun showDiagnostics() {
  val probes=Diagnostics.probes(this)
  val report=Diagnostics.report(this,probes,payload,"Estado de vista previa: ${state.text}")
  val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12)) }
  content.addView(text(report,14f))
  if(widgetId>0) {
   content.addView(text("Después de observarlo en el escritorio:",16f,accent))
   listOf("Se anima en el Motorola","Está congelado","Presenta errores visuales").forEach { value->
    content.addView(button(value) { BlackHoleWidget.diagnostics(this).edit().putString("observation_$widgetId","$value · ${java.time.Instant.now()}").apply();Toast.makeText(this,"Observación registrada",Toast.LENGTH_SHORT).show() })
   }
  }
  content.addView(button("Copiar informe") {
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RabbitGlance",Diagnostics.report(this,probes,payload,"Estado de vista previa: ${state.text}")))
   Toast.makeText(this,"Informe copiado",Toast.LENGTH_SHORT).show()
  })
  AlertDialog.Builder(this).setTitle("Diagnóstico · prueba local").setView(ScrollView(this).apply { addView(content) }).setPositiveButton("Cerrar",null).show()
 }
}
