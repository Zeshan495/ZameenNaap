package pk.zameennaap

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Draws the field: shape, numbered corners (corner 1 in gold), side lengths,
 * your live position with its GPS accuracy circle, a scale bar and north.
 * Coordinates are in "data units" (meters for GPS, feet for manual).
 */
class FieldView(ctx: Context) : View(ctx) {
    private var pts: List<P> = emptyList()
    private var labels: List<String> = emptyList()
    private var cur: P? = null
    private var closed = false
    private var hint = ""
    private var accRadius = 0.0
    private var minSpan = 1.0
    private var unit = ""
    private val d = resources.displayMetrics.density

    fun set(points: List<P>, sideLabels: List<String>, current: P?, isClosed: Boolean, hintText: String,
            accuracy: Double = 0.0, minimumSpan: Double = 1.0, unitName: String = "") {
        pts = points; labels = sideLabels; cur = current; closed = isClosed; hint = hintText
        accRadius = accuracy; minSpan = minimumSpan; unit = unitName
        invalidate()
    }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 244, 239) }
    private val grid = Paint().apply { color = Color.rgb(221, 233, 225) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(75, 46, 158, 106) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.GREEN2; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val live = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 110, 230); style = Paint.Style.STROKE }
    private val closing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 160, 30); style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.GREEN2 }
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 150, 20) }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val hintP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED; textAlign = Paint.Align.CENTER }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 255, 255, 255) }
    private val pillLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE; style = Paint.Style.STROKE }
    private val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 110, 230) }
    private val accFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 30, 110, 230) }
    private val accLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 30, 110, 230); style = Paint.Style.STROKE }
    private val scaleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT; strokeCap = Paint.Cap.SQUARE }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        stroke.strokeWidth = 3.5f * d
        live.strokeWidth = 2 * d; live.pathEffect = DashPathEffect(floatArrayOf(8 * d, 6 * d), 0f)
        closing.strokeWidth = 2 * d; closing.pathEffect = DashPathEffect(floatArrayOf(4 * d, 6 * d), 0f)
        accLine.strokeWidth = 1.5f * d; pillLine.strokeWidth = 1 * d; scaleP.strokeWidth = 2.5f * d
        num.textSize = 12 * d; lbl.textSize = 11.5f * d; hintP.textSize = 15 * d

        c.drawRoundRect(0f, 0f, w, h, 18 * d, 18 * d, bg)
        val step = 24 * d
        grid.strokeWidth = 1f
        var gx = step; while (gx < w) { c.drawLine(gx, 0f, gx, h, grid); gx += step }
        var gy = step; while (gy < h) { c.drawLine(0f, gy, w, gy, grid); gy += step }
        drawNorth(c, w)

        val all = pts + listOfNotNull(cur)
        if (all.isEmpty()) { c.drawText(hint, w / 2, h / 2, hintP); return }

        // Same scale on x and y so the shape is true
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        val pad = 40 * d
        val spanX = max(maxX - minX, minSpan); val spanY = max(maxY - minY, minSpan)
        val s = min((w - 2 * pad) / spanX, (h - 2 * pad - 24 * d) / spanY)
        val mx = (minX + maxX) / 2; val my = (minY + maxY) / 2
        fun sx(p: P) = (w / 2 + (p.x - mx) * s).toFloat()
        fun sy(p: P) = (h / 2 - 8 * d - (p.y - my) * s).toFloat()

        if (pts.size >= 2) {
            val path = Path()
            path.moveTo(sx(pts[0]), sy(pts[0]))
            for (p in pts.drop(1)) path.lineTo(sx(p), sy(p))
            if (closed) { path.close(); c.drawPath(path, fill) }
            c.drawPath(path, stroke)
        }
        val cp = cur
        if (cp != null && pts.isNotEmpty()) {
            val last = pts.last()
            c.drawLine(sx(last), sy(last), sx(cp), sy(cp), live)
            if (pts.size >= 2) c.drawLine(sx(cp), sy(cp), sx(pts[0]), sy(pts[0]), closing)
        }
        // Side lengths
        val n = if (closed) pts.size else pts.size - 1
        for (i in 0 until max(0, n)) {
            if (i >= labels.size) break
            val a = pts[i]; val b = pts[(i + 1) % pts.size]
            val x = (sx(a) + sx(b)) / 2; val y = (sy(a) + sy(b)) / 2
            val tw = lbl.measureText(labels[i]) / 2 + 7 * d
            val r = RectF(x - tw, y - 11 * d, x + tw, y + 8 * d)
            c.drawRoundRect(r, 9 * d, 9 * d, pill); c.drawRoundRect(r, 9 * d, 9 * d, pillLine)
            c.drawText(labels[i], x, y + 2.5f * d, lbl)
        }
        // You are here + GPS accuracy circle
        if (cp != null) {
            if (accRadius > 0) {
                val r = (accRadius * s).toFloat().coerceAtMost(max(w, h))
                c.drawCircle(sx(cp), sy(cp), r, accFill); c.drawCircle(sx(cp), sy(cp), r, accLine)
            }
            c.drawCircle(sx(cp), sy(cp), 8 * d, white)
            c.drawCircle(sx(cp), sy(cp), 6 * d, blue)
        }
        // Corners (1 = gold start corner)
        pts.forEachIndexed { i, p ->
            c.drawCircle(sx(p), sy(p), 12 * d, white)
            c.drawCircle(sx(p), sy(p), 10 * d, if (i == 0) gold else dot)
            c.drawText("${i + 1}", sx(p), sy(p) + 4 * d, num)
        }
        drawScale(c, h, s)
    }

    /** Scale bar with a "nice" round length (1, 2, 5, 10, 20, 50…). */
    private fun drawScale(c: Canvas, h: Float, s: Double) {
        if (unit.isEmpty() || s <= 0) return
        val target = 90 * d / s
        val p10 = 10.0.pow(floor(log10(target)))
        val nice = listOf(1.0, 2.0, 5.0, 10.0).map { it * p10 }.last { it <= target * 1.0001 }
        val len = (nice * s).toFloat()
        val x0 = 14 * d; val y0 = h - 14 * d
        c.drawLine(x0, y0, x0 + len, y0, scaleP)
        c.drawLine(x0, y0 - 5 * d, x0, y0, scaleP); c.drawLine(x0 + len, y0 - 5 * d, x0 + len, y0, scaleP)
        val label = (if (nice >= 1) nice.toLong().toString() else nice.toString()) + " " + unit
        lbl.textAlign = Paint.Align.LEFT
        c.drawText(label, x0 + len + 6 * d, y0 + 4 * d, lbl)
        lbl.textAlign = Paint.Align.CENTER
    }

    private fun drawNorth(c: Canvas, w: Float) {
        val x = w - 24 * d; val y = 14 * d
        c.drawCircle(x, y + 12 * d, 16 * d, white)
        val p = Path().apply {
            moveTo(x, y); lineTo(x - 6 * d, y + 15 * d); lineTo(x, y + 11 * d); lineTo(x + 6 * d, y + 15 * d); close()
        }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.GREEN })
        lbl.textSize = 9 * d
        c.drawText("N", x, y + 25 * d, lbl)
        lbl.textSize = 11.5f * d
    }
}
