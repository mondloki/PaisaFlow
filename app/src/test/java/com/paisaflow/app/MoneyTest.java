package com.paisaflow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyTest {
    @Test public void parsesRupeesIntoExactMinorUnits() {
        assertEquals(123456L, Money.parseMinor("1,234.56"));
        assertEquals(100L, Money.parseMinor("1"));
        assertEquals(1L, Money.parseMinor("0.01"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZero() {
        Money.parseMinor("0");
    }
}

