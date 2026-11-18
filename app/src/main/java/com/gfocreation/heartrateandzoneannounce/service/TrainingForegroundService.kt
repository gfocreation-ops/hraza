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
import androidx.core.app.NotificationManagerCompat
import com.gfocreation.heartrateandzoneannounce.DEBUG_MODE
import com.gfocreation.heartrateandzoneannounce.Hraza
import com.gfocreation.heartrateandzoneannounce.MainActivity
import com.gfocreation.heartrateandzoneannounce.R
import com.gfocreation.heartrateandzoneannounce.model.TrainingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.random.Random


class TrainingForegroundService : Service() {

    private val CHANNEL_ID = "hraza_free_training_channel"
    private val NOTIFICATION_ID = 1

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var bpmJob: Job? = null

    private lateinit var app: Hraza
    private val statEntrainement by lazy { app.appContainer.statEntrainement }

    private val sensorManager by lazy { app.appContainer.sensorManager }


    override fun onCreate() {
        super.onCreate()

        app = application as Hraza

        statEntrainement.trainingMode = TrainingMode.LIBRE

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Capteur cardio connecté")
        )
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (bpmJob == null) {
            bpmJob = sensorManager.bpmFlow
                .onEach { sample ->
                    if (statEntrainement.isRunning &&
                        statEntrainement.trainingMode == TrainingMode.LIBRE
                    ) {

                        val bpmUsed = sample.bpm // + if (DEBUG_MODE) Random.nextInt(10, 49) else 0

                        statEntrainement.ajouterBpm(bpmUsed, this)

                        val bpmAff = if (bpmUsed>0) bpmUsed else "-"
                        NotificationManagerCompat.from(this)
                            .notify(
                                NOTIFICATION_ID,
                                buildNotification("FC : $bpmAff bpm")
                            )
                    }
                }
                .launchIn(serviceScope)
        }

        return START_STICKY
    }


    override fun onDestroy() {
        bpmJob?.cancel()
        bpmJob = null
        super.onDestroy()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Entraînement en cours",
                NotificationManager.IMPORTANCE_DEFAULT      // IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {

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
            .setSmallIcon(R.drawable.heart_contour)
            .setContentTitle("HeartRateAndZoneAnnounce")
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // icône status bar
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }


}