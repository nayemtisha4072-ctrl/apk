package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ads.AdBannerBar
import com.example.di.AppContainer
import com.example.ui.navigation.CalculatorRoute
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.NoteEditorRoute
import com.example.ui.navigation.NotesRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MultiCalculatorTheme
import com.example.viewmodel.CalculatorEvent
import com.example.viewmodel.CalculatorViewModel
import com.example.viewmodel.HistoryEvent
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.NotesEvent
import com.example.viewmodel.NotesViewModel
import com.example.viewmodel.SettingsEvent
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = AppContainer(applicationContext)
        container.adMobManager.gatherConsentAndInitialize(this)

        setContent {
            val prefs by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = com.example.data.preferences.UserPreferences()
            )
            MultiCalculatorTheme(themeMode = prefs.themeMode) {
                MultiCalculatorApp(
                    container = container,
                    activity = this
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::container.isInitialized) {
            container.connectivityMonitor.unregister()
        }
    }
}

private data class TopLevelTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val testTag: String
)

@Composable
fun MultiCalculatorApp(
    container: AppContainer,
    activity: MainActivity? = null
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val calculatorViewModel: CalculatorViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CalculatorViewModel(
                    mathEngine = container.mathEngine,
                    repository = container.repository,
                    preferencesRepository = container.preferencesRepository
                )
            }
        }
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(container.repository) }
        }
    )
    val notesViewModel: NotesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NotesViewModel(container.repository) }
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    repository = container.repository,
                    preferencesRepository = container.preferencesRepository,
                    connectivityMonitor = container.connectivityMonitor,
                    adMobManager = container.adMobManager
                )
            }
        }
    )

    val savedToNoteMsg = stringResource(R.string.saved_to_note)

    LaunchedEffect(Unit) {
        calculatorViewModel.events.collect { event ->
            when (event) {
                is CalculatorEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is CalculatorEvent.OpenSavedNote -> {
                    snackbarHostState.showSnackbar(savedToNoteMsg)
                    navController.navigate(NoteEditorRoute(noteId = event.noteId))
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        historyViewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(Unit) {
        notesViewModel.events.collect { event ->
            when (event) {
                is NotesEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is NotesEvent.NoteDeletedAndClosed -> {
                    navController.popBackStack()
                    snackbarHostState.showSnackbar("Note deleted")
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val tabs = listOf(
        TopLevelTab(
            label = stringResource(R.string.nav_calculator),
            selectedIcon = Icons.Filled.Calculate,
            unselectedIcon = Icons.Outlined.Calculate,
            route = CalculatorRoute,
            testTag = "nav_tab_calculator"
        ),
        TopLevelTab(
            label = stringResource(R.string.nav_notes),
            selectedIcon = Icons.Filled.Description,
            unselectedIcon = Icons.Outlined.Description,
            route = NotesRoute,
            testTag = "nav_tab_notes"
        ),
        TopLevelTab(
            label = stringResource(R.string.nav_history),
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
            route = HistoryRoute,
            testTag = "nav_tab_history"
        ),
        TopLevelTab(
            label = stringResource(R.string.nav_settings),
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            route = SettingsRoute,
            testTag = "nav_tab_settings"
        )
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isInNoteEditor = currentDestination?.hasRoute<NoteEditorRoute>() == true
    val isInCalculator = currentDestination?.hasRoute<CalculatorRoute>() == true || currentDestination == null
    val isOnline by container.connectivityMonitor.isOnline.collectAsStateWithLifecycle()

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail && !isInNoteEditor) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Show non-intrusive banner ad only on secondary tabs (never over active calculator keys)
                        if (!isInCalculator) {
                            AdBannerBar(
                                adMobManager = container.adMobManager,
                                isOnline = isOnline
                            )
                        }
                        NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                            tabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    is CalculatorRoute -> currentDestination?.hasRoute<CalculatorRoute>() == true
                                    is NotesRoute -> currentDestination?.hasRoute<NotesRoute>() == true
                                    is HistoryRoute -> currentDestination?.hasRoute<HistoryRoute>() == true
                                    is SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && !isInNoteEditor) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is CalculatorRoute -> currentDestination?.hasRoute<CalculatorRoute>() == true
                                is NotesRoute -> currentDestination?.hasRoute<NotesRoute>() == true
                                is HistoryRoute -> currentDestination?.hasRoute<HistoryRoute>() == true
                                is SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (useNavRail && !isInNoteEditor && !isInCalculator) {
                        AdBannerBar(
                            adMobManager = container.adMobManager,
                            isOnline = isOnline
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        NavHost(
                            navController = navController,
                            startDestination = CalculatorRoute
                        ) {
                            composable<CalculatorRoute> {
                                val state by calculatorViewModel.uiState.collectAsStateWithLifecycle()
                                CalculatorScreen(
                                    uiState = state,
                                    onAppendToken = calculatorViewModel::appendToken,
                                    onToggleSign = calculatorViewModel::toggleSign,
                                    onBackspace = calculatorViewModel::backspace,
                                    onClearAll = calculatorViewModel::clearAll,
                                    onEvaluate = calculatorViewModel::evaluateAndSave,
                                    onModeChange = calculatorViewModel::setCalculatorMode,
                                    onToggleAngleUnit = calculatorViewModel::toggleAngleUnit,
                                    onToggleScientificNotation = calculatorViewModel::toggleScientificNotation,
                                    onSaveToNote = calculatorViewModel::saveCurrentToNote,
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable<NotesRoute> {
                                val notes by notesViewModel.notes.collectAsStateWithLifecycle()
                                val query by notesViewModel.searchQuery.collectAsStateWithLifecycle()
                                NotesScreen(
                                    notes = notes,
                                    searchQuery = query,
                                    onSearchQueryChange = notesViewModel::updateSearchQuery,
                                    onCreateNote = {
                                        navController.navigate(NoteEditorRoute(noteId = null))
                                    },
                                    onOpenNote = { noteId ->
                                        navController.navigate(NoteEditorRoute(noteId = noteId))
                                    },
                                    onTogglePin = notesViewModel::togglePinned,
                                    onDeleteNote = notesViewModel::deleteNote,
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable<NoteEditorRoute> { entry ->
                                val route = entry.toRoute<NoteEditorRoute>()
                                LaunchedEffect(route.noteId) {
                                    notesViewModel.openNote(route.noteId)
                                }
                                val editorState by notesViewModel.editorState.collectAsStateWithLifecycle()
                                val historyItems by historyViewModel.historyItems.collectAsStateWithLifecycle()
                                NoteEditorScreen(
                                    editorState = editorState,
                                    latestCalculation = historyItems.firstOrNull(),
                                    onTitleChange = notesViewModel::updateTitle,
                                    onContentChange = notesViewModel::updateContent,
                                    onTogglePin = notesViewModel::toggleEditorPinned,
                                    onSaveNow = { notesViewModel.saveNoteNow(showConfirmation = true) },
                                    onFlushBeforeExit = notesViewModel::flushBeforeExit,
                                    onInsertCalculation = notesViewModel::appendCalculationToEditor,
                                    onDeleteNote = notesViewModel::deleteActiveNote,
                                    onNavigateBack = { navController.popBackStack() },
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable<HistoryRoute> {
                                val items by historyViewModel.historyItems.collectAsStateWithLifecycle()
                                val query by historyViewModel.searchQuery.collectAsStateWithLifecycle()
                                HistoryScreen(
                                    historyItems = items,
                                    searchQuery = query,
                                    onSearchQueryChange = historyViewModel::updateSearchQuery,
                                    onReuseCalculation = { entry ->
                                        calculatorViewModel.loadExpressionFromHistory(
                                            expression = entry.expression,
                                            result = entry.result
                                        )
                                        navigateToTab(CalculatorRoute)
                                    },
                                    onSaveToNote = historyViewModel::saveEntryToNote,
                                    onDeleteEntry = historyViewModel::deleteEntry,
                                    onClearAll = historyViewModel::clearAllHistory,
                                    onShowMessage = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable<SettingsRoute> {
                                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                                SettingsScreen(
                                    uiState = settingsState,
                                    onThemeChange = settingsViewModel::setThemeMode,
                                    onModeChange = settingsViewModel::setCalculatorMode,
                                    onAngleUnitChange = settingsViewModel::setAngleUnit,
                                    onPrecisionChange = settingsViewModel::setPrecision,
                                    onScientificNotationChange = settingsViewModel::setUseScientificNotation,
                                    onClearHistory = settingsViewModel::clearHistory,
                                    onGenerateBackupJson = {
                                        val json = settingsViewModel.generateBackupJson()
                                        activity?.let {
                                            container.adMobManager.maybeShowInterstitialAfterMilestone(it)
                                        }
                                        json
                                    },
                                    onBackupExported = settingsViewModel::notifyBackupExported,
                                    onBackupFailed = settingsViewModel::notifyBackupFailed,
                                    onRestoreBackupJson = settingsViewModel::restoreFromBackupJson,
                                    onShowPrivacyOptions = settingsViewModel::showPrivacyOptions,
                                    onWatchRewardedAd = settingsViewModel::watchVoluntaryRewardedAd
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
