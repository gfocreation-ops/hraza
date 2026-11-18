package com.gfocreation.heartrateandzoneannounce.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gfocreation.heartrateandzoneannounce.Hraza
import com.gfocreation.heartrateandzoneannounce.MainActivity
import com.gfocreation.heartrateandzoneannounce.R
import com.gfocreation.heartrateandzoneannounce.ble.HeartRateAdvertiser
import com.gfocreation.heartrateandzoneannounce.ble.HeartRateGattServer
import com.gfocreation.heartrateandzoneannounce.misc.RealHeartRateManager
import com.gfocreation.heartrateandzoneannounce.relay.RelayPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BleHeartRateRelayService : Service() {

    private lateinit var heartRateGattServer: HeartRateGattServer
    private lateinit var heartRateAdvertiser: HeartRateAdvertiser
    private lateinit var heartRateManager: RealHeartRateManager
    private lateinit var preferenceStore: RelayPreferenceStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var heartRateCollectJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "ble_relay_channel"
        private const val ACTION_STOP_RELAY = "ACTION_STOP_RELAY"
    }


    override fun onCreate() {
        super.onCreate()

        val hraza = application as Hraza

        heartRateManager = hraza.appContainer.realHeartRateManager
        preferenceStore = hraza.relayPreferenceStore

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification(0)
        )

        heartRateGattServer = hraza.appContainer.heartRateGattServer

    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        // Bouton stop de la notification
        if (intent?.action == ACTION_STOP_RELAY) {
            // Décoche la préférence
            CoroutineScope(Dispatchers.IO).launch {
                preferenceStore.setRelayEnabled(false)
            }
            // Stop service
            stopSelf()
            return START_NOT_STICKY
        }


        heartRateGattServer.start()

        val hraza = application as Hraza
        hraza.appContainer.refreshVirtualDeviceName()
        heartRateAdvertiser = HeartRateAdvertiser(
            this,
            hraza.appContainer.virtualDeviceName
        )

        heartRateAdvertiser.start()

        observeHeartRate()

        return START_STICKY
    }


    override fun onDestroy() {
        heartRateAdvertiser.stop()
        heartRateGattServer.stop()
        serviceScope.cancel()
        heartRateCollectJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeHeartRate() {

        heartRateCollectJob?.cancel()

        heartRateCollectJob = serviceScope.launch {
//            heartRateManager.bpmFlow.collect { bpm: Int ->
            heartRateManager.bpmFlow.collect { sample ->
                val bpm = sample.bpm
                heartRateGattServer.updateHeartRate(bpm)
                updateNotification(bpm)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Relais cardio BLE",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Relais cardio BLE actif"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }


    private fun createNotification(bpm: Int): Notification {

        val stopIntent = Intent(this, BleHeartRateRelayService::class.java).apply {
            action = ACTION_STOP_RELAY
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (bpm > 0) {
            "$bpm bpm"
        } else {
            "En attente de fréquence cardiaque"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Relais cardio BLE actif")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.heart_contour) // utiliser icône existante
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_launcher_foreground,   // icône stop existante
                "STOP",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(bpm: Int) {
        val notification = createNotification(bpm)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }


}
