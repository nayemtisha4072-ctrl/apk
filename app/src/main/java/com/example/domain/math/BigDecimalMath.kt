package com.example.domain.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.ln as kotlinLn
import kotlin.math.sqrt as kotlinSqrt

/**
 * Arbitrary-precision mathematical functions backed by [BigDecimal] and [BigInteger].
 * Works across all Android API levels (minSdk 24+) without floating-point rounding drift.
 */
object BigDecimalMath {

    private const val PI_DIGITS =
        "3.141592653589793238462643383279502884197169399375105820974944592307816406286208998628034825342117067982148086513282306647"
    private const val E_DIGITS =
        "2.718281828459045235360287471352662497757247093699959574966967627724076630353547594571382178525166427427466391932003059921"
    private const val LN2_DIGITS =
        "0.693147180559945309417232121458176568075500134360255254120680009493393621969694715605863326996418687542001481020570685733"
    private const val LN10_DIGITS =
        "2.302585092994045684017991454684364207601101488628772976033327900967572609677352480235997205089598298341967784042286248633"

    val PI_HIGH = BigDecimal(PI_DIGITS)
    val E_HIGH = BigDecimal(E_DIGITS)
    private val LN2_HIGH = BigDecimal(LN2_DIGITS)
    private val LN10_HIGH = BigDecimal(LN10_DIGITS)
    private val TWO = BigDecimal.valueOf(2)
    private val HALF = BigDecimal("0.5")
    private val QUARTER = BigDecimal("0.25")
    private val SQRT2_HALF = BigDecimal("0.7071067811865475244")
    private val SQRT2 = BigDecimal("1.4142135623730950488")

    const val MAX_EXACT_DIGITS = 4000
    const val MAX_FACTORIAL_N = 1000
    const val MAX_EXACT_POWER_EXP = 500

    fun pi(mc: MathContext): BigDecimal = PI_HIGH.round(mc)

    fun e(mc: MathContext): BigDecimal = E_HIGH.round(mc)

