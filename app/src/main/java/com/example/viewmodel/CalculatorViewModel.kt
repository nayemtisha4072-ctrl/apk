package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.CalculatorMode
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MultiCalculatorRepository
import com.example.domain.math.AngleUnit
import com.example.domain.math.CalculationResult
import com.example.domain.math.PrecisionMathEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalculatorUiState(
    val expression: String = "",
    val committedResult: CalculationResult.Success? = null,
    val livePreview: CalculationResult? = null,
    val errorMessage: String? = null,
    val justEvaluated: Boolean = false,
    val calculatorMode: CalculatorMode = CalculatorMode.STANDARD,
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val precision: Int = PrecisionMathEngine.DEFAULT_PRECISION,
    val useScientificNotation: Boolean = false
)

sealed interface CalculatorEvent {
    data class ShowMessage(val message: String) : CalculatorEvent
    data class OpenSavedNote(val noteId: String) : CalculatorEvent
}

class CalculatorViewModel(
    private val mathEngine: PrecisionMathEngine,
    private val repository: MultiCalculatorRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private data class DraftState(
        val expression: String = "",
        val committedResult: CalculationResult.Success? = null,
        val errorMessage: String? = null,
        val justEvaluated: Boolean = false
    )

    private val draft = MutableStateFlow(DraftState())

    private val _events = MutableSharedFlow<CalculatorEvent>()
    val events: SharedFlow<CalculatorEvent> = _events.asSharedFlow()

    val uiState: StateFlow<CalculatorUiState> = combine(
        draft,
        preferencesRepository.preferences
    ) { state, prefs ->
        val preview = if (state.expression.isNotBlank() && !state.justEvaluated) {
            mathEngine.evaluate(
                expression = state.expression,
                precision = prefs.precision,
                angleUnit = prefs.angleUnit
            )
        } else {
            null
        }
        CalculatorUiState(
            expression = state.expression,
            committedResult = state.committedResult,
            livePreview = preview,
            errorMessage = state.errorMessage,
            justEvaluated = state.justEvaluated,
            calculatorMode = prefs.calculatorMode,
            angleUnit = prefs.angleUnit,
            precision = prefs.precision,
            useScientificNotation = prefs.useScientificNotation
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CalculatorUiState()
        )

    fun appendToken(token: String) {
        draft.update { current ->
            val isChainOperator = token in setOf("+", "-", "×", "÷", "^", "!", "%", "^2", "^3")
            val baseExpression = when {
                current.justEvaluated && isChainOperator && current.committedResult != null -> {
                    current.committedResult.plainString
                }
                current.justEvaluated -> ""
                else -> current.expression
            }

            if (baseExpression.length + token.length > PrecisionMathEngine.MAX_EXPRESSION_LENGTH) {
                return@update current.copy(errorMessage = "Maximum expression length reached")
            }

            // Prevent multiple consecutive decimal points in the same number segment
            if (token == ".") {
                val trailingNum = baseExpression.takeLastWhile { it.isDigit() || it == '.' }
                if (trailingNum.contains('.')) return@update current
                val prefix = if (trailingNum.isEmpty()) "0." else "."
                return@update current.copy(
                    expression = baseExpression + prefix,
                    committedResult = if (current.justEvaluated) null else current.committedResult,
                    errorMessage = null,
                    justEvaluated = false
                )
            }

            // Replace trailing binary operator if another binary operator is tapped
            val binaryOps = setOf('+', '-', '×', '÷', '^')
            val updatedExpr = if (token.length == 1 && token[0] in binaryOps &&
                baseExpression.isNotEmpty() && baseExpression.last() in binaryOps
            ) {
                if (token == "-" && baseExpression.last() in setOf('×', '÷', '^')) {
                    baseExpression + token
                } else {
                    baseExpression.dropLast(1) + token
                }
            } else {
                baseExpression + token
            }

            current.copy(
                expression = updatedExpr,
                committedResult = if (current.justEvaluated) null else current.committedResult,
                errorMessage = null,
                justEvaluated = false
            )
        }
    }

    fun toggleSign() {
        draft.update { current ->
            val expr = if (current.justEvaluated && current.committedResult != null) {
                current.committedResult.plainString
            } else {
                current.expression
            }

            if (expr.isBlank()) {
                return@update current.copy(expression = "-", errorMessage = null, justEvaluated = false)
            }

            // Check if expression ends with "(-<number>)"
            val wrappedNegativeRegex = Regex("""\(-(\d+(?:\.\d+)?)\)$""")
            val wrappedMatch = wrappedNegativeRegex.find(expr)
            if (wrappedMatch != null) {
                val unwrapped = expr.removeRange(wrappedMatch.range) + wrappedMatch.groupValues[1]
                return@update current.copy(expression = unwrapped, errorMessage = null, justEvaluated = false)
            }

            // Check if whole expression is "-<number>"
            val simpleNegativeRegex = Regex("""^-(\d+(?:\.\d+)?)$""")
            val simpleMatch = simpleNegativeRegex.find(expr)
            if (simpleMatch != null) {
                return@update current.copy(
                    expression = simpleMatch.groupValues[1],
                    errorMessage = null,
                    justEvaluated = false
                )
            }

            // Check if expression ends with a positive number
            val trailingNumberRegex = Regex("""(\d+(?:\.\d+)?)$""")
            val numMatch = trailingNumberRegex.find(expr)
            if (numMatch != null) {
                val startIdx = numMatch.range.first
                val updated = if (startIdx == 0) {
                    "-${numMatch.value}"
                } else {
                    expr.substring(0, startIdx) + "(-${numMatch.value})"
                }
                return@update current.copy(expression = updated, errorMessage = null, justEvaluated = false)
            }

            current
        }
    }

    fun backspace() {
        draft.update { current ->
            if (current.expression.isEmpty()) return@update current
            val functionSuffixes = listOf("asin(", "acos(", "atan(", "sqrt(", "sin(", "cos(", "tan(", "log(", "ln(")
            val matchedSuffix = functionSuffixes.firstOrNull { current.expression.endsWith(it) }
            val nextExpr = if (matchedSuffix != null) {
                current.expression.dropLast(matchedSuffix.length)
            } else {
                current.expression.dropLast(1)
            }
            current.copy(
                expression = nextExpr,
                errorMessage = null,
                justEvaluated = false
            )
        }
    }

    fun clearAll() {
        draft.value = DraftState()
    }

    fun evaluateAndSave() {
        val currentExpr = draft.value.expression.trim()
        if (currentExpr.isEmpty()) return

        val stateSnapshot = uiState.value
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                mathEngine.evaluate(
                    expression = currentExpr,
                    precision = stateSnapshot.precision,
                    angleUnit = stateSnapshot.angleUnit
                )
            }

            when (result) {
                is CalculationResult.Success -> {
                    draft.value = DraftState(
                        expression = currentExpr,
                        committedResult = result,
                        errorMessage = null,
                        justEvaluated = true
                    )
                    repository.saveCalculation(
                        expression = currentExpr,
                        result = result.plainString,
                        mode = stateSnapshot.calculatorMode,
                        angleUnit = stateSnapshot.angleUnit,
                        precision = stateSnapshot.precision,
                        isApproximated = result.isApproximated
                    )
                }
                is CalculationResult.Error -> {
                    draft.update { it.copy(errorMessage = result.message, justEvaluated = false) }
                }
            }
        }
    }

    fun loadExpressionFromHistory(expression: String, result: String? = null) {
        draft.value = DraftState(
            expression = expression,
            committedResult = result?.let {
                runCatching {
                    val bd = java.math.BigDecimal(it)
                    CalculationResult.Success(
                        value = bd,
                        plainString = it,
                        scientificString = mathEngine.formatScientific(bd),
                        isApproximated = false
                    )
                }.getOrNull()
            },
            errorMessage = null,
            justEvaluated = false
        )
    }

    fun saveCurrentToNote() {
        val state = uiState.value
        val activeResult = state.committedResult
            ?: (state.livePreview as? CalculationResult.Success)
        if (state.expression.isBlank() || activeResult == null) {
            viewModelScope.launch {
                _events.emit(CalculatorEvent.ShowMessage("Calculate a valid result first to save it to Notes"))
            }
            return
        }
        viewModelScope.launch {
            val note = repository.saveCalculationToNewNote(
                expression = state.expression,
                result = activeResult.plainString
            )
            _events.emit(CalculatorEvent.OpenSavedNote(note.id))
        }
    }

    fun setCalculatorMode(mode: CalculatorMode) {
        viewModelScope.launch {
            preferencesRepository.setCalculatorMode(mode)
        }
    }

    fun toggleAngleUnit() {
        val next = if (uiState.value.angleUnit == AngleUnit.DEG) AngleUnit.RAD else AngleUnit.DEG
        viewModelScope.launch {
            preferencesRepository.setAngleUnit(next)
        }
    }

    fun toggleScientificNotation() {
        val next = !uiState.value.useScientificNotation
        viewModelScope.launch {
            preferencesRepository.setUseScientificNotation(next)
        }
    }
}
