package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.preferences.CalculatorMode
import com.example.domain.math.AngleUnit
import com.example.domain.math.CalculationResult
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.CalculatorUiState

@Composable
fun CalculatorScreen(
    uiState: CalculatorUiState,
    onAppendToken: (String) -> Unit,
    onToggleSign: () -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onEvaluate: () -> Unit,
    onModeChange: (CalculatorMode) -> Unit,
    onToggleAngleUnit: () -> Unit,
    onToggleScientificNotation: () -> Unit,
    onSaveToNote: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.copied_to_clipboard)

    val displayedSuccess: CalculationResult.Success? = uiState.committedResult
        ?: (uiState.livePreview as? CalculationResult.Success)
    val liveDomainError: String? = uiState.errorMessage ?: (uiState.livePreview as? CalculationResult.Error)
        ?.takeIf { !it.isIncomplete }
        ?.message

    val formattedResultText = when {
        displayedSuccess == null -> ""
        uiState.useScientificNotation -> displayedSuccess.scientificString
        else -> displayedSuccess.plainString
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val isWideLayout = maxWidth >= 700.dp

        if (isWideLayout) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeControlBar(
                        mode = uiState.calculatorMode,
                        angleUnit = uiState.angleUnit,
                        precision = uiState.precision,
                        useScientificNotation = uiState.useScientificNotation,
                        onModeChange = onModeChange,
                        onToggleAngleUnit = onToggleAngleUnit,
                        onToggleScientificNotation = onToggleScientificNotation
                    )
                    CalculatorDisplayCard(
                        expression = uiState.expression,
                        displayedSuccess = displayedSuccess,
                        formattedResultText = formattedResultText,
                        errorMessage = liveDomainError,
                        onCopyResult = {
                            val full = displayedSuccess?.plainString
                            if (!full.isNullOrBlank()) {
                                clipboard.setText(AnnotatedString(full))
                                onShowMessage(copiedMsg)
                            }
                        },
                        onSaveToNote = onSaveToNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    if (uiState.calculatorMode == CalculatorMode.SCIENTIFIC) {
                        ScientificKeypadGrid(
                            onAppendToken = onAppendToken,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                StandardKeypadGrid(
                    onAppendToken = onAppendToken,
                    onToggleSign = onToggleSign,
                    onBackspace = onBackspace,
                    onClearAll = onClearAll,
                    onEvaluate = onEvaluate,
                    hasResult = displayedSuccess != null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeControlBar(
                    mode = uiState.calculatorMode,
                    angleUnit = uiState.angleUnit,
                    precision = uiState.precision,
                    useScientificNotation = uiState.useScientificNotation,
                    onModeChange = onModeChange,
                    onToggleAngleUnit = onToggleAngleUnit,
                    onToggleScientificNotation = onToggleScientificNotation
                )

                CalculatorDisplayCard(
                    expression = uiState.expression,
                    displayedSuccess = displayedSuccess,
                    formattedResultText = formattedResultText,
                    errorMessage = liveDomainError,
                    onCopyResult = {
                        val full = displayedSuccess?.plainString
                        if (!full.isNullOrBlank()) {
                            clipboard.setText(AnnotatedString(full))
                            onShowMessage(copiedMsg)
                        }
                    },
                    onSaveToNote = onSaveToNote,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 148.dp)
                        .weight(if (uiState.calculatorMode == CalculatorMode.SCIENTIFIC) 0.34f else 0.38f)
                )

                AnimatedVisibility(
                    visible = uiState.calculatorMode == CalculatorMode.SCIENTIFIC,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ScientificKeypadGrid(
                        onAppendToken = onAppendToken,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                StandardKeypadGrid(
                    onAppendToken = onAppendToken,
                    onToggleSign = onToggleSign,
                    onBackspace = onBackspace,
                    onClearAll = onClearAll,
                    onEvaluate = onEvaluate,
                    hasResult = displayedSuccess != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (uiState.calculatorMode == CalculatorMode.SCIENTIFIC) 0.44f else 0.62f)
                )
            }
        }
    }
}

@Composable
private fun ModeControlBar(
    mode: CalculatorMode,
    angleUnit: AngleUnit,
    precision: Int,
    useScientificNotation: Boolean,
    onModeChange: (CalculatorMode) -> Unit,
    onToggleAngleUnit: () -> Unit,
    onToggleScientificNotation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = mode == CalculatorMode.STANDARD,
            onClick = { onModeChange(CalculatorMode.STANDARD) },
            label = { Text(stringResource(R.string.mode_standard)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Functions,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.testTag("calc_btn_mode_standard")
        )
        FilterChip(
            selected = mode == CalculatorMode.SCIENTIFIC,
            onClick = { onModeChange(CalculatorMode.SCIENTIFIC) },
            label = { Text(stringResource(R.string.mode_scientific)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.testTag("calc_btn_mode_scientific")
        )
        if (mode == CalculatorMode.SCIENTIFIC) {
            FilterChip(
                selected = angleUnit == AngleUnit.DEG,
                onClick = onToggleAngleUnit,
                label = { Text(angleUnit.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.testTag("calc_btn_angle_toggle")
            )
        }
        FilterChip(
            selected = useScientificNotation,
            onClick = onToggleScientificNotation,
            label = { Text(if (useScientificNotation) "SCI" else "FULL") },
            modifier = Modifier.testTag("calc_btn_notation_toggle")
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "${precision}d",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CalculatorDisplayCard(
    expression: String,
    displayedSuccess: CalculationResult.Success?,
    formattedResultText: String,
    errorMessage: String?,
    onCopyResult: () -> Unit,
    onSaveToNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exprScroll = rememberScrollState()
    val resScroll = rememberScrollState()

    LaunchedEffect(expression) {
        exprScroll.scrollTo(exprScroll.maxValue)
    }
    LaunchedEffect(formattedResultText) {
        resScroll.scrollTo(0)
    }

    val cardBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (displayedSuccess != null) {
                        "${displayedSuccess.plainString.replace("-", "").replace(".", "").length} digits"
                    } else {
                        "Arbitrary Precision"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onCopyResult,
                        enabled = displayedSuccess != null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calc_copy_result_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_result),
                            tint = if (displayedSuccess != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onSaveToNote,
                        enabled = displayedSuccess != null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calc_save_note_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.NoteAdd,
                            contentDescription = stringResource(R.string.cd_save_to_note),
                            tint = if (displayedSuccess != null) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(exprScroll),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = expression.ifEmpty { "0" },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = if (expression.length > 22) 18.sp else 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (expression.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        modifier = Modifier.testTag("calc_expression_text")
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_error_text")
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (displayedSuccess != null) {
                            Text(
                                text = if (displayedSuccess.isApproximated) "≈ " else "= ",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .horizontalScroll(resScroll),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = formattedResultText,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = when {
                                    formattedResultText.length > 24 -> 20.sp
                                    formattedResultText.length > 14 -> 24.sp
                                    else -> 30.sp
                                },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.testTag("calc_result_text")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScientificKeypadGrid(
    onAppendToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf(
            "sin" to "sin(",
            "cos" to "cos(",
            "tan" to "tan(",
            "log" to "log(",
            "ln" to "ln("
        ),
        listOf(
            "sin⁻¹" to "asin(",
            "cos⁻¹" to "acos(",
            "tan⁻¹" to "atan(",
            "x²" to "^2",
            "x³" to "^3"
        ),
        listOf(
            "√" to "√(",
            "xʸ" to "^",
            "n!" to "!",
            "π" to "π",
            "e" to "e"
        )
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { (label, token) ->
                    CalcButton(
                        label = label,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = { onAppendToken(token) },
                        fontSize = 13.sp,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("calc_sci_${label.replace("⁻¹", "_inv")}")
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardKeypadGrid(
    onAppendToken: (String) -> Unit,
    onToggleSign: () -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onEvaluate: () -> Unit,
    hasResult: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: AC, (, ), Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcButton(
                label = "AC",
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.error,
                onClick = onClearAll,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_clear")
            )
            CalcButton(
                label = "(",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onAppendToken("(") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_lparen")
            )
            CalcButton(
                label = ")",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onAppendToken(")") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_rparen")
            )
            CalcIconButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onBackspace,
                contentDescription = stringResource(R.string.cd_backspace),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_backspace")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.cd_backspace),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Row 2: +/-, %, ÷ (rightmost operator), wait — keep 4 columns aligned with numpad!
        // Let's put: ± | % | ÷ | ×? Wait, better: ± | % | ^? No, let's keep +, -, ×, ÷ cleanly in col 4!
        // Wait: 6 rows x 4 columns keeps ÷ × - + = ALL in column 4!
        // Row 2: ± | % | 00 | ÷
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcButton(
                label = "±",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onToggleSign,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_sign")
            )
            CalcButton(
                label = "%",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onAppendToken("%") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_percent")
            )
            CalcButton(
                label = "00",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onAppendToken("00") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_00")
            )
            CalcButton(
                label = "÷",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { onAppendToken("÷") },
                fontSize = 24.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_div")
            )
        }

        // Row 3: 7, 8, 9, ×
        NumberOperatorRow(
            digits = listOf("7", "8", "9"),
            operatorLabel = "×",
            operatorTag = "calc_btn_mul",
            onAppendToken = onAppendToken,
            modifier = Modifier.weight(1f)
        )

        // Row 4: 4, 5, 6, -
        NumberOperatorRow(
            digits = listOf("4", "5", "6"),
            operatorLabel = "-",
            operatorTag = "calc_btn_sub",
            onAppendToken = onAppendToken,
            modifier = Modifier.weight(1f)
        )

        // Row 5: 1, 2, 3, +
        NumberOperatorRow(
            digits = listOf("1", "2", "3"),
            operatorLabel = "+",
            operatorTag = "calc_btn_add",
            onAppendToken = onAppendToken,
            modifier = Modifier.weight(1f)
        )

        // Row 6: 0 (wide 2x), ., =
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalcButton(
                label = "0",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onAppendToken("0") },
                modifier = Modifier
                    .weight(2.05f)
                    .fillMaxHeight()
                    .testTag("calc_btn_0")
            )
            CalcButton(
                label = ".",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onAppendToken(".") },
                fontSize = 24.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_dot")
            )
            CalcButton(
                label = "=",
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                onClick = onEvaluate,
                fontSize = 26.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_equals")
            )
        }
    }
}

@Composable
private fun NumberOperatorRow(
    digits: List<String>,
    operatorLabel: String,
    operatorTag: String,
    onAppendToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        digits.forEach { d ->
            CalcButton(
                label = d,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = { onAppendToken(d) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("calc_btn_$d")
            )
        }
        CalcButton(
            label = operatorLabel,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onAppendToken(operatorLabel) },
            fontSize = 24.sp,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(operatorTag)
        )
    }
}

@Composable
private fun CalcButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CalcIconButton(
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = contentDescription) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            icon()
        }
    }
}
