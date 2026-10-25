package com.eone.autodraw

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import kotlin.math.*

class DrawService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var status: TextView
    private var bar: View? = null
    private var overlay: View? = null
    private val btns = ArrayList<View>()
    private var running = false
    private var plan: Plan? = null
    private var gi = 0
    private var ri = 0
    private var total = 1
    private var done = 0
    private var cell = 1f
    private var ox = 0f
    private var oy = 0f

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showBar()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { running = false }

    override fun onDestroy() {
        running = false
        if (::wm.isInitialized) {
            try { bar?.let { wm.removeView(it) } } catch (_: Exception) {}
            closeOverlay()
        }
        super.onDestroy()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun lp(full: Boolean): WindowManager.LayoutParams {
        val size = if (full) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (full) WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0)
        val p = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return p
    }

    private fun tv(t: String) = TextView(this).apply { text = t; textSize = 20f; setTextColor(Color.WHITE); setPadding(18, 10, 18, 10) }

    private fun showBar() {
        val b = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xCC000000.toInt()); gravity = Gravity.CENTER_VERTICAL }
        val l = lp(false); l.x = 20; l.y = 120
        val grip = tv("⠿")
        grip.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ix = 0; var iy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ix = l.x; iy = l.y }
                    MotionEvent.ACTION_MOVE -> { l.x = ix + (e.rawX - sx).toInt(); l.y = iy + (e.rawY - sy).toInt(); wm.updateViewLayout(b, l) }
                }
                return true
            }
        })
        val play = tv("▶"); play.setOnClickListener { startDraw() }
        val stop = tv("■"); stop.setOnClickListener { running = false; status.text = "หยุด" }
        val area = tv("⬚"); area.setOnClickListener { editArea() }
        val cal = tv("🎨"); cal.setOnClickListener { calibrate() }
        status = tv("พร้อม"); status.textSize = 12f
        val mini = tv("─")
        btns.clear(); btns.addAll(listOf(play, stop, area, cal, status))
        mini.setOnClickListener {
            val hide = btns[0].visibility == View.VISIBLE
            btns.forEach { it.visibility = if (hide) View.GONE else View.VISIBLE }
            mini.text = if (hide) "🖌" else "─"
        }
        b.addView(grip); btns.forEach { b.addView(it) }; b.addView(mini)
        wm.addView(b, l); bar = b
    }

    private fun show(v: View) { closeOverlay(); overlay = v; wm.addView(v, lp(true)) }
    private fun closeOverlay() { overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }; overlay = null }

    // ---------- area ----------
    private fun editArea() {
        val root = FrameLayout(this)
        val av = AreaView(this, Store.area)
        root.addView(av)
        val row = LinearLayout(this)
        val save = Button(this); save.text = "บันทึก"
        save.setOnClickListener {
            val loc = IntArray(2); av.getLocationOnScreen(loc)
            val r = av.rectF
            Store.area = Rect(r.left.toInt() + loc[0], r.top.toInt() + loc[1], r.right.toInt() + loc[0], r.bottom.toInt() + loc[1])
            closeOverlay(); toast("บันทึกพื้นที่แล้ว")
        }
        val cancel = Button(this); cancel.text = "ยกเลิก"; cancel.setOnClickListener { closeOverlay() }
        row.addView(save); row.addView(cancel)
        val rlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        rlp.bottomMargin = 160
        root.addView(row, rlp)
        show(root)
    }

    // ---------- calibrate ----------
    private fun calibrate() {
        when (Store.mode) {
            0 -> collect(listOf("แตะทุกช่องสีในจานสีของเกม ตามลำดับ แล้วกดเสร็จ"), true) { readPalette(it) }
            1 -> collect(listOf("1/4 แตะกึ่งกลางวงล้อสี", "2/4 แตะจุดสีแดงที่ขอบวงล้อ", "3/4 แตะบนสุดของแถบความสว่าง", "4/4 แตะล่างสุดของแถบความสว่าง"), false) { p ->
                Store.wheelC = p[0]; Store.wheelRed = p[1]; Store.barTop = p[2]; Store.barBot = p[3]; toast("บันทึกแล้ว")
            }
            else -> collect(listOf("1/2 แตะช่องกรอก Hex", "2/2 แตะปุ่มยืนยัน"), false) { p ->
                Store.hexField = p[0]; Store.hexOk = p[1]; toast("บันทึกแล้ว")
            }
        }
    }

    private fun collect(prompts: List<String>, unlimited: Boolean, onDone: (List<PointF>) -> Unit) {
        val pts = ArrayList<PointF>()
        val dots = ArrayList<PointF>()
        val msg = tv("")
        msg.setBackgroundColor(0xCC000000.toInt())
        fun refresh() {
            msg.text = if (unlimited) prompts[0] + " (แตะแล้ว ${pts.size})" else if (pts.size < prompts.size) prompts[pts.size] else "ครบแล้ว กดเสร็จ"
        }
        refresh()
        val cv = object : View(this) {
            val p = Paint().apply { color = 0xFFEC4899.toInt(); textSize = 40f }
            override fun onDraw(c: Canvas) {
                dots.forEachIndexed { i, d -> c.drawCircle(d.x, d.y, 18f, p); c.drawText("${i + 1}", d.x + 24, d.y - 10, p) }
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_UP && (unlimited || pts.size < prompts.size)) {
                    pts.add(PointF(e.rawX, e.rawY)); dots.add(PointF(e.x, e.y)); refresh(); invalidate()
                }
                return true
            }
        }
        val root = FrameLayout(this)
        root.addView(cv)
        root.addView(msg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        val row = LinearLayout(this)
        val ok = Button(this); ok.text = "เสร็จ"
        ok.setOnClickListener {
            if (!unlimited && pts.size < prompts.size) toast("แตะให้ครบก่อน")
            else { closeOverlay(); h.postDelayed({ onDone(pts) }, 350) }
        }
        val no = Button(this); no.text = "ยกเลิก"; no.setOnClickListener { closeOverlay() }
        row.addView(ok); row.addView(no)
        if (Store.mode == 1) {
            val dir = Button(this); dir.text = if (Store.ccw) "ทิศ: ทวนเข็ม" else "ทิศ: ตามเข็ม"
            dir.setOnClickListener { Store.ccw = !Store.ccw; dir.text = if (Store.ccw) "ทิศ: ทวนเข็ม" else "ทิศ: ตามเข็ม" }
            row.addView(dir)
        }
        val rlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        rlp.bottomMargin = 160
        root.addView(row, rlp)
        show(root)
    }

    private fun readPalette(pts: List<PointF>) {
        if (pts.isEmpty()) return
        if (Build.VERSION.SDK_INT < 30) { toast("โหมดพาเลทต้องใช้ Android 11 ขึ้นไป"); return }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val hb = r.hardwareBuffer
                val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hb.close()
                if (bmp == null) { toast("อ่านสีไม่สำเร็จ"); return }
                Store.palPts.clear(); Store.palCols.clear()
                for (p in pts) {
                    val x = p.x.toInt().coerceIn(0, bmp.width - 1); val y = p.y.toInt().coerceIn(0, bmp.height - 1)
                    Store.palPts.add(p); Store.palCols.add(bmp.getPixel(x, y) or (0xFF shl 24))
                }
                toast("อ่านสีแล้ว ${pts.size} สี")
            }
            override fun onFailure(code: Int) { toast("แคปหน้าจอไม่ได้ (code $code)") }
        })
    }

    // ---------- drawing ----------
    private fun ready() = when (Store.mode) {
        0 -> Store.palPts.isNotEmpty() && Store.palPts.size == Store.palCols.size
        1 -> Store.wheelC != null && Store.wheelRed != null && Store.barTop != null && Store.barBot != null
        else -> Store.hexField != null && Store.hexOk != null
    }

    private fun mapColor(c: Int): Int {
        if (Store.mode != 0 || Store.palCols.isEmpty()) return c
        var best = Store.palCols[0]; var bd = Long.MAX_VALUE
        for (p in Store.palCols) {
            val dr = Color.red(p) - Color.red(c); val dg = Color.green(p) - Color.green(c); val db = Color.blue(p) - Color.blue(c)
            val d = (dr * dr + dg * dg + db * db).toLong()
            if (d < bd) { bd = d; best = p }
        }
        return best
    }

    private fun startDraw() {
        if (running) return
        val bmp = Store.bitmap
        if (bmp == null) { toast("เปิดแอพ AutoDraw แล้วเลือกรูปก่อน"); return }
        if (!ready()) { toast("ยังไม่ได้ตั้งค่าสีของเกม (กด 🎨)"); return }
        val pl = Quantizer.build(bmp, Store.res, Store.colors) { c -> mapColor(c) }
        if (pl.groups.isEmpty()) { toast("ไม่มีอะไรให้วาด"); return }
        plan = pl
        val a = Store.area
        cell = min(a.width().toFloat() / pl.gw, a.height().toFloat() / pl.gh)
        ox = a.left + (a.width() - pl.gw * cell) / 2f
        oy = a.top + (a.height() - pl.gh * cell) / 2f
        gi = 0; ri = 0; done = 0; total = pl.groups.sumOf { it.runs.size }
        running = true
        selectColor(pl.groups[0].color) { drawBatch() }
    }

    private fun tap(p: PointF, next: () -> Unit) {
        val path = Path(); path.moveTo(p.x, p.y)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { h.postDelayed({ next() }, 180) }
            override fun onCancelled(d: GestureDescription?) { h.postDelayed({ next() }, 180) }
        }
        if (!dispatchGesture(g, cb, null)) h.postDelayed({ next() }, 180)
    }

    private fun selectColor(c: Int, next: () -> Unit) {
        when (Store.mode) {
            0 -> {
                val i = Store.palCols.indexOf(c)
                if (i >= 0) tap(Store.palPts[i], next) else next()
            }
            1 -> {
                val cc = Store.wheelC; val rp = Store.wheelRed; val t = Store.barTop; val bt = Store.barBot
                if (cc == null || rp == null || t == null || bt == null) { next(); return }
                val hsv = FloatArray(3); Color.colorToHSV(c, hsv)
                val rad = hypot(rp.x - cc.x, rp.y - cc.y)
                val a0 = atan2(rp.y - cc.y, rp.x - cc.x)
                val a = a0 + (if (Store.ccw) -1f else 1f) * (hsv[0] * PI.toFloat() / 180f)
                val p1 = PointF(cc.x + hsv[1] * rad * cos(a), cc.y + hsv[1] * rad * sin(a))
                val p2 = PointF(t.x + (bt.x - t.x) * (1f - hsv[2]), t.y + (bt.y - t.y) * (1f - hsv[2]))
                tap(p1) { tap(p2, next) }
            }
            else -> {
                val f = Store.hexField; val ok = Store.hexOk
                if (f == null || ok == null) { next(); return }
                tap(f) {
                    val n = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (n != null) {
                        val b = Bundle()
                        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, String.format("#%06x", c and 0xFFFFFF))
                        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
                    }
                    tap(ok, next)
                }
            }
        }
    }

    private fun finish() { running = false; status.text = "เสร็จ ✔" }

    private fun drawBatch() {
        val pl = plan ?: return
        if (!running) return
        if (gi >= pl.groups.size) { finish(); return }
        val g = pl.groups[gi]
        if (ri >= g.runs.size) {
            gi++; ri = 0
            if (gi >= pl.groups.size) { finish(); return }
            selectColor(pl.groups[gi].color) { drawBatch() }
            return
        }
        val gb = GestureDescription.Builder()
        var t = 0L; var n = 0
        val cap = min(10, GestureDescription.getMaxStrokeCount())
        while (n < cap && ri < g.runs.size) {
            val r = g.runs[ri]
            val x0 = ox + (r.x0 + 0.5f) * cell
            val x1 = ox + (r.x1 + 0.5f) * cell
            val y = oy + (r.y + 0.5f) * cell
            val path = Path(); path.moveTo(x0, y); path.lineTo(if (x1 > x0) x1 else x0 + 1f, y)
            val dur = (30 + (x1 - x0) * 0.5f).toLong().coerceIn(30L, 800L)
            gb.addStroke(GestureDescription.StrokeDescription(path, t, dur))
            t += dur + 20; ri++; n++; done++
        }
        status.text = "${100 * done / total}%"
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { h.postDelayed({ drawBatch() }, 30) }
            override fun onCancelled(d: GestureDescription?) { h.postDelayed({ drawBatch() }, 30) }
        }
        if (!dispatchGesture(gb.build(), cb, null)) { running = false; status.text = "ส่งท่าทางไม่ได้" }
    }
}

