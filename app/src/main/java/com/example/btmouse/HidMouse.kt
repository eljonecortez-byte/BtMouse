package com.example.btmouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.concurrent.Executors
import kotlin.math.abs

/** Makes the phone appear as a standard Bluetooth HID mouse. */
@SuppressLint("MissingPermission")
class HidMouse(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var buttons = 0
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        const val LEFT = 1
        const val RIGHT = 2
        const val MIDDLE = 4

        // 3 buttons + relative X, Y + wheel. 4-byte report, no report ID.
        private val DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01,
            0x09, 0x01, 0xA1.toByte(), 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03,
            0x15, 0x00, 0x25, 0x01, 0x95.toByte(), 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x03,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x06,
            0xC0.toByte(), 0xC0.toByte()
        )
    }

    val isConnected get() = host != null

    fun start() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            onStatus("Turn Bluetooth on, then reopen the app")
            return
        }
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hid = proxy as BluetoothHidDevice
                    register()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null
                host = null
                onStatus("Bluetooth service lost")
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    private fun register() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BT Mouse", "Phone touchpad", "BtMouse",
            BluetoothHidDevice.SUBCLASS1_MOUSE, DESCRIPTOR
        )
        val ok = hid?.registerApp(sdp, null, null, executor, object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                onStatus(if (registered) "Ready. Pick a computer." else "Not registered")
            }

            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        host = device
                        onStatus("Connected to ${device?.name ?: "computer"}")
                    }
                    BluetoothProfile.STATE_CONNECTING -> onStatus("Connecting…")
                    else -> {
                        host = null
                        onStatus("Disconnected")
                    }
                }
            }
        }) ?: false
        if (!ok) onStatus("Could not register as a mouse on this phone")
    }

    fun pairedDevices(): List<BluetoothDevice> {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun connect(device: BluetoothDevice) {
        onStatus("Connecting to ${device.name}…")
        hid?.connect(device)
    }

    fun disconnect() {
        host?.let { hid?.disconnect(it) }
    }

    fun move(dx: Int, dy: Int, wheel: Int = 0) {
        var rx = dx
        var ry = dy
        var rw = wheel
        // A report holds -127..127 per axis, so split big moves.
        while (rx != 0 || ry != 0 || rw != 0) {
            val sx = rx.coerceIn(-127, 127)
            val sy = ry.coerceIn(-127, 127)
            val sw = rw.coerceIn(-127, 127)
            send(sx, sy, sw)
            rx -= sx; ry -= sy; rw -= sw
        }
    }

    fun setButton(mask: Int, down: Boolean) {
        buttons = if (down) buttons or mask else buttons and mask.inv()
        send(0, 0, 0)
    }

    fun click(mask: Int) {
        setButton(mask, true)
        setButton(mask, false)
    }

    private fun send(dx: Int, dy: Int, wheel: Int) {
        val h = host ?: return
        hid?.sendReport(h, 0, byteArrayOf(buttons.toByte(), dx.toByte(), dy.toByte(), wheel.toByte()))
    }

    fun stop() {
        hid?.unregisterApp()
        hid = null
        host = null
    }
}
