package com.gfocreation.heartrateandzoneannounce.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import androidx.annotation.RequiresPermission
import com.gfocreation.heartrateandzoneannounce.misc.UUID_VALUE_CCCD
import com.gfocreation.heartrateandzoneannounce.misc.UUI_VALUE_HEART_RATE_MEASUREMENT
import com.gfocreation.heartrateandzoneannounce.misc.UUI_VALUE_HEART_RATE_SERVICE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import android.os.Handler
import android.os.Looper


class HeartRateGattServer(
    private val context: Context
) {

    private val _connectedClientName = MutableStateFlow<String?>(null)
    val connectedClientName: StateFlow<String?> = _connectedClientName

    // Information de retour du bpm envoyé au device
    private val _lastNotifySuccess = MutableStateFlow<Boolean?>(null)
    val lastNotifySuccess: StateFlow<Boolean?> = _lastNotifySuccess


    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var gattServer: BluetoothGattServer? = null

    private var connectedDevice: BluetoothDevice? = null
    private var notifyEnabled = false

    // Pour filtrage du device connecté en entrée
    private var realSensorAddress: String? = null
    fun setRealSensorAddress(address: String?) {
        realSensorAddress = address
    }

//    private var notificationInFlight = false
//    private var pendingBpm: Int? = null


//    private var myId: Int = 0


    private lateinit var heartRateCharacteristic: BluetoothGattCharacteristic


//    init {
//        myId = System.identityHashCode(this)
//    }

    fun start() {

        if (gattServer != null) return

        gattServer = bluetoothManager.openGattServer(context, gattCallback)

        heartRateCharacteristic = BluetoothGattCharacteristic(
            UUID.fromString(UUI_VALUE_HEART_RATE_MEASUREMENT),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0       // BluetoothGattCharacteristic.PERMISSION_READ
        )



        val cccDescriptor = BluetoothGattDescriptor(
            UUID.fromString(UUID_VALUE_CCCD),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )

        heartRateCharacteristic.addDescriptor(cccDescriptor)

        val service = BluetoothGattService(
            UUID.fromString(UUI_VALUE_HEART_RATE_SERVICE),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        service.addCharacteristic(heartRateCharacteristic)

        gattServer?.addService(service)
    }


    fun stop() {
        connectedDevice = null
        notifyEnabled = false
        gattServer?.close()
        gattServer = null
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun updateHeartRate(bpm: Int) {

        val device = connectedDevice ?: return
        if (!notifyEnabled) return

//        if (notificationInFlight) {
//            pendingBpm = bpm
//            return
//        }

        sendNotification(device, bpm)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendNotification(device: BluetoothDevice, bpm: Int) {

//        val value = byteArrayOf(
//            0x00,
//            (bpm and 0xFF).toByte()
//        )
        val value = byteArrayOf(
            0x06,
            (bpm and 0xFF).toByte()
        )

        heartRateCharacteristic.value = value

//        notificationInFlight = true

        val success = gattServer?.notifyCharacteristicChanged(
            device,
            heartRateCharacteristic,
            false
        ) ?: false

        _lastNotifySuccess.value = success

//        if (!success) {
//            notificationInFlight = false
//        }
    }


    // fun isClientConnected(): Boolean = connectedDevice != null

    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                ConnectedDeviceAdd(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ConnectedDeviceRemove(device)
            }
        }

        private fun ConnectedDeviceAdd(device: BluetoothDevice) {
            // Filtrer le capteur connecté en entrée
            if (device.address == realSensorAddress) {
                return
            }
            if (connectedDevice == null) {
                connectedDevice = device

                // .name non indisponible juste après la connexion ; _connectedClientName.value = device.name ?: device.address
                val initialName = device.name
                _connectedClientName.value = initialName ?: device.address

                if (initialName == null) {
                    gattServer?.let {
                        Handler(Looper.getMainLooper()).postDelayed({
                            val refreshedName = device.name
                            if (connectedDevice == device && refreshedName != null) {
                                _connectedClientName.value = refreshedName
                            }
                        }, 500)
                    }
                }

            } else {
                // Refuse seconde connexion
                gattServer?.cancelConnection(device)
            }
        }

        private fun ConnectedDeviceRemove(device: BluetoothDevice) {
            if (device.address == realSensorAddress) {
                return
            }
            if (device == connectedDevice) {
                connectedDevice = null
                _connectedClientName.value = null
                notifyEnabled = false
            }
        }


        override fun onNotificationSent(
            device: BluetoothDevice,
            status: Int
        ) {

//            notificationInFlight = false
//
//            val next = pendingBpm
//            if (next != null && device == connectedDevice && notifyEnabled) {
//                pendingBpm = null
//                sendNotification(device, next)
//            }

            _lastNotifySuccess.value = (status == BluetoothGatt.GATT_SUCCESS)

        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {

            if (descriptor.uuid.toString().equals(UUID_VALUE_CCCD, true)) {

                descriptor.value = value

                notifyEnabled =
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            if (descriptor.uuid.toString().equals(UUID_VALUE_CCCD, true)) {

                val returnValue =
                    if (notifyEnabled)
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    returnValue
                )
            }
        }
    }
}
