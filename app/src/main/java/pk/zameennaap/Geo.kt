package pk.zameennaap

import kotlin.math.*

data class LatLon(val lat: Double, val lon: Double)
/** A boundary corner with its position error (1 sigma per axis, meters). */
data class Corner(val lat: Double, val lon: Double, val sigmaM: Double)
data class P(val x: Double, val y: Double)

object Geo {
    private const val R = 6371008.8   // mean Earth radius (m)

    /**
     * Lat/lon -> flat meters around the field's centre (local tangent plane).
     * For a field a few km wide the error is far below 0.01%.
     */
    fun project(pts: List<LatLon>, ref: LatLon): List<P> {
        val k = cos(Math.toRadians(ref.lat))
        return pts.map {
            P(R * Math.toRadians(it.lon - ref.lon) * k, R * Math.toRadians(it.lat - ref.lat))
        }
    }

    fun centroid(pts: List<LatLon>) = LatLon(pts.map { it.lat }.average(), pts.map { it.lon }.average())

    /** Shoelace (Gauss) formula: A = ½ |Σ (xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ)| */
    fun area(p: List<P>): Double {
        if (p.size < 3) return 0.0
        var s = 0.0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    fun dist(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)

    fun perimeter(p: List<P>): Double {
        if (p.size < 2) return 0.0
        var s = 0.0
        val n = if (p.size >= 3) p.size else p.size - 1
        for (i in 0 until n) s += dist(p[i], p[(i + 1) % p.size])
        return s
    }

    /**
     * How wrong could the area be? Error propagation through the shoelace formula:
     *   ∂A/∂xᵢ = (yᵢ₊₁ − yᵢ₋₁)/2,  ∂A/∂yᵢ = (xᵢ₋₁ − xᵢ₊₁)/2
     *   σ_A² = Σ σᵢ² · |pᵢ₊₁ − pᵢ₋₁|² / 4
     */
    fun areaSigma(p: List<P>, sigma: List<Double>): Double {
        val n = p.size
        if (n < 3) return 0.0
        var v = 0.0
        for (i in 0 until n) {
            val prev = p[(i - 1 + n) % n]; val next = p[(i + 1) % n]
            val d2 = (next.x - prev.x).pow(2) + (next.y - prev.y).pow(2)
            v += sigma[i].pow(2) * d2 / 4
        }
        return sqrt(v)
    }

    /**
     * Averages GPS readings taken while standing at a corner.
     * Weight = 1/accuracy², so poor readings count less.
     * GPS errors drift slowly, so averaging only partly helps; we stay conservative.
     */
    fun averageFixes(fixes: List<Triple<Double, Double, Double>>): Corner {
        var wl = 0.0; var wo = 0.0; var ws = 0.0; var accSum = 0.0
        for ((lat, lon, acc) in fixes) {
            val w = 1.0 / max(acc, 1.0).pow(2)
            wl += w * lat; wo += w * lon; ws += w; accSum += acc
        }
        val meanAcc = accSum / fixes.size
        // Android "accuracy" = 68% radius; per-axis sigma ≈ radius/1.5
        val sigma = (meanAcc / 1.5) / sqrt(min(fixes.size, 4).toDouble())
        return Corner(wl / ws, wo / ws, sigma)
    }

    /** Heron's formula: area of a triangle from its 3 sides. Null if sides don't fit. */
    fun heron(a: Double, b: Double, c: Double): Double? {
        if (a <= 0 || b <= 0 || c <= 0) return null
        if (a + b <= c || a + c <= b || b + c <= a) return null
        val s = (a + b + c) / 2
        return sqrt(s * (s - a) * (s - b) * (s - c))
    }

    /** Point at distance ra from (0,0) and rb from (d,0). up = above the x-axis. */
    fun triPoint(d: Double, ra: Double, rb: Double, up: Boolean): P {
        val x = (ra * ra - rb * rb + d * d) / (2 * d)
        val y = sqrt(max(0.0, ra * ra - x * x))
        return P(x, if (up) y else -y)
    }

    /** True if the boundary crosses itself (corners taken out of order). */
    fun selfIntersects(p: List<P>): Boolean {
        val n = p.size
        if (n < 4) return false
        fun cross(o: P, a: P, b: P) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        for (i in 0 until n) for (j in i + 1 until n) {
            if (abs(i - j) <= 1 || (i == 0 && j == n - 1)) continue
            val a = p[i]; val b = p[(i + 1) % n]; val c = p[j]; val d = p[(j + 1) % n]
            val d1 = cross(c, d, a); val d2 = cross(c, d, b)
            val d3 = cross(a, b, c); val d4 = cross(a, b, d)
            if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
        }
        return false
    }
}
