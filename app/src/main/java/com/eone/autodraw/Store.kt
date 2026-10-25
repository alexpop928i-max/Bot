package com.eone.autodraw

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect

object Store {
    var bitmap: Bitmap? = null
    var res = 60
    var colors = 6
    var mode = 0 // 0 = palette, 1 = wheel, 2 = hex
    var area = Rect(200, 300, 900, 1000)
    val palPts = ArrayList<PointF>()
    val palCols = ArrayList<Int>()
    var wheelC: PointF? = null
    var wheelRed: PointF? = null
    var barTop: PointF? = null
    var barBot: PointF? = null
    var ccw = false
    var hexField: PointF? = null
    var hexOk: PointF? = null
}
