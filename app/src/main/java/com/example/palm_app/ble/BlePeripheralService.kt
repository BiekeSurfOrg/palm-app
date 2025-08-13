package com.example.palm_app.ble

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import com.example.palm_app.util.NotificationUtils
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.min
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint

class BlePeripheralService : Service() {

    companion object {
        private const val TAG = "BlePeripheralService"
        const val ACTION_START = "START"
        const val ACTION_STOP  = "STOP"
        const val ACTION_UPDATE_PAYLOAD = "UPDATE_PAYLOAD"
        const val EXTRA_PAYLOAD = "payload" // ByteArray or String

        const val MANUFACTURER_ID = 0xFFFF
        const val ADVERTISING_DATA = "PALMKI"

        // YOUR UUIDs (keep what you already use)
        val SERVICE_UUID: UUID = UUID.fromString("e2a2b8e0-0b6c-4b6d-8868-c2b53f6c8d7b")
        val DATA_CHAR_UUID: UUID = UUID.fromString("c3b3c9f0-1c7d-4e7e-8a8b-9e0f1d0a2b3c")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun start(context: Context, payload: ByteArray) {
            val i = Intent(context, BlePeripheralService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PAYLOAD, payload)
            context.startForegroundService(i)
        }
        fun updatePayload(context: Context, payload: ByteArray) {
            val i = Intent(context, BlePeripheralService::class.java)
                .setAction(ACTION_UPDATE_PAYLOAD)
                .putExtra(EXTRA_PAYLOAD, payload)
            context.startService(i)
        }
        fun stop(context: Context) {
            val i = Intent(context, BlePeripheralService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }

    private var btMgr: BluetoothManager? = null
    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private val currentMtu = mutableMapOf<String, Int>()

    @Volatile private var payload: ByteArray = ByteArray(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val p = intent.getByteArrayExtra(EXTRA_PAYLOAD)
                    ?: (intent.getStringExtra(EXTRA_PAYLOAD)?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0))
                payload = p
                startForeground(42, NotificationUtils.foreground(this))
                startBle()
            }
            ACTION_UPDATE_PAYLOAD -> {
                val p = intent.getByteArrayExtra(EXTRA_PAYLOAD)
                    ?: (intent.getStringExtra(EXTRA_PAYLOAD)?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0))
                payload = p
                Log.i(TAG, "Payload updated: ${payload.size} bytes")
            }
            ACTION_STOP -> stopSelfSafely()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopBle()
        super.onDestroy()
    }

    private fun startBle() {
        btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = btMgr?.adapter
        advertiser = adapter?.bluetoothLeAdvertiser
        openGattServer()
        startAdvertising()
    }

    @SuppressLint("MissingPermission")
    private fun stopBle() {
        try {
            if (hasAdvertisePerm()) advertiser?.stopAdvertising(adCallback)
        } catch (_: Throwable) {}
        try {
            if (hasConnectPerm()) gattServer?.close()
        } catch (_: Throwable) {}
        advertiser = null; gattServer = null
    }

    private fun stopSelfSafely() {
        stopBle()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun openGattServer() {
        if (!hasConnectPerm()) { Log.w(TAG, "No BLUETOOTH_CONNECT; cannot open GATT"); return }
        val server = btMgr?.openGattServer(this, serverCb) ?: return
        val svc = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val data = BluetoothGattCharacteristic(
            DATA_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        data.addDescriptor(cccd)
        svc.addCharacteristic(data)

        server.addService(svc)
        gattServer = server
        dataChar = data
        Log.i(TAG, "GATT server ready")
    }

    private fun startAdvertising() {
        if (!hasAdvertisePerm()) { Log.w(TAG, "No BLUETOOTH_ADVERTISE; cannot advertise"); return }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)  // we want the scanner to connect
            .build()

        // --- Primary advertising data (keep it light!) ---
        val primary = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // saves bytes
            .addManufacturerData(MANUFACTURER_ID, buildManufacturerPayload())
            // Do NOT add the 128-bit service UUID here to avoid hitting 31B limit.
            .build()

        // --- Scan response (can carry extra info) ---
        val scanResp = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID)) // expose service UUID here
            // Optionally include the device name if you want:
            // .setIncludeDeviceName(true)
            .build()

        try {
            advertiser?.startAdvertising(settings, primary, scanResp, adCallback)
            Log.i(TAG, "Advertising started (MSD in primary, service UUID in scan response)")
        } catch (t: Throwable) {
            Log.e(TAG, "startAdvertising failed", t)
        }
    }


    private val adCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "Advertise onStartSuccess")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertise failed: $errorCode")
        }
    }

    private val serverCb = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.i(TAG, "Conn state ${device.address}: $newState")
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            currentMtu[device.address] = mtu
            Log.i(TAG, "MTU ${device.address} = $mtu")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic
        ) {
            if (!hasConnectPerm()) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT; cannot respond to read")
                return
            }
            if (characteristic.uuid == DATA_CHAR_UUID) {
                // Pass FULL value; stack applies offset
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, payload)
                } catch (se: SecurityException) {
                    Log.e(TAG, "sendResponse SecurityException", se)
                }
            } else {
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                } catch (se: SecurityException) {
                    Log.e(TAG, "sendResponse SecurityException", se)
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (!hasConnectPerm()) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT; cannot handle descriptor write")
                return
            }
            if (descriptor.uuid == CCCD_UUID && value.size >= 2 && value[0] == 0x01.toByte()) {
                if (responseNeeded) {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (_: SecurityException) {}
                }
                val mtu = currentMtu[device.address] ?: 185
                streamNotifications(device, mtu)
                return
            }
            if (responseNeeded) {
                try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (_: SecurityException) {}
            }
        }
    }

    private fun streamNotifications(device: BluetoothDevice, mtu: Int) {
        if (!hasConnectPerm()) { Log.w(TAG, "Missing BLUETOOTH_CONNECT; cannot notify"); return }
        val ch = dataChar ?: return
        val safeChunk = min(mtu - 3, 244)
        val total = (payload.size + safeChunk - 1) / safeChunk
        var seq = 0
        var pos = 0
        while (pos < payload.size) {
            val len = min(safeChunk, payload.size - pos)
            val header = byteArrayOf(
                (seq and 0xFF).toByte(), ((seq shr 8) and 0xFF).toByte(),
                (total and 0xFF).toByte(), ((total shr 8) and 0xFF).toByte(),
                (len and 0xFF).toByte(), ((len shr 8) and 0xFF).toByte()
            )
            val frame = header + payload.copyOfRange(pos, pos + len)
            ch.value = frame
            try {
                @Suppress("DEPRECATION")
                val ok = gattServer?.notifyCharacteristicChanged(device, ch, false) ?: false
                if (!ok) Log.w(TAG, "notify failed at seq=$seq")
            } catch (se: SecurityException) {
                Log.e(TAG, "notifyCharacteristicChanged SecurityException", se)
                break
            }
            pos += len; seq += 1
            try { Thread.sleep(3) } catch (_: InterruptedException) {}
        }
    }

    private fun buildManufacturerPayload(): ByteArray {
        // Example payload: [version(1B)=0x01][“PALMKI” ASCII][rolling counter (2B LE)]
        // Keep it short so the primary advertising stays within 31B budget.
        val tag = ADVERTISING_DATA.toByteArray(StandardCharsets.UTF_8) // "PALMKI"
        val version: Byte = 0x01
        val counterShort: Short = ((System.currentTimeMillis() / 1000) and 0xFFFF).toShort()
        return byteArrayOf(version) +
                tag +
                byteArrayOf(
                    (counterShort.toInt() and 0xFF).toByte(),
                    ((counterShort.toInt() shr 8) and 0xFF).toByte()
                )
    }

    private fun hasConnectPerm(): Boolean =
        if (Build.VERSION.SDK_INT >= 31)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else true

    private fun hasAdvertisePerm(): Boolean =
        if (Build.VERSION.SDK_INT >= 31)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        else true


}
