package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.local.entity.NoteEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(user: FirebaseUser) {
        val uid = requireUserId()
        require(uid == user.uid) { "Authenticated UID mismatch" }
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            val cleanName = user.displayName?.take(120)
            val cleanEmail = user.email?.take(200)
            if (snapshot.exists()) {
                val updates = buildMap<String, Any> {
                    if (!cleanName.isNullOrBlank()) put("displayName", cleanName)
                    if (!cleanEmail.isNullOrBlank()) put("email", cleanEmail)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    if (!cleanName.isNullOrBlank()) put("displayName", cleanName)
                    if (!cleanEmail.isNullOrBlank()) put("email", cleanEmail)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeCloudNotes(): Flow<List<NoteEntity>> = flow {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("notes")
        emitAll(
            collectionRef.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(CloudNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.takeIf { it.id.isNotBlank() }
                            ?.toNoteEntity()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, collectionRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudHistory(): Flow<List<CalculationHistoryEntity>> = flow {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("history")
        emitAll(
            collectionRef.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(CloudHistoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.takeIf { it.id.isNotBlank() }
                            ?.toHistoryEntity()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, collectionRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertNote(note: NoteEntity) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes").document(note.id)
        try {
            val existing = docRef.get().await()
            val cleanTitle = note.title.take(300)
            val cleanContent = note.content.take(100_000)
            if (existing.exists()) {
                val updates = mapOf(
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "isPinned" to note.isPinned,
                    "clientUpdatedAtMillis" to note.updatedAtMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "isPinned" to note.isPinned,
                    "clientCreatedAtMillis" to note.createdAtMillis,
                    "clientUpdatedAtMillis" to note.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteNote(noteId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun upsertHistoryEntry(entry: CalculationHistoryEntity) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("history").document(entry.id)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "expression" to entry.expression.take(20_000),
                    "result" to entry.result.take(100_000),
                    "calculatorMode" to entry.calculatorMode,
                    "angleUnit" to entry.angleUnit,
                    "precision" to entry.precision,
                    "isApproximated" to entry.isApproximated,
                    "clientCreatedAtMillis" to entry.createdAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteHistoryEntry(historyId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("history").document(historyId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun clearAllHistory() {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("history")
        try {
            val snapshot = collectionRef.get().await()
            val docs = snapshot.documents
            for (chunk in docs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, collectionRef.path)
            throw e
        }
    }

    suspend fun fetchAllCloudNotes(): List<NoteEntity> {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("notes")
        return try {
            val snapshot = collectionRef.get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(CloudNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.takeIf { it.id.isNotBlank() }
                    ?.toNoteEntity()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, collectionRef.path)
            throw e
        }
    }

    suspend fun fetchAllCloudHistory(): List<CalculationHistoryEntity> {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("history")
        return try {
            val snapshot = collectionRef.get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(CloudHistoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.takeIf { it.id.isNotBlank() }
                    ?.toHistoryEntity()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, collectionRef.path)
            throw e
        }
    }
}
