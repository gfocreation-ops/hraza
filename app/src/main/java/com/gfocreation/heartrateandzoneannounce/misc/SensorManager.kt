package com.gfocreation.heartrateandzoneannounce.misc

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.gfocreation.heartrateandzoneannounce.model.HeartRateProvider
import com.gfocreation.heartrateandzoneannounce.R
import com.gfocreation.heartrateandzoneannounce.ble.HeartRateGattServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Gestion indépendante du capteur (réel / simulé).
 * - Expose sensorState (modeSimulé, connecté, deviceName)
 * - Expose bpmFlow (int) provenant du provider actif (real or simulated)
 * - Expose devices (StateFlow<List<ScannedDevice>>) si provider réel présent
 *
 * Cette classe est autonome et ne mélange rien avec la logique des zones.
 */
class SensorManager(
    private val context: Context,
    private val realHeartRateManager: RealHeartRateManager,
    private val heartRateGattServer: HeartRateGattServer,
    externalScope: CoroutineScope? = null
) {
    private val scope: CoroutineScope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sensorCollectJob: Job? = null
    private val externalScopeProvided = externalScope != null
    private var connectedCollectJob: Job? = null

    // Providers
    private var realProvider: HeartRateProvider? = null
    private var simulatedProvider: KakeHeartRateSensor? = null
    private var providerWrapper: HeartRateProviderWrapper? = null

    // BPM flow (delegates to active provider)
    // private val _bpmFlow = MutableStateFlow(0)
    private val _bpmFlow = MutableSharedFlow<HeartRateSample>(
        // extraBufferCapacity = 64
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    // val bpmFlow: StateFlow<Int> = _bpmFlow.asStateFlow()
    val bpmFlow: Flow<HeartRateSample> = _bpmFlow
    private var lastBpm: Int = 0

    // connected flow is kept inside wrapper/provider; expose simplified boolean
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val isReconnecting: StateFlow<Boolean> =
        when {
            realProvider != null -> realProvider!!.isReconnecting
            providerWrapper != null -> providerWrapper!!.isReconnecting
            else -> MutableStateFlow(false)
        }

    // human-friendly device name (nullable)
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    // Standardized sensor state
    // data class SensorState(val modeSimulated: Boolean, val connected: Boolean, val deviceName: String?)
    data class SensorState(
        val modeSimulated: Boolean,
        val connected: Boolean,
        val deviceName: String?,
        val batteryLevel: Int? = null
    )

    // Battery level (0–100), nullable
    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _sensorState =
        MutableStateFlow(SensorState(modeSimulated = false, connected = false, deviceName = null))
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    // expose device address (nullable) so UI can mark which device line is connected
    private val _deviceAddress = MutableStateFlow<String?>(null)
    val deviceAddress: StateFlow<String?> = _deviceAddress.asStateFlow()

    // helper to pause/resume scan while connecting
    private var scanPausedForConnect = false
    private var connectAttemptJob: Job? = null

    init {
        setRealProvider(realHeartRateManager)
    }

    // Expose scanned devices if available (RealHeartRateManager provides devices StateFlow)
    fun getDevicesFlow(): StateFlow<List<ScannedDevice>>? {
        return (realProvider as? RealHeartRateManager)?.devices
    }

    // Set a real provider (BLE manager) instance
    fun setRealProvider(r: HeartRateProvider) {
        realProvider = r

        // Battery level callback (only if real provider supports it)
        (r as? RealHeartRateManager)?.onBatteryLevel = { level ->
            updateBatteryLevel(level)
        }

        // if currently using wrapper and it's supposed to use real provider, recreate wrapper
        if (providerWrapper is HeartRateProviderWrapper && providerWrapper?.isReal == true) {
            providerWrapper?.dispose()
            providerWrapper = HeartRateProviderWrapper(realProvider!!, scope, isReal = true)
            attachProvider(providerWrapper!!)
        }
        updateSensorState()
    }

    // Switch simulation on/off
    fun setSimulated(simulated: Boolean) {
        // stop and cleanup current provider to avoid duplicates
        try { stopProvider() } catch (_: Throwable) {}

        if (simulated) {
            // create fake and attach wrapper
            // simulatedProvider = KakeHeartRateSensor(scope)
            simulatedProvider = KakeHeartRateSensor(scope).apply {
                onBatteryLevel = { level ->
                    updateBatteryLevel(level)
                }
            }
            providerWrapper = HeartRateProviderWrapper(simulatedProvider!!, scope, isReal = false)
            _deviceName.value = context.getString(R.string.simulated_sensor) // requires strings.xml entry
            // _batteryLevel.value = null

            attachProvider(providerWrapper!!)
        } else {
            // switch back to real if available
            simulatedProvider?.dispose()
            simulatedProvider = null
            realProvider?.let {
            providerWrapper = HeartRateProviderWrapper(it, scope, isReal = true)
            // _batteryLevel.value = null

            attachProvider(providerWrapper!!)
            } ?: run {
                // no provider -> reset state
                _connected.value = false
                // _bpmFlow.value = 0
                lastBpm = 0
                _deviceName.value = null
            }
        }
        // ensure UI gets updated
        updateSensorState()
    }

    /**
     * Start/stop BLE scanning. Delegates to the real provider if present (RealHeartRateManager).
     * No-op for simulated provider.
     */

    fun startScan() {
        try {
            // Defensive checks: permissions & adapter available & enabled
            try {
                val pm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                if (pm != PackageManager.PERMISSION_GRANTED) {
                    // android.util.Log.w("SensorManager", "startScan aborted: BLUETOOTH_SCAN missing")
                    return
                }
            } catch (_: Throwable) {
                // If reflection or permission constant missing, continue best-effort
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                // android.util.Log.w("SensorManager", "startScan aborted: no Bluetooth adapter")
                return
            }
            if (!adapter.isEnabled) {
                // android.util.Log.w("SensorManager", "startScan aborted: Bluetooth disabled")
                return
            }

            // Existing scan logic unchanged
            (realProvider as? RealHeartRateManager)?.startScanOnly()
                ?: run {
                    providerWrapper?.let { pw ->
                        if (pw.isReal) {
                            (realProvider as? RealHeartRateManager)?.startScanOnly()
                        }
                    }
                }
        } catch (t: Throwable) {
            // android.util.Log.w("SensorManager", "startScan failed", t)
        }
    }


    fun stopScan() {
        try {
            (realProvider as? RealHeartRateManager)?.stopScanOnly()
                ?: run {
                    providerWrapper?.let { pw ->
                        if (pw.isReal) {
                            (realProvider as? RealHeartRateManager)?.stopScanOnly()
                        }
                    }
                }
        } catch (t: Throwable) {
            // android.util.Log.w("SensorManager", "stopScan failed", t)
        }
    }



    // Attach and start a provider wrapper
    private fun attachProvider(wrapper: HeartRateProviderWrapper) {
        // stop previous provider and collectors
        stopProvider()
        providerWrapper = wrapper

        // start provider only for simulated providers. For real BLE providers,
        // we *do not* auto-start here: the UI must explicitly start scanning.
        try {
            if (!wrapper.isReal) {
                wrapper.start()
            } else {
                // android.util.Log.d("SensorManager", "attachProvider: real provider attached (not auto-started)")
            }
        } catch (t: Throwable) {
            // android.util.Log.w("SensorManager", "attachProvider: failed to start wrapper", t)
        }


        // Initialize _connected and _bpmFlow immediately from wrapper's current values
        // This avoids a small race where updateSensorState() is called before collectors process first emission.
        try {
            // wrapper.connected and wrapper.bpmFlow are StateFlow-like; read current values synchronously

            // read current values safely via safe-cast to StateFlow<T>
            val initialConnected = try {
                (wrapper.connected as? StateFlow<Boolean>)?.value ?: false
            } catch (_: Throwable) {
                false
            }

//            val initialBpm = try {
//                // (wrapper.bpmFlow as? StateFlow<Int>)?.value ?: 0
//                lastBpm
//            } catch (_: Throwable) {
//                0
//            }

            _connected.value = initialConnected
            // _bpmFlow.value = initialBpm


            Log.d("SensorManager", "attachProvider: initialConnected=$initialConnected")        //  initialBpm=$initialBpm")
        } catch (_: Throwable) {
            // ignore any unexpected cast/read issues; collectors will set values soon after
        }

        // collect bpm — keep running in background
        sensorCollectJob?.cancel()
        sensorCollectJob = scope.launch {
            try {
//                wrapper.bpmFlow.collect { bpm ->
//                    _bpmFlow.value = bpm
//                }
                wrapper.bpmFlow.collect { sample ->
                    lastBpm = sample.bpm
                    _bpmFlow.emit(sample)
                }
            } catch (_: CancellationException) {
                // cancelled -> ignore
            } catch (t: Throwable) {
                // android.util.Log.w("SensorManager", "Error collecting bpm from wrapper", t)
            }
        }


        // collect connected changes
        connectedCollectJob?.cancel()
        connectedCollectJob = scope.launch {
            try {
                wrapper.connected.collect { c ->
                    _connected.value = c

                    if (!c) {
                        scanPausedForConnect = false
                        _deviceName.value = null
                        _deviceAddress.value = null
                        _batteryLevel.value = null
                    }

                    updateSensorState()
                }
            } catch (_: CancellationException) {
            }
        }

        // ensure immediate sensorState publish (reflects the initial values we just set)
        updateSensorState()
    }


    // Stop and cleanup current provider
    private fun stopProvider() {
        providerWrapper?.stop()
        providerWrapper?.dispose()
        providerWrapper = null
    }

    // Connect to device by address (delegates to underlying provider if real)
    fun connectToDevice(address: String?) {
        if (address == null) return

        // Filtrage du capteur en entrée pour RCBle
        heartRateGattServer.setRealSensorAddress(address)

        // optimistic local state: set address and name (address initially)
        _deviceAddress.value = address
        _deviceName.value = address

        // Try to resolve a friendly device name from devices flow (best-effort, async)
        getDevicesFlow()?.let { devicesFlow ->
            scope.launch {
                try {
                    // Read snapshot of discovered devices and try to find a matching address
                    val list: List<ScannedDevice> = devicesFlow.value
                    val foundName = list.firstOrNull { d -> d.address == address }?.name
                    if (!foundName.isNullOrBlank()) {
                        _deviceName.value = foundName
                        // push aggregated sensor state immediately so UI sees the friendly name
                        updateSensorState()
                    }
                } catch (_: Throwable) {
                    // best-effort only, ignore failures
                }
            }
        }

        // Pause scan before trying to connect (policy: stop scan to avoid interference)
        try {
            if (!scanPausedForConnect) {
                scanPausedForConnect = true
                try { stopScan() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        // cancel any previous attempt
        connectAttemptJob?.cancel()

        // launch a connect attempt with timeout (20s)
        connectAttemptJob = scope.launch {
            val success = withTimeoutOrNull(20_000L) {
                // delegate connect to real provider if present, else wrapper or simulated
                try {
                    when {
                        realProvider != null -> realProvider?.connect(address)
                        providerWrapper != null -> providerWrapper?.connect(address)
                        simulatedProvider != null -> providerWrapper?.connect(address)
                    }
                } catch (_: Throwable) {
                }

                // wait until provider reports connected or timeout
                // poll the provider.connected StateFlow if available
                val start = System.currentTimeMillis()
                while (isActive) {
                    val connectedNow = try {
                        providerWrapper?.connected?.value
                            ?: (realProvider as? RealHeartRateManager)?.connected?.value
                            ?: (simulatedProvider?.bpmFlow?.let { false } ?: false)
                    } catch (_: Throwable) {
                        false
                    }
                    if (connectedNow == true) return@withTimeoutOrNull true
                    // small sleep to avoid busy loop
                    delay(200L)
                    // optional early exit if we've waited very long (covered by timeout)
                    if (System.currentTimeMillis() - start > 19_000L) break
                }
                false
            } ?: false

            if (!success) {
                // connection failed or timed out -> resume scan automatically (best-effort)
                try {
                    scanPausedForConnect = false
                    // restart scan only if we have a real provider to scan
                    if (realProvider != null) {
                        startScan()
                    } else {
                        Log.d("SensorManager", "connect: no realProvider to restart scan")
                    }

                } catch (_: Throwable) {}
            } else {
                // success: leave scan paused (user explicitly can re-enable), update state already handled by collectors
                scanPausedForConnect = false
            }

            connectAttemptJob = null
            // publish aggregated sensor state
            updateSensorState()
        }
        // ensure sensor state UI sees the optimistic address/name
        updateSensorState()
    }


    // Disconnect current device and clear deviceName/address
    fun disconnectDevice() {
        try {
            connectAttemptJob?.cancel()
            connectAttemptJob = null

            when {
                realProvider != null -> realProvider?.disconnect()
                providerWrapper != null -> providerWrapper?.disconnect()
            }
        } catch (_: Throwable) {}

        // Pour RCBle
        heartRateGattServer.setRealSensorAddress(null)

        _deviceName.value = null
        _deviceAddress.value = null
        _connected.value = false
        _batteryLevel.value = null

        updateSensorState()
    }



    // Update aggregated sensor state
    private fun updateSensorState() {
        val mode = simulatedProvider != null || (providerWrapper?.isReal == false)
        // _sensorState.value = SensorState(modeSimulated = mode, connected = _connected.value, deviceName = _deviceName.value)
        _sensorState.value = SensorState(
            modeSimulated = mode,
            connected = _connected.value,
            deviceName = _deviceName.value,
            batteryLevel = _batteryLevel.value   // 👈 AJOUT
        )
    }

    fun updateBatteryLevel(level: Int?) {
        _batteryLevel.value = level
        updateSensorState()
    }

    // Expose cleanup
    fun dispose() {
        try { stopProvider() } catch (_: Throwable) {}
        simulatedProvider = null
        realProvider = null
        if (!externalScopeProvided) {
            try { scope.cancel() } catch (_: Throwable) {}
        }
    }

    /**
     * Simple wrapper class reused from ZoneManager context.
     * Kept local to SensorManager for delegation.
     */
    private class HeartRateProviderWrapper(
        private val src: Any,
        private val scope: CoroutineScope,
        val isReal: Boolean
    ) : HeartRateProvider {

        // override val bpmFlow: StateFlow<Int>
        override val bpmFlow: Flow<HeartRateSample>
        override val connected: StateFlow<Boolean>
        override val isReconnecting: StateFlow<Boolean>     //  = MutableStateFlow(false)


        init {
            when (src) {
                is HeartRateProvider -> {
                    bpmFlow = src.bpmFlow
                    connected = src.connected
                    isReconnecting = src.isReconnecting
                }
                is KakeHeartRateSensor -> {
                    bpmFlow = src.bpmFlow
                    connected = MutableStateFlow(true)
                    isReconnecting = MutableStateFlow(false)
                }
                else -> {
                    // bpmFlow = MutableStateFlow(0)
                    bpmFlow = MutableSharedFlow<HeartRateSample>()
                    connected = MutableStateFlow(false)
                    isReconnecting = MutableStateFlow(false)
                }
            }
        }

        override fun start() {
            when (src) {
                is HeartRateProvider -> src.start()
                is KakeHeartRateSensor -> src.start()
            }
        }

        override fun stop() {
            when (src) {
                is HeartRateProvider -> src.stop()
                is KakeHeartRateSensor -> src.stop()
            }
        }

        override fun connect(address: String?) {
            when (src) {
                is HeartRateProvider -> src.connect(address)
                is KakeHeartRateSensor -> src.connect(address)
            }
        }

        override fun disconnect() {
            when (src) {
                is HeartRateProvider -> src.disconnect()
                is KakeHeartRateSensor -> src.disconnect()
            }
        }

        override fun dispose() {
            when (src) {
                is HeartRateProvider -> src.dispose()
                is KakeHeartRateSensor -> src.dispose()
            }
        }
    }
}