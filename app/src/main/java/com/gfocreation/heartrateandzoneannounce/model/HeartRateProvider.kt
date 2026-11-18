package com.gfocreation.heartrateandzoneannounce.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Generic provider interface for heart rate sources.
 */
interface HeartRateProvider {
    // val bpmFlow: StateFlow<Int>
    val bpmFlow: Flow<HeartRateSample>
    val connected: StateFlow<Boolean>
    val isReconnecting: StateFlow<Boolean>

    fun start()
    fun stop()
    fun connect(address: String? = null)
    fun disconnect()

    fun dispose()
}
