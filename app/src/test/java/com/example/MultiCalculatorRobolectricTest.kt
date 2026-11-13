package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupSerializer
import com.example.data.local.MultiCalculatorDatabase
import com.example.data.preferences.CalculatorMode
import com.example.data.repository.MultiCalculatorRepository
import com.example.di.AppContainer
import com.example.domain.math.AngleUnit
import com.example.ui.theme.MultiCalculatorTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiCalculatorRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: MultiCalculatorDatabase
    private lateinit var repository: MultiCalculatorRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MultiCalculatorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MultiCalculatorRepository(db.historyDao(), db.noteDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `history repository saves searches deletes and clears`() = runBlocking {
        repository.saveCalculation(
            expression = "123456789123456789×987654321987654321",
            result = "121932631356500531347203169112635269",
            mode = CalculatorMode.STANDARD,
            angleUnit = AngleUnit.DEG,
            precision = 32,
            isApproximated = false
        )
        repository.saveCalculation(
            expression = "sin(30)",
            result = "0.5",
            mode = CalculatorMode.SCIENTIFIC,
            angleUnit = AngleUnit.DEG,
            precision = 32,
            isApproximated = true
        )

        val all = repository.observeHistory().first()
        assertEquals(2, all.size)

        val searchExact = repository.observeHistory("121932631356500531347203169112635269").first()
        assertEquals(1, searchExact.size)

        repository.deleteHistoryEntry(searchExact.first().id)
        assertEquals(1, repository.observeHistory().first().size)

        repository.clearHistory()
        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun `notes repository creates edits pins searches and deletes`() = runBlocking {
        val first = repository.upsertNote(
            id = null,
            title = "Physics Formula",
            content = "E = mc^2",
            isPinned = false
        )
        val second = repository.upsertNote(
            id = null,
            title = "Budget Calc",
            content = "Total = 4500.50",
            isPinned = true
        )

        val ordered = repository.observeNotes().first()
        assertEquals(2, ordered.size)
        // Pinned note must appear first
        assertEquals(second.id, ordered.first().id)

        repository.upsertNote(
            id = first.id,
            title = "Physics Formula Updated",
            content = "E = mc^2 + pc",
            isPinned = false
        )
        val searched = repository.observeNotes("Physics").first()
        assertEquals(1, searched.size)
        assertEquals("Physics Formula Updated", searched.first().title)

        repository.deleteNote(first.id)
        assertEquals(1, repository.observeNotes().first().size)
    }

    @Test
    fun `json backup export and restore preserves precision and deduplicates`() = runBlocking {
        repository.saveCalculation(
            expression = "99999999999999999999+1",
            result = "100000000000000000000",
            mode = CalculatorMode.STANDARD,
            angleUnit = AngleUnit.DEG,
            precision = 32,
            isApproximated = false
        )
        repository.upsertNote(
            id = null,
            title = "Precision Check",
            content = "100000000000000000000",
            isPinned = true
        )

        val exportedJson = repository.exportAllAsJson()
        val parsed = BackupSerializer.importFromJson(exportedJson).getOrThrow()
        assertEquals(1, parsed.first.size)
        assertEquals(1, parsed.second.size)
        assertEquals("100000000000000000000", parsed.second.first().result)

        // Wipe and restore
        repository.clearHistory()
        db.noteDao().deleteById(parsed.first.first().id)
        val summary = repository.restoreFromJson(exportedJson).getOrThrow()
        assertEquals(1, summary.notesRestored)
        assertEquals(1, summary.historyRestored)
    }

    @Test
    fun `debug build uses test ad configuration and never blocks calculation flow`() {
        assertTrue(BuildConfig.USE_TEST_ADS)
        assertTrue(BuildConfig.ADMOB_BANNER_ID.startsWith("ca-app-pub-3940256099942544"))
    }

    @Test
    fun `compose ui calculates saves history and navigates all four sections`() {
        val container = AppContainer(context)
        composeRule.setContent {
            MultiCalculatorTheme {
                MultiCalculatorApp(container = container)
            }
        }
        composeRule.waitForIdle()

        // Perform 7 * 8 = 56 in UI
        composeRule.onNodeWithTag("calc_btn_7").performClick()
        composeRule.onNodeWithTag("calc_btn_mul").performClick()
        composeRule.onNodeWithTag("calc_btn_8").performClick()
        composeRule.onNodeWithTag("calc_btn_equals").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("56").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("calc_result_text").assertIsDisplayed()
        composeRule.onNodeWithText("56").assertIsDisplayed()

        // Navigate to History and verify entry persisted
        composeRule.onNodeWithTag("nav_tab_history").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("7×8").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("7×8").assertIsDisplayed()

        // Navigate to Notes and create a note
        composeRule.onNodeWithTag("nav_tab_notes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fab_new_note").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("note_title_input").performTextInput("Test Note")
        composeRule.onNodeWithTag("note_content_input").performTextInput("Saved 56")
        composeRule.onNodeWithTag("note_save_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("note_back_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Test Note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Test Note").assertIsDisplayed()

        // Navigate to Settings
        composeRule.onNodeWithTag("nav_tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_theme_dark").assertIsDisplayed()
    }
}
