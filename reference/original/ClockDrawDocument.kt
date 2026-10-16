package com.isaaclisi.classicclock

import android.widget.RemoteViews
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Minimal encoder of the Android 16 platform's Remote Compose V6 wire format.
 * Uses only the public RemoteViews.DrawInstructions API, never a custom host view or reflection.
 * Operation layouts are verified against AOSP android16-release, Apache-2.0 licensed sources.
 * Keep raw IEEE NaN payloads: platform variable references and RPN operators live in those bits.
 */
@android.annotation.TargetApi(35)
internal class ClockDrawDocument(val width: Int, val height: Int) {
    private val bytes = ByteArrayOutputStream()
    private val out = DataOutputStream(bytes)
    // AOSP's application-data range starts at 42. Older V6 players cap override tables at 1000.
    private var nextId = 42
    private fun allocateId(): Int {
        check(nextId < 1000) { "Native clock exceeded the V6 resource budget" }
        return nextId++
    }

    init {
        op(0); int(0x048c0001); int(0); int(0); int(4)
        for ((tag, value) in listOf(5 to width, 6 to height, 8 to 30)) {
            out.writeShort(tag); out.writeShort(4); int(value)
        }
        val description = "Ubuntu Touch Original".toByteArray(Charsets.UTF_8)
        out.writeShort(9 or (3 shl 10)); out.writeShort(description.size + 4)
        int(description.size); out.write(description)
        op(65); int(0); int(34); int(2); int(4) // centered, scale-to-fit without distortion
    }

    fun expression(vararg tokens: Float): Float {
        val id = allocateId()
        op(81); int(id); int(tokens.size); tokens.forEach(::float)
        return variable(id)
    }

    fun circle(x: Float, y: Float, radius: Float, color: Int, alpha: Float = 1f) {
        paint(9, 0, 4, color, 12, alpha.toRawBits(), 14 or (1 shl 16), 8)
        op(46); float(x); float(y); float(radius)
    }

    /** V6 path coordinates may be dynamic FloatExpression reference ids. */
    fun path(points: FloatArray, color: Int, alpha: Float) =
        drawPath(points, color, alpha, false)

    fun pathColorId(points: FloatArray, colorId: Int, alpha: Float) =
        drawPath(points, colorId, alpha, true)

    fun strokePath(points: FloatArray, color: Int, width: Float, alpha: Float) =
        drawPath(points,color,alpha,false,width)

    fun clipPath(points: FloatArray) {
        val id=pathResource(points)
        op(38);int(id)
    }

    /** V6 DrawTweenPath also trims a single path; the host draws the actual stroke in order. */
    fun strokeProgress(points: FloatArray,color: Int,width: Float,progress: Float,alpha: Float) {
        val id=pathResource(points)
        paint(9,0,4,color,12,alpha.toRawBits(),14 or (1 shl 16),8 or (1 shl 16),
            5,width.toRawBits(),7 or (1 shl 16))
        op(125);int(id);int(id);float(0f);float(0f);float(progress)
    }

    private fun pathResource(points: FloatArray): Int {
        val encoded=platformPath(points);val id=allocateId()
        op(123);int(id);int(encoded.size);encoded.forEach(::float)
        return id
    }

    private fun drawPath(points: FloatArray, color: Int, alpha: Float, dynamicColor: Boolean, stroke: Float = 0f) {
        require(points.size in 4..256)
        val encoded = platformPath(points)
        val id = allocateId()
        op(123); int(id); int(encoded.size); encoded.forEach(::float)
        paint(9, 0, if (dynamicColor) 19 else 4, color,
            12, alpha.toRawBits(), 14 or (1 shl 16), 8 or ((if(stroke>0f)1 else 0) shl 16),
            5, stroke.toRawBits(),7 or (1 shl 16))
        op(124); int(id)
    }

    fun strokeLayers(points: FloatArray, layers: List<Triple<Int,Float,Float>>) {
        val encoded=platformPath(points)
        val id=allocateId()
        op(123);int(id);int(encoded.size);encoded.forEach(::float)
        for((color,width,alpha) in layers) {
            paint(9,0,4,color,12,alpha.toRawBits(),14 or (1 shl 16),8 or (1 shl 16),
                5,width.toRawBits(),7 or (1 shl 16))
            op(124);int(id)
        }
    }

