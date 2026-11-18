package com.gfocreation.heartrateandzoneannounce.model

import android.content.Context
import android.util.Log
import com.gfocreation.heartrateandzoneannounce.DEBUG_STAT_AUTO_STOP
import com.gfocreation.heartrateandzoneannounce.DEBUG_STAT_AUTO_STOP_DUREE
import com.gfocreation.heartrateandzoneannounce.misc.BPM_MIN_DEFAULT
import com.gfocreation.heartrateandzoneannounce.misc.BPM_TIMEOUT_MS
import com.gfocreation.heartrateandzoneannounce.misc.CSB_PRC_INIT
import com.gfocreation.heartrateandzoneannounce.misc.formatMsToHms
import com.gfocreation.heartrateandzoneannounce.misc.saveStat

enum class TrainingMode {
    LIBRE,
    SEANCE
}

enum class RunState {
    STOPPED,
    RUNNING,
    PAUSED_NO_BPM
}

data class StatEntrainement(
    var dureeMs: Long = 0L,
    var bpmMax: Int = 0,
    var bpmMin: Int = BPM_MIN_DEFAULT,
    var bpmTotal: Long = 0L,
    var valeurNb: Int = 0,
    var lastStartTimeMs: Long = 0L,
    // var lastStopTimeMs: Long = 0L,
    var seanceId: Int = 0,
    var kcalTotal: Double = 0.0,
    var glucidesTotalG: Double = 0.0,
    var glucidesDepuisResetG: Double = 0.0,
    var lipidesTotalG: Double = 0.0,
    var csbPercent: Int = CSB_PRC_INIT,
    var csbTteSec: Int? = null
) {

    private var lastBpmValue: Int = 0

    var trainingMode: TrainingMode = TrainingMode.LIBRE

    var lastBpmTimeMs: Long = 0L

    @Transient
    private var runState: RunState = RunState.STOPPED

    @Transient
    var metabolicAccumulator: MetabolicStatsAccumulator? = null
        private set

    @Transient
    var cardiacStrainManager: CardiacStrainManager? = null
        private set

    val isRunning: Boolean
        get() = runState != RunState.STOPPED        // == RunState.RUNNING

    val bpmMoyen: Int
        get() = if (valeurNb > 0) (bpmTotal / valeurNb).toInt() else 0

    val dureeCouranteMs: Long
        get() = when (runState) {
            RunState.RUNNING ->
                dureeMs + (System.currentTimeMillis() - lastStartTimeMs)
            RunState.PAUSED_NO_BPM,
            RunState.STOPPED ->
                dureeMs
        }

    val dureeAff: String
        get() = formatMsToHms(dureeCouranteMs)

    // --------------------------------------------------

    fun initAddsIfNeeded(       // initMetabolicAccumulatorIfNeeded
        zones: List<Zone>,
        poidsKg: Float,
        sexe: UserPhysioParams.Sexe,
        sportType: UserPhysioParams.SportType,
        niveau: UserPhysioParams.Niveau,
        energyUnit: UserPhysioParams.EnergyUnit
    ) {

        if (metabolicAccumulator == null) {
            metabolicAccumulator = MetabolicStatsAccumulator(zones).apply {
                this.poidsKg = poidsKg
                this.sexe = sexe
                this.sportType = sportType
                this.niveau = niveau
                restoreFromStat(
                    kcalTotal,
                    glucidesTotalG,
                    glucidesDepuisResetG,
                    lipidesTotalG
                )
            }
        } else {
            metabolicAccumulator?.updatePhysio(
                UserPhysioParams(poidsKg, sexe, sportType, niveau, energyUnit)
            )
        }

        if (cardiacStrainManager == null) {
            cardiacStrainManager = CardiacStrainManager(
                fcRepos = zones.first().minBpm,
                fcMax = zones.last().maxBpm,
            )
        }

    }

    // --------------------------------------------------

    @Synchronized
    fun reset() {
        dureeMs = 0L
        bpmMax = 0
        bpmMin = BPM_MIN_DEFAULT
        bpmTotal = 0L
        valeurNb = 0
        kcalTotal = 0.0
        glucidesTotalG = 0.0
        glucidesDepuisResetG = 0.0
        lipidesTotalG = 0.0

        csbPercent = CSB_PRC_INIT
        csbTteSec = null
        cardiacStrainManager?.resetAll()

        lastStartTimeMs = 0L
        // lastStopTimeMs = 0L
        lastBpmTimeMs = 0L

        runState = RunState.STOPPED
        metabolicAccumulator?.resetAll()
    }

    // --------------------------------------------------

    @Synchronized
    fun start(autoResumeWindowMs: Long? = null) {
        if (runState != RunState.STOPPED) return

        val now = System.currentTimeMillis()
        lastStartTimeMs = now
        lastBpmTimeMs = now
        runState = RunState.RUNNING
    }

    @Synchronized
    fun start(ctx: Context, autoResumeWindowMs: Long? = null) {
        if (metabolicAccumulator?.poidsKg == 0f) return
        start(autoResumeWindowMs)
        save(ctx)
    }

    // --------------------------------------------------

    @Synchronized
    fun stop() {
        if (runState == RunState.STOPPED) return

        val now = System.currentTimeMillis()

        if (runState == RunState.RUNNING && lastStartTimeMs > 0) {
            val deltaMs = now - lastStartTimeMs
            if (deltaMs > 0) dureeMs += deltaMs
        }

        // compléter énergie jusqu’au dernier BPM connu
        if (lastBpmTimeMs > 0 && lastBpmValue > 0) {
            val deltaMs = now - lastBpmTimeMs
            if (deltaMs > 0) {
                metabolicAccumulator?.onSample(lastBpmValue, deltaMs)
                metabolicAccumulator?.let { acc ->
                    kcalTotal = acc.kcalTotal
                    glucidesTotalG = acc.glucidesGrammesTotal
                    glucidesDepuisResetG = acc.glucidesGrammesDepuisReset
                    lipidesTotalG = acc.lipidesGrammesTotal
                }
            }
        }

        lastStartTimeMs = 0L
        lastBpmTimeMs = 0L
        runState = RunState.STOPPED
    }


    @Synchronized
    fun stop(ctx: Context) {
        stop()
        save(ctx)
    }

    // --------------------------------------------------

    @Synchronized
    fun ajouterBpm(bpm: Int) {

        val now = System.currentTimeMillis()

        if (bpm <= 0) {
            if (runState == RunState.RUNNING) {
                lastStartTimeMs = 0L
                runState = RunState.PAUSED_NO_BPM
            }

            if (
                runState == RunState.PAUSED_NO_BPM &&
                lastBpmTimeMs > 0 &&
                now - lastBpmTimeMs > BPM_TIMEOUT_MS
            ) {
                stop()
            }
            return
        }

        lastBpmValue = bpm

        if (runState == RunState.PAUSED_NO_BPM) {
            lastStartTimeMs = now
            lastBpmTimeMs = now
            runState = RunState.RUNNING
        }

        if (runState != RunState.RUNNING) return


        // val acc = metabolicAccumulator ?: return

        val deltaMs = now - lastBpmTimeMs

        // Log.d("StatAjout", "StatAjout : $bpm bpm pour $deltaMs ms")

        if (lastBpmTimeMs > 0) {
            if (deltaMs > 0) {

                if (metabolicAccumulator != null) {
                    val acc = metabolicAccumulator!!
                    acc.onSample(bpm, deltaMs)
                    kcalTotal = acc.kcalTotal
                    glucidesTotalG = acc.glucidesGrammesTotal
                    glucidesDepuisResetG = acc.glucidesGrammesDepuisReset
                    lipidesTotalG = acc.lipidesGrammesTotal
                }

                // Log.d("CSB", "Csb.stat ; avant if ($(cardiacStrainManager != null))")
                if (cardiacStrainManager != null) {
                    val csb = cardiacStrainManager!!
                    // Log.d("CSB", "Csb.stat ; in avec $bpm bmp pour $deltaMs ms")
                    val (percent, tte) = csb.update(bpm, deltaMs)
                    csbPercent = percent
                    csbTteSec = tte
                    // Log.d("CSB", "Csb.stat ; post avec $percent % et $tte sec")
                }

            }
        }

//        cardiacStrainManager?.let {
//            val (percent, tte) = it.update(bpm, deltaMs)
//            csbPercent = percent
//            csbTteSec = tte
//        }

        lastBpmTimeMs = now

        bpmMax = maxOf(bpmMax, bpm)
        bpmMin = minOf(bpmMin, bpm)
        bpmTotal += bpm
        valeurNb++

        if (DEBUG_STAT_AUTO_STOP && dureeCouranteMs >= DEBUG_STAT_AUTO_STOP_DUREE) {
            stop()
        }

    }

    @Synchronized
    fun ajouterBpm(bpm: Int, ctx: Context) {
        ajouterBpm(bpm)
        save(ctx)
    }

    @Synchronized
    fun resetGlucides(ctx: Context? = null) {
        glucidesDepuisResetG = 0.0
        metabolicAccumulator?.resetGlucides()
        ctx?.let { save(it) }
    }

    private fun save(ctx: Context) {
        saveStat(ctx, this)
    }
}