    fun stripTrailingZerosSafe(value: BigDecimal): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        return value.stripTrailingZeros()
    }

    /**
     * Newton-Raphson square root compatible with minSdk 24.
     */
    fun sqrt(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x < BigDecimal.ZERO) {
            throw MathDomainException("Cannot take square root of a negative number")
        }
        if (x.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO

        val guardPrecision = (mc.precision + 14).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)

        // Scale x by an even power of 10 into [1, 100) for fast initial double seed
        val approxDouble = x.toDouble()
        var current: BigDecimal = if (approxDouble.isFinite() && approxDouble > 0.0) {
            BigDecimal.valueOf(kotlinSqrt(approxDouble))
        } else {
            // Large or tiny BigDecimal beyond Double range
            val sciPrecision = x.precision()
            val scaleShift = x.scale() - sciPrecision + 1
            val exponentHalf = (-scaleShift) / 2
            BigDecimal.ONE.scaleByPowerOfTen(exponentHalf)
        }

        val epsilon = BigDecimal.ONE.scaleByPowerOfTen(-(guardPrecision - 2))
        var iterations = 0
        while (iterations < 160) {
            val next = current.add(x.divide(current, guardMc), guardMc).divide(TWO, guardMc)
            val diff = next.subtract(current, guardMc).abs()
            val threshold = epsilon.multiply(next.abs().max(BigDecimal.ONE), guardMc)
            current = next
            if (diff <= threshold) break
            iterations++
        }
        return current.round(mc)
    }

    fun exp(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE
        if (x.abs() > BigDecimal("2000")) {
            throw MathDomainException("Exponent overflow: magnitude exceeds safe limit")
        }

        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val ln2 = LN2_HIGH.round(guardMc)

        // Argument reduction: x = k * ln(2) + r, |r| <= ln(2)/2
        val k = x.divide(ln2, 0, RoundingMode.HALF_EVEN).toInt()
        val r = x.subtract(ln2.multiply(BigDecimal.valueOf(k.toLong()), guardMc), guardMc)

        var sum = BigDecimal.ONE
        var term = BigDecimal.ONE
        val tolerance = BigDecimal.ONE.scaleByPowerOfTen(-guardPrecision)
        var n = 1
        while (n < 500) {
            term = term.multiply(r, guardMc).divide(BigDecimal.valueOf(n.toLong()), guardMc)
            sum = sum.add(term, guardMc)
            if (term.abs() <= tolerance) break
            n++
        }

        val pow2 = if (k >= 0) {
            BigDecimal(BigInteger.valueOf(2).pow(k))
        } else {
            BigDecimal.ONE.divide(BigDecimal(BigInteger.valueOf(2).pow(-k)), guardMc)
        }

        return sum.multiply(pow2, guardMc).round(mc)
    }

    fun ln(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x <= BigDecimal.ZERO) {
            throw MathDomainException("Logarithm is undefined for zero or negative numbers")
        }
        if (x.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO

        val guardPrecision = (mc.precision + 18).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val ln2 = LN2_HIGH.round(guardMc)

        // Normalize m into [sqrt(2)/2, sqrt(2)] using powers of 2
        var m = x
        var k = 0

        // Fast coarse scaling via base-10 log estimate -> powers of 2
        val approxLog2 = ((m.precision() - m.scale()) * 3.321928094887362).toInt()
        if (kotlin.math.abs(approxLog2) > 2) {
            k += approxLog2
            m = if (approxLog2 > 0) {
                m.divide(BigDecimal(BigInteger.valueOf(2).pow(approxLog2)), guardMc)
            } else {
                m.multiply(BigDecimal(BigInteger.valueOf(2).pow(-approxLog2)), guardMc)
            }
        }

        while (m > SQRT2) {
            m = m.divide(TWO, guardMc)
            k++
        }
        while (m < SQRT2_HALF) {
            m = m.multiply(TWO, guardMc)
            k--
        }

        // Hyperbolic arctanh series: ln(m) = 2 * sum_{n=0} u^(2n+1) / (2n+1)
        val u = m.subtract(BigDecimal.ONE, guardMc)
            .divide(m.add(BigDecimal.ONE, guardMc), guardMc)
        val u2 = u.multiply(u, guardMc)
        var term = u
        var sum = term
        val tolerance = BigDecimal.ONE.scaleByPowerOfTen(-guardPrecision)
        var denom = 3L
        var iter = 0
        while (iter < 600) {
            term = term.multiply(u2, guardMc)
            val addend = term.divide(BigDecimal.valueOf(denom), guardMc)
            sum = sum.add(addend, guardMc)
            if (addend.abs() <= tolerance) break
            denom += 2L
            iter++
        }

        val lnM = sum.multiply(TWO, guardMc)
        val result = lnM.add(ln2.multiply(BigDecimal.valueOf(k.toLong()), guardMc), guardMc)
        return result.round(mc)
    }

    fun log10(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x <= BigDecimal.ZERO) {
            throw MathDomainException("Logarithm is undefined for zero or negative numbers")
        }
        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val lnX = ln(x, guardMc)
        val ln10 = LN10_HIGH.round(guardMc)
        return lnX.divide(ln10, guardMc).round(mc)
    }

    fun pow(base: BigDecimal, exponent: BigDecimal, mc: MathContext): Pair<BigDecimal, Boolean> {
        val cleanExp = stripTrailingZerosSafe(exponent)
        if (cleanExp.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE to true
        }
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            if (cleanExp < BigDecimal.ZERO) {
                throw MathDomainException("Cannot raise 0 to a negative power")
            }
            return BigDecimal.ZERO to true
        }

        // Exact integer exponent fast path when within safe bounds
        val isIntegerExp = cleanExp.scale() <= 0
        if (isIntegerExp) {
            val expInt = try {
                cleanExp.intValueExact()
            } catch (_: ArithmeticException) {
                throw MathDomainException("Exponent is too large")
            }
            if (kotlin.math.abs(expInt) <= MAX_EXACT_POWER_EXP) {
                val absExp = kotlin.math.abs(expInt)
                val estDigits = (base.precision().toLong() * absExp)
                if (expInt >= 0 && estDigits <= MAX_EXACT_DIGITS) {
                    return base.pow(expInt) to true
                }
                if (expInt < 0 && estDigits <= MAX_EXACT_DIGITS) {
                    val denom = base.pow(absExp)
                    return try {
                        BigDecimal.ONE.divide(denom) to true
                    } catch (_: ArithmeticException) {
                        BigDecimal.ONE.divide(denom, mc) to false
                    }
                }
            }
            // Large integer exponent falls back to high-precision log/exp if positive base
            if (base < BigDecimal.ZERO) {
                val sign = if (expInt % 2 != 0) -BigDecimal.ONE else BigDecimal.ONE
                val guardMc = MathContext((mc.precision + 16).coerceAtMost(130), RoundingMode.HALF_UP)
                val magnitude = exp(cleanExp.multiply(ln(base.abs(), guardMc), guardMc), guardMc)
                return magnitude.multiply(sign, guardMc).round(mc) to false
            }
        }

        if (base < BigDecimal.ZERO) {
            throw MathDomainException("Fractional power of a negative number is not real")
        }

        val guardMc = MathContext((mc.precision + 16).coerceAtMost(130), RoundingMode.HALF_UP)
        val result = exp(cleanExp.multiply(ln(base, guardMc), guardMc), guardMc).round(mc)
        return result to false
    }

    fun factorial(value: BigDecimal): BigDecimal {
        val clean = stripTrailingZerosSafe(value)
        if (clean < BigDecimal.ZERO || clean.scale() > 0) {
            throw MathDomainException("Factorial is only defined for non-negative integers")
        }
        val n = try {
            clean.intValueExact()
        } catch (_: ArithmeticException) {
            throw MathDomainException("Factorial limit exceeded (max $MAX_FACTORIAL_N!)")
        }
        if (n > MAX_FACTORIAL_N) {
            throw MathDomainException("Factorial limit exceeded (max $MAX_FACTORIAL_N!)")
        }
        var acc = BigInteger.ONE
        for (i in 2..n) {
            acc = acc.multiply(BigInteger.valueOf(i.toLong()))
        }
        return BigDecimal(acc)
    }

    fun sin(angle: BigDecimal, angleUnit: AngleUnit, mc: MathContext): BigDecimal {
        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val rad = toRadians(angle, angleUnit, guardMc)
        val raw = sinRad(rad, guardMc)
        return snapTrigValue(raw, mc)
    }

    fun cos(angle: BigDecimal, angleUnit: AngleUnit, mc: MathContext): BigDecimal {
        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val rad = toRadians(angle, angleUnit, guardMc)
        val raw = cosRad(rad, guardMc)
        return snapTrigValue(raw, mc)
    }

    fun tan(angle: BigDecimal, angleUnit: AngleUnit, mc: MathContext): BigDecimal {
        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val rad = toRadians(angle, angleUnit, guardMc)
        val c = snapTrigValue(cosRad(rad, guardMc), guardMc)
        if (c.compareTo(BigDecimal.ZERO) == 0) {
            throw MathDomainException("Tangent is undefined at this angle")
        }
        val s = snapTrigValue(sinRad(rad, guardMc), guardMc)
        return snapTrigValue(s.divide(c, guardMc), mc)
    }

    fun asin(value: BigDecimal, angleUnit: AngleUnit, mc: MathContext): BigDecimal {
        if (value.abs() > BigDecimal.ONE) {
            throw MathDomainException("asin input must be between -1 and 1")
        }
        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val rad = when {
            value.compareTo(BigDecimal.ONE) == 0 -> PI_HIGH.round(guardMc).divide(TWO, guardMc)
            value.compareTo(BigDecimal.ONE.negate()) == 0 ->
                PI_HIGH.round(guardMc).divide(TWO, guardMc).negate()
            value.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO
            else -> {
                val underRoot = BigDecimal.ONE.subtract(value.multiply(value, guardMc), guardMc)
                atanRad(value.divide(sqrt(underRoot, guardMc), guardMc), guardMc)
            }
        }
        val out = fromRadians(rad, angleUnit, guardMc)
        return snapNearZero(out, mc)
    }

    fun acos(value: BigDecimal, angleUnit: AngleUnit, mc: MathContext): BigDecimal {
        if (value.abs() > BigDecimal.ONE) {
            throw MathDomainException("acos input must be between -1 and 1")
        }
        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val halfPi = PI_HIGH.round(guardMc).divide(TWO, guardMc)
        val asinRadVal = when {
            value.compareTo(BigDecimal.ONE) == 0 -> halfPi
            value.compareTo(BigDecimal.ONE.negate()) == 0 -> halfPi.negate()
            value.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO
            else -> {
                val underRoot = BigDecimal.ONE.subtract(value.multiply(value, guardMc), guardMc)
                atanRad(value.divide(sqrt(underRoot, guardMc), guardMc), guardMc)
            }
        }
        val rad = halfPi.subtract(asinRadVal, guardMc)
        val out = fromRadians(rad, angleUnit, guardMc)
        return snapNearZero(out, mc)
    }

    fun atan(value: BigDecimal, angleUnit: AngleUnit, mc: MathContext): BigDecimal {
        val guardPrecision = (mc.precision + 16).coerceAtMost(130)
        val guardMc = MathContext(guardPrecision, RoundingMode.HALF_UP)
        val rad = atanRad(value, guardMc)
        val out = fromRadians(rad, angleUnit, guardMc)
        return snapNearZero(out, mc)
    }

    private fun toRadians(angle: BigDecimal, unit: AngleUnit, mc: MathContext): BigDecimal {
        return when (unit) {
            AngleUnit.RAD -> angle
            AngleUnit.DEG -> {
                val degMod = angle.remainder(BigDecimal.valueOf(360), mc)
                degMod.multiply(PI_HIGH.round(mc), mc).divide(BigDecimal.valueOf(180), mc)
            }
        }
    }

    private fun fromRadians(rad: BigDecimal, unit: AngleUnit, mc: MathContext): BigDecimal {
        return when (unit) {
            AngleUnit.RAD -> rad
            AngleUnit.DEG -> rad.multiply(BigDecimal.valueOf(180), mc).divide(PI_HIGH.round(mc), mc)
        }
    }

    private fun sinRad(rad: BigDecimal, mc: MathContext): BigDecimal {
        val pi = PI_HIGH.round(mc)
        val twoPi = pi.multiply(TWO, mc)
        val halfPi = pi.divide(TWO, mc)
        var x = rad.remainder(twoPi, mc)
        if (x > pi) x = x.subtract(twoPi, mc)
        if (x < pi.negate()) x = x.add(twoPi, mc)

        var sign = 1
        if (x < BigDecimal.ZERO) {
            x = x.negate()
            sign = -1
        }
        if (x > halfPi) {
            x = pi.subtract(x, mc)
        }

        // Now x in [0, pi/2]. If x > pi/4, compute cos(pi/2 - x) for faster convergence
        val raw = if (x > pi.divide(BigDecimal.valueOf(4), mc)) {
            cosTaylor(halfPi.subtract(x, mc), mc)
        } else {
            sinTaylor(x, mc)
        }
        return if (sign < 0) raw.negate() else raw
    }

    private fun cosRad(rad: BigDecimal, mc: MathContext): BigDecimal {
        val pi = PI_HIGH.round(mc)
        val twoPi = pi.multiply(TWO, mc)
        val halfPi = pi.divide(TWO, mc)
        var x = rad.remainder(twoPi, mc).abs()
        if (x > pi) x = twoPi.subtract(x, mc)

        var sign = 1
        if (x > halfPi) {
            x = pi.subtract(x, mc)
            sign = -1
        }
        val raw = if (x > pi.divide(BigDecimal.valueOf(4), mc)) {
            sinTaylor(halfPi.subtract(x, mc), mc)
        } else {
            cosTaylor(x, mc)
        }
        return if (sign < 0) raw.negate() else raw
    }

    private fun sinTaylor(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        val x2 = x.multiply(x, mc)
        var term = x
        var sum = x
        val tolerance = BigDecimal.ONE.scaleByPowerOfTen(-mc.precision)
        var k = 1L
        while (k < 250) {
            val denom = BigDecimal.valueOf((2 * k) * (2 * k + 1))
            term = term.multiply(x2, mc).negate().divide(denom, mc)
            sum = sum.add(term, mc)
            if (term.abs() <= tolerance) break
            k++
        }
        return sum
    }

    private fun cosTaylor(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE
        val x2 = x.multiply(x, mc)
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        val tolerance = BigDecimal.ONE.scaleByPowerOfTen(-mc.precision)
        var k = 1L
        while (k < 250) {
            val denom = BigDecimal.valueOf((2 * k - 1) * (2 * k))
            term = term.multiply(x2, mc).negate().divide(denom, mc)
            sum = sum.add(term, mc)
            if (term.abs() <= tolerance) break
            k++
        }
        return sum
    }

    private fun atanRad(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        val sign = if (x < BigDecimal.ZERO) -1 else 1
        var v = x.abs()
        val pi = PI_HIGH.round(mc)
        val halfPi = pi.divide(TWO, mc)

        val invert = v > BigDecimal.ONE
        if (invert) {
            v = BigDecimal.ONE.divide(v, mc)
        }

        // Half-angle reduction: atan(v) = 2 * atan(v / (1 + sqrt(1 + v^2)))
        var reductions = 0
        while (v > QUARTER && reductions < 6) {
            val denom = BigDecimal.ONE.add(sqrt(BigDecimal.ONE.add(v.multiply(v, mc), mc), mc), mc)
            v = v.divide(denom, mc)
            reductions++
        }

        // Taylor series: atan(v) = v - v^3/3 + v^5/5 - ...
        val v2 = v.multiply(v, mc)
        var power = v
        var sum = v
        val tolerance = BigDecimal.ONE.scaleByPowerOfTen(-mc.precision)
        var n = 1L
        while (n < 300) {
            power = power.multiply(v2, mc).negate()
            val addend = power.divide(BigDecimal.valueOf(2 * n + 1), mc)
            sum = sum.add(addend, mc)
            if (addend.abs() <= tolerance) break
            n++
        }

        var result = sum
        if (reductions > 0) {
            result = result.multiply(BigDecimal.valueOf(1L shl reductions), mc)
        }
        if (invert) {
            result = halfPi.subtract(result, mc)
        }
        return if (sign < 0) result.negate() else result
    }

    private fun snapTrigValue(raw: BigDecimal, mc: MathContext): BigDecimal {
        val eps = BigDecimal.ONE.scaleByPowerOfTen(-(mc.precision - 2).coerceAtLeast(6))
        val rounded = raw.round(mc)
        return when {
            rounded.abs() <= eps -> BigDecimal.ZERO
            rounded.subtract(HALF).abs() <= eps -> HALF
            rounded.add(HALF).abs() <= eps -> HALF.negate()
            rounded.subtract(BigDecimal.ONE).abs() <= eps -> BigDecimal.ONE
            rounded.add(BigDecimal.ONE).abs() <= eps -> BigDecimal.ONE.negate()
            else -> rounded
        }
    }

    private fun snapNearZero(raw: BigDecimal, mc: MathContext): BigDecimal {
        val eps = BigDecimal.ONE.scaleByPowerOfTen(-(mc.precision - 2).coerceAtLeast(6))
        val rounded = raw.round(mc)
        return if (rounded.abs() <= eps) BigDecimal.ZERO else rounded
    }
}

enum class AngleUnit {
    DEG,
    RAD
}

class MathDomainException(message: String) : RuntimeException(message)
