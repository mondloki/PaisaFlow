package com.paisaflow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DateRangesTest {
    @Test public void customRangeNormalizesReverseSelection() {
        LedgerModels.DateWindow window = DateRanges.custom(20260718, 20260701);
        assertEquals(20260701, window.start);
        assertEquals(20260718, window.end);
    }

    @Test public void calendarKeyRoundTrips() {
        assertEquals(20260401, DateRanges.key(DateRanges.calendar(20260401)));
    }
}
