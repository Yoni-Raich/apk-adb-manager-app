package com.apkmanager.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apk_manager_prefs")

/**
 * Manages persistent application preferences using DataStore.
 */
class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_LAST_PORT = intPreferencesKey("last_connection_port")
        private val KEY_IS_PAIRED = booleanPreferencesKey("is_paired")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val KEY_KEEP_WIRELESS_ON = booleanPreferencesKey("keep_wireless_debugging_on")
        private val KEY_CUSTOM_TRACKED_REPOS = stringSetPreferencesKey("custom_tracked_repos")
    }

    /** Flow of the last used connection port. */
    val lastPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_PORT] ?: 0
    }

    /** Flow of whether the device is paired. */
    val isPaired: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_PAIRED] ?: false
    }

    /** Flow of whether auto-connect is enabled. */
    val autoConnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CONNECT] ?: true
    }

    /** Flow of whether the app switches Wireless Debugging back on by itself. */
    val keepWirelessDebuggingOn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_KEEP_WIRELESS_ON] ?: true
    }

    /** Flow of custom tracked GitHub repos in format "packageName|owner/repo". */
    val customTrackedRepos: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_TRACKED_REPOS] ?: emptySet()
    }

    /** Saves the last used connection port. */
    suspend fun saveLastPort(port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_PORT] = port
        }
    }

    /** Saves the paired state. */
    suspend fun saveIsPaired(isPaired: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_PAIRED] = isPaired
        }
    }

    /** Saves the auto-connect preference. */
    suspend fun saveAutoConnect(autoConnect: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_CONNECT] = autoConnect
        }
    }

    /** Saves the keep-Wireless-Debugging-on preference. */
    suspend fun saveKeepWirelessDebuggingOn(keepOn: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KEEP_WIRELESS_ON] = keepOn
        }
    }

    /** Adds a custom tracked GitHub repository. */
    suspend fun addCustomTrackedRepo(entry: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_TRACKED_REPOS] ?: emptySet()
            prefs[KEY_CUSTOM_TRACKED_REPOS] = current + entry
        }
    }

    /** Removes a custom tracked GitHub repository. */
    suspend fun removeCustomTrackedRepo(entry: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_TRACKED_REPOS] ?: emptySet()
            prefs[KEY_CUSTOM_TRACKED_REPOS] = current - entry
        }
    }
}
