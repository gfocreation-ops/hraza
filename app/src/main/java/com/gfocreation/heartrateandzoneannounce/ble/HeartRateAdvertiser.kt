package com.gfocreation.heartrateandzoneannounce.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.gfocreation.heartrateandzoneannounce.misc.UUI_VALUE_HEART_RATE_SERVICE
import java.util.UUID
// import com.gfocreation.heartrateandzoneannounce.misc.CAPTEUR_VIRTUEL_NOM


class HeartRateAdvertiser(
    private val context: Context,
    private val deviceName: String
) {

    private val advertiser =
        BluetoothAdapter.getDefaultAdapter().bluetoothLeAdvertiser

    private var advertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
        }
    }



    fun start() {

        if (advertising) return

        val settings = AdvertiseSettings.Builder()
            .setConnectable(true)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(
                ParcelUuid(UUID.fromString(UUI_VALUE_HEART_RATE_SERVICE))
            )
            .build()

        BluetoothAdapter.getDefaultAdapter().name = deviceName      // CAPTEUR_VIRTUEL_NOM     // "Cardio Hraza"

        advertiser.startAdvertising(settings, data, advertiseCallback)
        advertising = true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stop() {
        if (!advertising) return
        advertiser.stopAdvertising(advertiseCallback)
        advertising = false
    }

    // fun isAdvertising(): Boolean = advertising

}
