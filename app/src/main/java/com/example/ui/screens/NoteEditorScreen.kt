package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.CalculationHistoryEntity
import com.example.viewmodel.NoteEditorState
import com.example.viewmodel.NoteSaveStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    editorState: NoteEditorState,
    latestCalculation: CalculationHistoryEntity?,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onSaveNow: () -> Unit,
    onFlushBeforeExit: () -> Unit,
    onInsertCalculation: (String, String) -> Unit,
    onDeleteNote: () -> Unit,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val noteCopiedMsg = stringResource(R.string.note_copied)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault()) }

    BackHandler {
        onFlushBeforeExit()
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            onFlushBeforeExit()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (editorState.saveStatus) {
                        NoteSaveStatus.SAVED -> MaterialTheme.colorScheme.secondaryContainer
                        NoteSaveStatus.SAVING -> MaterialTheme.colorScheme.primaryContainer
                        NoteSaveStatus.UNSAVED -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = editorState.saveStatus.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        onFlushBeforeExit()
                        onNavigateBack()
                    },
                    modifier = Modifier.testTag("note_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Notes"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.testTag("note_editor_pin_btn")
                ) {
                    Icon(
                        imageVector = if (editorState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.cd_pin_note),
                        tint = if (editorState.isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                IconButton(
                    onClick = {
                        val copyText = buildString {
                            if (editorState.title.isNotBlank()) appendLine(editorState.title)
                            append(editorState.content)
                        }
                        if (copyText.isNotBlank()) {
                            clipboard.setText(AnnotatedString(copyText))
                            onShowMessage(noteCopiedMsg)
                        }
                    },
                    modifier = Modifier.testTag("note_editor_copy_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy)
                    )
                }
                IconButton(
                    onClick = onSaveNow,
                    modifier = Modifier.testTag("note_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save note"
                    )
                }
                if (!editorState.id.isNullOrBlank()) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("note_editor_delete_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (latestCalculation != null) {
                    FilledTonalButton(
                        onClick = {
                            onInsertCalculation(
                                latestCalculation.expression,
                                latestCalculation.result
                            )
                        },
                        modifier = Modifier.testTag("note_insert_last_calc_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Insert Latest Calculation (${latestCalculation.expression.take(18)})")
                    }
                }

                OutlinedTextField(
                    value = editorState.title,
                    onValueChange = onTitleChange,
                    placeholder = {
                        Text(
                            text = "Note title",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Created: ${dateFormatter.format(Date(editorState.createdAtMillis))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Modified: ${dateFormatter.format(Date(editorState.updatedAtMillis))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                OutlinedTextField(
                    value = editorState.content,
                    onValueChange = onContentChange,
                    placeholder = {
                        Text("Write notes, formulas, or paste full-precision calculations...")
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 12,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this note?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteNote()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
