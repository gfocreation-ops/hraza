package com.gfocreation.heartrateandzoneannounce.misc

import com.gfocreation.heartrateandzoneannounce.DEBUG_CAPTEUR_DEMO_FIXE
import com.gfocreation.heartrateandzoneannounce.DEBUG_CAPTEUR_DEMO_LINEAIRE
import com.gfocreation.heartrateandzoneannounce.DEBUG_CAPTEUR_DEMO_VALEUR_INIT
import com.gfocreation.heartrateandzoneannounce.DEBUG_CAPTEUR_DEMO_AVEC_ZERO
import com.gfocreation.heartrateandzoneannounce.model.HeartRateProvider
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * KakeHeartRateSensor : capteur simulé configurable.
 *
 * - Conserve l'API HeartRateProvider.
 * - Accepte un CoroutineScope externe (optionnel). Si aucun scope externe fourni,
 *   crée un scope interne (penser à appeler dispose()).
 *
 * Fonctionnalités :
 * - start()/stop() pour commencer/arrêter l'émission.
 * - connect(address: String? = null) simule une connexion (with optional delay).
 * - Mode de génération : random autour d'une moyenne, ou pattern (sequence) si fourni.
 * - Paramètres :
 *   - baseBpm : bpm moyen initial
 *   - variability : amplitude max de variation instantanée
 *   - updateIntervalMs : fréquence des émissions
 *   - pattern : optional IntArray sequence of BPM used cyclically (overrides random)
 */
class KakeHeartRateSensor(
    private val externalScope: CoroutineScope? = null,
    // private val baseBpm: Int = if (CAPTEUR_DEMO_LINEAIRE) 60 else 75,
    private val baseBpm: Int = DEBUG_CAPTEUR_DEMO_VALEUR_INIT,
    private val variability: Int = 10,
    private val updateIntervalMs: Long = 1750,      // 2000,
    private val pattern: IntArray? = null, // optional explicit pattern
    private val simulatedConnectDelayMs: Long = 800L
) : HeartRateProvider {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val externalScopeProvided = externalScope != null

    // private val _bpm = MutableStateFlow(0)
    private val _bpm = MutableSharedFlow<HeartRateSample>(
        // extraBufferCapacity = 64
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    // override val bpmFlow: StateFlow<Int> = _bpm.asStateFlow()
    override val bpmFlow: Flow<HeartRateSample> = _bpm

    private var lastBpm: Int = baseBpm

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    override val isReconnecting: StateFlow<Boolean> = MutableStateFlow(false)

    private var job: Job? = null
    private var patternIndex = 0
    private val rng = Random(System.currentTimeMillis())

    // Pour avec DEBUG_SIMUL_LINEAIRE
    private val lin_max = 170       // 199
    private val lin_min = 50        // 36
    private var lin_pas = -4        // 6


    // Battery simulation
    var onBatteryLevel: ((Int) -> Unit)? = null
    private var batteryLevel = 100
    private val batteryDrainStep = 1          // % perdu
    private val batteryDrainEvery = 30         // toutes les 30 émissions BPM
    private var batteryTick = 0


    // Optional callback hook (kept for parity with RealHeartRateManager)
    var onBpmCallback: ((Int) -> Unit)? = null

    override fun start() {
        // si déjà actif, rien à faire
        if (job?.isActive == true) return

        // considérer le capteur comme "connecté" dès le démarrage de la simulation
        _connected.value = true

        onBatteryLevel?.invoke(batteryLevel) // émission immédiate

        // initialise une valeur BPM de départ si nécessaire
        // if (_bpm.value <= 0) {
        // _bpm.value = pattern?.getOrNull(0) ?: baseBpm
//        _bpm.emit(HeartRateSample(pattern?.getOrNull(0) ?: baseBpm, System.currentTimeMillis()))
        scope.launch {
            _bpm.emit(HeartRateSample(pattern?.getOrNull(0) ?: baseBpm, System.currentTimeMillis()))
        }

        job = scope.launch {
            while (isActive) {
                // produit et émet la prochaine valeur BPM
                val bpm = produceNextBpm()
                // _bpm.value = bpm
                _bpm.emit(HeartRateSample(bpm, System.currentTimeMillis()))
                onBpmCallback?.invoke(bpm)

                // 🔋 simulate battery drain
                batteryTick++
                if (batteryTick >= batteryDrainEvery) {
                    batteryTick = 0
                    if (batteryLevel > 5) {
                        batteryLevel -= batteryDrainStep
                        onBatteryLevel?.invoke(batteryLevel)
                    }
                }

                delay(updateIntervalMs)
            }
        }
    }


    override fun stop() {
        job?.cancel()
        job = null
    }

    override fun connect(address: String?) {
        // simulate asynchronous connection
        scope.launch {

            // small delay to simulate BLE connect handshake
            delay(simulatedConnectDelayMs)
            _connected.value = true

            // initialize with a sensible first value
            val first = pattern?.getOrNull(0) ?: baseBpm

            // _bpm.value = first
            // _bpm.emit(HeartRateSample(first, System.currentTimeMillis()))
            val bpm = pattern?.getOrNull(0) ?: baseBpm
            lastBpm = bpm
            _bpm.emit(
                HeartRateSample(
                    bpm = bpm,
                    timestamp = System.currentTimeMillis()
                )
            )

            batteryLevel = 100
            onBatteryLevel?.invoke(batteryLevel)

        }
    }

    override fun disconnect() {
        scope.launch {
            _connected.value = false
            // _bpm.value = 0
            _bpm.emit(HeartRateSample(0, System.currentTimeMillis()))
            onBatteryLevel?.invoke(0)
        }
    }

    override fun dispose() {
        stop()
        if (!externalScopeProvided) {
            try {
                scope.cancel()
            } catch (_: Throwable) {}
        }
    }

    private fun produceNextBpm(): Int {
        // If pattern supplied, follow it cyclically
        pattern?.let {
            if (it.isEmpty()) return baseBpm
            val value = it[patternIndex % it.size]
            patternIndex++
            return value.coerceAtLeast(30).coerceAtMost(240)
        }

        if (DEBUG_CAPTEUR_DEMO_FIXE) {
            return baseBpm
        }

        if (DEBUG_CAPTEUR_DEMO_AVEC_ZERO) {
            if (Math.random() > 0.7) {
                return 0
            }
        }

        // Random walk around baseBpm to create smoother fluctuations
        val last = lastBpm      // _bpm.value.takeIf { it > 0 } ?: baseBpm
        val delta = (rng.nextDouble() * variability * 2 - variability).roundToInt()
        val jitter = (rng.nextDouble() * 2 - 1).roundToInt() // small extra jitter -1/0/1
        var next = last + delta + jitter

        // slowly drift base towards configured baseBpm to avoid runaway
        var drift = last
        if (DEBUG_CAPTEUR_DEMO_LINEAIRE) {
            drift = last + lin_pas
            if (drift >= lin_max) {
                lin_pas = -lin_pas
                drift = lin_max
            } else if (drift <= lin_min) {
                lin_pas = -lin_pas
                drift = lin_min
            }

        } else {
            drift = when {
                next < 30 -> 30
                next > 240 -> 240
                else -> next
            }
        }

        if (drift != next) {
            next = drift
        }

        return drift
    }
}