    /**
     * AOSP FloatsToPath expects each segment's start point before its controls
     * and endpoint. Omitting it can leave the player's parser on a coordinate
     * with no matching opcode, looping indefinitely instead of throwing.
     */
    private fun platformPath(points: FloatArray): FloatArray {
        val result = ArrayList<Float>()
        var i = 0
        var x = 0f
        var y = 0f
        while (i < points.size) {
            val command = points[i++].also { result.add(it) }.toRawBits() and 0x3fffff
            val count = when (command) {
                10, 11 -> 2
                12 -> 4
                13 -> 5
                14 -> 6
                15, 16 -> 0
                else -> error("Unknown clock path command: $command")
            }
            require(i + count <= points.size)
            if (command in 11..14) { result.add(x); result.add(y) }
            repeat(count) { result.add(points[i + it]) }
            if (count > 0) {
                val end = i + count - if (command == 13) 3 else 2
                x = points[end]; y = points[end + 1]
            }
            i += count
        }
        return result.toFloatArray()
    }

    /** V6 matrix rotate in degrees, evaluated on the host; save/restore the scope. */
    fun rotate(degrees: Float, px: Float, py: Float) {
        op(129); float(degrees); float(px); float(py)
    }

    fun scale(x: Float, y: Float, px: Float, py: Float) {
        op(126); float(x); float(y); float(px); float(py)
    }

    fun translate(x: Float, y: Float) { op(127); float(x); float(y) }
    fun skew(x: Float, y: Float) { op(128); float(x); float(y) }
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {
        op(39); float(left); float(top); float(right); float(bottom)
    }

    fun circleColorId(x: Float, y: Float, radius: Float, colorId: Int, alpha: Float) {
        paint(9, 0, 19, colorId, 12, alpha.toRawBits(), 14 or (1 shl 16), 8)
        op(46); float(x); float(y); float(radius)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int, alpha: Float = 1f) {
        paint(9, 0, 4, color, 12, alpha.toRawBits(), 5, width.toRawBits(), 7 or (1 shl 16),
            14 or (1 shl 16), 8)
        op(47); float(x1); float(y1); float(x2); float(y2)
    }

    /** DrawBitmap V6 accepts variable float bounds, unlike DrawBitmapInt. */
    fun image(bitmap: android.graphics.Bitmap, left: Float, top: Float, right: Float, bottom: Float) {
        drawImage(imageResource(bitmap),left,top,right,bottom)
    }

