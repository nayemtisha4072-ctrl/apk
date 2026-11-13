package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.MultiCalculatorDatabase
import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.preferences.CalculatorMode
import com.example.data.remote.FirestoreSyncRepository
import com.example.domain.math.AngleUnit
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun `owner can create update observe and delete cloud notes`() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val syncRepo = FirestoreSyncRepository(firestore, auth)
        val noteId = UUID.randomUUID().toString()
        val original = NoteEntity(
            id = noteId,
            title = "Initial Note",
            content = "Exact value: 121932631356500531347203169112635269",
            isPinned = true,
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_000_000L
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            syncRepo.upsertNote(original)
        }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            syncRepo.observeCloudNotes().first { list -> list.any { it.id == noteId } }
        }
        assertEquals("Initial Note", observed.first { it.id == noteId }.title)

        val updated = original.copy(
            title = "Updated Note Title",
            updatedAtMillis = 1_700_000_050_000L
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            syncRepo.upsertNote(updated)
        }

        val observedUpdated = withTimeout(FLOW_TIMEOUT_MS) {
            syncRepo.observeCloudNotes().first { list ->
                list.any { it.id == noteId && it.title == "Updated Note Title" }
            }
        }
        assertTrue(observedUpdated.any { it.id == noteId && it.title == "Updated Note Title" })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            syncRepo.deleteNote(noteId)
        }
    }

    @Test
    fun `reinstall sync restores cloud notes and history into empty local room database`() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val syncRepo = FirestoreSyncRepository(firestore, auth)
        val noteId = UUID.randomUUID().toString()
        val historyId = UUID.randomUUID().toString()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            syncRepo.upsertNote(
                NoteEntity(
                    id = noteId,
                    title = "Reinstall Recovery Note",
                    content = "Preserved in cloud for $uid",
                    isPinned = true,
                    createdAtMillis = 1_700_000_000_000L,
                    updatedAtMillis = 1_700_000_010_000L
                )
            )
            syncRepo.upsertHistoryEntry(
                CalculationHistoryEntity(
                    id = historyId,
                    expression = "123456789123456789×987654321987654321",
                    result = "121932631356500531347203169112635269",
                    calculatorMode = CalculatorMode.STANDARD.name,
                    angleUnit = AngleUnit.DEG.name,
                    precision = 32,
                    isApproximated = false,
                    createdAtMillis = 1_700_000_000_000L,
                    updatedAtMillis = 1_700_000_000_000L
                )
            )
        }

        // Simulate fresh app reinstall with a brand-new empty Room database
        val context = ApplicationProvider.getApplicationContext<Context>()
        val freshDb = Room.inMemoryDatabaseBuilder(context, MultiCalculatorDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val freshRepo = MultiCalculatorRepository(
                historyDao = freshDb.historyDao(),
                noteDao = freshDb.noteDao(),
                syncRepository = syncRepo,
                isSignedInProvider = { auth.currentUser != null }
            )

            val summary = withTimeout(DEFAULT_TIMEOUT_MS) {
                freshRepo.syncWithCloud(auth.currentUser).getOrThrow()
            }
            assertTrue(summary.notesRestored >= 1)
            assertTrue(summary.historyRestored >= 1)

            val restoredNotes = freshRepo.observeNotes().first()
            val restoredHistory = freshRepo.observeHistory().first()
            assertTrue(restoredNotes.any { it.id == noteId && it.title == "Reinstall Recovery Note" })
            assertTrue(restoredHistory.any { it.id == historyId && it.result == "121932631356500531347203169112635269" })
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun `cross user access to another users cloud notes fails with permission denied`() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceSync = FirestoreSyncRepository(firestore, auth)
        val noteId = UUID.randomUUID().toString()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceSync.upsertNote(
                NoteEntity(
                    id = noteId,
                    title = "Alice Private Note",
                    content = "Secret",
                    isPinned = false,
                    createdAtMillis = 1000L,
                    updatedAtMillis = 1000L
                )
            )
        }

        signInTestUser(BOB_EMAIL)
        var caught: FirebaseFirestoreException? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("notes").document(noteId)
                    .get()
                    .await()
            }
        } catch (e: Exception) {
            caught = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    @Test
    fun `unauthenticated firestore query fails with permission denied`() = runBlocking {
        auth.signOut()
        var caught: FirebaseFirestoreException? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("users").document("some_uid")
                    .collection("notes")
                    .snapshots()
                    .first()
            }
        } catch (e: Exception) {
            caught = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_multicalc@test.com"
        const val BOB_EMAIL = "bob_multicalc@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
