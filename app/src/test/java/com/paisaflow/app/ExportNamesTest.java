package com.paisaflow.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ExportNamesTest {
    @Test public void transactionBackupUsesExportDateNotDashboardRange() {
        assertEquals("PaisaFlow_backup_2026-09-26.csv", ExportNames.transactions(20260926));
    }
}
