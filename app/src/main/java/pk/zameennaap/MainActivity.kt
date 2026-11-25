package pk.zameennaap

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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

    // ---- GPS mode state ----
    private val corners = mutableListOf<Corner>()
    private var lastFix: Location? = null
    private var capturing = false
    private val captureBuf = mutableListOf<Location>()
    private var autoWalk = false
    private var gpsView: FieldView? = null
    private var gpsStatus: TextView? = null
    private var gpsArea: TextView? = null
    private var gpsSub: TextView? = null
    private var captureBtn: TextView? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        store = FieldStore(this)
        urdu = prefs.getBoolean("urdu", true)
        mode = if (prefs.getBoolean("society", false)) MarlaMode.SOCIETY else MarlaMode.REVENUE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Ui.GREEN
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        build()
        if (!hasGps()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7)
    }

    private fun hasGps() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        startGps()
        if (tab == 0) refreshGps()
    }

    override fun onResume() { super.onResume(); startGps() }
    override fun onPause() { super.onPause(); lm?.removeUpdates(this) }

    private fun startGps() {
        if (!hasGps()) return
        try {
            lm?.removeUpdates(this)
            // GPS_PROVIDER = satellites only. Works with no internet and no SIM.
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            lastFix = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.takeIf { System.currentTimeMillis() - it.time < 30_000 }
        } catch (e: SecurityException) { }
    }

    // =====================================================================
    // Layout: top bar, content, bottom navigation
    // =====================================================================
    private fun build() {
        navItems.clear()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            layoutDirection = if (urdu) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.GREEN)
            setPadding(dp(18), dp(14), dp(12), dp(14))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text(t("Zameen Naap", "زمین ناپ"), 22f, Color.WHITE, true))
        titles.addView(text(t("Land measurement • works offline", "زمین کی پیمائش • بغیر انٹرنیٹ"), 12f, Color.rgb(190, 225, 205)))
        top.addView(titles, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        fun topPill(s: String, click: () -> Unit) = TextView(this).apply {
            text = s; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(Color.argb(45, 255, 255, 255), 16)
            setOnClickListener { click() }
        }
        top.addView(topPill(if (urdu) "English" else "اردو") {
            urdu = !urdu; prefs.edit().putBoolean("urdu", urdu).apply()
            saveManualVals(); build()
        })
        top.addView(topPill(t("Marla ", "مرلہ ") + (if (mode == MarlaMode.REVENUE) "272" else "225")) { marlaDialog() },
            lp(LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        root.addView(top)

        content = FrameLayout(this)
        root.addView(content, lp(h = 0, weight = 1f))

        // Bottom navigation
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
            setPadding(0, dp(6), 0, dp(8))
        }
        val tabs = listOf(
            Triple(R.drawable.ic_gps, "GPS walk", "GPS سے ناپیں"),
            Triple(R.drawable.ic_ruler, "By hand", "ہاتھ سے (کرم)"),
            Triple(R.drawable.ic_folder, "Saved", "محفوظ زمینیں")
        )
        tabs.forEachIndexed { i, (icon, en, ur) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setOnClickListener { saveManualVals(); showTab(i) }
            }
            val iv = ImageView(this).apply { setImageResource(icon) }
            val tv = text(t(en, ur), 12f).apply { gravity = Gravity.CENTER }
            item.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)))
            item.addView(tv)
            navItems.add(iv to tv)
            nav.addView(item, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(nav)
        setContentView(root)
        showTab(tab)
    }

    private fun showTab(i: Int) {
        tab = i
        navItems.forEachIndexed { j, (iv, tv) ->
            val c = if (j == i) Ui.GREEN2 else Ui.MUTED
            iv.setColorFilter(c); tv.setTextColor(c)
            tv.setTypeface(null, if (j == i) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        gpsView = null; manView = null
        content.removeAllViews()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }
        when (i) {
            0 -> buildGps(col)
            1 -> buildManual(col)
            else -> buildSaved(col)
        }
        content.addView(ScrollView(this).apply { addView(col); isFillViewport = true })
    }

    // =====================================================================
    // TAB 1: GPS walk-around
    // =====================================================================
    private fun buildGps(col: LinearLayout) {
        val status = card().apply { setPadding(dp(16), dp(10), dp(16), dp(10)) }
        gpsStatus = text("", 14f, Ui.MUTED, true)
        status.addView(gpsStatus)
        col.addView(status)

        val mapCard = card().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        gpsView = FieldView(this)
        mapCard.addView(gpsView, lp(h = dp(300)))
        col.addView(mapCard)

        val res = card()
        res.addView(text(t("Area", "رقبہ"), 13f, Ui.MUTED))
        gpsArea = text("", 24f, Ui.GREEN, true)
        gpsSub = text("", 13f, Ui.MUTED)
        res.addView(gpsArea); res.addView(gpsSub)
        col.addView(res)

        captureBtn = button(t("＋  Mark corner here", "＋  یہاں کونا لگائیں")) { startCapture() }
        col.addView(captureBtn, lp().apply { bottomMargin = dp(8) })

        val undo = button(t("Undo", "واپس"), false) {
            if (corners.isNotEmpty()) corners.removeAt(corners.size - 1); refreshGps()
        }
        val auto = button(if (autoWalk) t("Auto: ON", "خودکار: آن") else t("Auto walk", "چلتے ہوئے"), !autoWalk) {}
        auto.setOnClickListener {
            autoWalk = !autoWalk
            toast(if (autoWalk) t("Auto mode: walk along the boundary, a point is added every 3 m",
                "خودکار: حد کے ساتھ چلیں، ہر 3 میٹر پر نقطہ لگے گا") else t("Auto mode off", "خودکار بند"))
            showTab(0)
        }
        val clear = button(t("Clear", "صاف کریں"), false) {
            if (corners.isEmpty()) return@button
            AlertDialog.Builder(this).setMessage(t("Remove all corners?", "سارے کونے مٹا دیں؟"))
                .setPositiveButton(t("Yes", "ہاں")) { _, _ -> corners.clear(); refreshGps() }
                .setNegativeButton(t("No", "نہیں"), null).show()
        }
        col.addView(row(undo, auto, clear), lp().apply { bottomMargin = dp(8) })

        col.addView(button(t("✓  Save this field", "✓  زمین محفوظ کریں")) {
            if (corners.size < 3) { toast(t("Mark at least 3 corners first", "پہلے کم از کم 3 کونے لگائیں")); return@button }
            val sqft = gpsSqft()
            askName { name ->
                store.add(FieldStore.Field(name, System.currentTimeMillis(), sqft, "GPS",
                    corners.map { LatLon(it.lat, it.lon) }))
                corners.clear(); refreshGps()
                toast(t("Saved", "محفوظ ہو گیا"))
            }
        }, lp().apply { bottomMargin = dp(12) })

        val tips = card()
        tips.addView(text(t("How to measure", "کیسے ناپیں"), 15f, Ui.TEXT, true))
        tips.addView(text(t(
            "1. Go to the field in open sky (not under trees).\n" +
            "2. Wait until accuracy is ±5 m or better (green).\n" +
            "3. Stand exactly on corner 1 and press \"Mark corner\". Stay still for 8 seconds.\n" +
            "4. Walk to the next corner IN ORDER around the field and repeat.\n" +
            "5. For curved edges use \"Auto walk\" and walk along the boundary.\n" +
            "Tip: bigger fields give more accurate results. For small plots use the \"By hand\" tab.",
            "1۔ کھیت میں کھلے آسمان تلے جائیں (درختوں کے نیچے نہیں)۔\n" +
            "2۔ درستگی ±5 میٹر یا بہتر (سبز) ہونے تک انتظار کریں۔\n" +
            "3۔ پہلے کونے پر بالکل کھڑے ہو کر \"کونا لگائیں\" دبائیں۔ 8 سیکنڈ ساکت رہیں۔\n" +
            "4۔ کھیت کے گرد ترتیب سے اگلے کونے پر جائیں اور یہی کریں۔\n" +
            "5۔ ٹیڑھی حد کے لیے \"چلتے ہوئے\" آن کر کے حد کے ساتھ چلیں۔\n" +
            "مشورہ: بڑے کھیت میں نتیجہ زیادہ درست آتا ہے۔ چھوٹے پلاٹ کے لیے \"ہاتھ سے\" والا حصہ استعمال کریں۔"),
            14f, Ui.MUTED))
        col.addView(tips)
        refreshGps()
    }

    private fun projectedCorners(): Pair<List<P>, P?> {
        val ll = corners.map { LatLon(it.lat, it.lon) }
        val fix = lastFix
        val cur = fix?.let { LatLon(it.latitude, it.longitude) }
        val ref = if (ll.isNotEmpty()) Geo.centroid(ll) else cur ?: return emptyList<P>() to null
        val pts = Geo.project(ll, ref)
        return pts to cur?.let { Geo.project(listOf(it), ref)[0] }
    }

    private fun gpsSqft() = Geo.area(projectedCorners().first) * Units.SQM_TO_SQFT

    private fun refreshGps() {
        val status = gpsStatus ?: return
        val fix = lastFix
        val gpsOn = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        when {
            !hasGps() -> { status.text = t("Location permission needed. Tap here.", "لوکیشن کی اجازت چاہیے۔ یہاں دبائیں۔"); status.setTextColor(Ui.RED)
                status.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7) } }
            !gpsOn -> { status.text = t("● GPS is off. Tap to turn on.", "● GPS بند ہے۔ آن کرنے کے لیے دبائیں۔"); status.setTextColor(Ui.RED)
                status.setOnClickListener { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
            fix == null -> { status.text = t("● Searching satellites… stand in open sky", "● سیٹلائٹ تلاش ہو رہے ہیں… کھلی جگہ پر کھڑے ہوں"); status.setTextColor(Ui.AMBER) }
            else -> {
                val acc = fix.accuracy
                val q = when { acc <= 5 -> t("Excellent", "بہترین"); acc <= 10 -> t("OK", "ٹھیک"); else -> t("Weak, wait", "کمزور، انتظار کریں") }
                status.text = t("● GPS accuracy ±%.0f m  •  %s", "● GPS درستگی ±%.0f میٹر  •  %s").format(Locale.US, acc, q) +
                        if (capturing) t("  •  recording corner…", "  •  کونا محفوظ ہو رہا ہے…") else ""
                status.setTextColor(when { acc <= 5 -> Ui.GREEN2; acc <= 10 -> Ui.AMBER; else -> Ui.RED })
            }
        }

        val (pts, cur) = projectedCorners()
        val karam = Units.lengthUnits[0].ft
        val labels = pts.indices.map { i ->
            val m = Geo.dist(pts[i], pts[(i + 1) % pts.size])
            f1(m * 3.2808399 / karam) + t(" karam", " کرم")
        }
        gpsView?.set(pts, labels, cur, pts.size >= 3,
            t("Stand on the first corner", "پہلے کونے پر کھڑے ہوں"))

        if (pts.size >= 3) {
            val sqft = Geo.area(pts) * Units.SQM_TO_SQFT
            val sig = Geo.areaSigma(pts, corners.map { it.sigmaM }) * Units.SQM_TO_SQFT
            val perFt = Geo.perimeter(pts) * 3.2808399
            gpsArea?.text = Units.breakdown(sqft, mode, urdu)
            var sub = t("≈ %s acre  •  ± %s marla  •  boundary %s karam (%s ft)  •  %d corners",
                "≈ %s ایکڑ  •  ± %s مرلہ  •  حد %s کرم (%s فٹ)  •  %d کونے").format(
                Units.num(sqft / Units.ACRE), f1(sig / Units.marla(mode)), Units.num(perFt / karam), Units.num(perFt), pts.size)
            if (Geo.selfIntersects(pts)) sub += t("\n⚠ Lines cross: corners were marked out of order. Undo and redo.",
                "\n⚠ لکیریں ایک دوسرے کو کاٹ رہی ہیں: کونے ترتیب سے نہیں لگے۔ واپس کر کے دوبارہ لگائیں۔")
            gpsSub?.text = sub
        } else {
            gpsArea?.text = t("Mark %d more corner(s)", "مزید %d کونے لگائیں").format(Locale.US, 3 - pts.size)
            gpsSub?.text = t("Corners marked: %d", "لگائے گئے کونے: %d").format(Locale.US, pts.size)
        }
    }

    private fun startCapture() {
        if (capturing) return
        val f = lastFix
        if (f == null || f.accuracy > 30) {
            toast(t("Waiting for GPS. Stand in open sky for a minute.", "GPS کا انتظار ہے۔ ایک منٹ کھلی جگہ پر کھڑے رہیں۔")); return
        }
        capturing = true
        captureBuf.clear(); captureBuf.add(f)
        handler.post(object : Runnable {
            var left = 8
            override fun run() {
                if (left <= 0) { finishCapture(); return }
                captureBtn?.text = t("Stay still… %d", "ساکت رہیں… %d").format(Locale.US, left)
                left--
                handler.postDelayed(this, 1000)
            }
        })
        refreshGps()
    }

    private fun finishCapture() {
        capturing = false
        if (captureBuf.isNotEmpty()) {
            corners.add(Geo.averageFixes(captureBuf.map { Triple(it.latitude, it.longitude, it.accuracy.toDouble()) }))
            vibrate()
        }
        captureBtn?.text = t("＋  Mark corner here", "＋  یہاں کونا لگائیں")
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
            if (last == null || d[0] >= 3f) corners.add(Corner(loc.latitude, loc.longitude, loc.accuracy / 1.5))
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

    private fun buildManual(col: LinearLayout) {
        inputs.clear()
        val shapeCard = card()
        shapeCard.addView(text(t("Field shape", "کھیت کی شکل"), 13f, Ui.MUTED))
        val shapes = listOf(t("Rectangle", "مستطیل"), t("4 sides", "چار اطراف"), t("Triangle", "تکون"))
        shapeCard.addView(row(*shapes.mapIndexed { i, s ->
            chip(s, i == shape) { if (shape != i) { shape = i; manualVals.clear(); showTab(1) } }
        }.toTypedArray()), lp().apply { topMargin = dp(8) })
        shapeCard.addView(text(t("Measuring in", "پیمائش کی اکائی"), 13f, Ui.MUTED).apply { setPadding(0, dp(12), 0, 0) })
        shapeCard.addView(row(*Units.lengthUnits.mapIndexed { i, u ->
            chip(t(u.en, u.ur), i == lenUnit) { saveManualVals(); lenUnit = i; showTab(1) }
        }.toTypedArray(), gapDp = 6), lp().apply { topMargin = dp(8) })
        col.addView(shapeCard)

        val u = Units.lengthUnits[lenUnit]
        val un = t(u.en.lowercase(), u.ur)
        val fields: List<String> = when (shape) {
            0 -> listOf(t("Length", "لمبائی"), t("Width", "چوڑائی"))
            1 -> listOf(t("Side 1 (corner 1 → 2)", "پہلی طرف (کونا 1 سے 2)"),
                        t("Side 2 (corner 2 → 3)", "دوسری طرف (کونا 2 سے 3)"),
                        t("Side 3 (corner 3 → 4)", "تیسری طرف (کونا 3 سے 4)"),
                        t("Side 4 (corner 4 → 1)", "چوتھی طرف (کونا 4 سے 1)"),
                        t("Diagonal (corner 1 → 3), for exact area", "وتر (کونا 1 سے 3) درست رقبے کے لیے"))
            else -> listOf(t("Side 1", "پہلی طرف"), t("Side 2", "دوسری طرف"), t("Side 3", "تیسری طرف"))
        }
        val inCard = card()
        fields.forEachIndexed { i, label ->
            inCard.addView(text("$label ($un)", 13f, Ui.MUTED).apply { setPadding(0, if (i == 0) 0 else dp(10), 0, dp(4)) })
            val e = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 18f; setTextColor(Ui.TEXT); hint = "0"
                background = rounded(Ui.BG, 12, Ui.LINE)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                textDirection = View.TEXT_DIRECTION_LTR
                setText(manualVals.getOrNull(i) ?: "")
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) = recomputeManual()
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }
            inputs.add(e)
            inCard.addView(e, lp())
        }
        col.addView(inCard)

        val mapCard = card().apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        manView = FieldView(this)
        mapCard.addView(manView, lp(h = dp(230)))
        col.addView(mapCard)

        val res = card()
        res.addView(text(t("Area", "رقبہ"), 13f, Ui.MUTED))
        manArea = text("", 24f, Ui.GREEN, true); manSub = text("", 13f, Ui.MUTED)
        res.addView(manArea); res.addView(manSub)
        manTable = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        res.addView(manTable, lp().apply { topMargin = dp(10) })
        col.addView(res)

        col.addView(button(t("✓  Save this field", "✓  زمین محفوظ کریں")) {
            if (manSqft <= 0) { toast(t("Enter the sides first", "پہلے اطراف لکھیں")); return@button }
            val sq = manSqft
            askName { name ->
                store.add(FieldStore.Field(name, System.currentTimeMillis(), sq, "Manual", emptyList()))
                toast(t("Saved", "محفوظ ہو گیا"))
            }
        })
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
                            note = t("✓ Exact (two triangles, Heron's formula)", "✓ درست حساب (دو تکون، ہیرون کا فارمولا)")
                        }
                    } else {
                        // Traditional patwari method: average opposite sides
                        sqft = ((a + c) / 2) * ((b + d) / 2)
                        val l = (a + c) / 2; val w = (b + d) / 2
                        pts = listOf(P(0.0, 0.0), P(l, 0.0), P(l, w), P(0.0, w))
                        labels = listOf(lab(a), lab(b), lab(c), lab(d))
                        note = t("≈ Patwari method (average of opposite sides). It gives a bit MORE than the true area if the field is not square. Add the diagonal for the exact area.",
                            "≈ پٹواری طریقہ (آمنے سامنے اطراف کی اوسط)۔ اگر کھیت ٹیڑھا ہو تو رقبہ کچھ زیادہ آتا ہے۔ درست رقبے کے لیے وتر بھی لکھیں۔")
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
        view.set(pts, labels, null, pts.size >= 3, t("Enter the sides above", "اوپر اطراف لکھیں"))
        if (sqft > 0) {
            manArea?.text = Units.breakdown(sqft, mode, urdu)
            manSub?.text = note
            manTable?.let { fillTable(it, sqft) }
        } else {
            manArea?.text = "—"
            manSub?.text = note
            manTable?.removeAllViews()
        }
    }

    // =====================================================================
    // TAB 3: Saved fields
    // =====================================================================
    private fun buildSaved(col: LinearLayout) {
        val list = store.all()
        if (list.isEmpty()) {
            val c = card()
            c.addView(text(t("No saved fields yet", "ابھی کوئی زمین محفوظ نہیں"), 17f, Ui.TEXT, true))
            c.addView(text(t("Measure a field with GPS or by hand, then tap Save.",
                "GPS یا ہاتھ سے زمین ناپیں، پھر محفوظ کریں دبائیں۔"), 14f, Ui.MUTED))
            col.addView(c); return
        }
        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
        list.withIndex().reversed().forEach { (idx, f) ->
            val c = card()
            c.addView(text(f.name, 18f, Ui.TEXT, true))
            c.addView(text(fmt.format(Date(f.date)) + "  •  " +
                    (if (f.method == "GPS") "GPS" else t("By hand", "ہاتھ سے")), 12f, Ui.MUTED))
            c.addView(text(Units.breakdown(f.sqft, mode, urdu), 20f, Ui.GREEN, true).apply { setPadding(0, dp(6), 0, dp(2)) })
            c.addView(text("≈ ${Units.num(f.sqft / Units.ACRE)} ${t("acre", "ایکڑ")}  •  ${Units.num(f.sqft)} ${t("sq ft", "مربع فٹ")}", 13f, Ui.MUTED))
            val details = button(t("All units", "تمام اکائیاں"), false) {
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
                    layoutDirection = if (urdu) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
                fillTable(box, f.sqft)
                AlertDialog.Builder(this).setTitle(f.name).setView(box).setPositiveButton("OK", null).show()
            }
            val share = button(t("Share", "شیئر"), true) { share(f) }
            val del = button(t("Delete", "مٹائیں"), false) {
                AlertDialog.Builder(this).setMessage(t("Delete \"${f.name}\"?", "\"${f.name}\" مٹا دیں؟"))
                    .setPositiveButton(t("Delete", "مٹائیں")) { _, _ -> store.delete(idx); showTab(2) }
                    .setNegativeButton(t("Cancel", "رہنے دیں"), null).show()
            }
            c.addView(row(details, share, del), lp().apply { topMargin = dp(10) })
            col.addView(c)
        }
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
                setPadding(dp(4), dp(8), dp(4), dp(8))
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
}
