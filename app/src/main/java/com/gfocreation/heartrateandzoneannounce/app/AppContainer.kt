package com.gfocreation.heartrateandzoneannounce.app

import android.content.Context
import com.gfocreation.heartrateandzoneannounce.ble.HeartRateGattServer
import com.gfocreation.heartrateandzoneannounce.misc.CAPTEUR_VIRTUEL_NOM
import com.gfocreation.heartrateandzoneannounce.misc.CHARS_LETTRES_ET_NUMS
import com.gfocreation.heartrateandzoneannounce.misc.RealHeartRateManager
import com.gfocreation.heartrateandzoneannounce.misc.SensorManager
import com.gfocreation.heartrateandzoneannounce.model.StatEntrainement

class AppContainer(context: Context) {

    val realHeartRateManager: RealHeartRateManager =
            RealHeartRateManager(context.applicationContext)

    val heartRateGattServer: HeartRateGattServer =
            HeartRateGattServer(context.applicationContext)

    val sensorManager: SensorManager =
        SensorManager(
            context = context.applicationContext,
            realHeartRateManager = realHeartRateManager,
            heartRateGattServer = heartRateGattServer
        )

    val statEntrainement: StatEntrainement = StatEntrainement()


    // Nom du capteur cardio virtuel, avec suffixe aléatoire
    var virtualDeviceName: String = generateVirtualDeviceName()
        private set
    private fun generateVirtualDeviceName(): String {
        val random = java.security.SecureRandom()
        val id = buildString(3) {
            repeat(3) { append(CHARS_LETTRES_ET_NUMS[random.nextInt(CHARS_LETTRES_ET_NUMS.length)]) }
        }
        return "$CAPTEUR_VIRTUEL_NOM $id"
    }
    fun refreshVirtualDeviceName() {
        virtualDeviceName = generateVirtualDeviceName()
    }

}