class AreaView(c: Context, a: Rect) : View(c) {
    val rectF = RectF(a)
    private val pf = Paint()
    private val fill = Paint()
    private val hp = Paint()
    private val hs = Paint()
    private val fx = floatArrayOf(0f, .5f, 1f, 0f, 1f, 0f, .5f, 1f)
    private val fy = floatArrayOf(0f, 0f, 0f, .5f, .5f, 1f, 1f, 1f)
    private var hit = -1
    private var lx = 0f
    private var ly = 0f

    init {
        pf.color = 0xFFF472B6.toInt(); pf.style = Paint.Style.STROKE; pf.strokeWidth = 5f
        pf.setPathEffect(DashPathEffect(floatArrayOf(24f, 14f), 0f))
        fill.color = 0x33FFFFFF
        hp.color = 0xFFF9A8D4.toInt()
        hs.color = Color.WHITE; hs.style = Paint.Style.STROKE; hs.strokeWidth = 5f
        post {
            val l = IntArray(2); getLocationOnScreen(l)
            rectF.offset(-l[0].toFloat(), -l[1].toFloat()); invalidate()
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(rectF, fill); c.drawRect(rectF, pf)
        for (i in 0..7) {
            val x = rectF.left + fx[i] * rectF.width(); val y = rectF.top + fy[i] * rectF.height()
            c.drawCircle(x, y, 26f, hp); c.drawCircle(x, y, 26f, hs)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hit = -1
                for (i in 0..7) {
                    val x = rectF.left + fx[i] * rectF.width(); val y = rectF.top + fy[i] * rectF.height()
                    if (hypot(e.x - x, e.y - y) < 70f) { hit = i; break }
                }
                if (hit < 0 && rectF.contains(e.x, e.y)) hit = 8
                lx = e.x; ly = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (hit < 0) return true
                val dx = e.x - lx; val dy = e.y - ly
                if (hit == 8) rectF.offset(dx, dy)
                else {
                    if (fx[hit] == 0f) rectF.left = min(rectF.left + dx, rectF.right - 100f)
                    if (fx[hit] == 1f) rectF.right = max(rectF.right + dx, rectF.left + 100f)
                    if (fy[hit] == 0f) rectF.top = min(rectF.top + dy, rectF.bottom - 100f)
                    if (fy[hit] == 1f) rectF.bottom = max(rectF.bottom + dy, rectF.top + 100f)
                }
                lx = e.x; ly = e.y; invalidate()
            }
        }
        return true
    }
}
