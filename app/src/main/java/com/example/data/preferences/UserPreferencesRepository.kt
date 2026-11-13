package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.math.AngleUnit
import com.example.domain.math.PrecisionMathEngine
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "multi_calc_prefs")

enum class CalculatorMode(val label: String) {
    STANDARD("Standard"),
    SCIENTIFIC("Scientific")
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val calculatorMode: CalculatorMode = CalculatorMode.STANDARD,
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val precision: Int = PrecisionMathEngine.DEFAULT_PRECISION,
    val useScientificNotation: Boolean = false,
    val rewardedSupportCount: Int = 0,
    val lastBackupTimestampMillis: Long? = null
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CALCULATOR_MODE = stringPreferencesKey("calculator_mode")
        val ANGLE_UNIT = stringPreferencesKey("angle_unit")
        val PRECISION = intPreferencesKey("precision")
        val USE_SCIENTIFIC_NOTATION = booleanPreferencesKey("use_scientific_notation")
        val REWARDED_SUPPORT_COUNT = intPreferencesKey("rewarded_support_count")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeMode = prefs[Keys.THEME_MODE]?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name == raw }
        } ?: ThemeMode.SYSTEM

        val calculatorMode = prefs[Keys.CALCULATOR_MODE]?.let { raw ->
            CalculatorMode.entries.firstOrNull { it.name == raw }
        } ?: CalculatorMode.STANDARD

        val angleUnit = prefs[Keys.ANGLE_UNIT]?.let { raw ->
            AngleUnit.entries.firstOrNull { it.name == raw }
        } ?: AngleUnit.DEG

        val precision = (prefs[Keys.PRECISION] ?: PrecisionMathEngine.DEFAULT_PRECISION)
            .coerceIn(PrecisionMathEngine.MIN_PRECISION, PrecisionMathEngine.MAX_PRECISION)

        UserPreferences(
            themeMode = themeMode,
            calculatorMode = calculatorMode,
            angleUnit = angleUnit,
            precision = precision,
            useScientificNotation = prefs[Keys.USE_SCIENTIFIC_NOTATION] ?: false,
            rewardedSupportCount = prefs[Keys.REWARDED_SUPPORT_COUNT] ?: 0,
            lastBackupTimestampMillis = prefs[Keys.LAST_BACKUP_TIMESTAMP]
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCalculatorMode(mode: CalculatorMode) {
        context.dataStore.edit { it[Keys.CALCULATOR_MODE] = mode.name }
    }

    suspend fun setAngleUnit(unit: AngleUnit) {
        context.dataStore.edit { it[Keys.ANGLE_UNIT] = unit.name }
    }

    suspend fun setPrecision(precision: Int) {
        val clamped = precision.coerceIn(
            PrecisionMathEngine.MIN_PRECISION,
            PrecisionMathEngine.MAX_PRECISION
        )
        context.dataStore.edit { it[Keys.PRECISION] = clamped }
    }

    suspend fun setUseScientificNotation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_SCIENTIFIC_NOTATION] = enabled }
    }

    suspend fun incrementRewardedSupportCount() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.REWARDED_SUPPORT_COUNT] ?: 0
            prefs[Keys.REWARDED_SUPPORT_COUNT] = current + 1
        }
    }

    suspend fun setLastBackupTimestamp(timestampMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_TIMESTAMP] = timestampMillis }
    }
}
