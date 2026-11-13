package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.preferences.CalculatorMode
import com.example.data.repository.CloudSyncStatus
import com.example.domain.math.AngleUnit
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onModeChange: (CalculatorMode) -> Unit,
    onAngleUnitChange: (AngleUnit) -> Unit,
    onPrecisionChange: (Int) -> Unit,
    onScientificNotationChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onGenerateBackupJson: suspend () -> String,
    onBackupExported: () -> Unit,
    onBackupFailed: (String) -> Unit,
    onRestoreBackupJson: (String) -> Unit,
    onShowPrivacyOptions: (Activity) -> Unit,
    onWatchRewardedAd: (Activity) -> Unit,
    onSyncNow: () -> Unit = {},
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    var showClearConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                val json = onGenerateBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open output file")
                }
            }.onSuccess {
                onBackupExported()
            }.onFailure { err ->
                onBackupFailed("Export failed: ${err.localizedMessage ?: "I/O error"}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Unable to read selected file")
                }
            }.onSuccess { rawJson ->
                onRestoreBackupJson(rawJson)
            }.onFailure { err ->
                onBackupFailed("Import failed: ${err.localizedMessage ?: "I/O error"}")
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Theme, arbitrary precision, backup, ads, and legal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Appearance / Theme
            item {
                SettingsSectionCard(title = "Appearance") {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.preferences.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(mode.label.replace(" Mode", "").replace(" Default", "")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_theme_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 2. Calculator Engine Preferences
            item {
                SettingsSectionCard(title = "Calculator & Precision") {
                    Text("Default Mode", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalculatorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.preferences.calculatorMode == mode,
                                onClick = { onModeChange(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    Text("Trigonometric Angle Unit", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AngleUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = uiState.preferences.angleUnit == unit,
                                onClick = { onAngleUnitChange(unit) },
                                label = { Text(if (unit == AngleUnit.DEG) "Degrees (DEG)" else "Radians (RAD)") }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Decimal Precision", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Significant digits for division & scientific functions (exact integers have no 15-digit limit).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${uiState.preferences.precision} digits",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Slider(
                        value = uiState.preferences.precision.toFloat(),
                        onValueChange = { onPrecisionChange(it.roundToInt()) },
                        valueRange = 16f..100f,
                        steps = 13,
                        modifier = Modifier.testTag("settings_precision_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scientific Notation Display", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Display huge or tiny decimals in E-notation while always copying full digits.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.preferences.useScientificNotation,
                            onCheckedChange = onScientificNotationChange,
                            modifier = Modifier.testTag("settings_sci_notation_switch")
                        )
                    }
                }
            }

            // 3. Connectivity, Cloud Sync Status & Local Backup/Restore
            item {
                SettingsSectionCard(title = "Backup, Sync & Offline Status") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = if (uiState.isOnline) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                        Text(
                            text = if (uiState.isOnline) {
                                stringResource(R.string.status_online)
                            } else {
                                stringResource(R.string.status_offline)
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (uiState.cloudSyncStatus is CloudSyncStatus.Syncing) {
                                    Icons.Filled.CloudSync
                                } else {
                                    Icons.Filled.CloudDone
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = uiState.accountDisplayName?.takeIf { it.isNotBlank() }
                                        ?: "Google Account Connected",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                uiState.accountEmail?.takeIf { it.isNotBlank() }?.let { email ->
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val syncLabel = when (val status = uiState.cloudSyncStatus) {
                                    CloudSyncStatus.Idle -> "Cloud Sync Ready"
                                    CloudSyncStatus.Syncing -> "Syncing notes & history..."
                                    is CloudSyncStatus.Synced ->
                                        "Synced (${status.notesCount} notes, ${status.historyCount} history) · ${dateFormatter.format(Date(status.lastSyncedMillis))}"
                                    is CloudSyncStatus.Error ->
                                        "Sync pending (${status.message}) — offline changes stay queued safely"
                                }
                                Text(
                                    text = syncLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSyncNow,
                            enabled = uiState.cloudSyncStatus !is CloudSyncStatus.Syncing,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_sync_now_btn")
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (uiState.cloudSyncStatus is CloudSyncStatus.Syncing) "Syncing..." else "Sync Now")
                        }
                        OutlinedButton(
                            onClick = { showSignOutConfirm = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_sign_out_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Sign Out")
                        }
                    }

                    uiState.preferences.lastBackupTimestampMillis?.let { ts ->
                        Text(
                            text = "Last JSON backup: ${dateFormatter.format(Date(ts))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val fileName = "multi_calculator_backup_${System.currentTimeMillis()}.json"
                                exportLauncher.launch(fileName)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_export_backup_btn")
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_import_backup_btn")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Restore JSON")
                        }
                    }
                }
            }

            // 4. AdMob Monetization & Privacy Consent
            item {
                SettingsSectionCard(title = "Ads & Consent") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                !BuildConfig.ADS_ENABLED -> "Ads Status: Turned Off (Ad-Free Mode)"
                                BuildConfig.USE_TEST_ADS -> "AdMob Mode: Test Ads (Debug)"
                                else -> "AdMob Mode: Production Release"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.preferences.rewardedSupportCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Supported ×${uiState.preferences.rewardedSupportCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (BuildConfig.ADS_ENABLED) {
                        Button(
                            onClick = { activity?.let(onWatchRewardedAd) },
                            enabled = uiState.isOnline && activity != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_rewarded_ad_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolunteerActivism,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Support Multi Calculator (Watch Voluntary Ad)")
                        }
                    } else {
                        Text(
                            text = "Test ads are currently disabled. Configure production AdMob IDs for release when ready to monetize.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.isPrivacyOptionsRequired && activity != null) {
                        OutlinedButton(
                            onClick = { onShowPrivacyOptions(activity) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Manage Ad Consent Preferences")
                        }
                    }
                }
            }

            // 5. Data Management & Legal
            item {
                SettingsSectionCard(title = "Data & Legal") {
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_history_btn")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Clear Calculation History")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    TextButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Privacy Policy", modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { showTermsDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Terms & Conditions", modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_about_btn")
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("About Multi Calculator (v${BuildConfig.VERSION_NAME})", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all calculation history?") },
            text = { Text("This removes every calculation entry from this device and your synced Google Cloud history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out of Google?") },
            text = { Text("Your synced Notes and Calculation History remain safely stored in your Google account and will restore automatically next time you sign in.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    }
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showPrivacyDialog) {
        LegalInfoDialog(
            title = "Privacy Policy",
            body = """
                Multi Calculator stores your calculations, notes, and app preferences locally on your device by default.

                • Local Storage: Your notes and calculation history reside in an on-device Room database. You can export or delete them at any time.
                • Cloud Sync: Optional Google Account Cloud Sync (Firebase Authentication & Cloud Firestore) is disabled unless explicitly configured and signed in by the user. When enabled, data is isolated under your authenticated User ID, and retention depends on service/account availability.
                • Advertising: Multi Calculator integrates Google AdMob. AdMob may process your device's Advertising ID and IP-derived diagnostics according to Google's Privacy Policy and your UMP consent selections.
                • Zero Unnecessary Permissions: The app does not access Contacts, Camera, Microphone, Location, or broad Storage permissions.
            """.trimIndent(),
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showTermsDialog) {
        LegalInfoDialog(
            title = "Terms & Conditions",
            body = """
                By using Multi Calculator, you agree to use the application for lawful personal, educational, and professional calculation and note-taking purposes.

                • Precision & Limits: Multi Calculator uses arbitrary-precision arithmetic within device memory and CPU safeguards. Always verify mission-critical engineering or financial outputs independently.
                • Data Responsibility: Offline data remains on your device unless exported via JSON backup or synced to a configured cloud account. Cloud storage is not guaranteed for permanent lifetime retention.
                • Advertisements: Banner, limited interstitial, and voluntary rewarded ads may appear outside active calculation keypads when online.
            """.trimIndent(),
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.multi_calc_logo),
                        contentDescription = stringResource(R.string.cd_app_logo),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text("Multi Calculator", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Text(
                    "High-precision Standard & Scientific Calculator with integrated Notepad, Calculation History, offline Room database, JSON backup/restore, and AdMob monetization."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun LegalInfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
