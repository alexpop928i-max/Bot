package com.eone.autodraw

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var preview: ImageView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 15f; setPadding(0, 16, 0, 8) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        fun seek(title: String, lo: Int, hi: Int, init: Int, set: (Int) -> Unit) {
            val tv = label("$title: $init")
            val sb = SeekBar(this)
            sb.max = hi - lo
            sb.progress = init - lo
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, u: Boolean) { set(p + lo); tv.text = "$title: ${p + lo}" }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            root.addView(tv); root.addView(sb)
        }

        root.addView(label("AutoDraw").apply { textSize = 24f })
        root.addView(btn("1. เลือกรูป") { pick() })
        preview = ImageView(this).apply { adjustViewBounds = true; maxHeight = 500 }
        root.addView(preview)
        seek("ความละเอียด", 20, 120, Store.res) { Store.res = it }
        seek("จำนวนสี", 2, 12, Store.colors) { Store.colors = it }
        root.addView(label("โหมดเลือกสีในเกม"))
        val rg = RadioGroup(this)
        listOf("พาเลท", "วงล้อสี", "Hex (ทดลอง)").forEachIndexed { i, t -> rg.addView(RadioButton(this).apply { text = t; id = 100 + i }) }
        rg.check(100)
        rg.setOnCheckedChangeListener { _, id -> Store.mode = id - 100 }
        root.addView(rg)
        root.addView(btn("2. เปิด Accessibility ให้ AutoDraw") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(label("เมื่อเปิดแล้วจะมีแถบลอยขึ้นเหนือทุกแอพ\n⬚ กำหนดพื้นที่วาด\n🎨 ตั้งค่าสีของเกม (ทำครั้งเดียวต่อเกม)\n▶ เริ่มวาด  ■ หยุด  ─ ย่อ\nลากแถบไปไว้นอกพื้นที่วาด"))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pick() {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 1)
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        val uri = d?.data ?: return
        if (rq != 1 || rs != RESULT_OK) return
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (o.outWidth / sample > 1600 || o.outHeight / sample > 1600) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        val bm = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
        if (bm != null) { Store.bitmap = bm; preview.setImageBitmap(bm) }
    }
}
