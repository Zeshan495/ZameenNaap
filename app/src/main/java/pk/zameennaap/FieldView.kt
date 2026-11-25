package pk.zameennaap

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Draws the field shape, numbered corners, side lengths, your live position and north. */
class FieldView(ctx: Context) : View(ctx) {
    private var pts: List<P> = emptyList()
    private var labels: List<String> = emptyList()
    private var cur: P? = null
    private var closed = false
    private var hint = ""
    private val d = resources.displayMetrics.density

    fun set(points: List<P>, sideLabels: List<String>, current: P?, isClosed: Boolean, hintText: String) {
        pts = points; labels = sideLabels; cur = current; closed = isClosed; hint = hintText
        invalidate()
    }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 244, 239) }
    private val grid = Paint().apply { color = Color.rgb(219, 232, 224); strokeWidth = 1f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 46, 158, 106) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.GREEN; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 110, 230); style = Paint.Style.STROKE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.GREEN }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT; textAlign = Paint.Align.CENTER }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED; textAlign = Paint.Align.CENTER }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255) }
    private val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 110, 230) }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 30, 110, 230) }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        stroke.strokeWidth = 3 * d
        dash.strokeWidth = 2 * d
        dash.pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f)
        txt.textSize = 12 * d; lbl.textSize = 12 * d; hintP.textSize = 15 * d

        c.drawRoundRect(0f, 0f, w, h, 16 * d, 16 * d, bg)
        val step = 28 * d
        var gx = step; while (gx < w) { c.drawLine(gx, 0f, gx, h, grid); gx += step }
        var gy = step; while (gy < h) { c.drawLine(0f, gy, w, gy, grid); gy += step }
        drawNorth(c, w)

        val all = pts + listOfNotNull(cur)
        if (all.isEmpty()) {
            c.drawText(hint, w / 2, h / 2, hintP); return
        }

        // Fit everything in the box, keeping the true shape (same scale on x and y)
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        val pad = 34 * d
        val s = min((w - 2 * pad) / max(maxX - minX, 1.0), (h - 2 * pad) / max(maxY - minY, 1.0))
        val mx = (minX + maxX) / 2; val my = (minY + maxY) / 2
        fun sx(p: P) = (w / 2 + (p.x - mx) * s).toFloat()
        fun sy(p: P) = (h / 2 - (p.y - my) * s).toFloat()

        if (pts.size >= 2) {
            val path = Path()
            path.moveTo(sx(pts[0]), sy(pts[0]))
            for (p in pts.drop(1)) path.lineTo(sx(p), sy(p))
            if (closed) { path.close(); c.drawPath(path, fill) }
            c.drawPath(path, stroke)
        }
        // Live line from the last corner to where you're standing
        val cp = cur
        if (cp != null && pts.isNotEmpty()) {
            val last = pts.last()
            c.drawLine(sx(last), sy(last), sx(cp), sy(cp), dash)
        }
        // Side lengths
        val n = if (closed) pts.size else pts.size - 1
        for (i in 0 until max(0, n)) {
            if (i >= labels.size) break
            val a = pts[i]; val b = pts[(i + 1) % pts.size]
            val x = (sx(a) + sx(b)) / 2; val y = (sy(a) + sy(b)) / 2
            val tw = lbl.measureText(labels[i]) / 2 + 6 * d
            c.drawRoundRect(x - tw, y - 11 * d, x + tw, y + 7 * d, 8 * d, 8 * d, pill)
            c.drawText(labels[i], x, y + 2 * d, lbl)
        }
        // Corners
        pts.forEachIndexed { i, p ->
            c.drawCircle(sx(p), sy(p), 11 * d, white)
            c.drawCircle(sx(p), sy(p), 9 * d, dot)
            c.drawText("${i + 1}", sx(p), sy(p) + 4 * d, txt)
        }
        // You are here
        if (cp != null) {
            c.drawCircle(sx(cp), sy(cp), 14 * d, halo)
            c.drawCircle(sx(cp), sy(cp), 7 * d, white)
            c.drawCircle(sx(cp), sy(cp), 5 * d, blue)
        }
    }

    private fun drawNorth(c: Canvas, w: Float) {
        val x = w - 22 * d; val y = 14 * d
        val p = Path().apply {
            moveTo(x, y); lineTo(x - 7 * d, y + 16 * d); lineTo(x, y + 12 * d); lineTo(x + 7 * d, y + 16 * d); close()
        }
        c.drawPath(p, dot)
        lbl.textSize = 11 * d
        c.drawText("N", x, y + 30 * d, lbl)
        lbl.textSize = 12 * d
    }
}
