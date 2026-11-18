package com.gfocreation.heartrateandzoneannounce.misc

import android.content.Context
import android.util.Log
import com.gfocreation.heartrateandzoneannounce.model.HeartRateProvider
import com.gfocreation.heartrateandzoneannounce.model.HeartRateSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ZoneManager : responsable uniquement de la gestion des zones cardio et de la
 * consommation d'un HeartRateProvider injecté.
 *
 * Ne doit pas contenir la logique de scan/connexion/simulation BLE.
 */
class ZoneManager(
    private val context: Context,
    externalScope: CoroutineScope? = null
) {

    private val externalScopeProvided = externalScope != null
    private val scope: CoroutineScope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // private val _bpmFlow = MutableStateFlow(0)
    private val _bpmFlow = MutableSharedFlow<HeartRateSample>(
        // extraBufferCapacity = 64
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    // val bpmFlow: StateFlow<Int> = _bpmFlow
    val bpmFlow: Flow<HeartRateSample> = _bpmFlow

    private val _connectedFlow = MutableStateFlow(false)
    val connectedFlow: StateFlow<Boolean> = _connectedFlow

    private val _runningFlow = MutableStateFlow(false)
    val runningFlow: StateFlow<Boolean> = _runningFlow

    // provider is injected; ZoneManager does NOT create/own it
    private var provider: HeartRateProvider? = null

    private var bpmCollectorJob: Job? = null
    private var connectedCollectorJob: Job? = null

    fun setProvider(p: HeartRateProvider?) {
        val wasRunning = _runningFlow.value
        if (wasRunning) stop()

        provider = p

        if (p == null) {
            // clear state when provider is removed to avoid stale UI
            // _bpmFlow.value = 0
            _bpmFlow.tryEmit(
                HeartRateSample(0, System.currentTimeMillis())
            )
            _connectedFlow.value = false
            _runningFlow.value = false
            return
        }

        if (wasRunning) start()
    }


    fun start() {
        Log.d("ZoneManager", "start() called, provider=${provider?.javaClass?.simpleName}")

        val p = provider ?: return
        if (_runningFlow.value) return

        _runningFlow.value = true

        bpmCollectorJob = scope.launch {
            try {
                // p.bpmFlow.collect { v -> _bpmFlow.value = v }
                p.bpmFlow.collect { sample ->
                    _bpmFlow.tryEmit(sample)
                }
            } catch (_: CancellationException) { }
        }

        try { p.start() } catch (_: Throwable) {}


        connectedCollectorJob = scope.launch {
            try {
                p.connected.collect { c ->
                    _connectedFlow.value = c
                    // if (!c) _bpmFlow.value = 0
                    if (!c) {
                        _bpmFlow.tryEmit(
                            HeartRateSample(0, System.currentTimeMillis())
                        )
                    }
                }
            } catch (_: CancellationException) { }
        }

        // seed initial connected if available
        try { _connectedFlow.value = p.connected.value } catch (_: Throwable) { _connectedFlow.value = false }

    }

    fun stop() {
        Log.d("ZoneManager", "stop() called")

        try { provider?.stop() } catch (_: Throwable) {}

        // immediately reset public state to avoid stale values in the UI
        _runningFlow.value = false
        _connectedFlow.value = false
        // _bpmFlow.value = 0
        _bpmFlow.tryEmit(
            HeartRateSample(0, System.currentTimeMillis())
        )

        bpmCollectorJob?.cancel(); bpmCollectorJob = null
        connectedCollectorJob?.cancel(); connectedCollectorJob = null
    }


    fun dispose() {
        try { stop() } catch (_: Throwable) {}
        provider = null
        if (!externalScopeProvided) {
            try { scope.cancel() } catch (_: Throwable) {}
        }
    }
}