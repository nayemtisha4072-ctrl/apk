package com.example.domain.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed interface CalculationResult {
    data class Success(
        val value: BigDecimal,
        val plainString: String,
        val scientificString: String,
        val isApproximated: Boolean
    ) : CalculationResult

    data class Error(
        val message: String,
        val isIncomplete: Boolean = false
    ) : CalculationResult
}

/**
 * Recursive-descent arbitrary-precision expression parser and evaluator.
 * Supports:
 * - Standard: +, -, ×, ÷, %, parentheses, unary +/-, arbitrary-size numbers & decimals
 * - Scientific: sin, cos, tan, asin, acos, atan, log, ln, sqrt (√), ^, !, π, e
 * - Implicit multiplication: 2π, 3(4+5), (1+2)(3+4), 2sin(30)
 */
class PrecisionMathEngine {

    private sealed interface Token {
        data class NumberLit(val value: BigDecimal) : Token
        data class Constant(val id: String) : Token
        data class Operator(val symbol: Char) : Token
        data class Function(val name: String) : Token
        data object LeftParen : Token
        data object RightParen : Token
        data object Factorial : Token
        data object Percent : Token
    }

    fun evaluate(
        expression: String,
        precision: Int = DEFAULT_PRECISION,
        angleUnit: AngleUnit = AngleUnit.DEG
    ): CalculationResult {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) {
            return CalculationResult.Error("Enter an expression", isIncomplete = true)
        }
        if (trimmed.length > MAX_EXPRESSION_LENGTH) {
            return CalculationResult.Error("Expression exceeds maximum length")
        }

        val clampedPrecision = precision.coerceIn(MIN_PRECISION, MAX_PRECISION)
        val mc = MathContext(clampedPrecision, RoundingMode.HALF_UP)

