package pk.zameennaap

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Colours and small builders for a clean, consistent look. */
object Ui {
    val GREEN = Color.rgb(1, 65, 28)          // Pakistan flag green
    val GREEN2 = Color.rgb(20, 120, 72)
    val LIGHT = Color.rgb(226, 242, 232)
    val BG = Color.rgb(244, 247, 245)
    val TEXT = Color.rgb(25, 38, 31)
    val MUTED = Color.rgb(104, 120, 111)
    val AMBER = Color.rgb(214, 140, 0)
    val RED = Color.rgb(200, 40, 40)
    val LINE = Color.rgb(222, 230, 225)
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radiusDp).toFloat()
    if (strokeColor != null) setStroke(dp(1), strokeColor)
}

fun lp(w: Int = LinearLayout.LayoutParams.MATCH_PARENT, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight)

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(Color.WHITE, 18, Ui.LINE)
    setPadding(dp(16), dp(14), dp(16), dp(14))
    elevation = dp(1).toFloat()
    layoutParams = lp().apply { setMargins(0, 0, 0, dp(12)) }
}

fun Context.text(s: String, sizeSp: Float = 15f, color: Int = Ui.TEXT, bold: Boolean = false) = TextView(this).apply {
    text = s; textSize = sizeSp; setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    setLineSpacing(0f, 1.15f)
}

private fun Context.pressable(v: View, bg: Int, radius: Int, stroke: Int? = null) {
    v.background = RippleDrawable(ColorStateList.valueOf(Color.argb(50, 0, 0, 0)), rounded(bg, radius, stroke), null)
    v.isClickable = true; v.isFocusable = true
}

fun Context.button(s: String, filled: Boolean = true, onClick: () -> Unit) = TextView(this).apply {
    text = s; textSize = 16f; gravity = Gravity.CENTER
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(if (filled) Color.WHITE else Ui.GREEN)
    setPadding(dp(14), dp(14), dp(14), dp(14))
    pressable(this, if (filled) Ui.GREEN2 else Color.WHITE, 14, if (filled) null else Ui.GREEN2)
    setOnClickListener { onClick() }
}

fun Context.chip(s: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
    text = s; textSize = 14f; gravity = Gravity.CENTER
    setTextColor(if (selected) Color.WHITE else Ui.GREEN)
    if (selected) setTypeface(typeface, Typeface.BOLD)
    setPadding(dp(12), dp(9), dp(12), dp(9))
    pressable(this, if (selected) Ui.GREEN2 else Color.WHITE, 20, if (selected) null else Ui.LINE)
    setOnClickListener { onClick() }
}

fun Context.row(vararg views: View, gapDp: Int = 8): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    views.forEachIndexed { i, v ->
        addView(v, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (i > 0) marginStart = dp(gapDp)
        })
    }
}
