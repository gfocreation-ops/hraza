package com.gfocreation.heartrateandzoneannounce.model

import android.content.Context
import com.gfocreation.heartrateandzoneannounce.misc.RAPPEL_DUREE_DEFAUT
import com.gfocreation.heartrateandzoneannounce.misc.RAPPEL_LIBELLE_DEFAUT
import com.gfocreation.heartrateandzoneannounce.misc.saveRappelParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


data class RappelParam (
    var minutes: Int = RAPPEL_DUREE_DEFAUT,
    var label: String = RAPPEL_LIBELLE_DEFAUT
)

class PeriodicReminder(
    private val scope: CoroutineScope
) {

    // var enabled: Boolean = false
    var minutes: Int = RAPPEL_DUREE_DEFAUT
    var label: String = RAPPEL_LIBELLE_DEFAUT

    private val _isRunning = MutableStateFlow(false)
    val isRunningFlow: StateFlow<Boolean> = _isRunning

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSecondsFlow: StateFlow<Int> = _remainingSeconds

    var onTrigger: (() -> Unit)? = null

    private var job: Job? = null

    // temps restant mémorisé pour pause / reprise
    private var remainingMs: Long = minutes * 60_000L
    private var startTimestampMs: Long = 0L

    /* ---------------- START (reset complet) ---------------- */

    fun start() {
        // if (!enabled) return
        reset()
        resume()
    }

    /* ---------------- PAUSE ---------------- */

    fun pause() {
        if (!_isRunning.value) return

        val now = System.currentTimeMillis()
        remainingMs -= (now - startTimestampMs)
        remainingMs = remainingMs.coerceAtLeast(0)

        _isRunning.value = false
        job?.cancel()
        job = null
    }

    /* ---------------- RESUME ---------------- */

    fun resume() {
        if (_isRunning.value || remainingMs <= 0) return        // !enabled ||

        startTimestampMs = System.currentTimeMillis()
        _isRunning.value = true

        job?.cancel()
        job = scope.launch {
            while (_isRunning.value) {
                val elapsed = System.currentTimeMillis() - startTimestampMs
                val left = remainingMs - elapsed

                if (left <= 0) {
                    _remainingSeconds.value = 0
                    _isRunning.value = false
                    onTrigger?.invoke()
                    return@launch
                }

                _remainingSeconds.value = (left / 1000).toInt()
                delay(500)
            }
        }
    }

    /* ---------------- RESET ---------------- */

    fun reset() {
        pause()
        remainingMs = minutes * 60_000L
        _remainingSeconds.value = (remainingMs / 1000).toInt()
    }

    /* ---------------- TOGGLE (pause / resume) ---------------- */

//    fun togglePauseResume() {
//        if (_isRunning.value) pause() else resume()
//    }

    /* ---------------- CHANGEMENT DURÉE ---------------- */

//    fun changeMinutes(newMinutes: Int) {
//        minutes = newMinutes.coerceAtLeast(1)
//        reset()
//    }


    fun applyConfig(
        ctx: Context,
        newMinutes: Int,
        newLabel: String
        // newEnabled: Boolean
    ) {
        label = newLabel
        // enabled = newEnabled

        val wasRunning = _isRunning.value

        minutes = newMinutes.coerceAtLeast(1)

        saveRappelParam(ctx, RappelParam( minutes = minutes, label = label) )

        remainingMs = minutes * 60_000L
        _remainingSeconds.value = (remainingMs / 1000).toInt()

        if (wasRunning) {   //  && enabled
            resume()
        } else {
            pause()
        }
    }

}
