package com.paisaflow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class LedgerDatabaseDeviceTest {
    private static final String DATABASE_NAME = "paisaflow.db";
    private Context context;
    private LedgerDatabase database;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
    }

    @After public void tearDown() {
        if (database != null) database.close();
        context.deleteDatabase(DATABASE_NAME);
    }

    @Test public void versionTwoUpgradePreservesUserStateAndClassifiesCategories() {
        createVersionTwoDatabase();
        database = new LedgerDatabase(context);

        List<LedgerModels.Category> categories = database.categories();
        LedgerModels.Category food = find(categories, "Food");
        LedgerModels.Category test = find(categories, "Test");
        LedgerModels.Category deposit = find(categories, "Deposit");
        LedgerModels.Category dividends = find(categories, "Dividends");

        assertTrue(food.standard);
        assertFalse(test.standard);
        assertEquals("piggy", deposit.icon);
        assertEquals("dividend", dividends.icon);
        assertNotNull(find(categories, "Miscellaneous Expense"));
        assertNotNull(find(categories, "Miscellaneous Investment"));
        assertNotNull(find(categories, "Miscellaneous Credit"));
        assertFalse(contains(categories, "Subscriptions"));
        assertFalse(contains(categories, "Refund"));
        assertEquals(123_45L, database.openingBalance());
    }

    @Test public void customCategoryRecordsCanMoveButStandardCategoryCannotBeDeleted() {
        database = new LedgerDatabase(context);
        List<LedgerModels.Category> categories = database.categories();
        LedgerModels.Category miscellaneous = find(categories, "Miscellaneous Expense");
        LedgerModels.Category food = find(categories, "Food");
        long customId = database.addCategory("Test", LedgerModels.EXPENSE, "dots", 0xFF607D8B);
        database.addEntry(987_65L, 20260719, customId, LedgerModels.EXPENSE, "kept");

        assertEquals(1, database.entryCountForCategory(customId));
        database.deleteCustomCategory(customId, miscellaneous.id, false);
        LedgerModels.Snapshot snapshot = database.snapshot(20260719, 20260719, 20);
        assertEquals(1, snapshot.entries.size());
        assertEquals("Miscellaneous Expense", snapshot.entries.get(0).category.name);
        assertEquals("kept", snapshot.entries.get(0).note);

        long disposableId = database.addCategory("Disposable", LedgerModels.EXPENSE, "dots", 0xFF607D8B);
        database.addEntry(111_00L, 20260719, disposableId, LedgerModels.EXPENSE, "remove");
        database.deleteCustomCategory(disposableId, null, true);
        assertEquals(0, database.entryCountForCategory(disposableId));
        assertFalse(contains(database.categories(), "Disposable"));

        try {
            database.deleteCustomCategory(food.id, null, true);
            fail("A standard category must not be deletable");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Standard"));
        }
    }

    @Test public void transactionImportMergesAndCreatesMissingCategories() {
        database = new LedgerDatabase(context);
        List<TransactionCsv.Row> rows = Arrays.asList(
                new TransactionCsv.Row(20260720, "Food", LedgerModels.EXPENSE, 125_50L, "Lunch"),
                new TransactionCsv.Row(20260720, "Side Project", LedgerModels.CREDIT, 5000_00L, "Invoice"));

        List<String> missing = database.missingTransactionCategories(rows);
        assertEquals(1, missing.size());
        assertTrue(missing.get(0).contains("Side Project"));
        database.mergeTransactions(rows, true);

        LedgerModels.Snapshot snapshot = database.snapshot(20260720, 20260720, 10);
        assertEquals(2, snapshot.entries.size());
        assertNotNull(find(database.categories(), "Side Project"));
        assertEquals(5125_50L, snapshot.summary.credits + snapshot.summary.expenses);
    }

    private void createVersionTwoDatabase() {
        File path = context.getDatabasePath(DATABASE_NAME);
        File parent = path.getParentFile();
        if (parent != null) assertTrue(parent.exists() || parent.mkdirs());
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path, null)) {
            db.execSQL("CREATE TABLE categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL COLLATE NOCASE UNIQUE," +
                    "flow TEXT NOT NULL CHECK(flow IN ('EXPENSE','INVESTMENT','CREDIT'))," +
                    "icon TEXT NOT NULL,color INTEGER NOT NULL,active INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE TABLE entries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "amount_minor INTEGER NOT NULL CHECK(amount_minor > 0)," +
                    "date_key INTEGER NOT NULL,category_id INTEGER NOT NULL REFERENCES categories(id)," +
                    "flow TEXT NOT NULL CHECK(flow IN ('EXPENSE','INVESTMENT','CREDIT'))," +
                    "note TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX idx_entries_date ON entries(date_key DESC)");
            db.execSQL("CREATE INDEX idx_entries_category_date ON entries(category_id, date_key DESC)");
            db.execSQL("CREATE TABLE app_settings (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            addLegacyCategory(db, "Food", LedgerModels.EXPENSE, "food");
            addLegacyCategory(db, "Test", LedgerModels.EXPENSE, "dot");
            addLegacyCategory(db, "Subscriptions", LedgerModels.EXPENSE, "repeat");
            addLegacyCategory(db, "Deposit", LedgerModels.CREDIT, "deposit");
            addLegacyCategory(db, "Dividends", LedgerModels.CREDIT, "coin");
            addLegacyCategory(db, "Refund", LedgerModels.CREDIT, "refund");
            db.execSQL("INSERT INTO app_settings(key,value) VALUES('opening_balance_minor','12345')");
            db.setVersion(2);
        }
    }

    private static void addLegacyCategory(SQLiteDatabase db, String name, String flow, String icon) {
        ContentValues values = new ContentValues(5);
        values.put("name", name);
        values.put("flow", flow);
        values.put("icon", icon);
        values.put("color", 0xFF607D8B);
        values.put("active", 1);
        db.insertOrThrow("categories", null, values);
    }

    private static LedgerModels.Category find(List<LedgerModels.Category> categories, String name) {
        for (LedgerModels.Category category : categories) {
            if (name.equals(category.name)) return category;
        }
        throw new AssertionError("Missing category: " + name);
    }

    private static boolean contains(List<LedgerModels.Category> categories, String name) {
        for (LedgerModels.Category category : categories) if (name.equals(category.name)) return true;
        return false;
    }
}
