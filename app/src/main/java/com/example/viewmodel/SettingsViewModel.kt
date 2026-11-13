package com.example.viewmodel

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.AdActionState
import com.example.ads.AdMobManager
import com.example.data.auth.GoogleAuthManager
import com.example.data.network.ConnectivityMonitor
import com.example.data.preferences.CalculatorMode
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CloudSyncStatus
import com.example.data.repository.MultiCalculatorRepository
import com.example.domain.math.AngleUnit
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val isOnline: Boolean = false,
    val isPrivacyOptionsRequired: Boolean = false,
    val rewardedAdState: AdActionState = AdActionState.Idle,
    val cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.Idle,
    val accountDisplayName: String? = null,
    val accountEmail: String? = null
)

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
}

class SettingsViewModel(
    private val repository: MultiCalculatorRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val adMobManager: AdMobManager,
    private val accountDisplayName: String? = null,
    private val accountEmail: String? = null
) : ViewModel() {

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        connectivityMonitor.isOnline,
        adMobManager.isPrivacyOptionsRequired,
        adMobManager.rewardedState,
        repository.cloudSyncStatus
    ) { prefs, online, privacyRequired, rewardedState, syncStatus ->
        SettingsUiState(
            preferences = prefs,
            isOnline = online,
            isPrivacyOptionsRequired = privacyRequired,
            rewardedAdState = rewardedState,
            cloudSyncStatus = syncStatus,
            accountDisplayName = accountDisplayName,
            accountEmail = accountEmail
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(
            accountDisplayName = accountDisplayName,
            accountEmail = accountEmail
        )
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setCalculatorMode(mode: CalculatorMode) {
        viewModelScope.launch { preferencesRepository.setCalculatorMode(mode) }
    }

    fun setAngleUnit(unit: AngleUnit) {
        viewModelScope.launch { preferencesRepository.setAngleUnit(unit) }
    }

    fun setPrecision(precision: Int) {
        viewModelScope.launch { preferencesRepository.setPrecision(precision) }
    }

    fun setUseScientificNotation(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseScientificNotation(enabled) }
    }

    fun syncNow() {
        viewModelScope.launch {
            val user = runCatching { Firebase.auth.currentUser }.getOrNull()
            repository.syncWithCloud(user)
                .onSuccess { summary ->
                    val restoredTotal = summary.notesRestored + summary.historyRestored
                    val msg = if (restoredTotal > 0) {
                        "Synced! Restored ${summary.notesRestored} notes & ${summary.historyRestored} history items"
                    } else {
                        "All notes and history are synced with Google Cloud"
                    }
                    _events.emit(SettingsEvent.ShowMessage(msg))
                }
                .onFailure { err ->
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            "Sync failed: ${err.localizedMessage ?: "Check internet connection"}"
                        )
                    )
                }
        }
    }

    fun signOut(credentialManager: CredentialManager) {
        viewModelScope.launch {
            val user = runCatching { Firebase.auth.currentUser }.getOrNull()
            if (user != null) {
                runCatching { repository.syncWithCloud(user) }
            }
            repository.clearLocalCacheForSignOut()
            GoogleAuthManager.signOut(credentialManager)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _events.emit(SettingsEvent.ShowMessage("All calculation history cleared"))
        }
    }

    suspend fun generateBackupJson(): String {
        val json = repository.exportAllAsJson()
        preferencesRepository.setLastBackupTimestamp(System.currentTimeMillis())
        return json
    }

    fun notifyBackupExported() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowMessage("Backup exported successfully"))
        }
    }

    fun notifyBackupFailed(message: String) {
        viewModelScope.launch {
            _events.emit(SettingsEvent.ShowMessage(message))
        }
    }

    fun restoreFromBackupJson(rawJson: String) {
        viewModelScope.launch {
            repository.restoreFromJson(rawJson)
                .onSuccess { summary ->
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            "Restored ${summary.notesRestored} notes and ${summary.historyRestored} history entries"
                        )
                    )
                }
                .onFailure { err ->
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            "Invalid backup file: ${err.localizedMessage ?: "Parse error"}"
                        )
                    )
                }
        }
    }

    fun showPrivacyOptions(activity: Activity) {
        adMobManager.showPrivacyOptionsForm(activity) { err ->
            if (err != null) {
                viewModelScope.launch {
                    _events.emit(SettingsEvent.ShowMessage("Consent options unavailable: $err"))
                }
            }
        }
    }

    fun watchVoluntaryRewardedAd(activity: Activity) {
        adMobManager.showRewardedAd(
            activity = activity,
            onRewardEarned = {
                viewModelScope.launch {
                    preferencesRepository.incrementRewardedSupportCount()
                    _events.emit(SettingsEvent.ShowMessage("Thank you for supporting Multi Calculator!"))
                }
            },
            onUnavailable = { msg ->
                viewModelScope.launch {
                    _events.emit(SettingsEvent.ShowMessage(msg))
                }
            }
        )
    }
}
