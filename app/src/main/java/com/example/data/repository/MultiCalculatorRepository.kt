package com.example.data.repository

import android.util.Log
import com.example.data.backup.BackupSerializer
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.preferences.CalculatorMode
import com.example.data.remote.FirestoreSyncRepository
import com.example.domain.math.AngleUnit
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

data class RestoreSummary(
    val notesRestored: Int,
    val historyRestored: Int
)

sealed interface CloudSyncStatus {
    data object Idle : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val lastSyncedMillis: Long, val notesCount: Int, val historyCount: Int) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}

class MultiCalculatorRepository(
    private val historyDao: HistoryDao,
    private val noteDao: NoteDao,
    private val syncRepository: FirestoreSyncRepository? = null,
    private val isSignedInProvider: () -> Boolean = { false }
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cloudSyncStatus = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle)
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = _cloudSyncStatus.asStateFlow()

    private fun activeSyncRepo(): FirestoreSyncRepository? {
        return if (isSignedInProvider()) syncRepository else null
    }

    fun observeHistory(query: String = ""): Flow<List<CalculationHistoryEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) historyDao.observeAll() else historyDao.search(trimmed)
    }

    fun observeNotes(query: String = ""): Flow<List<NoteEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) noteDao.observeAll() else noteDao.search(trimmed)
    }

    suspend fun getNoteById(id: String): NoteEntity? = noteDao.getById(id)

    suspend fun clearLocalCacheForSignOut() {
        historyDao.clearAll()
        noteDao.clearAll()
        _cloudSyncStatus.value = CloudSyncStatus.Idle
    }

    suspend fun saveCalculation(
        expression: String,
        result: String,
        mode: CalculatorMode,
        angleUnit: AngleUnit,
        precision: Int,
        isApproximated: Boolean
    ) {
        val latest = historyDao.getLatest()
        if (latest != null && latest.expression == expression && latest.result == result) {
            return
        }
        val now = System.currentTimeMillis()
        val entry = CalculationHistoryEntity(
            id = UUID.randomUUID().toString(),
            expression = expression,
            result = result,
            calculatorMode = mode.name,
            angleUnit = angleUnit.name,
            precision = precision,
            isApproximated = isApproximated,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        historyDao.insert(entry)

        val remote = activeSyncRepo()
        if (remote != null) {
            syncScope.launch {
                runCatching { remote.upsertHistoryEntry(entry) }
                    .onFailure { Log.w(TAG, "Deferred cloud history upload: ${it.message}") }
            }
        }
    }

    suspend fun deleteHistoryEntry(id: String) {
        historyDao.deleteById(id)
        val remote = activeSyncRepo()
        if (remote != null) {
            syncScope.launch {
                runCatching { remote.deleteHistoryEntry(id) }
                    .onFailure { Log.w(TAG, "Deferred cloud history delete: ${it.message}") }
            }
        }
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
        val remote = activeSyncRepo()
        if (remote != null) {
            syncScope.launch {
                runCatching { remote.clearAllHistory() }
                    .onFailure { Log.w(TAG, "Deferred cloud history clear: ${it.message}") }
            }
        }
    }

    suspend fun upsertNote(
        id: String?,
        title: String,
        content: String,
        isPinned: Boolean
    ): NoteEntity {
        val now = System.currentTimeMillis()
        val existing = if (!id.isNullOrBlank()) noteDao.getById(id) else null
        val note = if (existing != null) {
            existing.copy(
                title = title,
                content = content,
                isPinned = isPinned,
                updatedAtMillis = now
            )
        } else {
            NoteEntity(
                id = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                title = title,
                content = content,
                isPinned = isPinned,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }
        noteDao.upsert(note)

        val remote = activeSyncRepo()
        if (remote != null) {
            syncScope.launch {
                runCatching { remote.upsertNote(note) }
                    .onFailure { Log.w(TAG, "Deferred cloud note upload: ${it.message}") }
            }
        }
        return note
    }

    suspend fun toggleNotePinned(id: String, currentlyPinned: Boolean) {
        val updatedAt = System.currentTimeMillis()
        noteDao.setPinned(id, !currentlyPinned, updatedAt)
        val updated = noteDao.getById(id)
        val remote = activeSyncRepo()
        if (remote != null && updated != null) {
            syncScope.launch {
                runCatching { remote.upsertNote(updated) }
                    .onFailure { Log.w(TAG, "Deferred cloud note pin sync: ${it.message}") }
            }
        }
    }

    suspend fun deleteNote(id: String) {
        noteDao.deleteById(id)
        val remote = activeSyncRepo()
        if (remote != null) {
            syncScope.launch {
                runCatching { remote.deleteNote(id) }
                    .onFailure { Log.w(TAG, "Deferred cloud note delete: ${it.message}") }
            }
        }
    }

    suspend fun saveCalculationToNewNote(expression: String, result: String): NoteEntity {
        val cleanTitle = "Calc: ${expression.take(36)}"
        val body = buildString {
            appendLine("Expression:")
            appendLine(expression)
            appendLine()
            appendLine("Result:")
            append(result)
        }
        return upsertNote(
            id = null,
            title = cleanTitle,
            content = body,
            isPinned = false
        )
    }

    suspend fun exportAllAsJson(): String {
        val notes = noteDao.getAllOnce()
        val history = historyDao.getAllOnce()
        return BackupSerializer.exportToJson(notes, history)
    }

    suspend fun restoreFromJson(rawJson: String): Result<RestoreSummary> {
        return BackupSerializer.importFromJson(rawJson).map { (importedNotes, importedHistory) ->
            val existingNotes = noteDao.getAllOnce().associateBy { it.id }
            val mergedNotes = importedNotes.mapNotNull { incoming ->
                val local = existingNotes[incoming.id]
                if (local == null || incoming.updatedAtMillis >= local.updatedAtMillis) incoming else null
            }
            if (mergedNotes.isNotEmpty()) {
                noteDao.upsertAll(mergedNotes)
                val remote = activeSyncRepo()
                if (remote != null) {
                    syncScope.launch {
                        mergedNotes.forEach { note ->
                            runCatching { remote.upsertNote(note) }
                        }
                    }
                }
            }

            val existingHistoryIds = historyDao.getAllOnce().map { it.id }.toSet()
            val newHistory = importedHistory.filter { it.id !in existingHistoryIds }
            if (newHistory.isNotEmpty()) {
                historyDao.insertAll(newHistory)
                val remote = activeSyncRepo()
                if (remote != null) {
                    syncScope.launch {
                        newHistory.forEach { entry ->
                            runCatching { remote.upsertHistoryEntry(entry) }
                        }
                    }
                }
            }
            RestoreSummary(
                notesRestored = mergedNotes.size,
                historyRestored = newHistory.size
            )
        }
    }

    /**
     * Performs bidirectional sync between local Room database and Cloud Firestore
     * for the authenticated Google user. Safe to call after login or reinstall.
     */
    suspend fun syncWithCloud(user: FirebaseUser? = null): Result<RestoreSummary> {
        val remote = activeSyncRepo()
            ?: return Result.failure(IllegalStateException("Not signed in with Google"))

        _cloudSyncStatus.value = CloudSyncStatus.Syncing
        return try {
            if (user != null) {
                remote.ensureUserProfile(user)
            }

            // 1. Reconcile Notes
            val cloudNotes = remote.fetchAllCloudNotes()
            val localNotes = noteDao.getAllOnce()
            val localNoteMap = localNotes.associateBy { it.id }
            val cloudNoteMap = cloudNotes.associateBy { it.id }

            val notesToSaveLocally = cloudNotes.filter { cloud ->
                val local = localNoteMap[cloud.id]
                local == null || cloud.updatedAtMillis > local.updatedAtMillis
            }
            if (notesToSaveLocally.isNotEmpty()) {
                noteDao.upsertAll(notesToSaveLocally)
            }

            val notesToUpload = localNotes.filter { local ->
                val cloud = cloudNoteMap[local.id]
                cloud == null || local.updatedAtMillis > cloud.updatedAtMillis
            }
            notesToUpload.forEach { remote.upsertNote(it) }

            // 2. Reconcile History
            val cloudHistory = remote.fetchAllCloudHistory()
            val localHistory = historyDao.getAllOnce()
            val localHistoryIds = localHistory.map { it.id }.toSet()
            val cloudHistoryIds = cloudHistory.map { it.id }.toSet()

            val historyToSaveLocally = cloudHistory.filter { it.id !in localHistoryIds }
            if (historyToSaveLocally.isNotEmpty()) {
                historyDao.insertAll(historyToSaveLocally)
            }

            val historyToUpload = localHistory.filter { it.id !in cloudHistoryIds }
            historyToUpload.forEach { remote.upsertHistoryEntry(it) }

            val finalNotesCount = noteDao.getAllOnce().size
            val finalHistoryCount = historyDao.getAllOnce().size
            _cloudSyncStatus.value = CloudSyncStatus.Synced(
                lastSyncedMillis = System.currentTimeMillis(),
                notesCount = finalNotesCount,
                historyCount = finalHistoryCount
            )

            Result.success(
                RestoreSummary(
                    notesRestored = notesToSaveLocally.size,
                    historyRestored = historyToSaveLocally.size
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _cloudSyncStatus.value = CloudSyncStatus.Error(
                e.localizedMessage ?: "Cloud sync unavailable right now"
            )
            Result.failure(e)
        }
    }

    /**
     * Attaches real-time Firestore snapshot listeners inside the authenticated scope
     * so changes synced from other devices appear automatically.
     */
    suspend fun collectRealtimeCloudUpdates() = kotlinx.coroutines.coroutineScope {
        val remote = activeSyncRepo() ?: return@coroutineScope
        launch {
            remote.observeCloudNotes()
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud notes listener error: ${e.message}")
                }
                .collect { cloudNotes ->
                    val localMap = noteDao.getAllOnce().associateBy { it.id }
                    val newerOrMissing = cloudNotes.filter { cloud ->
                        val local = localMap[cloud.id]
                        local == null || cloud.updatedAtMillis > local.updatedAtMillis
                    }
                    if (newerOrMissing.isNotEmpty()) {
                        noteDao.upsertAll(newerOrMissing)
                    }
                }
        }
        launch {
            remote.observeCloudHistory()
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud history listener error: ${e.message}")
                }
                .collect { cloudHistory ->
                    val localIds = historyDao.getAllOnce().map { it.id }.toSet()
                    val missing = cloudHistory.filter { it.id !in localIds }
                    if (missing.isNotEmpty()) {
                        historyDao.insertAll(missing)
                    }
                }
        }
    }

    private companion object {
        const val TAG = "MultiCalcRepo"
    }
}
