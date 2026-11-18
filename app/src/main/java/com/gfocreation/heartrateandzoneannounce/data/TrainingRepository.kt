package com.gfocreation.heartrateandzoneannounce.data

import android.content.Context
import com.gfocreation.heartrateandzoneannounce.Hraza
import com.gfocreation.heartrateandzoneannounce.misc.SensorManager
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class TrainingRepository private constructor(
    context: Context
) {

    // ⚠️ applicationContext pour éviter toute fuite mémoire
    private val appContext = context.applicationContext

    // 3.1.0_c      private val sensorManager = SensorManager(appContext)
    private val app = appContext as Hraza
    private val sensorManager = app.appContainer.sensorManager

    // --- Exposition des flux ---
    val sensorState: StateFlow<SensorManager.SensorState>
        get() = sensorManager.sensorState

    // val bpmFlow: StateFlow<Int>
    val bpmFlow: Flow<HeartRateSample>
        get() = sensorManager.bpmFlow

    val isReconnecting: StateFlow<Boolean>
        get() = sensorManager.isReconnecting

//    // --- Commandes ---
//    fun startSensors() {
//        sensorManager.start()
//    }
//
//    fun stopSensors() {
//        sensorManager.stop()
//    }

    companion object {
        @Volatile
        private var INSTANCE: TrainingRepository? = null

        fun getInstance(context: Context): TrainingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrainingRepository(context).also {
                    INSTANCE = it
                }
            }
        }
    }
}
