package com.gfocreation.heartrateandzoneannounce.relay

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


private val Context.dataStore by preferencesDataStore(name = "relay_prefs")

class RelayPreferenceStore(private val context: Context) {

    private val RELAY_ENABLED = booleanPreferencesKey("relay_enabled")

    val relayEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[RELAY_ENABLED] ?: false
        }

    suspend fun setRelayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RELAY_ENABLED] = enabled
        }
    }
}
