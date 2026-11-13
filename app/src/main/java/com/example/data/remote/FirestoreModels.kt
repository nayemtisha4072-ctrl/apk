package com.example.data.remote

import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.local.entity.NoteEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String? = null,
    val email: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val clientCreatedAtMillis: Long = 0L,
    val clientUpdatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toNoteEntity(): NoteEntity {
        val fallbackCreated = createdAt?.toDate()?.time ?: System.currentTimeMillis()
        val fallbackUpdated = updatedAt?.toDate()?.time ?: fallbackCreated
        return NoteEntity(
            id = id,
            title = title,
            content = content,
            isPinned = isPinned,
            createdAtMillis = if (clientCreatedAtMillis > 0L) clientCreatedAtMillis else fallbackCreated,
            updatedAtMillis = if (clientUpdatedAtMillis > 0L) clientUpdatedAtMillis else fallbackUpdated
        )
    }
}

@IgnoreExtraProperties
data class CloudHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val expression: String = "",
    val result: String = "",
    val calculatorMode: String = "STANDARD",
    val angleUnit: String = "DEG",
    val precision: Int = 32,
    val isApproximated: Boolean = false,
    val clientCreatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toHistoryEntity(): CalculationHistoryEntity {
        val fallbackCreated = createdAt?.toDate()?.time ?: System.currentTimeMillis()
        return CalculationHistoryEntity(
            id = id,
            expression = expression,
            result = result,
            calculatorMode = calculatorMode,
            angleUnit = angleUnit,
            precision = precision,
            isApproximated = isApproximated,
            createdAtMillis = if (clientCreatedAtMillis > 0L) clientCreatedAtMillis else fallbackCreated,
            updatedAtMillis = if (clientCreatedAtMillis > 0L) clientCreatedAtMillis else fallbackCreated
        )
    }
}