    /** Share one texture across all wave strips instead of embedding it for every strip. */
    fun imageResource(bitmap: android.graphics.Bitmap): Int {
        val id=allocateId()
        val png = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png)
        val content = png.toByteArray()
        op(101); int(id); int(bitmap.width); int(bitmap.height); int(content.size); out.write(content)
        return id
    }

    fun drawImage(id: Int,left: Float,top: Float,right: Float,bottom: Float,alpha: Float=1f) {
        paint(9, 0, 12, alpha.toRawBits(),17 or (1 shl 16))
        op(44); int(id); float(left); float(top); float(right); float(bottom); int(0)
    }

    /** Shared atlas regions keep every original crest star without duplicating texture resources. */
    fun imageRegion(id: Int,source: android.graphics.Rect,destination: android.graphics.RectF,alpha: Float=1f) {
        paint(9,0,12,alpha.toRawBits(),17 or (1 shl 16))
        op(66);int(id);int(source.left);int(source.top);int(source.right);int(source.bottom)
        int(kotlin.math.round(destination.left).toInt());int(kotlin.math.round(destination.top).toInt())
        int(kotlin.math.round(destination.right).toInt());int(kotlin.math.round(destination.bottom).toInt());int(0)
    }

    fun color(first: Int, second: Int, blend: Float): Int {
        val id = allocateId()
        op(134); int(id); int(0); int(first); int(second); float(blend)
        return id
    }

    fun image(bitmap: android.graphics.Bitmap, left: Int, top: Int, size: Int) {
        val id = allocateId()
        val png = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png)
        val content = png.toByteArray()
        op(101); int(id); int(bitmap.width); int(bitmap.height); int(content.size); out.write(content)
        paint(9, 0, 12, 1f.toRawBits(),17 or (1 shl 16))
        op(66); int(id); int(0); int(0); int(bitmap.width); int(bitmap.height)
        int(left); int(top); int(left + size); int(top + size); int(0)
    }

    fun save() = op(130)
    fun restore() = op(131)

    fun clipOutsideCircle(cx: Float, cy: Float, radius: Float) {
        val id = allocateId()
        val k = kotlin.math.sqrt(.5f)
        val path = floatArrayOf(variable(10), cx + radius, cy,
            variable(13), cx + radius, cy, cx + radius, cy + radius, cx, cy + radius, k,
            variable(13), cx, cy + radius, cx - radius, cy + radius, cx - radius, cy, k,
            variable(13), cx - radius, cy, cx - radius, cy - radius, cx, cy - radius, k,
            variable(13), cx, cy - radius, cx + radius, cy - radius, cx + radius, cy, k,
            variable(15), variable(16))
        op(123); int(id); int(path.size); path.forEach(::float)
        op(38); int(id or (1 shl 24)) // difference, same as Canvas.clipOutPath
    }

    fun text(value: String): Int {
        val id = allocateId()
        val utf8 = value.toByteArray(Charsets.UTF_8)
        op(102); int(id); int(utf8.size); out.write(utf8)
        return id
    }

    fun number(value: Float, digits: Int = 1, padded: Boolean = false): Int {
        val id = allocateId()
        op(135); int(id); float(value); int(digits shl 16); int(if (padded) 12 else 4)
        return id
    }

    fun merge(vararg texts: Int): Int {
        require(texts.isNotEmpty())
        return texts.drop(1).fold(texts.first()) { previous, current ->
            val id = allocateId()
            op(136); int(id); int(previous); int(current); id
        }
    }

    fun lookup(labels: List<String>, index: Float): Int {
        val strings = labels.map(::text)
        val list = allocateId()
        op(146); int(list); int(strings.size); strings.forEach(::int)
        val result = allocateId()
        op(151); int(result); int(list); float(index)
        return result
    }

    /** refs/operators are marked in mask; literals never are. */
    fun integerExpression(mask: Int, vararg words: Int): Float {
        val id = allocateId()
        op(144); int(id); int(mask); int(words.size); words.forEach(::int)
        return variable(id)
    }

    fun textPaint(size: Float, color: Int = 0xfff7f4f7.toInt(), weight: Int = 300) {
        paint(9, 0, 4, color, 12, (android.graphics.Color.alpha(color) / 255f).toRawBits(), 14 or (1 shl 16), 8,
            1, size.toRawBits(), 16 or (weight shl 16), 1)
    }

    fun textWidth(id: Int): Float {
        val result = allocateId()
        op(155); int(result); int(id); int(0)
        return variable(result)
    }

    fun drawText(id: Int, x: Float, y: Float) {
        op(133); int(id); float(x); float(y); float(0f); float(0f); int(0)
    }

    fun click(id: Int) {
        val description = text("Configurar reloj")
        val metadata = text("clock-settings")
        op(64); int(id); int(description); float(0f); float(0f)
        float(width.toFloat()); float(height.toFloat()); int(metadata)
    }

    private fun paint(vararg words: Int) {
        op(40); int(words.size); words.forEach(::int)
    }

    fun build(): RemoteViews = RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(bytes.toByteArray())).build())
    fun byteArray(): ByteArray = bytes.toByteArray()
    private fun op(value: Int) = out.writeByte(value)
    private fun int(value: Int) = out.writeInt(value)
    private fun float(value: Float) = out.writeInt(value.toRawBits())

    companion object {
        fun variable(id: Int) = Float.fromBits(0xff800000.toInt() or id)
        fun operator(id: Int) = variable(0x310000 + id)
        val ADD = operator(1)
        val SUB = operator(2)
        val MUL = operator(3)
        val DIV = operator(4)
        val MOD = operator(5)
        val MIN = operator(6)
        val MAX = operator(7)
        val SIGN = operator(11)
        val FLOOR = operator(14)
        val SIN = operator(18)
        val COS = operator(19)
        val IFELSE = operator(26) // false, true, condition
        val CONTINUOUS_SECONDS = variable(1)
        val MINUTE = variable(3)
        val HOUR = variable(4)
        val ANIMATION_SECONDS = variable(30)
    }
}

