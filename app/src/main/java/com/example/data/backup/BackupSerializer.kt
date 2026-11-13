package com.example.data.backup

import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.local.entity.NoteEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupHistoryItem(
    val id: String,
    val expression: String,
    val result: String,
    val calculatorMode: String,
    val angleUnit: String,
    val precision: Int,
    val isApproximated: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Serializable
data class BackupNoteItem(
    val id: String,
    val title: String,
    val content: String,
    val isPinned: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Serializable
data class MultiCalculatorBackup(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val notes: List<BackupNoteItem> = emptyList(),
    val history: List<BackupHistoryItem> = emptyList()
)

object BackupSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportToJson(
        notes: List<NoteEntity>,
        history: List<CalculationHistoryEntity>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val payload = MultiCalculatorBackup(
            schemaVersion = 1,
            exportedAtMillis = exportedAtMillis,
            notes = notes.map {
                BackupNoteItem(
                    id = it.id,
                    title = it.title,
                    content = it.content,
                    isPinned = it.isPinned,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            },
            history = history.map {
                BackupHistoryItem(
                    id = it.id,
                    expression = it.expression,
                    result = it.result,
                    calculatorMode = it.calculatorMode,
                    angleUnit = it.angleUnit,
                    precision = it.precision,
                    isApproximated = it.isApproximated,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            }
        )
        return json.encodeToString(payload)
    }

    fun importFromJson(rawJson: String): Result<Pair<List<NoteEntity>, List<CalculationHistoryEntity>>> {
        return runCatching {
            val backup = json.decodeFromString<MultiCalculatorBackup>(rawJson)
            require(backup.schemaVersion >= 1) { "Unsupported backup schema version" }
            val notes = backup.notes.map {
                NoteEntity(
                    id = it.id.ifBlank { java.util.UUID.randomUUID().toString() },
                    title = it.title.take(200),
                    content = it.content,
                    isPinned = it.isPinned,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            }
            val history = backup.history.map {
                CalculationHistoryEntity(
                    id = it.id.ifBlank { java.util.UUID.randomUUID().toString() },
                    expression = it.expression,
                    result = it.result,
                    calculatorMode = it.calculatorMode,
                    angleUnit = it.angleUnit,
                    precision = it.precision,
                    isApproximated = it.isApproximated,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            }
            notes to history
        }
    }
}
