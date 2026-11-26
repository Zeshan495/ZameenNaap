package pk.zameennaap

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Pakistani land units.
 *
 * Revenue / Patwari system (Punjab & KP villages):
 *   1 karam (کرم)       = 5.5 ft
 *   1 sarsai (سرسائی)   = 1 square karam = 30.25 sq ft
 *   1 marla (مرلہ)      = 9 sarsai       = 272.25 sq ft
 *   1 kanal (کنال)      = 20 marla       = 5,445 sq ft
 *   1 acre / killa      = 8 kanal        = 43,560 sq ft
 *   1 murabba (مربع)    = 25 acre
 * Housing societies (DHA, LDA etc.) usually use 1 marla = 225 sq ft, 1 kanal = 4,500 sq ft.
 * Sindh: 1 acre = 40 ghunta, so 1 ghunta = 1,089 sq ft.
 */
enum class MarlaMode { REVENUE, SOCIETY }

object Units {
    const val SQM_TO_SQFT = 10.7639104
    const val ACRE = 43560.0
    const val SARSAI = 30.25

    fun marla(m: MarlaMode) = if (m == MarlaMode.REVENUE) 272.25 else 225.0
    fun kanal(m: MarlaMode) = 20 * marla(m)

    data class AreaUnit(val en: String, val ur: String, val sqft: Double)
    data class LenUnit(val en: String, val ur: String, val ft: Double)

    val lengthUnits = listOf(
        LenUnit("Karam", "کرم", 5.5),
        LenUnit("Feet", "فٹ", 1.0),
        LenUnit("Gaz", "گز", 3.0),
        LenUnit("Meter", "میٹر", 3.2808399)
    )

    fun table(m: MarlaMode) = listOf(
        AreaUnit("Murabba", "مربع", 25 * ACRE),
        AreaUnit("Acre / Killa", "ایکڑ / کلہ", ACRE),
        AreaUnit("Kanal", "کنال", kanal(m)),
        AreaUnit("Marla", "مرلہ", marla(m)),
        AreaUnit("Sarsai (sq. karam)", "سرسائی (مربع کرم)", SARSAI),
        AreaUnit("Ghunta (Sindh)", "گھنٹا (سندھ)", 1089.0),
        AreaUnit("Square gaz", "مربع گز", 9.0),
        AreaUnit("Square feet", "مربع فٹ", 1.0),
        AreaUnit("Square meter", "مربع میٹر", SQM_TO_SQFT),
        AreaUnit("Hectare", "ہیکٹر", 10000 * SQM_TO_SQFT)
    )

    /** "1 Acre 3 Kanal 12 Marla 4 Sarsai", the way a patwari says it. */
    fun breakdown(sqft: Double, m: MarlaMode, urdu: Boolean): String {
        val parts = mutableListOf<Pair<Long, Pair<String, String>>>()
        if (m == MarlaMode.REVENUE) {
            var s = (sqft / SARSAI).roundToLong()          // everything in sarsai
            val sizes = listOf(36000L to ("Murabba" to "مربع"), 1440L to ("Acre" to "ایکڑ"),
                180L to ("Kanal" to "کنال"), 9L to ("Marla" to "مرلہ"), 1L to ("Sarsai" to "سرسائی"))
            for ((size, name) in sizes) {
                val n = s / size; s %= size
                if (n > 0) parts.add(n to name)
            }
            if (parts.isEmpty()) parts.add(0L to ("Marla" to "مرلہ"))
        } else {
            var f = sqft.roundToLong()
            val k = f / 4500; f %= 4500
            val mr = f / 225; f %= 225
            if (k > 0) parts.add(k to ("Kanal" to "کنال"))
            if (mr > 0) parts.add(mr to ("Marla" to "مرلہ"))
            if (f > 0 || parts.isEmpty()) parts.add(f to ("sq ft" to "مربع فٹ"))
        }
        return parts.joinToString("  ") { (n, name) -> "$n ${if (urdu) name.second else name.first}" }
    }

    /** Pakistani digit grouping: 12345678 -> "1,23,45,678" */
    fun pkGroup(v: Long): String {
        val s = kotlin.math.abs(v).toString()
        if (s.length <= 3) return (if (v < 0) "-" else "") + s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val groups = mutableListOf<String>()
        while (rest.length > 2) { groups.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) groups.add(0, rest)
        return (if (v < 0) "-" else "") + groups.joinToString(",") + "," + last3
    }

    /** "Rs 1,23,45,000  (1 crore 23 lakh 45 thousand)" */
    fun pkr(v: Double, urdu: Boolean): String {
        val r = v.roundToLong()
        val crore = r / 10_000_000; val lakh = (r % 10_000_000) / 100_000; val th = (r % 100_000) / 1000
        val words = mutableListOf<String>()
        if (crore > 0) words.add("$crore ${if (urdu) "کروڑ" else "crore"}")
        if (lakh > 0) words.add("$lakh ${if (urdu) "لاکھ" else "lakh"}")
        if (th > 0) words.add("$th ${if (urdu) "ہزار" else "thousand"}")
        val head = (if (urdu) "روپے " else "Rs ") + pkGroup(r)
        return if (words.isEmpty()) head else "$head\n(${words.joinToString(" ")})"
    }

    fun num(v: Double): String = when {
        v >= 1000 -> String.format(Locale.US, "%,.0f", v)
        v >= 10 -> String.format(Locale.US, "%,.1f", v)
        v >= 0.01 -> String.format(Locale.US, "%.2f", v)
        v == 0.0 -> "0"
        else -> String.format(Locale.US, "%.4f", v)
    }
}
