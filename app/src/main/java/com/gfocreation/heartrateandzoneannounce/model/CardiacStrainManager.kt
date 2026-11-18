package com.gfocreation.heartrateandzoneannounce.model

import android.util.Log
import com.gfocreation.heartrateandzoneannounce.misc.CSB_PRC_INIT
import kotlin.math.roundToInt

class CardiacStrainManager(
    private val fcRepos: Int,
    private val fcMax: Int
) {

    private val csbMax = CSB_PRC_INIT.toDouble()
    private var csb = CSB_PRC_INIT.toDouble()

    private val k1 = 25.0 / 60.0 // 4 min à FC max ; 20.0 / 60.0 pour 5 min à FC max        // 35.0 / 60.0   // décharge (%/s)
    private val k2 = 20.0 / 60.0    // recharge (%/s)


    fun update(fc: Int, deltaMs: Long = 1000L): Pair<Int, Int?> {

        val i = ( (fc - fcRepos).toDouble() / (fcMax - fcRepos).toDouble() ).coerceIn(0.0, 1.0)

        val dcsb = when {
            // i > 0.75 -> -k1 * (i - 0.75)
            i > 0.75 -> {
                val x = (i - 0.75) / 0.25
                -k1 * x * x
            }
            i < 0.70 ->  k2 * (0.70 - i)
            else -> 0.0
        }

        val csbBefore = csb

        csb = (csb + dcsb * deltaMs/1000.0).coerceIn(0.0, csbMax)

        val percent = csb.roundToInt()

        val tte = if (dcsb < -0.000001)
            (csbBefore / -dcsb).roundToInt()
        else
            null

        // Log.d("CSB", "Csb.update : FC $fc ; deltaMs $deltaMs ms ; i $i")

        return Pair(percent, tte)
    }

    fun resetAll() {
        csb = CSB_PRC_INIT.toDouble()
    }

}