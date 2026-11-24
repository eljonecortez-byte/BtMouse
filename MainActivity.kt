package com.example.btmouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

class MainActivity : Activity() {

    private lateinit var mouse: HidMouse
    private lateinit var status: TextView
    private lateinit var pad: TouchPad

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mouse = HidMouse(this) { msg -> runOnUiThread { status.text = msg } }
        buildUi()

        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        } else {
            mouse.start()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) mouse.start()
        else status.text = "Bluetooth permission is required"
    }

    override fun onDestroy() {
        mouse.stop()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111418"))
            setPadding(dp(12), dp(36), dp(12), dp(12))
        }

        status = TextView(this).apply {
            text = "Starting…"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pick = Button(this).apply {
            text = "Choose computer"
            setOnClickListener { showDevicePicker() }
        }
        val off = Button(this).apply {
            text = "Disconnect"
            setOnClickListener { mouse.disconnect() }
        }
        top.addView(pick, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(off, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        pad = TouchPad(this, mouse).apply {
            setBackgroundColor(Color.parseColor("#1e252d"))
        }
        root.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })

        val hint = TextView(this).apply {
            text = "Tap = left click · Two-finger tap = right click · Two-finger drag = scroll"
            setTextColor(Color.parseColor("#8a96a3"))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        root.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(holdButton("LEFT", HidMouse.LEFT), LinearLayout.LayoutParams(0, dp(64), 1f))
        btns.addView(holdButton("MIDDLE", HidMouse.MIDDLE), LinearLayout.LayoutParams(0, dp(64), 0.7f))
        btns.addView(holdButton("RIGHT", HidMouse.RIGHT), LinearLayout.LayoutParams(0, dp(64), 1f))
        root.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val sensLabel = TextView(this).apply {
            text = "Speed"
            setTextColor(Color.WHITE)
        }
        val sens = SeekBar(this).apply {
            max = 40
            progress = 15
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    pad.sensitivity = 0.5f + p / 10f
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        pad.sensitivity = 0.5f + 15 / 10f
        root.addView(sensLabel, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        root.addView(sens, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun holdButton(label: String, mask: Int) = Button(this).apply {
        text = label
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> mouse.setButton(mask, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mouse.setButton(mask, false)
                    v.performClick()
                }
            }
            true
        }
    }

    private fun showDevicePicker() {
        val devices = try { mouse.pairedDevices() } catch (e: SecurityException) { emptyList() }
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No paired computers")
                .setMessage("On your computer, open Bluetooth settings and pair with this phone first (look for this phone's name). Then come back and tap Choose computer.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        @SuppressLint("MissingPermission")
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Connect to")
            .setItems(names) { _, i -> mouse.connect(devices[i]) }
            .show()
    }
}

/** Touchpad surface: move, tap-to-click, two-finger tap = right click, two-finger drag = scroll. */
class TouchPad(context: Context, private val mouse: HidMouse) : View(context) {

    var sensitivity = 2f

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var moved = false
    private var remX = 0f
    private var remY = 0f
    private var remScroll = 0f

    private val tapSlop = 14f * resources.displayMetrics.density
    private val scrollStep = 18f * resources.displayMetrics.density

    private fun centerX(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getX(i)
        return s / e.pointerCount
    }

    private fun centerY(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getY(i)
        return s / e.pointerCount
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                lastX = e.x; lastY = e.y
                downTime = e.eventTime
                maxPointers = 1
                moved = false
                remX = 0f; remY = 0f; remScroll = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                lastX = centerX(e); lastY = centerY(e)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Avoid a jump when one finger lifts.
                val skip = e.actionIndex
                var sx = 0f; var sy = 0f; var n = 0
                for (i in 0 until e.pointerCount) if (i != skip) { sx += e.getX(i); sy += e.getY(i); n++ }
                if (n > 0) { lastX = sx / n; lastY = sy / n }
            }
            MotionEvent.ACTION_MOVE -> {
                val cx = centerX(e)
                val cy = centerY(e)
                val dx = cx - lastX
                val dy = cy - lastY
                lastX = cx; lastY = cy
                if (hypot(cx - downX, cy - downY) > tapSlop) moved = true

                if (e.pointerCount >= 2) {
                    remScroll += dy
                    val steps = (remScroll / scrollStep).toInt()
                    if (steps != 0) {
                        remScroll -= steps * scrollStep
                        mouse.move(0, 0, -steps) // natural direction: drag up scrolls down
                    }
                } else {
                    remX += dx * sensitivity
                    remY += dy * sensitivity
                    val ix = remX.toInt()
                    val iy = remY.toInt()
                    if (ix != 0 || iy != 0) {
                        remX -= ix; remY -= iy
                        mouse.move(ix, iy)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val quick = e.eventTime - downTime < 250
                if (!moved && quick) {
                    mouse.click(if (maxPointers >= 2) HidMouse.RIGHT else HidMouse.LEFT)
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
