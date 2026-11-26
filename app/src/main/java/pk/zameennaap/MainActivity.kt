package pk.zameennaap

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var store: FieldStore
    private var urdu = true
    private var mode = MarlaMode.REVENUE
    private var tab = 0
    private lateinit var content: FrameLayout
    private val navItems = mutableListOf<Pair<ImageView, TextView>>()
    private val handler = Handler(Looper.getMainLooper())
    private var lm: LocationManager? = null
    private var tone: ToneGenerator? = null

    // ---- GPS mode state ----
    private val corners = mutableListOf<Corner>()
    private var lastFix: Location? = null
    private var capturing = false
    private val captureBuf = mutableListOf<Location>()
    private var autoWalk = false
    private var gpsView: FieldView? = null
    private var accPill: TextView? = null
    private var satPill: TextView? = null
    private var livePill: TextView? = null
    private var satsUsed = 0
    private var satsSeen = 0
    private var gpsArea: TextView? = null
    private var gpsSub: TextView? = null
    private var captureBtn: TextView? = null
    private var captureProg: ProgressBar? = null

    // ---- Manual mode state ----
    private var shape = 1          // 0 rectangle, 1 four sides, 2 triangle
    private var lenUnit = 0        // index in Units.lengthUnits (0 = karam)
    private var manualVals = mutableListOf<String>()
    private val inputs = mutableListOf<EditText>()
    private var manView: FieldView? = null
    private var manArea: TextView? = null
    private var manSub: TextView? = null
    private var manTable: LinearLayout? = null
    private var manSqft = 0.0

    private fun t(en: String, ur: String) = if (urdu) ur else en
    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private val dirFlag get() = if (urdu) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    private fun karam(m: Double) = f1(m * 3.2808399 / 5.5) + t(" karam", " کرم")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        store = FieldStore(this)
        urdu = prefs.getBoolean("urdu", true)
        mode = if (prefs.getBoolean("society", false)) MarlaMode.SOCIETY else MarlaMode.REVENUE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Draw edge-to-edge on every Android version (Android 15 forces it anyway)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run { window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.WHITE }
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
        loadDraft()
        build()
        if (!hasGps()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7)
        if (!prefs.getBoolean("seenIntro", false)) showIntro()
    }

    override fun onDestroy() { super.onDestroy(); tone?.release() }

    private fun hasGps() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        startGps()
        if (tab == 0) refreshGps()
    }

    override fun onResume() { super.onResume(); startGps() }
    override fun onPause() {
        super.onPause()
        lm?.removeUpdates(this)
        runCatching { lm?.unregisterGnssStatusCallback(gnssCb) }
    }

    /** Counts satellites: shows the user WHY accuracy is weak (few satellites = trees/roof overhead). */
    private val gnssCb = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satsSeen = status.satelliteCount
            satsUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
            if (tab == 0) refreshStatus()
        }
    }

    // Corners are saved instantly, so nothing is lost if the phone locks or the app closes
    private fun saveDraft() {
        val a = JSONArray()
        corners.forEach { a.put(JSONArray().put(it.lat).put(it.lon).put(it.sigmaM)) }
        prefs.edit().putString("draft", a.toString()).apply()
    }
    private fun loadDraft() {
        val a = runCatching { JSONArray(prefs.getString("draft", "[]")) }.getOrNull() ?: return
        corners.clear()
        for (i in 0 until a.length()) {
            val p = a.getJSONArray(i); corners.add(Corner(p.getDouble(0), p.getDouble(1), p.getDouble(2)))
        }
    }

    private fun startGps() {
        if (!hasGps()) return
        try {
            lm?.removeUpdates(this)
            // GPS_PROVIDER = satellites only. Works with no internet and no SIM.
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            @Suppress("DEPRECATION")
            lm?.registerGnssStatusCallback(gnssCb, handler)
            lastFix = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.takeIf { System.currentTimeMillis() - it.time < 30_000 }
        } catch (e: SecurityException) { }
    }

    // =====================================================================
    // Layout: top bar, page (scroll + sticky action bar), bottom navigation
    // =====================================================================
    private fun build() {
        navItems.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            layoutDirection = dirFlag
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.GREEN)
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text(t("Zameen Naap", "زمین ناپ"), 22f, Color.WHITE, true))
        titles.addView(text(t("Land measurement • works offline", "زمین کی پیمائش • بغیر انٹرنیٹ"), 12f, Color.rgb(190, 225, 205)))
        top.addView(titles, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        fun topPill(s: String, click: () -> Unit) = TextView(this).apply {
            text = s; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.argb(45, 255, 255, 255), 20)
            setOnClickListener { click() }
        }
        top.addView(topPill(if (urdu) "English" else "اردو") {
            urdu = !urdu; prefs.edit().putBoolean("urdu", urdu).apply()
            saveManualVals(); build()
        })
        top.addView(topPill(t("Marla ", "مرلہ ") + (if (mode == MarlaMode.REVENUE) "272" else "225")) { marlaDialog() },
            lp(LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        top.addView(topPill(" ? ") { showIntro() }, lp(LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        root.addView(top)

        content = FrameLayout(this)
        root.addView(content, lp(h = 0, weight = 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        val tabs = listOf(
            Triple(R.drawable.ic_gps, "GPS", "GPS"),
            Triple(R.drawable.ic_ruler, "By hand", "ہاتھ سے"),
            Triple(R.drawable.ic_folder, "Saved", "محفوظ")
        )
        tabs.forEachIndexed { i, (icon, en, ur) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                minimumHeight = dp(56)
                setOnClickListener { saveManualVals(); showTab(i) }
            }
            val iv = ImageView(this).apply { setImageResource(icon) }
            val tv = text(t(en, ur), 13f).apply { gravity = Gravity.CENTER; maxLines = 1 }
            item.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)))
            item.addView(tv)
            navItems.add(iv to tv)
            nav.addView(item, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(nav)

        // Keep the bars clear of the status bar, gesture bar and keyboard
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            val bars = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeOn = ins.isVisible(WindowInsetsCompat.Type.ime())
            val ime = ins.getInsets(WindowInsetsCompat.Type.ime())
            top.setPaddingRelative(dp(18), bars.top + dp(12), dp(12), dp(12))
            nav.setPadding(0, dp(4), 0, dp(4) + bars.bottom)
            nav.visibility = if (imeOn) View.GONE else View.VISIBLE   // more room while typing
            root.setPadding(bars.left, 0, bars.right, if (imeOn) ime.bottom else 0)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        showTab(tab)
    }

    private fun showTab(i: Int) {
        tab = i
        navItems.forEachIndexed { j, (iv, tv) ->
            val c = if (j == i) Ui.GREEN2 else Ui.MUTED
            iv.setColorFilter(c); tv.setTextColor(c)
            tv.setTypeface(null, if (j == i) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        gpsView = null; manView = null; captureProg = null; accPill = null; livePill = null
        content.removeAllViews()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(16))
        }
        // Sticky bar: the main result + main buttons stay visible, always under the thumb
        val sticky = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        when (i) {
            0 -> buildGps(col, sticky)
            1 -> buildManual(col, sticky)
            else -> buildSaved(col)
        }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (i == 0) page.addView(col, lp(h = 0, weight = 1f))      // map fills the screen, no scrolling
        else page.addView(ScrollView(this).apply { addView(col); isFillViewport = true }, lp(h = 0, weight = 1f))
        if (sticky.childCount > 0) page.addView(sticky)
        content.addView(page)
    }

    // =====================================================================
    // TAB 1: GPS walk-around
    // =====================================================================
    private fun statusChip(): TextView = TextView(this).apply {
        textSize = 13f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8)); maxLines = 1
    }

    private fun styleChip(v: TextView, c: Int) {
        v.setTextColor(c)
        v.background = rounded(Color.argb(30, Color.red(c), Color.green(c), Color.blue(c)), 18)
    }

    private fun buildGps(col: LinearLayout, sticky: LinearLayout) {
        // ---- Status chips: accuracy, satellites, clear ----
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        accPill = statusChip(); satPill = statusChip()
        chips.addView(accPill)
        chips.addView(satPill, lp(LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
        chips.addView(View(this), lp(0, 1, 1f))
        chips.addView(statusChip().apply {
            text = t("Clear", "مٹائیں"); setTextColor(Ui.RED); background = rounded(Color.WHITE, 18, Ui.LINE)
            setOnClickListener {
                if (corners.isEmpty()) return@setOnClickListener
                AlertDialog.Builder(this@MainActivity).setMessage(t("Remove all corners?", "سارے کونے مٹا دیں؟"))
                    .setPositiveButton(t("Yes", "ہاں")) { _, _ -> corners.clear(); saveDraft(); refreshGps() }
                    .setNegativeButton(t("No", "نہیں"), null).show()
            }
        })
        col.addView(chips, lp().apply { bottomMargin = dp(10) })

        // ---- Map fills all remaining space, with a live-distance pill ----
        val mapCard = card().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        val frame = FrameLayout(this)
        gpsView = FieldView(this)
        frame.addView(gpsView, FrameLayout.LayoutParams(-1, -1))
        livePill = TextView(this).apply {
            textSize = 13f; setTextColor(Ui.TEXT); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(Color.argb(240, 255, 255, 255), 14, Ui.LINE)
            visibility = View.GONE
        }
        frame.addView(livePill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(10), dp(10), dp(10), dp(10))
        })
        mapCard.addView(frame, lp(h = 0, weight = 1f))
        col.addView(mapCard, lp(h = 0, weight = 1f))

        // ---- Sticky bottom: result + actions ----
        gpsArea = text("", 21f, Ui.GREEN, true)
        gpsSub = text("", 12.5f, Ui.MUTED).apply { maxLines = 3 }
        sticky.addView(gpsArea); sticky.addView(gpsSub)
        captureBtn = button(t("＋  Mark corner here", "＋  یہاں کونا لگائیں")) { startCapture() }.apply {
            textSize = 18f; minHeight = dp(56)
        }
        sticky.addView(captureBtn, lp().apply { topMargin = dp(8) })
        captureProg = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 80; progress = 0; visibility = if (capturing) View.VISIBLE else View.GONE
            progressTintList = ColorStateList.valueOf(Ui.GREEN2)
        }
        sticky.addView(captureProg, lp())

        val undo = button(t("↶ Undo", "↶ واپس"), false, small = true) {
            if (corners.isNotEmpty()) { corners.removeAt(corners.size - 1); saveDraft() }
            refreshGps()
        }
        val auto = button(if (autoWalk) t("● Auto ON", "● خودکار آن") else t("Auto walk", "خودکار"), autoWalk, small = true) {}
        auto.setOnClickListener {
            autoWalk = !autoWalk
            toast(if (autoWalk) t("Walk along the boundary. A point is added every 3 m.",
                "حد کے ساتھ ساتھ چلیں، ہر 3 میٹر پر نقطہ لگے گا۔") else t("Auto off", "خودکار بند"))
            showTab(0)
        }
        val save = button(t("✓ Save", "✓ محفوظ"), false, small = true) { saveGpsField() }
        sticky.addView(row(undo, auto, save), lp().apply { topMargin = dp(8) })
        refreshGps()
    }

    private fun projectedCorners(): Pair<List<P>, P?> {
        val ll = corners.map { LatLon(it.lat, it.lon) }
        val cur = lastFix?.let { LatLon(it.latitude, it.longitude) }
        val ref = if (ll.isNotEmpty()) Geo.centroid(ll) else cur ?: return emptyList<P>() to null
        return Geo.project(ll, ref) to cur?.let { Geo.project(listOf(it), ref)[0] }
    }

    private fun gpsSqft() = Geo.area(projectedCorners().first) * Units.SQM_TO_SQFT

    private fun refreshStatus() {
        val acc = accPill ?: return
        val sat = satPill ?: return
        val fix = lastFix
        val gpsOn = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        acc.setOnClickListener(null)
        when {
            !hasGps() -> { acc.text = t("Allow location", "لوکیشن کی اجازت دیں"); styleChip(acc, Ui.RED)
                acc.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7) } }
            !gpsOn -> { acc.text = t("GPS off: tap", "GPS بند: دبائیں"); styleChip(acc, Ui.RED)
                acc.setOnClickListener { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
            fix == null -> { acc.text = t("Finding GPS…", "GPS تلاش ہو رہا ہے…"); styleChip(acc, Ui.AMBER) }
            else -> {
                val a = fix.accuracy
                acc.text = "± ${a.toInt()} " + t("m", "میٹر") + "  •  " + when {
                    a <= 5 -> t("Excellent", "بہترین"); a <= 10 -> t("OK", "ٹھیک"); else -> t("Wait", "انتظار کریں") }
                styleChip(acc, when { a <= 5 -> Ui.GREEN2; a <= 10 -> Ui.AMBER; else -> Ui.RED })
                acc.setOnClickListener {
                    toast(t("Tip: move away from trees, walls and roofs. Accuracy improves after 1–2 minutes in open sky.",
                        "مشورہ: درختوں، دیواروں اور چھت سے دور ہو جائیں۔ کھلی جگہ پر 1–2 منٹ میں درستگی بہتر ہو جاتی ہے۔"))
                }
            }
        }
        sat.text = "🛰 $satsUsed/$satsSeen"
        styleChip(sat, when { satsUsed >= 8 -> Ui.GREEN2; satsUsed >= 4 -> Ui.AMBER; else -> Ui.MUTED })
    }

    private fun refreshGps() {
        refreshStatus()
        val view = gpsView ?: return
        val (pts, cur) = projectedCorners()
        val labels = pts.indices.map { i -> karam(Geo.dist(pts[i], pts[(i + 1) % pts.size])) }
        view.set(pts, labels, cur, pts.size >= 3, t("Stand on the first corner", "پہلے کونے پر کھڑے ہوں"),
            accuracy = lastFix?.accuracy?.toDouble() ?: 0.0, minimumSpan = 25.0, unitName = t("m", "میٹر"))

        // Live distance from the last corner (and back to corner 1)
        val pill = livePill
        if (pill != null) {
            if (cur != null && pts.isNotEmpty()) {
                var s = t("From corner ${pts.size}: ", "کونا ${pts.size} سے: ") + karam(Geo.dist(pts.last(), cur))
                if (pts.size >= 2) s += "\n" + t("To corner 1: ", "کونا 1 تک: ") + karam(Geo.dist(pts[0], cur))
                pill.text = s; pill.visibility = View.VISIBLE
            } else pill.visibility = View.GONE
        }

        if (pts.size >= 3) {
            val sqft = Geo.area(pts) * Units.SQM_TO_SQFT
            val sig = Geo.areaSigma(pts, corners.map { it.sigmaM }) * Units.SQM_TO_SQFT
            val perFt = Geo.perimeter(pts) * 3.2808399
            gpsArea?.text = Units.breakdown(sqft, mode, urdu)
            var sub = t("≈ %s acre  •  ± %s marla  •  %d corners  •  boundary %s karam",
                "≈ %s ایکڑ  •  ± %s مرلہ  •  %d کونے  •  حد %s کرم").format(
                Units.num(sqft / Units.ACRE), f1(sig / Units.marla(mode)), pts.size, Units.num(perFt / 5.5))
            if (Geo.selfIntersects(pts)) sub += t("\n⚠ Lines cross: corners out of order. Undo and redo.",
                "\n⚠ لکیریں آپس میں کٹ رہی ہیں: کونے ترتیب سے نہیں۔ واپس کر کے دوبارہ لگائیں۔")
            gpsSub?.text = sub
        } else {
            gpsArea?.text = if (pts.isEmpty()) t("Go to corner 1", "پہلے کونے پر جائیں")
                            else t("Walk to corner %d", "کونا %d کی طرف چلیں").format(Locale.US, pts.size + 1)
            gpsSub?.text = t("Mark %d more corner(s) to see the area", "رقبہ دیکھنے کے لیے مزید %d کونے لگائیں")
                .format(Locale.US, 3 - pts.size)
        }
    }

    private fun startCapture() {
        if (capturing) return
        val f = lastFix
        if (f == null) { toast(t("Waiting for GPS. Stand in open sky.", "GPS کا انتظار ہے۔ کھلی جگہ پر کھڑے ہوں۔")); return }
        if (f.accuracy > 10) {
            AlertDialog.Builder(this)
                .setTitle(t("GPS is weak (±%d m)", "GPS کمزور ہے (±%d میٹر)").format(Locale.US, f.accuracy.toInt()))
                .setMessage(t("This corner could be off by that much. Move away from trees and roofs and wait 1–2 minutes for green.",
                    "یہ کونا اتنا غلط لگ سکتا ہے۔ درختوں اور چھت سے دور ہو کر 1–2 منٹ سبز ہونے کا انتظار کریں۔"))
                .setPositiveButton(t("Mark anyway", "پھر بھی لگائیں")) { _, _ -> doCapture() }
                .setNegativeButton(t("I'll wait", "انتظار کروں گا"), null).show()
            return
        }
        doCapture()
    }

    /** Records GPS for 8 seconds at the corner and averages it (smooth progress bar). */
    private fun doCapture() {
        val f = lastFix ?: return
        capturing = true
        captureBuf.clear(); captureBuf.add(f)
        captureProg?.visibility = View.VISIBLE
        handler.post(object : Runnable {
            var ticks = 0
            override fun run() {
                captureProg?.progress = ticks
                if (ticks >= 80) { finishCapture(); return }
                captureBtn?.text = t("Stay still… %d", "ساکت رہیں… %d").format(Locale.US, (89 - ticks) / 10)
                ticks++
                handler.postDelayed(this, 100)
            }
        })
        refreshGps()
    }

    private fun saveGpsField() {
        if (corners.size < 3) { toast(t("Mark at least 3 corners first", "پہلے کم از کم 3 کونے لگائیں")); return }
        val sqft = gpsSqft()
        askName { name ->
            val f = FieldStore.Field(name, System.currentTimeMillis(), sqft, "GPS", corners.map { LatLon(it.lat, it.lon) })
            store.add(f)
            corners.clear(); saveDraft(); refreshGps()
            toast(t("Saved", "محفوظ ہو گیا"))
            showTools(f)
        }
    }

    private fun finishCapture() {
        capturing = false
        if (captureBuf.isNotEmpty()) {
            corners.add(Geo.averageFixes(captureBuf.map { Triple(it.latitude, it.longitude, it.accuracy.toDouble()) }))
            saveDraft()
            vibrate()
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200) }
        }
        captureBtn?.text = t("＋  Mark corner here", "＋  یہاں کونا لگائیں")
        captureProg?.visibility = View.GONE
        refreshGps()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(150)
    }

    override fun onLocationChanged(loc: Location) {
        lastFix = loc
        if (capturing) captureBuf.add(loc)
        if (autoWalk && !capturing && loc.accuracy <= 15f) {
            val last = corners.lastOrNull()
            val d = FloatArray(1)
            if (last != null) Location.distanceBetween(last.lat, last.lon, loc.latitude, loc.longitude, d)
            if (last == null || d[0] >= 3f) { corners.add(Corner(loc.latitude, loc.longitude, loc.accuracy / 1.5)); saveDraft() }
        }
        if (tab == 0) refreshGps()
    }
    // Needed on Android 7–10, where these are not default methods
    @Deprecated("old API") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) { if (tab == 0) refreshGps() }
    override fun onProviderDisabled(p: String) { if (tab == 0) refreshGps() }

    // =====================================================================
    // TAB 2: By hand (karam / feet), the patwari way
    // =====================================================================
    private fun saveManualVals() {
        if (inputs.isNotEmpty()) manualVals = inputs.map { it.text.toString() }.toMutableList()
    }

    private fun numberInput(label: String, index: Int): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(label, 13f, Ui.MUTED).apply { setPadding(dp(2), 0, dp(2), dp(4)) })
        val e = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 20f; setTextColor(Ui.TEXT); hint = "0"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = rounded(Ui.BG, 12, Ui.LINE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setSelectAllOnFocus(true)
            setText(manualVals.getOrNull(index) ?: "")
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = recomputeManual()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        inputs.add(e)
        box.addView(e, lp())
        return box
    }

    private fun buildManual(col: LinearLayout, sticky: LinearLayout) {
        inputs.clear()
        val shapeCard = card()
        shapeCard.addView(text(t("Field shape", "کھیت کی شکل"), 13f, Ui.MUTED))
        val shapes = listOf(t("▭ Rectangle", "▭ مستطیل"), t("▱ 4 sides", "▱ چار اطراف"), t("△ Triangle", "△ تکون"))
        shapeCard.addView(row(*shapes.mapIndexed { i, s ->
            chip(s, i == shape) { if (shape != i) { shape = i; manualVals.clear(); showTab(1) } }
        }.toTypedArray(), gapDp = 6), lp().apply { topMargin = dp(8) })
        shapeCard.addView(text(t("Measuring in", "پیمائش کی اکائی"), 13f, Ui.MUTED).apply { setPadding(0, dp(12), 0, 0) })
        shapeCard.addView(row(*Units.lengthUnits.mapIndexed { i, u ->
            chip(t(u.en, u.ur), i == lenUnit) { saveManualVals(); lenUnit = i; showTab(1) }
        }.toTypedArray(), gapDp = 6), lp().apply { topMargin = dp(8) })
        col.addView(shapeCard)

        val u = Units.lengthUnits[lenUnit]
        val un = t(u.en.lowercase(), u.ur)
        val inCard = card()
        fun pair(a: View, b: View?) {
            val r = if (b != null) row(a, b, gapDp = 10) else a
            inCard.addView(r, lp().apply { bottomMargin = dp(10) })
        }
        when (shape) {
            0 -> pair(numberInput(t("Length", "لمبائی") + " ($un)", 0), numberInput(t("Width", "چوڑائی") + " ($un)", 1))
            1 -> {
                pair(numberInput(t("Side 1 (1→2)", "طرف 1 (1←2)"), 0), numberInput(t("Side 2 (2→3)", "طرف 2 (2←3)"), 1))
                pair(numberInput(t("Side 3 (3→4)", "طرف 3 (3←4)"), 2), numberInput(t("Side 4 (4→1)", "طرف 4 (4←1)"), 3))
                pair(numberInput(t("Diagonal 1→3 ($un) for EXACT area", "وتر 1 سے 3 ($un) درست رقبے کے لیے"), 4), null)
            }
            else -> {
                pair(numberInput(t("Side 1", "طرف 1"), 0), numberInput(t("Side 2", "طرف 2"), 1))
                pair(numberInput(t("Side 3", "طرف 3"), 2), null)
            }
        }
        if (shape != 0) inCard.addView(text(t("All sides in $un", "تمام اطراف $un میں"), 12f, Ui.MUTED))
        inputs.lastOrNull()?.imeOptions = EditorInfo.IME_ACTION_DONE
        col.addView(inCard)

        val mapCard = card().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        manView = FieldView(this)
        mapCard.addView(manView, lp(h = dp(220)))
        col.addView(mapCard)

        val tableCard = card()
        tableCard.addView(text(t("In all units", "تمام اکائیوں میں"), 15f, Ui.TEXT, true))
        manTable = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tableCard.addView(manTable, lp().apply { topMargin = dp(8) })
        col.addView(tableCard)

        // ---- Sticky bottom: live result while typing ----
        manArea = text("", 21f, Ui.GREEN, true)
        manSub = text("", 12f, Ui.MUTED).apply { maxLines = 3 }
        sticky.addView(manArea); sticky.addView(manSub)
        val tools = button(t("₨ Price & divide", "₨ قیمت و تقسیم"), false) {
            if (manSqft <= 0) toast(t("Enter the sides first", "پہلے اطراف لکھیں"))
            else showTools(FieldStore.Field(t("This field", "یہ زمین"), System.currentTimeMillis(), manSqft, "Manual", emptyList()), saved = false)
        }
        val saveBtn = button(t("✓  Save", "✓  محفوظ کریں")) {
            if (manSqft <= 0) { toast(t("Enter the sides first", "پہلے اطراف لکھیں")); return@button }
            val sq = manSqft
            askName { name ->
                store.add(FieldStore.Field(name, System.currentTimeMillis(), sq, "Manual", emptyList()))
                toast(t("Saved", "محفوظ ہو گیا"))
            }
        }
        sticky.addView(row(tools, saveBtn), lp().apply { topMargin = dp(8) })
        recomputeManual()
    }

    private fun recomputeManual() {
        val view = manView ?: return
        val u = Units.lengthUnits[lenUnit]
        val v = inputs.map { (it.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0) * u.ft } // feet
        fun lab(ft: Double) = f1(ft / u.ft) + " " + t(u.en.lowercase(), u.ur)
        var sqft = 0.0
        var note = ""
        var pts: List<P> = emptyList()
        var labels: List<String> = emptyList()
        when (shape) {
            0 -> if (v[0] > 0 && v[1] > 0) {
                sqft = v[0] * v[1]
                pts = listOf(P(0.0, 0.0), P(v[0], 0.0), P(v[0], v[1]), P(0.0, v[1]))
                labels = listOf(lab(v[0]), lab(v[1]), lab(v[0]), lab(v[1]))
            }
            1 -> {
                val (a, b, c, d) = v.take(4); val diag = v[4]
                if (a > 0 && b > 0 && c > 0 && d > 0) {
                    if (diag > 0) {
                        val t1 = Geo.heron(a, b, diag); val t2 = Geo.heron(d, c, diag)
                        if (t1 == null || t2 == null) {
                            note = t("⚠ These sides and diagonal can't form a field. Please re-check.",
                                "⚠ یہ اطراف اور وتر آپس میں نہیں ملتے۔ دوبارہ ناپ کر دیکھیں۔")
                        } else {
                            sqft = t1 + t2
                            pts = listOf(P(0.0, 0.0), Geo.triPoint(diag, a, b, true), P(diag, 0.0), Geo.triPoint(diag, d, c, false))
                            labels = listOf(lab(a), lab(b), lab(c), lab(d))
                            note = t("✓ Exact area (two triangles, Heron's formula)", "✓ درست رقبہ (دو تکون، ہیرون کا فارمولا)")
                        }
                    } else {
                        sqft = ((a + c) / 2) * ((b + d) / 2)
                        val l = (a + c) / 2; val w = (b + d) / 2
                        pts = listOf(P(0.0, 0.0), P(l, 0.0), P(l, w), P(0.0, w))
                        labels = listOf(lab(a), lab(b), lab(c), lab(d))
                        note = t("≈ Patwari method. A bit MORE than true area if the field isn't square. Add the diagonal for exact.",
                            "≈ پٹواری طریقہ۔ ٹیڑھے کھیت میں رقبہ کچھ زیادہ آتا ہے۔ درست رقبے کے لیے وتر لکھیں۔")
                    }
                }
            }
            else -> {
                val (a, b, c) = v.take(3)
                if (a > 0 && b > 0 && c > 0) {
                    val ar = Geo.heron(a, b, c)
                    if (ar == null) note = t("⚠ These 3 sides can't make a triangle.", "⚠ یہ تین اطراف تکون نہیں بنا سکتیں۔")
                    else {
                        sqft = ar
                        pts = listOf(P(0.0, 0.0), P(a, 0.0), Geo.triPoint(a, c, b, true))
                        labels = listOf(lab(a), lab(b), lab(c))
                    }
                }
            }
        }
        manSqft = sqft
        view.set(pts, labels, null, pts.size >= 3, t("Enter the sides above", "اوپر اطراف لکھیں"), unitName = t("ft", "فٹ"))
        if (sqft > 0) {
            manArea?.text = Units.breakdown(sqft, mode, urdu)
            manSub?.text = (if (note.isNotEmpty()) "$note\n" else "") +
                    "≈ ${Units.num(sqft / Units.ACRE)} ${t("acre", "ایکڑ")}  •  ${Units.num(sqft)} ${t("sq ft", "مربع فٹ")}"
            manTable?.let { fillTable(it, sqft) }
        } else {
            manArea?.text = t("Area: —", "رقبہ: —")
            manSub?.text = note.ifEmpty { t("Type the sides. The area updates as you type.", "اطراف لکھیں، رقبہ ساتھ ساتھ نظر آئے گا۔") }
            manTable?.removeAllViews()
        }
    }

    // =====================================================================
    // TAB 3: Saved fields
    // =====================================================================
    private fun buildSaved(col: LinearLayout) {
        val list = store.all()
        if (list.isEmpty()) {
            val c = card().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(32), dp(20), dp(32)) }
            c.addView(ImageView(this).apply { setImageResource(R.drawable.ic_folder); setColorFilter(Ui.LINE) },
                LinearLayout.LayoutParams(dp(64), dp(64)))
            c.addView(text(t("No saved fields yet", "ابھی کوئی زمین محفوظ نہیں"), 17f, Ui.TEXT, true).apply { gravity = Gravity.CENTER })
            c.addView(text(t("Measure a field with GPS or by hand, then tap Save.",
                "GPS یا ہاتھ سے زمین ناپیں، پھر محفوظ کریں دبائیں۔"), 14f, Ui.MUTED).apply { gravity = Gravity.CENTER })
            col.addView(c); return
        }
        // Summary header
        val total = list.sumOf { it.sqft }
        val head = card().apply { background = rounded(Ui.LIGHT, 18) }
        head.addView(text(t("${list.size} fields • total", "${list.size} زمینیں • کل رقبہ"), 13f, Ui.MUTED))
        head.addView(text(Units.breakdown(total, mode, urdu), 19f, Ui.GREEN, true))
        col.addView(head)

        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
        list.withIndex().reversed().forEach { (idx, f) ->
            val c = card()
            val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            title.addView(text(f.name, 18f, Ui.TEXT, true), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            title.addView(text(if (f.method == "GPS") "GPS" else t("By hand", "ہاتھ سے"), 11f, Ui.GREEN2, true).apply {
                background = rounded(Ui.LIGHT, 10); setPadding(dp(8), dp(3), dp(8), dp(3))
            })
            c.addView(title)
            c.addView(text(fmt.format(Date(f.date)), 12f, Ui.MUTED))
            if (f.points.size >= 3) {
                val mini = FieldView(this)
                mini.set(Geo.project(f.points, Geo.centroid(f.points)), emptyList(), null, true, "")
                c.addView(mini, lp(h = dp(110)).apply { topMargin = dp(8) })
            }
            c.addView(text(Units.breakdown(f.sqft, mode, urdu), 20f, Ui.GREEN, true).apply { setPadding(0, dp(6), 0, dp(2)) })
            c.addView(text("≈ ${Units.num(f.sqft / Units.ACRE)} ${t("acre", "ایکڑ")}  •  ${Units.num(f.sqft)} ${t("sq ft", "مربع فٹ")}", 13f, Ui.MUTED))
            val details = button(t("Open", "کھولیں"), true, small = true) { showTools(f) }
            val share = button(t("Share", "شیئر"), false, small = true) { share(f) }
            val del = button(t("Delete", "مٹائیں"), false, small = true) {
                AlertDialog.Builder(this).setMessage(t("Delete \"${f.name}\"?", "\"${f.name}\" مٹا دیں؟"))
                    .setPositiveButton(t("Delete", "مٹائیں")) { _, _ -> store.delete(idx); showTab(2) }
                    .setNegativeButton(t("Cancel", "رہنے دیں"), null).show()
            }
            c.addView(row(details, share, del), lp().apply { topMargin = dp(10) })
            col.addView(c)
        }
    }

    // =====================================================================
    // Field tools: drawing, price (lakh/crore), divide among heirs, all units
    // =====================================================================
    private fun section(parent: LinearLayout, title: String) =
        parent.addView(text(title, 15f, Ui.TEXT, true).apply { setPadding(0, dp(18), 0, dp(6)) })

    private fun watch(e: EditText, f: () -> Unit) = e.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = f()
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    })

    private fun field(hintText: String, numeric: Boolean) = EditText(this).apply {
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
        textSize = 17f; hint = hintText; textDirection = View.TEXT_DIRECTION_LTR; setTextColor(Ui.TEXT)
        background = rounded(Ui.BG, 12, Ui.LINE); setPadding(dp(14), dp(11), dp(14), dp(11))
    }

    private fun showTools(f: FieldStore.Field, saved: Boolean = true) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), dp(10)); layoutDirection = dirFlag
        }
        if (f.points.size >= 3) {
            val v = FieldView(this)
            val pts = Geo.project(f.points, Geo.centroid(f.points))
            v.set(pts, pts.indices.map { karam(Geo.dist(pts[it], pts[(it + 1) % pts.size])) }, null, true, "",
                unitName = t("m", "میٹر"))
            box.addView(v, lp(h = dp(210)).apply { bottomMargin = dp(8) })
        }
        box.addView(text(Units.breakdown(f.sqft, mode, urdu), 22f, Ui.GREEN, true))
        box.addView(text("≈ ${Units.num(f.sqft / Units.ACRE)} ${t("acre", "ایکڑ")}  •  ${Units.num(f.sqft)} ${t("sq ft", "مربع فٹ")}", 13f, Ui.MUTED))

        // ---- Price ----
        section(box, t("₨  Land price", "₨  زمین کی قیمت"))
        val rate = field(t("Rate, e.g. 500000", "ریٹ، مثلاً 500000"), true)
        box.addView(rate, lp())
        val perUnits = listOf(
            Triple("per marla", "فی مرلہ", Units.marla(mode)),
            Triple("per kanal", "فی کنال", Units.kanal(mode)),
            Triple("per acre", "فی ایکڑ", Units.ACRE))
        var per = prefs.getInt("rateUnit", 1)
        val perRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        box.addView(perRow, lp().apply { topMargin = dp(8) })
        val priceOut = text("", 18f, Ui.GREEN, true).apply { setPadding(0, dp(8), 0, 0) }
        box.addView(priceOut)
        val updPrice = {
            val r = rate.text.toString().replace(",", "").toDoubleOrNull()
            priceOut.text = if (r == null || r <= 0) t("Enter the rate to see the total price", "کل قیمت دیکھنے کے لیے ریٹ لکھیں")
                            else Units.pkr(f.sqft / perUnits[per].third * r, urdu)
        }
        fun drawPer() {
            perRow.removeAllViews()
            perUnits.forEachIndexed { i, (en, ur, _) ->
                perRow.addView(chip(t(en, ur), i == per) {
                    per = i; prefs.edit().putInt("rateUnit", i).apply(); drawPer(); updPrice()
                }, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(6) })
            }
        }
        drawPer(); watch(rate) { updPrice() }; updPrice()

        // ---- Divide (taqseem) ----
        section(box, t("⚖  Divide among heirs / partners", "⚖  ورثاء / حصہ داروں میں تقسیم"))
        box.addView(text(t("Shares: 3 = three equal parts, or 2,2,1,1 = two sons and two daughters",
            "حصے: 3 = تین برابر حصے، یا 2,2,1,1 = دو بیٹے اور دو بیٹیاں"), 12.5f, Ui.MUTED).apply { setPadding(0, 0, 0, dp(6)) })
        val shares = field("2,2,1,1", false)
        box.addView(shares, lp())
        val divOut = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, 0) }
        box.addView(divOut)
        watch(shares) {
            divOut.removeAllViews()
            var w = shares.text.toString().split(',', '،', ' ', '+', ':', '/')
                .mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0 }
            if (w.size == 1 && w[0] == Math.floor(w[0]) && w[0] in 2.0..100.0) w = List(w[0].toInt()) { 1.0 }
            if (w.size >= 2) {
                val sum = w.sum()
                w.forEachIndexed { i, wi ->
                    val r = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(8))
                        if (i % 2 == 0) background = rounded(Ui.LIGHT, 8)
                    }
                    r.addView(text(t("Share ${i + 1}", "حصہ ${i + 1}") + "  (${Units.num(wi)}/${Units.num(sum)})", 13.5f, Ui.MUTED),
                        lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    r.addView(text(Units.breakdown(f.sqft * wi / sum, mode, urdu), 14.5f, Ui.GREEN, true))
                    divOut.addView(r)
                }
            }
        }

        // ---- All units ----
        section(box, t("All units", "تمام اکائیاں"))
        val tbl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fillTable(tbl, f.sqft); box.addView(tbl)

        val b = AlertDialog.Builder(this).setTitle(f.name).setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(t("Close", "بند کریں"), null)
        if (saved) b.setNeutralButton(t("Share", "شیئر")) { _, _ -> share(f) }
        b.show()
    }

    private fun share(f: FieldStore.Field) {
        val sb = StringBuilder()
        sb.append("🌾 ${f.name}\n")
        sb.append("رقبہ: ${Units.breakdown(f.sqft, mode, true)}\n")
        sb.append("Area: ${Units.breakdown(f.sqft, mode, false)}\n")
        sb.append("= ${Units.num(f.sqft / Units.ACRE)} acre = ${Units.num(f.sqft / Units.kanal(mode))} kanal = " +
                "${Units.num(f.sqft / Units.marla(mode))} marla = ${Units.num(f.sqft)} sq ft\n")
        sb.append("(1 marla = ${if (mode == MarlaMode.REVENUE) "272.25" else "225"} sq ft)\n")
        if (f.points.isNotEmpty()) {
            val c = Geo.centroid(f.points)
            sb.append("📍 https://maps.google.com/?q=%.6f,%.6f\n".format(Locale.US, c.lat, c.lon))
        }
        sb.append("— Zameen Naap")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString())
        }, t("Share", "شیئر کریں")))
    }

    // =====================================================================
    // Shared helpers
    // =====================================================================
    private fun fillTable(box: LinearLayout, sqft: Double) {
        box.removeAllViews()
        Units.table(mode).forEachIndexed { i, u ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(9), dp(8), dp(9))
                if (i % 2 == 0) background = rounded(Ui.LIGHT, 8)
            }
            r.addView(text(t(u.en, u.ur), 14f, Ui.TEXT), lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(text(Units.num(sqft / u.sqft), 15f, Ui.GREEN, true).apply { textDirection = View.TEXT_DIRECTION_LTR })
            box.addView(r)
        }
    }

    private fun askName(onOk: (String) -> Unit) {
        val e = EditText(this).apply {
            hint = t("e.g. Wheat field near canal", "مثلاً نہر والا کھیت")
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val wrap = FrameLayout(this).apply { setPadding(dp(16), dp(8), dp(16), 0); addView(e) }
        AlertDialog.Builder(this).setTitle(t("Field name", "زمین کا نام")).setView(wrap)
            .setPositiveButton(t("Save", "محفوظ کریں")) { _, _ ->
                onOk(e.text.toString().trim().ifEmpty { t("Field", "زمین") + " " + (store.all().size + 1) })
            }
            .setNegativeButton(t("Cancel", "رہنے دیں"), null).show()
    }

    private fun marlaDialog() {
        val opts = arrayOf(
            t("272.25 sq ft: Patwari / revenue record (villages, farmland)",
              "272.25 مربع فٹ: پٹواری / محکمہ مال (دیہات، زرعی زمین)"),
            t("225 sq ft: housing societies (DHA, LDA, towns)",
              "225 مربع فٹ: ہاؤسنگ سوسائٹی (DHA، LDA، شہر)"))
        AlertDialog.Builder(this).setTitle(t("Size of 1 marla", "ایک مرلہ کتنا؟"))
            .setSingleChoiceItems(opts, if (mode == MarlaMode.REVENUE) 0 else 1) { d, w ->
                mode = if (w == 0) MarlaMode.REVENUE else MarlaMode.SOCIETY
                prefs.edit().putBoolean("society", w == 1).apply()
                d.dismiss(); saveManualVals(); build()
            }.show()
    }

    private fun showIntro() {
        AlertDialog.Builder(this)
            .setTitle(t("Welcome to Zameen Naap", "زمین ناپ میں خوش آمدید"))
            .setMessage(t(
                "Measure any field without internet.\n\n" +
                "📍 GPS walk: stand on each corner and tap \"Mark corner\".\n\n" +
                "📏 By hand: enter sides in karam or feet, the patwari way.\n\n" +
                "📁 Saved: keep fields and share them on WhatsApp.\n\n" +
                "Results in murabba, acre, kanal, marla and sarsai.",
                "بغیر انٹرنیٹ کے کوئی بھی زمین ناپیں۔\n\n" +
                "📍 GPS سے: ہر کونے پر کھڑے ہو کر \"کونا لگائیں\" دبائیں۔\n\n" +
                "📏 ہاتھ سے: پٹواری کی طرح کرم یا فٹ میں اطراف لکھیں۔\n\n" +
                "📁 محفوظ: زمینیں محفوظ کریں اور واٹس ایپ پر بھیجیں۔\n\n" +
                "نتیجہ مربع، ایکڑ، کنال، مرلہ اور سرسائی میں۔"))
            .setPositiveButton(t("Start", "شروع کریں")) { _, _ -> prefs.edit().putBoolean("seenIntro", true).apply() }
            .setCancelable(false)
            .show()
    }
}
