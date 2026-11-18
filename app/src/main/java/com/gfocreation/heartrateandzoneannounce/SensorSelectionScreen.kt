package com.gfocreation.heartrateandzoneannounce

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.HorizontalDivider

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.repeatOnLifecycle
import com.gfocreation.heartrateandzoneannounce.misc.PermissionHelper
import com.gfocreation.heartrateandzoneannounce.misc.ScannedDevice
import com.gfocreation.heartrateandzoneannounce.misc.SensorManager
import com.gfocreation.heartrateandzoneannounce.misc.ZoneManager
import com.gfocreation.heartrateandzoneannounce.misc.loadZoneSmoothing
import com.gfocreation.heartrateandzoneannounce.misc.long2Int
import com.gfocreation.heartrateandzoneannounce.misc.markBlePermissionsAsked
import com.gfocreation.heartrateandzoneannounce.misc.saveZoneSmoothing
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import kotlin.math.roundToInt


/**
 * SensorSelectionScreen - version corrigée des erreurs de compilation :
 * - safe handling de device.rssi (nullable)
 * - appel "disconnect" via reflection pour éviter les références non résolues
 */

// Helper: best-effort check if ZoneManager is currently configured as simulated.
// Uses reflection to avoid compile-time dependency on a specific API.
private fun isSimulated(zoneManager: ZoneManager): Boolean {
    return runCatching {
        val cls = zoneManager::class.java
        // try common method names or boolean property holders
        val m1 = cls.methods.firstOrNull { it.name == "isSimulated" && it.parameterCount == 0 }
        val m2 = cls.methods.firstOrNull { it.name == "getIsSimulated" && it.parameterCount == 0 }
        when {
            m1 != null -> (m1.invoke(zoneManager) as? Boolean) ?: false
            m2 != null -> (m2.invoke(zoneManager) as? Boolean) ?: false
            else -> false
        }
    }.getOrDefault(false)
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorSelectionScreen(
    onBack: () -> Unit,
    sensorManager: SensorManager
) {
    val scope = rememberCoroutineScope()

    // --- Permission & Bluetooth enable helpers (insert after `val scope = rememberCoroutineScope()`)
    val ctx = LocalContext.current
    val activity = ctx as Activity


    // Pour être sûr d'avoir les permissions à jour
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var permissionsGranted by remember { mutableStateOf(false) }


    var scanning by remember { mutableStateOf(false) }

    // Launcher to request user to enable Bluetooth (system dialog)
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val enabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        if (!enabled) {
            Toast.makeText(ctx, "Bluetooth nécessaire pour scanner les capteurs", Toast.LENGTH_SHORT).show()
        } else {
            // Optionnel : démarrer le scan automatiquement si l'utilisateur avait demandé de scanner
            if (scanning) {
                try { sensorManager.startScan() } catch (_: Throwable) {}
            }
        }
    }



    // Small helper to check if all required permissions are granted
    fun hasBluetoothPermissions(): Boolean {
        val scanGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val connectGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        // On some Android versions NEARBY_DEVICES is also required (manifest has it), check if present before requiring it
        val nearbyGranted = true
//        try {
//            ContextCompat.checkSelfPermission(ctx, "android.permission.NEARBY_DEVICES") == PackageManager.PERMISSION_GRANTED
//        } catch (_: Throwable) { true }
        return scanGranted && connectGranted && nearbyGranted
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(
            androidx.lifecycle.Lifecycle.State.STARTED
        ) {
            permissionsGranted = hasBluetoothPermissions()
        }
    }

// Launcher to request multiple permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val grantedScan = result[Manifest.permission.BLUETOOTH_SCAN] == true
        val grantedConnect = result[Manifest.permission.BLUETOOTH_CONNECT] == true
        // val grantedNearby = (result["android.permission.NEARBY_DEVICES"] ?: true)
        val allGranted = grantedScan && grantedConnect // && grantedNearby
        if (!allGranted) {
            Toast.makeText(ctx, ctx.getString(R.string.permissions_missing), Toast.LENGTH_SHORT).show()
        } else {
            permissionsGranted = true
            Toast.makeText(ctx, ctx.getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
            // if permissions are now ok and Bluetooth is off, prompt to enable
            if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
                enableBluetoothLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }


    // observe sensor state (modeSimulated, connected, deviceName)
    // Source de vérité unique : sensorManager.sensorState
    val sensorState by sensorManager.sensorState.collectAsState()
    // Local switch state (checked == REAL). On initialise depuis sensorState
    var isRealState by remember { mutableStateOf(!sensorState.modeSimulated) }

    // Keep isRealState in sync when sensorState changes (ex: other screen toggled)
    LaunchedEffect(sensorState.modeSimulated) {
        isRealState = !sensorState.modeSimulated
    }

    // BPM and connected taken from sensorManager (authoritative)
    // val bpm by sensorManager.bpmFlow.collectAsState(initial = 0)
    val sample by sensorManager.bpmFlow.collectAsState(
        initial = HeartRateSample(0, 0)
    )
    val bpm = sample.bpm
    val connected = sensorState.connected

    // devicesFlow peut être null si aucun provider réel n'est présent
    val devicesFlow: StateFlow<List<ScannedDevice>>? = sensorManager.getDevicesFlow()
    val devicesState: State<List<ScannedDevice>>? = devicesFlow?.collectAsState(initial = emptyList())
    val devices: List<ScannedDevice> = devicesState?.value ?: emptyList()

    // get current connected device address (nullable)
    val connectedDeviceAddr by sensorManager.deviceAddress.collectAsState(initial = null)
    val isConnectedGlobal by sensorManager.connected.collectAsState()

    val batteryLevel by sensorManager.batteryLevel.collectAsState(initial = null)

    // UI state
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    // Friendly device name from sensorManager (source of truth)
    val connectedDeviceLabel = sensorState.deviceName

    Scaffold(
        topBar = {
            // Custom single-line header:
            // - centered title "Choix du capteur"
            // - "Retour" button aligned to the right (blue background), ergonomique main droite
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()              // <-- assure le décalage sous la barre système
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Retour ou annuler
                    Button(onClick = onBack) {
                        Text(text = stringResource(id = R.string.but_back))
                    }
                    // Titre
                    Text(
                        text = stringResource(id = R.string.sensor_choix_titre),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(2f),
                        textAlign = TextAlign.Center
                    )

                    // spacer to keep title visually centered relative to right button
                    // Spacer(modifier = Modifier.width(90.dp))

                }
            }
        }

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Permissions card + action : seulement si nécessaire
            if (!permissionsGranted) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = stringResource(id = R.string.permissions_section_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.permissions_missing)
                        )
                            Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (PermissionHelper.permissionsPermanentlyDenied(ctx, activity)) {
                                    openAppSettings(ctx)
                                } else {
                                    markBlePermissionsAsked(ctx)
                                    permissionLauncher.launch(PermissionHelper.requiredBlePermissions(ctx))
                                    permissionLauncher.launch(PermissionHelper.requiredBlePermissions(ctx))
                                }
                            }

                            ,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(id = R.string.permissions_request_button))
                        }

                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }


            // Connected device + BPM + disconnect
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!connected) {
                        Text(text = stringResource(id = R.string.no_sensor_connected))
                    } else {
                        val label = connectedDeviceLabel ?: stringResource(id = R.string.connected_label)

                        // Single-row layout: left = name + bpm, right = small disconnect button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = label, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                // Text(text = stringResource(id = R.string.bpm_label_value, bpm), fontSize = 18.sp)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(id = R.string.bpm_label_value, bpm),
                                        fontSize = 18.sp
                                    )

                                    // Battery (only if available)
                                    batteryLevel?.let { level ->
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "🔋 $level%",
                                            fontSize = 14.sp,
                                            color = when {
                                                level <= 15 -> MaterialTheme.colorScheme.error
                                                level <= 30 -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }

                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // small disconnect button aligned to the right
                            Button(
                                onClick = {
                                    try {
                                        sensorManager.disconnectDevice()

                                        // Si on était en mode simulé, on le désactive aussi
                                        if (sensorState.modeSimulated) {
                                            sensorManager.setSimulated(false)
                                        }

                                    } catch (_: Throwable) {}
                                },
                                modifier = Modifier.defaultMinSize(minWidth = 96.dp)
                            ) {
                                Text(text = stringResource(id = R.string.disconnect))
                            }

                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(12.dp))


            // --- Lissage changement de zone ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {

                        val context = LocalContext.current
                        var smoothingMSec by remember { mutableStateOf(loadZoneSmoothing(context)) }

                        // valeurs autorisées
                        val smoothingOptions = listOf(0, 1, 3, 5, 10)
                        val smoothingIndex =
                            smoothingOptions.indexOf(long2Int(smoothingMSec)).coerceAtLeast(0)

                    Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Text(
                            text = stringResource(id = R.string.zone_lissage),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 20.dp)
                        )
                    }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = smoothingIndex.toFloat(),
                                // enabled = !isSimulatedSensor,   // désactivé si capteur simulé
                                onValueChange = { v ->
                                    val idx = v.roundToInt().coerceIn(smoothingOptions.indices)
                                    smoothingMSec = (smoothingOptions[idx] * 1000L)
                                    saveZoneSmoothing(context, smoothingMSec)
                                    // announcer?.updateZoneSmoothing(smoothingMSec)
                                },
                                valueRange = 0f..(smoothingOptions.size - 1).toFloat(),
                                steps = smoothingOptions.size - 2,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = if (smoothingMSec == 0L) stringResource(id = R.string.but_off) else "${
                                    long2Int(
                                        smoothingMSec
                                    )
                                }s",
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scan controls & devices list
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {

                    // Bouton remplacés par un Switch (scan ON/OFF)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Label à gauche pour pousser le Switch à droite
                        Text(
                            text = if (scanning) stringResource(id = R.string.scanning) else stringResource(id = R.string.to_scan),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Switch(
                            checked = scanning,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // If we try to enable scanning but lack permissions -> request them
                                    if (!permissionsGranted) {      // hasBluetoothPermissions()) {
                                        permissionLauncher.launch(arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            "android.permission.NEARBY_DEVICES"
                                        ))
                                        return@Switch
                                    }
                                    // Permissions OK -> ensure Bluetooth enabled
                                    val adapter = BluetoothAdapter.getDefaultAdapter()
                                    if (adapter == null) {
                                        Toast.makeText(ctx, "Bluetooth non disponible sur cet appareil", Toast.LENGTH_SHORT).show()
                                        return@Switch
                                    }
                                    if (!adapter.isEnabled) {
                                        // request user to enable Bluetooth; we will start scan on success (see enableBluetoothLauncher)
                                        scanning = true // preserve UI intent
                                        enableBluetoothLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                        return@Switch
                                    }
                                    // All good -> start scanning
                                    scanning = true
                                    try { sensorManager.startScan() } catch (_: Throwable) {}
                                } else {
                                    scanning = false
                                    try { sensorManager.stopScan() } catch (_: Throwable) {}
                                }
                            },
                            enabled = true,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                    }

                    Spacer(Modifier.height(12.dp))

                    if (devices.isEmpty()) {
                        Text(text = stringResource(id = R.string.no_device))
                    } else {
                        // add a top separator before the first item
                        if (devices.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp))
                        }
                        LazyColumn(modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)) {
                            items(items = devices, key = { it.address ?: it.name ?: it.hashCode().toString() }) { d ->
                                // item content
                                DeviceRowSelectable(
                                    device = d,
                                    selected = selectedDeviceAddress == d.address,
                                    isConnected = (connectedDeviceAddr != null && connectedDeviceAddr == d.address),
                                    onSelect = { selectedDeviceAddress = if (selectedDeviceAddress == d.address) null else d.address },
                                    onConnect = { addr -> sensorManager.connectToDevice(addr) },
                                    onDisconnect = { sensorManager.disconnectDevice() } // not used in row but kept for signature
                                )
                                // separator after each item
                                HorizontalDivider(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode real ou simulé (démo)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = stringResource(id = R.string.sensor_mode_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )

// Observe sensor state from SensorManager (source of truth for simulated/real + connected + name)
                        val sensorState by sensorManager.sensorState.collectAsState()

// Switch state: true == REAL, false == SIMULATED
                        var isRealState by remember { mutableStateOf(!sensorState.modeSimulated) }

// Keep isRealState in sync when sensorState changes
                        LaunchedEffect(sensorState.modeSimulated) {
                            isRealState = !sensorState.modeSimulated
                        }

                        // Switch: checked == false => Simulé (left) ; checked == true => Réel (right)
                        Switch(
                            checked = !isRealState,
                            onCheckedChange = { checked ->
                                // UI update
                                isRealState = !checked
                                // Propagate to SensorManager: checked(true) => REAL => setSimulated(false)
                                try {
                                    sensorManager.setSimulated(checked)
                                } catch (t: Throwable) {
                                    // log and surface minimal feedback if desired
                                    // android.util.Log.w("SensorSelection", "Failed to setSimulated=${
                                    // checked
                                    // }", t)
                                }
                            }
                        )

                        // Right label : Réel
                        Text(
                            text = stringResource(id = R.string.simulated_sensor),      // sensor_mode_simulated),
                            modifier = Modifier.padding(start = 8.dp)
                        )

                    }

                }
            }

            Spacer(modifier = Modifier.height(16.dp))

        }
    }
}

@Composable
private fun DeviceRowSelectable(
    device: ScannedDevice,
    selected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = device.name ?: stringResource(id = R.string.device_unknown), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(text = device.address ?: "", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val rssiValue = device.rssi ?: 0
                    Text(text = stringResource(id = R.string.dbm, rssiValue), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    // right-side action: Connect or connected indicator (no disconnect here)
                    if (isConnected) {
                        // show connected indicator only — disconnect handled in the Connected card above
                        Text(text = stringResource(id = R.string.connected_label), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Button(onClick = { onConnect(device.address ?: return@Button) }, modifier = Modifier.defaultMinSize(minWidth = 96.dp)) {
                            Text(text = stringResource(id = R.string.connect))
                        }
                    }
                }
            }
        }
    }
}
