package com.gfocreation.heartrateandzoneannounce

import android.app.Application
import com.gfocreation.heartrateandzoneannounce.app.AppContainer
import com.gfocreation.heartrateandzoneannounce.relay.RelayStateManager
import com.gfocreation.heartrateandzoneannounce.relay.RelayPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class Hraza : Application() {

    lateinit var appContainer: AppContainer
        private set

    lateinit var relayPreferenceStore: RelayPreferenceStore
        private set

    lateinit var relayStateManager: RelayStateManager
        private set

    override fun onCreate() {
        super.onCreate()

        appContainer = AppContainer(this)

        relayPreferenceStore = RelayPreferenceStore(this)

        relayStateManager = RelayStateManager(
            context = this,
            heartRateManager = appContainer.realHeartRateManager,
            preferenceStore = relayPreferenceStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        )

        relayStateManager.start()
    }

}
