package com.paisaflow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class TransactionCsvTest {
    @Test public void parsesQuotedTransactionAndIsoDate() {
        TransactionCsv.Parsed parsed = TransactionCsv.parse(
                "Date,Category,Type,Amount INR,Note\n" +
                        "2026-07-20,Food,Expense,123.45,\"Lunch, tea\"\n");
        assertEquals(1, parsed.rows.size());
        TransactionCsv.Row row = parsed.rows.get(0);
        assertEquals(20260720, row.dateKey);
        assertEquals("Food", row.category);
        assertEquals(LedgerModels.EXPENSE, row.flow);
        assertEquals(12345L, row.amountMinor);
        assertEquals("Lunch, tea", row.note);
    }

    @Test public void acceptsLegacyDisplayDate() {
        TransactionCsv.Parsed parsed = TransactionCsv.parse(
                "Date,Category,Type,Amount INR,Note\n20 Jul 2026,Salary,Credit,50000.00,Pay\n");
        assertEquals(20260720, parsed.rows.get(0).dateKey);
    }

    @Test public void acceptsStrictDayMonthYearDate() {
        TransactionCsv.Parsed parsed = TransactionCsv.parse(
                "Date,Category,Type,Amount INR,Note\n26-02-2022,Food,Expense,500.00,Groceries\n");
        assertEquals(20220226, parsed.rows.get(0).dateKey);
    }

    @Test public void rejectsImpossibleDayMonthYearDate() {
        assertThrows(IllegalArgumentException.class, () -> TransactionCsv.parse(
                "Date,Category,Type,Amount INR,Note\n31-02-2022,Food,Expense,500.00,Groceries\n"));
    }
}
