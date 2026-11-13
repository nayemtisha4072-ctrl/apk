package com.example.data.repository

import com.example.data.backup.BackupSerializer
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.preferences.CalculatorMode
import com.example.domain.math.AngleUnit
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class RestoreSummary(
    val notesRestored: Int,
    val historyRestored: Int
)

class MultiCalculatorRepository(
    private val historyDao: HistoryDao,
    private val noteDao: NoteDao
) {
    fun observeHistory(query: String = ""): Flow<List<CalculationHistoryEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) historyDao.observeAll() else historyDao.search(trimmed)
    }

    fun observeNotes(query: String = ""): Flow<List<NoteEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) noteDao.observeAll() else noteDao.search(trimmed)
    }

    suspend fun getNoteById(id: String): NoteEntity? = noteDao.getById(id)

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
        historyDao.insert(
            CalculationHistoryEntity(
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
        )
    }

    suspend fun deleteHistoryEntry(id: String) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clearAll()

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
        return note
    }

    suspend fun toggleNotePinned(id: String, currentlyPinned: Boolean) {
        noteDao.setPinned(id, !currentlyPinned, System.currentTimeMillis())
    }

    suspend fun deleteNote(id: String) = noteDao.deleteById(id)

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
            // Merge safely by keeping newer updatedAtMillis for existing IDs
            val existingNotes = noteDao.getAllOnce().associateBy { it.id }
            val mergedNotes = importedNotes.mapNotNull { incoming ->
                val local = existingNotes[incoming.id]
                if (local == null || incoming.updatedAtMillis >= local.updatedAtMillis) incoming else null
            }
            if (mergedNotes.isNotEmpty()) {
                noteDao.upsertAll(mergedNotes)
            }

            val existingHistoryIds = historyDao.getAllOnce().map { it.id }.toSet()
            val newHistory = importedHistory.filter { it.id !in existingHistoryIds }
            if (newHistory.isNotEmpty()) {
                historyDao.insertAll(newHistory)
            }
            RestoreSummary(
                notesRestored = mergedNotes.size,
                historyRestored = newHistory.size
            )
        }
    }
}
