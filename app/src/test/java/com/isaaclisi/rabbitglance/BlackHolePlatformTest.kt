package com.isaaclisi.rabbitglance

import android.app.Activity
import android.graphics.*
import android.os.Parcel
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** Framework simulation, not a Motorola certification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlackHolePlatformTest {
 private fun capture(view: View,w: Int=320,h: Int=360): Bitmap {
  view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
  view.layout(0,0,w,h)
  return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
 }
 @Test fun documentSurvivesParcelAndAnimatesWithoutRepublishing() {
  val a=Robolectric.buildActivity(Activity::class.java).setup().get()
  val doc=BlackHoleScene.create(a,ClockPreferences(showClock=false,ringAnimation=false),320,360)
  assertTrue(doc.bytes.size<1024*1024)
  val parcel=Parcel.obtain();doc.views.writeToParcel(parcel,0);parcel.setDataPosition(0)
  val rv=RemoteViews.CREATOR.createFromParcel(parcel);parcel.recycle()
  val view=rv.apply(a,FrameLayout(a));a.setContentView(view)
  capture(view).recycle();val first=capture(view)
  assertEquals(0,Color.alpha(first.getPixel(0,0)))
  val core=first.getPixel(160,180);assertTrue(Color.alpha(core)>240);assertTrue(Color.red(core)<12)
  ShadowSystemClock.advanceBy(Duration.ofSeconds(4));val second=capture(view)
  assertFalse("Same document must animate the accretion flow",first.sameAs(second))
  java.io.File("build/evidence").mkdirs()
  for((name,b) in listOf("black-hole-before.png" to first,"black-hole-after.png" to second))java.io.File("build/evidence/$name").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
  first.recycle();second.recycle();a.finish()
 }
 @Test fun pausedArtworkAndReducedMotionRemainFixed() {
  val a=Robolectric.buildActivity(Activity::class.java).setup().get()
  val doc=BlackHoleScene.create(a,ClockPreferences(showClock=false,ringAnimation=false),320,360,motionScale=0f)
  val view=doc.views.apply(a,FrameLayout(a));a.setContentView(view);capture(view).recycle();val before=capture(view)
  ShadowSystemClock.advanceBy(Duration.ofSeconds(4));val after=capture(view)
  assertTrue("Reduced motion must keep all artwork fixed",before.sameAs(after));before.recycle();after.recycle();a.finish()
 }
 @Test fun fullSceneAndOptionalStarsRenderAcrossSizesAndMonthLimits() {
  val a=Robolectric.buildActivity(Activity::class.java).setup().get()
  for((w,h) in listOf(140 to 160,320 to 360,360 to 200,220 to 450)) {
   val doc=BlackHoleScene.create(a,ClockPreferences(starsEnabled=true,starDensity=StarDensity.RICH,showSeconds=true,face=if(w==220)ClockFace.ANALOG else ClockFace.DIGITAL),w,h,today=if(w==320)java.time.LocalDate.now() else java.time.LocalDate.of(2026,10,31))
   val view=doc.views.apply(a,FrameLayout(a));a.setContentView(view);capture(view,w,h).recycle();val b=capture(view,w,h)
   assertEquals(0,Color.alpha(b.getPixel(0,0)))
   assertTrue((0 until h).any { Color.alpha(b.getPixel(w/2,it))>100 })
   if(w==320)java.io.File("build/evidence/black-hole-clock.png").also {it.parentFile?.mkdirs()}.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
   b.recycle()
  }
  val errors=Diagnostics.probes(a).filterNot {it.passed};assertTrue(errors.toString(),errors.isEmpty());a.finish()
 }
 @Test fun configurationOpensAndInstancesKeepIndependentSettings() {
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
  val activity=controller.get()
  val a=ClockPreferences(motionSpeed=.5f,starsEnabled=true)
  val b=ClockPreferences(size=SceneSize.LARGE,motionSpeed=2f)
  SettingsStore.save(activity,11,a);SettingsStore.save(activity,12,b)
  assertEquals(a,SettingsStore.read(activity,11));assertEquals(b,SettingsStore.read(activity,12))
  controller.pause().stop().destroy()
 }

}
