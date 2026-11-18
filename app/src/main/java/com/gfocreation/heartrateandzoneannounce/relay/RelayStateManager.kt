package com.gfocreation.heartrateandzoneannounce.relay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.gfocreation.heartrateandzoneannounce.misc.RealHeartRateManager
import com.gfocreation.heartrateandzoneannounce.service.BleHeartRateRelayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class RelayStateManager(
    private val context: Context,
    private val heartRateManager: RealHeartRateManager,
    private val preferenceStore: RelayPreferenceStore,
    private val scope: CoroutineScope
) {

    private var serviceRunning = false

    fun start() {

        scope.launch {
            combine(
                preferenceStore.relayEnabledFlow,
                heartRateManager.connected
            ) { enabled: Boolean, connected: Boolean ->
                enabled && connected
            }
            .collect { shouldRelay ->

                if (shouldRelay && !serviceRunning) {
                    startService()
                } else if (!shouldRelay && serviceRunning) {
                    stopService()
                }
            }
        }

    }

    private fun startService() {
        val intent = Intent(context, BleHeartRateRelayService::class.java)
        ContextCompat.startForegroundService(context, intent)
        serviceRunning = true
    }

    private fun stopService() {
        context.stopService(Intent(context, BleHeartRateRelayService::class.java))
        serviceRunning = false
    }
}