        return try {
            val tokens = tokenize(trimmed)
            if (tokens.isEmpty()) {
                return CalculationResult.Error("Enter an expression", isIncomplete = true)
            }
            val parser = Parser(tokens, mc, angleUnit)
            val eval = parser.parseExpression()
            if (parser.hasMore()) {
                return CalculationResult.Error("Unexpected token near '${trimmed.takeLast(12)}'")
            }

            val clean = BigDecimalMath.stripTrailingZerosSafe(
                if (eval.isApproximated) eval.value.round(mc) else eval.value
            )
            if (clean.precision() > BigDecimalMath.MAX_EXACT_DIGITS) {
                return CalculationResult.Error("Result exceeds safe display digit limit")
            }

            val plain = clean.toPlainString()
            val sci = formatScientific(clean, clampedPrecision.coerceAtMost(12))
            CalculationResult.Success(
                value = clean,
                plainString = plain,
                scientificString = sci,
                isApproximated = eval.isApproximated
            )
        } catch (e: IncompleteExpressionException) {
            CalculationResult.Error(e.message ?: "Incomplete expression", isIncomplete = true)
        } catch (e: MathDomainException) {
            CalculationResult.Error(e.message ?: "Math domain error", isIncomplete = false)
        } catch (e: SyntaxException) {
            CalculationResult.Error(e.message ?: "Invalid syntax", isIncomplete = false)
        } catch (_: ArithmeticException) {
            CalculationResult.Error("Arithmetic overflow or division by zero", isIncomplete = false)
        }
    }

    fun formatScientific(value: BigDecimal, sigFigs: Int = 10): String {
        val clean = BigDecimalMath.stripTrailingZerosSafe(value)
        if (clean.compareTo(BigDecimal.ZERO) == 0) return "0"
        val rounded = clean.round(MathContext(sigFigs.coerceIn(4, 24), RoundingMode.HALF_UP))
        val str = rounded.toString()
        // If BigDecimal chose plain representation and it's within normal magnitude, format as mantissa x 10^exp
        val exponent = (rounded.precision() - rounded.scale()) - 1
        if (exponent in -4..7 && !str.contains('E')) {
            return BigDecimalMath.stripTrailingZerosSafe(rounded).toPlainString()
        }
        val sign = if (rounded.signum() < 0) "-" else ""
        val unscaledDigits = rounded.abs().unscaledValue().toString().trimEnd('0').ifEmpty { "0" }
        val mantissa = if (unscaledDigits.length == 1) {
            unscaledDigits
        } else {
            "${unscaledDigits.first()}.${unscaledDigits.drop(1)}"
        }
        return "$sign${mantissa}E$exponent"
    }

    private fun tokenize(input: String): List<Token> {
        val normalized = input
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("√", "sqrt")
            .replace(",", "")
            .replace(" ", "")

        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < normalized.length) {
            val ch = normalized[i]
            when {
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    var dotCount = 0
                    while (i < normalized.length && (normalized[i].isDigit() || normalized[i] == '.')) {
                        if (normalized[i] == '.') dotCount++
                        if (dotCount > 1) throw SyntaxException("Invalid decimal number")
                        i++
                    }
                    // Support scientific E notation inside literals like 1.5E10 or 2E-3
                    if (i < normalized.length && (normalized[i] == 'E' || normalized[i] == 'e')) {
                        val nextIdx = i + 1
                        val isExponent = nextIdx < normalized.length && (
                            normalized[nextIdx].isDigit() ||
                                ((normalized[nextIdx] == '+' || normalized[nextIdx] == '-') &&
                                    nextIdx + 1 < normalized.length && normalized[nextIdx + 1].isDigit())
                            )
                        if (isExponent) {
                            i++ // consume E/e
                            if (normalized[i] == '+' || normalized[i] == '-') i++
                            while (i < normalized.length && normalized[i].isDigit()) i++
                        }
                    }
                    val numStr = normalized.substring(start, i)
                    if (numStr == ".") throw SyntaxException("Invalid decimal '.'")
                    val bd = try {
                        BigDecimal(numStr)
                    } catch (_: NumberFormatException) {
                        throw SyntaxException("Invalid number '$numStr'")
                    }
                    maybeInsertImplicitMultiply(tokens)
                    tokens.add(Token.NumberLit(bd))
                }
                ch.isLetter() -> {
                    val start = i
                    while (i < normalized.length && (normalized[i].isLetter() || normalized[i].isDigit())) {
                        i++
                    }
                    val ident = normalized.substring(start, i).lowercase()
                    maybeInsertImplicitMultiply(tokens)
                    when (ident) {
                        "pi", "e" -> tokens.add(Token.Constant(ident))
                        "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln", "sqrt", "abs" -> {
                            tokens.add(Token.Function(ident))
                        }
                        else -> throw SyntaxException("Unknown symbol '$ident'")
                    }
                }
                ch == '(' -> {
                    maybeInsertImplicitMultiply(tokens)
                    tokens.add(Token.LeftParen)
                    i++
                }
                ch == ')' -> {
                    tokens.add(Token.RightParen)
                    i++
                }
                ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '^' -> {
                    tokens.add(Token.Operator(ch))
                    i++
                }
                ch == '!' -> {
                    tokens.add(Token.Factorial)
                    i++
                }
                ch == '%' -> {
                    tokens.add(Token.Percent)
                    i++
                }
                else -> throw SyntaxException("Unexpected character '$ch'")
            }
        }
        return tokens
    }

    private fun maybeInsertImplicitMultiply(tokens: MutableList<Token>) {
        val last = tokens.lastOrNull() ?: return
        if (last is Token.NumberLit ||
            last is Token.Constant ||
            last is Token.RightParen ||
            last is Token.Factorial ||
            last is Token.Percent
        ) {
            tokens.add(Token.Operator('*'))
        }
    }

    private data class EvalNode(val value: BigDecimal, val isApproximated: Boolean)

    private class Parser(
        private val tokens: List<Token>,
        private val mc: MathContext,
        private val angleUnit: AngleUnit
    ) {
        private var pos = 0

        fun hasMore(): Boolean = pos < tokens.size

        private fun peek(): Token? = tokens.getOrNull(pos)

        private fun consume(): Token {
            if (pos >= tokens.size) throw IncompleteExpressionException("Unexpected end of expression")
            return tokens[pos++]
        }

        fun parseExpression(): EvalNode {
            var left = parseTerm()
            while (true) {
                val tok = peek()
                if (tok is Token.Operator && (tok.symbol == '+' || tok.symbol == '-')) {
                    consume()
                    val right = parseTerm()
                    val combinedApprox = left.isApproximated || right.isApproximated
                    val value = if (tok.symbol == '+') {
                        left.value.add(right.value)
                    } else {
                        left.value.subtract(right.value)
                    }
                    left = EvalNode(value, combinedApprox)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseTerm(): EvalNode {
            var left = parseUnary()
            while (true) {
                val tok = peek()
                if (tok is Token.Operator && (tok.symbol == '*' || tok.symbol == '/')) {
                    consume()
                    val right = parseUnary()
                    if (tok.symbol == '*') {
                        if (left.value.precision() + right.value.precision() > BigDecimalMath.MAX_EXACT_DIGITS) {
                            throw MathDomainException("Multiplication exceeds safe digit limit")
                        }
                        left = EvalNode(
                            left.value.multiply(right.value),
                            left.isApproximated || right.isApproximated
                        )
                    } else {
                        if (right.value.compareTo(BigDecimal.ZERO) == 0) {
                            throw MathDomainException("Cannot divide by zero")
                        }
                        val (quotient, exact) = divideExactOrRounded(left.value, right.value)
                        left = EvalNode(
                            quotient,
                            left.isApproximated || right.isApproximated || !exact
                        )
                    }
                } else {
                    break
                }
            }
            return left
        }

        private fun divideExactOrRounded(a: BigDecimal, b: BigDecimal): Pair<BigDecimal, Boolean> {
            return try {
                a.divide(b) to true
            } catch (_: ArithmeticException) {
                val guardMc = MathContext((mc.precision + 12).coerceAtMost(130), RoundingMode.HALF_UP)
                a.divide(b, guardMc).round(mc) to false
            }
        }

        private fun parseUnary(): EvalNode {
            val tok = peek()
            if (tok is Token.Operator && (tok.symbol == '+' || tok.symbol == '-')) {
                consume()
                val operand = parseUnary()
                return if (tok.symbol == '-') {
                    EvalNode(operand.value.negate(), operand.isApproximated)
                } else {
                    operand
                }
            }
            return parsePower()
        }

        private fun parsePower(): EvalNode {
            val base = parsePostfix()
            val tok = peek()
            if (tok is Token.Operator && tok.symbol == '^') {
                consume()
                // Right-associative exponentiation, allowing unary sign in exponent like 2^-3
                val exp = parseUnary()
                val (powValue, exact) = BigDecimalMath.pow(base.value, exp.value, mc)
                return EvalNode(powValue, base.isApproximated || exp.isApproximated || !exact)
            }
            return base
        }

        private fun parsePostfix(): EvalNode {
            var node = parsePrimary()
            while (true) {
                when (peek()) {
                    is Token.Factorial -> {
                        consume()
                        node = EvalNode(BigDecimalMath.factorial(node.value), node.isApproximated)
                    }
                    is Token.Percent -> {
                        consume()
                        node = EvalNode(
                            node.value.multiply(BigDecimal("0.01")),
                            node.isApproximated
                        )
                    }
                    else -> break
                }
            }
            return node
        }

        private fun parsePrimary(): EvalNode {
            val tok = peek() ?: throw IncompleteExpressionException("Expected value")
            return when (tok) {
                is Token.NumberLit -> {
                    consume()
                    EvalNode(tok.value, isApproximated = false)
                }
                is Token.Constant -> {
                    consume()
                    when (tok.id) {
                        "pi" -> EvalNode(BigDecimalMath.pi(mc), isApproximated = true)
                        "e" -> EvalNode(BigDecimalMath.e(mc), isApproximated = true)
                        else -> throw SyntaxException("Unknown constant '${tok.id}'")
                    }
                }
                is Token.LeftParen -> {
                    consume()
                    val inner = parseExpression()
                    val close = peek()
                    if (close !is Token.RightParen) {
                        throw IncompleteExpressionException("Missing closing ')'")
                    }
                    consume()
                    inner
                }
                is Token.Function -> {
                    consume()
                    val arg = if (peek() is Token.LeftParen) {
                        consume()
                        val expr = parseExpression()
                        if (peek() !is Token.RightParen) {
                            throw IncompleteExpressionException("Missing ')' after ${tok.name}")
                        }
                        consume()
                        expr
                    } else {
                        parseUnary()
                    }
                    applyFunction(tok.name, arg)
                }
                else -> throw SyntaxException("Unexpected token in expression")
            }
        }

        private fun applyFunction(name: String, arg: EvalNode): EvalNode {
            return when (name) {
                "sqrt" -> {
                    val res = BigDecimalMath.sqrt(arg.value, mc)
                    // Check if sqrt was exact (res^2 == arg.value)
                    val isExact = !arg.isApproximated &&
                        res.multiply(res).compareTo(arg.value) == 0
                    EvalNode(res, isApproximated = !isExact)
                }
                "abs" -> EvalNode(arg.value.abs(), arg.isApproximated)
                "ln" -> EvalNode(BigDecimalMath.ln(arg.value, mc), isApproximated = true)
                "log" -> {
                    val res = BigDecimalMath.log10(arg.value, mc)
                    EvalNode(res, isApproximated = true)
                }
                "sin" -> EvalNode(BigDecimalMath.sin(arg.value, angleUnit, mc), isApproximated = true)
                "cos" -> EvalNode(BigDecimalMath.cos(arg.value, angleUnit, mc), isApproximated = true)
                "tan" -> EvalNode(BigDecimalMath.tan(arg.value, angleUnit, mc), isApproximated = true)
                "asin" -> EvalNode(BigDecimalMath.asin(arg.value, angleUnit, mc), isApproximated = true)
                "acos" -> EvalNode(BigDecimalMath.acos(arg.value, angleUnit, mc), isApproximated = true)
                "atan" -> EvalNode(BigDecimalMath.atan(arg.value, angleUnit, mc), isApproximated = true)
                else -> throw SyntaxException("Unknown function '$name'")
            }
        }
    }

    private class SyntaxException(message: String) : RuntimeException(message)
    private class IncompleteExpressionException(message: String) : RuntimeException(message)

    companion object {
        const val MIN_PRECISION = 16
        const val DEFAULT_PRECISION = 32
        const val MAX_PRECISION = 100
        const val MAX_EXPRESSION_LENGTH = 512
    }
}
