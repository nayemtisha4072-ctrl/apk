package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object CalculatorRoute

@Serializable
data object NotesRoute

@Serializable
data class NoteEditorRoute(val noteId: String? = null)

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute
