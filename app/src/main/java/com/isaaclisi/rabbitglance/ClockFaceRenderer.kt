package com.isaaclisi.rabbitglance

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Hands and hour markers use the same center and quiet colors as the original sphere. */
internal object ClockFaceRenderer {
    fun analog(doc: ClockDrawDocument, cx: Float, cy: Float, diameter: Float, seconds: Boolean) {
        val tau = (2 * PI).toFloat()
        repeat(12) { i ->
            val angle = tau * i / 12 - (PI / 2).toFloat()
            doc.circle(cx + cos(angle) * diameter * .238f,
                cy + sin(angle) * diameter * .238f,
                diameter * if (i % 3 == 0) .0048f else .0028f,
                0xffc9a7bc.toInt(), if (i % 3 == 0) .7f else .4f)
        }
        val second = doc.expression(ClockDrawDocument.CONTINUOUS_SECONDS, 60f, ClockDrawDocument.MOD)
        val minute = doc.expression(ClockDrawDocument.MINUTE, second, 60f, ClockDrawDocument.DIV, ClockDrawDocument.ADD)
        val hour = doc.expression(ClockDrawDocument.HOUR, 12f, ClockDrawDocument.MOD,
            minute, 60f, ClockDrawDocument.DIV, ClockDrawDocument.ADD)
        fun hand(value: Float, period: Float, length: Float, width: Float, color: Int, tail: Float) {
            val angle = doc.expression(value, tau / period, ClockDrawDocument.MUL,
                (PI / 2).toFloat(), ClockDrawDocument.SUB)
            val cosine = doc.expression(angle, ClockDrawDocument.COS)
            val sine = doc.expression(angle, ClockDrawDocument.SIN)
            fun x(distance: Float) = doc.expression(cosine, diameter * distance,
                ClockDrawDocument.MUL, cx, ClockDrawDocument.ADD)
            fun y(distance: Float) = doc.expression(sine, diameter * distance,
                ClockDrawDocument.MUL, cy, ClockDrawDocument.ADD)
            doc.line(x(-tail), y(-tail), x(length), y(length), diameter * width, color)
        }
        hand(hour, 12f, .158f, .012f, 0xffe0c7d7.toInt(), .028f)
        hand(minute, 60f, .212f, .008f, 0xfff7f4f7.toInt(), .035f)
        if (seconds) hand(second, 60f, .225f, .0032f, 0xffe996b3.toInt(), .045f)
        doc.circle(cx, cy, diameter * .013f, 0xffe0c7d7.toInt())
        doc.circle(cx, cy, diameter * .005f, 0xff292331.toInt())
    }
}
