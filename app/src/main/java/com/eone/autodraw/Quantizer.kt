package com.eone.autodraw

import android.graphics.Bitmap
import android.graphics.Color

class Run(val x0: Int, val x1: Int, val y: Int)
class Group(val color: Int, val runs: List<Run>)
class Plan(val gw: Int, val gh: Int, val groups: List<Group>)

object Quantizer {
    fun build(src: Bitmap, w: Int, k: Int, mapper: (Int) -> Int): Plan {
        val h = maxOf(4, w * src.height / src.width)
        val sc = Bitmap.createScaledBitmap(src, w, h, true)
        val n = w * h
        val px = IntArray(n)
        sc.getPixels(px, 0, w, 0, 0, w, h)
        val r = IntArray(n); val g = IntArray(n); val b = IntArray(n)
        for (i in 0 until n) {
            val p = px[i]; val a = Color.alpha(p)
            r[i] = (Color.red(p) * a + 255 * (255 - a)) / 255
            g[i] = (Color.green(p) * a + 255 * (255 - a)) / 255
            b[i] = (Color.blue(p) * a + 255 * (255 - a)) / 255
        }
        val cr = FloatArray(k); val cg = FloatArray(k); val cb = FloatArray(k)
        for (j in 0 until k) {
            val idx = ((j + 0.5f) * n / k).toInt().coerceIn(0, n - 1)
            cr[j] = r[idx].toFloat(); cg[j] = g[idx].toFloat(); cb[j] = b[idx].toFloat()
        }
        val lab = IntArray(n)
        repeat(6) {
            val sr = FloatArray(k); val sg = FloatArray(k); val sb = FloatArray(k); val cnt = IntArray(k)
            for (i in 0 until n) {
                var best = 0; var bd = Float.MAX_VALUE
                for (j in 0 until k) {
                    val dr = cr[j] - r[i]; val dg = cg[j] - g[i]; val db = cb[j] - b[i]
                    val d = dr * dr + dg * dg + db * db
                    if (d < bd) { bd = d; best = j }
                }
                lab[i] = best; sr[best] += r[i].toFloat(); sg[best] += g[i].toFloat(); sb[best] += b[i].toFloat(); cnt[best]++
            }
            for (j in 0 until k) if (cnt[j] > 0) { cr[j] = sr[j] / cnt[j]; cg[j] = sg[j] / cnt[j]; cb[j] = sb[j] / cnt[j] }
        }
        val mapped = IntArray(k) { mapper(Color.rgb(cr[it].toInt(), cg[it].toInt(), cb[it].toInt())) }
        val by = LinkedHashMap<Int, ArrayList<Run>>()
        for (y in 0 until h) {
            var x0 = 0
            for (x in 1..w) {
                val a = mapped[lab[y * w + x - 1]]
                val e = if (x < w) mapped[lab[y * w + x]] else 0
                if (a != e) {
                    if (!isWhite(a)) by.getOrPut(a) { ArrayList() }.add(Run(x0, x - 1, y))
                    x0 = x
                }
            }
        }
        val groups = by.entries.sortedByDescending { it.value.size }.map { Group(it.key, it.value) }
        return Plan(w, h, groups)
    }

    private fun isWhite(c: Int) = Color.red(c) >= 235 && Color.green(c) >= 235 && Color.blue(c) >= 235
}
