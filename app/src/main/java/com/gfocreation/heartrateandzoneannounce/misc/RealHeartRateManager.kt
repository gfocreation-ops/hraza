package com.gfocreation.heartrateandzoneannounce.misc

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import android.bluetooth.BluetoothManager
import android.os.Build
import com.gfocreation.heartrateandzoneannounce.DEBUG_ALL_DEVICE
import com.gfocreation.heartrateandzoneannounce.model.HeartRateProvider
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow


data class ScannedDevice(val name: String?, val address: String, val rssi: Int)


sealed interface BleState {
    object Idle : BleState
    object Scanning : BleState
    data class Connecting(val name: String?, val address: String?) : BleState
    data class Connected(val name: String?, val address: String?) : BleState
    data class Error(val message: String) : BleState
}


interface HeartRateListener {
    fun onHeartRate(bpm: Int)
}


class RealHeartRateManager(private val context: Context) : HeartRateProvider {

    private val tag = "RealHeartRateManager"

    private enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private val uuidHeartRateService = UUID.fromString(UUI_VALUE_HEART_RATE_SERVICE)
    private val uuidHeartRateMeasurement = UUID.fromString(UUI_VALUE_HEART_RATE_MEASUREMENT)
    private val uuidCccd = UUID.fromString(UUID_VALUE_CCCD)

    private val uuidBatteryService = UUID.fromString(UUID_VALUE_BATTERY_SERVICE)
    private val uuidBatteryLevel = UUID.fromString(UUID_VALUE_BATTERY_LEVEL)

    private var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    var onBatteryLevel: ((Int?) -> Unit)? = null

    private var reconnectStartTime: Long = 0L

    // private val _bpm = MutableStateFlow(0)
    private val _bpm = MutableSharedFlow<HeartRateSample>(
        // extraBufferCapacity = 64
        replay = 0,         // 1,     // 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    // override val bpmFlow: StateFlow<Int> = _bpm.asStateFlow()
    override val bpmFlow: Flow<HeartRateSample> = _bpm

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    override val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _bleState = MutableStateFlow<BleState>(BleState.Idle)
    val bleState: StateFlow<BleState> = _bleState.asStateFlow()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    // Service
    var heartRateListener: HeartRateListener? = null

    // BLE
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false
    private val deviceMap = mutableMapOf<String, ScannedDevice>()
    private val rssiMap = mutableMapOf<String, Int>()

    // control flags
    private var running = false
    private var onBpmCallback: ((Int) -> Unit)? = null

    // reconnect/backoff
    private var autoReconnectEnabled = true
    private var reconnectAttempts = 0
    private var maxReconnectAttempts = 5
    private var backoffBaseMillis = 1000L
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var lastConnectedDevice: BluetoothDevice? = null

    fun enableAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
        if (!enabled) cancelReconnectJob()
    }

    fun setMaxReconnectAttempts(max: Int) { maxReconnectAttempts = max }
    fun setBackoffBaseMillis(baseMillis: Long) { backoffBaseMillis = baseMillis.coerceAtLeast(200L) }

    private fun cancelReconnectJob() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectStartTime = 0L
    }


    private inline fun <T> runWithPermission(permission: String, block: () -> T?): T? {
        return if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            try {
                @SuppressLint("MissingPermission")
                block()
            } catch (se: SecurityException) {
                // Log.w(tag, "SecurityException in BLE call", se)
                null
            } catch (t: Throwable) {
                // Log.w(tag, "Exception in BLE call", t)
                null
            }
        } else {
            // Log.w(tag, "Permission $permission not granted")
            null
        }
    }

    // --- Limitation de la fréquence de mise à jour de la liste des devices (toutes les ~500 ms)
    private val handler = Handler(Looper.getMainLooper())
    private var lastEmitTime = 0L
    private val emitIntervalMs = 500L
    private var emitScheduled = false

    // expose a simple API to the rest of the app
    override fun start() {
        if (running) return
        running = true
        deviceMap.clear()
        rssiMap.clear()
        emitDevicesList()
        emitState(BleState.Scanning)
        startScan()
    }

    override fun stop() {
        stopScan()
        disconnectGatt()
        running = false
        emitState(BleState.Idle)
    }

    override fun connect(address: String?) {
//        if (address == null) {
//            // Log.w(tag, "connect called with null address")
//            return
//        }
        if (connectionState != ConnectionState.DISCONNECTED) return

        connectionState = ConnectionState.CONNECTING
        _connected.value = false

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            // Log.w(tag, "Device not found for address $address")
            emitState(BleState.Error("Device not found"))
            return
        }
        connectDevice(device)
    }

    override fun disconnect() {
//        disconnectGatt()
//        _connected.value = false
        forceDisconnect()
    }

    private fun forceDisconnect() {
        _isReconnecting.value = false
        cancelReconnectJob()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}

        bluetoothGatt = null
        connectionState = ConnectionState.DISCONNECTED
        _connected.value = false
        // _bpm.value = 0
