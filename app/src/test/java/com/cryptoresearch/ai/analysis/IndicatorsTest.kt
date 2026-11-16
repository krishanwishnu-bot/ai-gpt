package com.cryptoresearch.ai.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {
    private fun close(expected: Double, actual: Double, tolerance: Double = 1e-8) {
        assertEquals(expected, actual, tolerance)
    }

    @Test fun smaUsesTrailingWindowAndLeavesWarmupEmpty() {
        val result = Ind.sma(doubleArrayOf(1.0, 2.0, 3.0, 4.0), 3)
        assertTrue(result[0].isNaN()); assertTrue(result[1].isNaN())
        close(2.0, result[2]); close(3.0, result[3])
    }

    @Test fun emaSeedsWithFirstPeriodAverage() {
        val result = Ind.ema(doubleArrayOf(1.0, 2.0, 3.0, 4.0), 3)
        assertTrue(result[0].isNaN()); close(2.0, result[2]); close(3.0, result[3])
    }

    @Test fun rsiIsBoundedAndIdentifiesMonotonicRise() {
        val result = Ind.rsi(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        close(100.0, result[3]); close(100.0, result[4])
        assertTrue(result.all { it.isNaN() || it in 0.0..100.0 })
    }

    @Test fun bollingerBandsContainMiddleLine() {
        val result = Ind.bollinger(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertTrue(result.mid[1].isNaN())
        assertTrue(result.lower[2] <= result.mid[2]); assertTrue(result.mid[2] <= result.upper[2])
    }

    @Test fun atrUsesTrueRangeAndWilderSmoothing() {
        val result = Ind.atr(doubleArrayOf(11.0, 12.0, 13.0, 14.0), doubleArrayOf(9.0, 10.0, 11.0, 12.0), doubleArrayOf(10.0, 11.0, 12.0, 13.0), 2)
        close(2.0, result[1]); close(2.0, result[2]); close(2.0, result[3])
    }

    @Test fun invalidAtrPeriodReturnsNaNValues() {
        val result = Ind.atr(doubleArrayOf(2.0), doubleArrayOf(1.0), doubleArrayOf(1.5), 0)
        assertTrue(result[0].isNaN())
    }

    @Test(expected = IllegalArgumentException::class)
    fun atrRejectsMismatchedInputLengths() {
        Ind.atr(doubleArrayOf(2.0, 3.0), doubleArrayOf(1.0), doubleArrayOf(1.5), 1)
    }
}
