package com.example

import com.example.domain.math.AngleUnit
import com.example.domain.math.CalculationResult
import com.example.domain.math.PrecisionMathEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecisionMathEngineTest {

    private val engine = PrecisionMathEngine()

    private fun evalSuccess(
        expr: String,
        precision: Int = 32,
        angleUnit: AngleUnit = AngleUnit.DEG
    ): CalculationResult.Success {
        val result = engine.evaluate(expr, precision, angleUnit)
        assertTrue("Expected success for '$expr' but got $result", result is CalculationResult.Success)
        return result as CalculationResult.Success
    }

    private fun evalError(
        expr: String,
        precision: Int = 32,
        angleUnit: AngleUnit = AngleUnit.DEG
    ): CalculationResult.Error {
        val result = engine.evaluate(expr, precision, angleUnit)
        assertTrue("Expected error for '$expr' but got $result", result is CalculationResult.Error)
        return result as CalculationResult.Error
    }

    @Test
    fun `standard arithmetic operations`() {
        assertEquals("42", evalSuccess("19+23").plainString)
        assertEquals("-4", evalSuccess("19-23").plainString)
        assertEquals("144", evalSuccess("12×12").plainString)
        assertEquals("0.25", evalSuccess("1÷4").plainString)
        assertEquals("0.5", evalSuccess("50%").plainString)
        assertEquals("30", evalSuccess("200×15%").plainString)
    }

    @Test
    fun `parentheses and negative numbers`() {
        assertEquals("21", evalSuccess("(3+4)×(5-2)").plainString)
        assertEquals("-15", evalSuccess("-3×5").plainString)
        assertEquals("8", evalSuccess("(-2)^3+16").plainString)
    }

    @Test
    fun `exact large integer multiplication without 16 digit truncation`() {
        val res = evalSuccess("123456789123456789×987654321987654321")
        assertEquals("121932631356500531347203169112635269", res.plainString)
        assertFalse(res.isApproximated)
    }

    @Test
    fun `high precision decimal calculation preserves configured digits`() {
        val res = evalSuccess("1÷3", precision = 50)
        assertEquals("0." + "3".repeat(50), res.plainString)
        assertTrue(res.isApproximated)

        val decimalSum = evalSuccess("0.1+0.2")
        assertEquals("0.3", decimalSum.plainString)
        assertFalse(decimalSum.isApproximated)
    }

    @Test
    fun `scientific functions powers roots logarithms and factorial`() {
        assertEquals("9", evalSuccess("√(81)").plainString)
        assertEquals("1024", evalSuccess("2^10").plainString)
        assertEquals("49", evalSuccess("7^2").plainString)
        assertEquals("343", evalSuccess("7^3").plainString)
        assertEquals("2", evalSuccess("log(100)").plainString)
        assertEquals("1", evalSuccess("ln(e)", precision = 32).plainString)
        assertEquals("3628800", evalSuccess("10!").plainString)
    }

    @Test
    fun `trigonometric and inverse trigonometric functions in DEG and RAD`() {
        assertEquals("0.5", evalSuccess("sin(30)", angleUnit = AngleUnit.DEG).plainString)
        assertEquals("0.5", evalSuccess("cos(60)", angleUnit = AngleUnit.DEG).plainString)
        assertEquals("1", evalSuccess("tan(45)", angleUnit = AngleUnit.DEG).plainString)
        assertEquals("30", evalSuccess("asin(0.5)", angleUnit = AngleUnit.DEG).plainString)
        assertEquals("60", evalSuccess("acos(0.5)", angleUnit = AngleUnit.DEG).plainString)
        assertEquals("45", evalSuccess("atan(1)", angleUnit = AngleUnit.DEG).plainString)

        assertEquals("1", evalSuccess("sin(π÷2)", angleUnit = AngleUnit.RAD).plainString)
        assertEquals("0", evalSuccess("cos(π÷2)", angleUnit = AngleUnit.RAD).plainString)
    }

    @Test
    fun `division by zero and invalid expressions fail safely`() {
        val divZero = evalError("42÷0")
        assertTrue(divZero.message.contains("zero", ignoreCase = true))

        val sqrtNeg = evalError("√(-9)")
        assertTrue(sqrtNeg.message.contains("negative", ignoreCase = true))

        val badLog = evalError("ln(0)")
        assertTrue(badLog.message.contains("undefined", ignoreCase = true))

        val badAsin = evalError("asin(2)")
        assertTrue(badAsin.message.contains("between -1 and 1", ignoreCase = true))

        val badSyntax = evalError("12++*3")
        assertFalse(badSyntax.isIncomplete)

        val unclosedParen = evalError("(5+3")
        assertTrue(unclosedParen.isIncomplete)
    }
}
