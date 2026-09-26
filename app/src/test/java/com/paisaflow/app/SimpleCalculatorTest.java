package com.paisaflow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class SimpleCalculatorTest {
    @Test public void evaluatesCommonMoneyCalculations() {
        assertEquals("1250", SimpleCalculator.evaluate("1000 + 250"));
        assertEquals("250", SimpleCalculator.evaluate("1000 ÷ 4"));
        assertEquals("35", SimpleCalculator.evaluate("5 + 10 × 3"));
        assertEquals("12.5", SimpleCalculator.evaluate("25 / 2"));
    }

    @Test public void rejectsInvalidAndZeroDivision() {
        assertThrows(IllegalArgumentException.class, () -> SimpleCalculator.evaluate("10 +"));
        assertThrows(IllegalArgumentException.class, () -> SimpleCalculator.evaluate("10 / 0"));
    }
}