//        scope.launch {
//            // Log.d("ReelBPM", "scope.launch 0 bpm")
//            _bpm.emit(HeartRateSample(0, System.currentTimeMillis()))
//        }
        _bpm.tryEmit(HeartRateSample(0, System.currentTimeMillis()))

        emitState(BleState.Idle)
        if (running) {
            startScanOnly()
        }
    }


    private fun handleGattDisconnection() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Throwable) {}

        bluetoothGatt = null
        connectionState = ConnectionState.DISCONNECTED
        _connected.value = false
        //_bpm.value = 0
//        scope.launch {
//            // Log.d("ReelBPM", "scope.launch 0 bpm")
//            _bpm.emit(HeartRateSample(0, System.currentTimeMillis()))
//        }
        _bpm.tryEmit(HeartRateSample(0, System.currentTimeMillis()))

        if (running && autoReconnectEnabled && lastConnectedDevice != null) {
            scheduleReconnect(lastConnectedDevice!!)
        } else {
            emitState(BleState.Idle)
            if (running) {
                startScanOnly()
            }
        }
    }

    override fun dispose() {
        stop()
        // cancel any reconnect job & background scope to free resources
        cancelReconnectJob()
        // Log.d("ReelBPM", "scope.cancel")
        try { scope.cancel() } catch (_: Throwable) {}
    }


    private fun emitState(s: BleState) {
        _bleState.value = s
    }

    private fun emitDevicesList() {
        val now = System.currentTimeMillis()
        // Si moins de 500 ms depuis la dernière émission, planifie une mise à jour différée
        if (now - lastEmitTime < emitIntervalMs) {
            if (!emitScheduled) {
                emitScheduled = true
                val delay = emitIntervalMs - (now - lastEmitTime)
                handler.postDelayed({
                    _devices.value = deviceMap.values.sortedByDescending { it.rssi }
                    lastEmitTime = System.currentTimeMillis()
                    emitScheduled = false
                }, delay)
            }
        } else {
            // Sinon, on met à jour immédiatement
            _devices.value = deviceMap.values.sortedByDescending { it.rssi }
            lastEmitTime = now
        }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val name = dev.name ?: result.scanRecord?.deviceName
            val addr = dev.address
            val rssi = result.rssi
            rssiMap[addr] = rssi
            deviceMap[addr] = ScannedDevice(name, addr, rssi)
            emitDevicesList()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r)
        }

        override fun onScanFailed(errorCode: Int) {
            // Log.w(tag, "Scan failed code=$errorCode")
            emitState(BleState.Error("Scan failed: $errorCode"))
            scanning = false
        }
    }

    private fun startScan() {
        if (scanning) return
        if (bluetoothAdapter == null) {
            emitState(BleState.Error("Bluetooth not available"))
            return
        }
        // permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Log.w(tag, "startScan aborted: BLUETOOTH_SCAN missing")
            emitState(BleState.Error("Missing BLUETOOTH_SCAN"))
            return
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            // filter for heart rate service (some devices don't advertise service UUID, so we allow broad scan)
//            val filters = listOf(
//                ScanFilter.Builder()
//                    .setServiceUuid(ParcelUuid(uuidHeartRateService))
//                    .build()
//            )
            // Decide filters based on debug flag: by default only heart-rate devices are filtered
            val filters = if (DEBUG_ALL_DEVICE) {
                // empty list -> no filtering, get all advertising devices
                emptyList<ScanFilter>()
            } else {
                listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(uuidHeartRateService))
                        .build()
                )
            }

            bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            scanning = true
        } catch (ise: IllegalStateException) {
            // Log.w(tag, "startScan IllegalStateException", ise)
            emitState(BleState.Error("startScan IllegalStateException"))
        } catch (se: SecurityException) {
            // Log.w(tag, "startScan SecurityException", se)
            emitState(BleState.Error("startScan SecurityException"))
        } catch (t: Throwable) {
            // Log.w(tag, "startScan exception", t)
            emitState(BleState.Error("startScan exception: ${t.message}"))
        }
    }

    private fun stopScan() {
        if (!scanning) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Log.w(tag, "stopScan aborted: BLUETOOTH_SCAN missing")
            scanning = false
            emitState(BleState.Idle)
            return
        }
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (ise: IllegalStateException) {
            // Log.w(tag, "stopScan IllegalStateException", ise)
        } catch (se: SecurityException) {
            // Log.w(tag, "stopScan SecurityException", se)
        }

        scanning = false
    }

    private fun connectDevice(device: BluetoothDevice) {
        cancelReconnectJob()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Log.w(tag, "connectDevice aborted: BLUETOOTH_CONNECT missing")
            emitState(BleState.Error("Missing BLUETOOTH_CONNECT"))
            return
        }
        emitState(BleState.Connecting(device.name, device.address))
        // ensure we stop scanning (we're connecting now)
        stopScan()

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            lastConnectedDevice = device
        } catch (se: SecurityException) {
            // Log.w(tag, "connectGatt denied", se)
            emitState(BleState.Error("connectGatt denied"))
        } catch (t: Throwable) {
            // Log.w(tag, "connectGatt exception", t)
            emitState(BleState.Error("connectGatt exception: ${t.message}"))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleGattDisconnection()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    bluetoothGatt = gatt
                    connectionState = ConnectionState.CONNECTED
                    _connected.value = true
                    _isReconnecting.value = false
                    cancelReconnectJob()
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    handleGattDisconnection()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Log.d(tag, "onServicesDiscovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitState(BleState.Error("Service discovery failed: $status"))
                return
            }
            // find heart rate characteristic
            val svc = gatt.services.firstOrNull { it.uuid == uuidHeartRateService }
            if (svc == null) {
                // Log.w(tag, "Heart rate service not found")
                emitState(BleState.Error("Heart rate service not found"))
                return
            }
            val char = svc.characteristics.firstOrNull { it.uuid == uuidHeartRateMeasurement }
            if (char == null) {
                // Log.w(tag, "Heart rate measurement characteristic not found")
                emitState(BleState.Error("Heart rate measurement characteristic not found"))
                return
            }

            // enable notifications
            val cccd = char.getDescriptor(uuidCccd)
            if (cccd == null) {
                // Log.w(tag, "CCCD descriptor not found")
                emitState(BleState.Error("CCCD not found"))
                return
            }

            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    gatt.setCharacteristicNotification(char, true)

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // API 33+ : utilise la nouvelle API sans setValue()
                            val res = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            // if (res <= 0) {
                            // Log.w(tag, "writeDescriptor returned non-positive result: $res")
                            // }
                        } else {
                            // API < 33 : on doit encore passer par setValue() puis writeDescriptor()
                            @Suppress("DEPRECATION")
                            cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            @Suppress("DEPRECATION")
                            val ok = gatt.writeDescriptor(cccd)
                            // if (!ok) {
                            // Log.w(tag, "writeDescriptor returned false")
                            // }
                        }
                    } catch (t: Throwable) {
                        // Log.w(tag, "Exception while writing descriptor", t)
                    }


                } else {
                    // Log.w(tag, "Cannot enable notifications: BLUETOOTH_CONNECT missing")
                    emitState(BleState.Error("Missing BLUETOOTH_CONNECT"))
                }
            } catch (t: Throwable) {
                // Log.w(tag, "Exception while enabling notifications", t)
                emitState(BleState.Error("Cannot enable notifications"))
            }

        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            if (descriptor.uuid == uuidCccd) {
                // CCCD écrit → on peut lire la batterie
                val batteryService = gatt.getService(uuidBatteryService)
                val batteryChar = batteryService?.getCharacteristic(uuidBatteryLevel)

                if (batteryChar != null &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        gatt.readCharacteristic(batteryChar)
                    } catch (_: Throwable) {}
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (connectionState != ConnectionState.CONNECTED) return

            if (characteristic.uuid == uuidHeartRateMeasurement) {
//                // ensure connected flag is set
//                if (!_connected.value) {
//                    _connected.value = true
//                    Log.d(tag, "onCharacteristicChanged: _connected set to true")
//                }
//                emitState(BleState.Connected(gatt.device?.name, gatt.device?.address))
                parseHeartRateMeasurement(value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            when (characteristic.uuid) {
                uuidHeartRateMeasurement -> {
                    parseHeartRateMeasurement(characteristic.value)
                }

                uuidBatteryLevel -> {
                    val level = characteristic.value?.firstOrNull()?.toInt()?.coerceIn(0, 100)
                    onBatteryLevel?.invoke(level)
                }

            }
        }

    }


    private fun parseHeartRateMeasurement(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt()
        val formatUint16 = (flags and 0x01) != 0
        val bpm = if (formatUint16) {
            if (value.size >= 3) {
                ((value[2].toInt() and 0xff) shl 8) or (value[1].toInt() and 0xff)
            } else {
                0
            }
        } else {
            if (value.size >= 2) {
                value[1].toInt() and 0xff
            } else {
                0
            }
        }
        onBpmCallback?.invoke(bpm)
        heartRateListener?.onHeartRate(bpm)
        // _bpm.value = bpm
        // Log.d("ReelBPM", "bpm $bpm")
//        Log.d("ReelBPM","scope active="+scope.isActive)
//        Log.d("ReelBPM","scope cancelled="+scope.coroutineContext[Job]?.isCancelled)
//        scope.launch {
//            Log.d("ReelBPM", "emit $bpm bpm")
//            _bpm.emit(HeartRateSample(bpm, System.currentTimeMillis()))
//        }
        _bpm.tryEmit(HeartRateSample(bpm, System.currentTimeMillis()))
    }

    private fun scheduleReconnect(device: BluetoothDevice) {
        if (reconnectJob?.isActive == true) return

        reconnectStartTime = System.currentTimeMillis()
        _isReconnecting.value = true

        reconnectJob = scope.launch {
            var delayMs = INITIAL_RECONNECT_DELAY_MS

            while (isActive) {
                val elapsed = System.currentTimeMillis() - reconnectStartTime
                if (elapsed >= MAX_RECONNECT_DURATION_MS) {
                    break
                }

                connect(device.address)

                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            }

            reconnectJob = null

            if (!_connected.value) {
                emitState(BleState.Idle)
                if (running) {
                    startScanOnly()
                }
            }
        }

        _isReconnecting.value = false

    }



    private fun cleanupGatt() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothGatt?.apply {
                    try { disconnect() } catch (t: Throwable) { // Log.w(tag, "disconnect failed", t)
                    }
                    try { close() } catch (t: Throwable) { // Log.w(tag, "close failed", t)
                    }
                }
            } else {
                // Log.w(tag, "stop: BLUETOOTH_CONNECT missing - skipping disconnect/close")
            }
        } catch (t: Throwable) {
            // Log.w(tag, "cleanupGatt exception", t)
        }
        bluetoothGatt = null
        running = false
        onBpmCallback = null
        lastConnectedDevice = null
        onBatteryLevel?.invoke(null)

        emitState(BleState.Idle)
    }

    fun isRunning(): Boolean = running

    fun startScanOnly() {
        if (!running) {
            onBpmCallback = null
            running = true
        }
        deviceMap.clear()
        rssiMap.clear()
        emitDevicesList()
        emitState(BleState.Scanning)
        startScan()
    }

    fun stopScanOnly() {
        stopScan()
        emitState(BleState.Idle)
    }

    fun connectTo(address: String) {
        connect(address)
    }

    fun setOnBpmCallback(cb: ((Int) -> Unit)?) {
        onBpmCallback = cb
    }

    // public helpers used by UI
    fun getDevicesList(): List<ScannedDevice> = deviceMap.values.sortedByDescending { it.rssi }

    private fun disconnectGatt() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            } else {
                // Log.w(tag, "disconnectGatt skipped: BLUETOOTH_CONNECT missing")
            }
        } catch (t: Throwable) {
            // Log.w(tag, "disconnectGatt exception", t)
        } finally {
            bluetoothGatt = null
            _connected.value = false
            onBatteryLevel?.invoke(null)

        }
    }

//    fun close() {
//        cancelReconnectJob()
//        scope.cancel()
//    }
}
