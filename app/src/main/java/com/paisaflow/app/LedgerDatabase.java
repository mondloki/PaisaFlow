package com.paisaflow.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

final class LedgerDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "paisaflow.db";
    private static final int DB_VERSION = 5;

    LedgerDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL COLLATE NOCASE UNIQUE," +
                "flow TEXT NOT NULL CHECK(flow IN ('EXPENSE','INVESTMENT','CREDIT'))," +
                "icon TEXT NOT NULL," +
                "color INTEGER NOT NULL," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "standard INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "amount_minor INTEGER NOT NULL CHECK(amount_minor > 0)," +
                "date_key INTEGER NOT NULL," +
                "category_id INTEGER NOT NULL REFERENCES categories(id)," +
                "flow TEXT NOT NULL CHECK(flow IN ('EXPENSE','INVESTMENT','CREDIT'))," +
                "note TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_entries_date ON entries(date_key DESC)");
        db.execSQL("CREATE INDEX idx_entries_category_date ON entries(category_id, date_key DESC)");
        createSettingsTable(db);
        seed(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createSettingsTable(db);
            db.execSQL("UPDATE categories SET active=0 WHERE name='Bills'");
            updateIcon(db, "Food", "food");
            updateIcon(db, "Transport", "car");
            updateIcon(db, "Shopping", "bag");
            updateIcon(db, "Health", "health");
            updateIcon(db, "Stocks", "chart");
            updateIcon(db, "Bonds", "bond");
            updateIcon(db, "Mutual Funds", "layers");
            updateIcon(db, "F & O", "swap");
            updateIcon(db, "Salary", "rupee");
            updateIcon(db, "Bond Interest", "percent");
            updateIcon(db, "Dividends", "coin");
            seed(db);
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE categories ADD COLUMN standard INTEGER NOT NULL DEFAULT 0");
            markCurrentStandardCategories(db);
            removeUnusedRetiredCategory(db, "Subscriptions");
            removeUnusedRetiredCategory(db, "Refund");
            updateIcon(db, "Deposit", "piggy");
            updateIcon(db, "Dividends", "dividend");
            seed(db);
        }
        if (oldVersion < 4) {
            seed(db);
            markCurrentStandardCategories(db);
        }
        if (oldVersion < 5) {
            seed(db);
            markCurrentStandardCategories(db);
        }
    }

    List<LedgerModels.Category> categories() {
        ArrayList<LedgerModels.Category> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,name,flow,icon,color,standard FROM categories WHERE active=1 " +
                        "ORDER BY CASE flow WHEN 'EXPENSE' THEN 0 WHEN 'INVESTMENT' THEN 1 ELSE 2 END," +
                        "name COLLATE NOCASE ASC", null)) {
            while (cursor.moveToNext()) result.add(readCategory(cursor, 0));
        }
        return result;
    }

    long addCategory(String name, String flow, String icon, int color) {
        ContentValues values = new ContentValues(6);
        values.put("name", name.trim());
        values.put("flow", flow);
        values.put("icon", icon);
        values.put("color", color);
        values.put("active", 1);
        values.put("standard", 0);
        return getWritableDatabase().insertOrThrow("categories", null, values);
    }

    long entryCountForCategory(long categoryId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM entries WHERE category_id=?",
                new String[]{Long.toString(categoryId)})) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }

    void deleteCustomCategory(long categoryId, Long replacementCategoryId, boolean deleteEntries) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String flow;
            try (Cursor cursor = db.rawQuery(
                    "SELECT flow,standard FROM categories WHERE id=? AND active=1",
                    new String[]{Long.toString(categoryId)})) {
                if (!cursor.moveToFirst()) throw new IllegalArgumentException("Category not found");
                flow = cursor.getString(0);
                if (cursor.getInt(1) == 1) throw new IllegalArgumentException("Standard categories cannot be deleted");
            }

            long recordCount = entryCountForCategory(categoryId);
            if (replacementCategoryId != null) {
                try (Cursor cursor = db.rawQuery(
                        "SELECT flow FROM categories WHERE id=? AND active=1",
                        new String[]{Long.toString(replacementCategoryId)})) {
                    if (!cursor.moveToFirst() || categoryId == replacementCategoryId
                            || !flow.equals(cursor.getString(0))) {
                        throw new IllegalArgumentException("Choose another category of the same type");
                    }
                }
                ContentValues move = new ContentValues(1);
                move.put("category_id", replacementCategoryId);
                db.update("entries", move, "category_id=?", new String[]{Long.toString(categoryId)});
            } else if (deleteEntries) {
                db.delete("entries", "category_id=?", new String[]{Long.toString(categoryId)});
            } else if (recordCount > 0) {
                throw new IllegalArgumentException("This category still has records");
            }

            if (db.delete("categories", "id=? AND standard=0", new String[]{Long.toString(categoryId)}) != 1) {
                throw new IllegalStateException("Category could not be deleted");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    long addEntry(long amountMinor, int dateKey, long categoryId, String flow, String note) {
        ContentValues values = new ContentValues(6);
        values.put("amount_minor", amountMinor);
        values.put("date_key", dateKey);
        values.put("category_id", categoryId);
        values.put("flow", flow);
        values.put("note", note == null ? "" : note.trim());
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("entries", null, values);
    }

    List<String> missingTransactionCategories(List<TransactionCsv.Row> rows) {
        Map<String, ImportCategory> available = importCategories(getReadableDatabase());
        LinkedHashMap<String, String> missing = new LinkedHashMap<>();
        Map<String, String> plannedFlows = new HashMap<>();
        for (TransactionCsv.Row row : rows) {
            String key = row.category.toLowerCase(Locale.ROOT);
            ImportCategory existing = available.get(key);
            if (existing != null && !existing.flow.equals(row.flow)) {
                throw new IllegalArgumentException(row.category + " already exists as a different type");
            }
            if (existing == null) {
                String plannedFlow = plannedFlows.get(key);
                if (plannedFlow != null && !plannedFlow.equals(row.flow)) {
                    throw new IllegalArgumentException(row.category + " is used with different types in the CSV");
                }
                if (plannedFlow == null) plannedFlows.put(key, row.flow);
                missing.put(key, row.category + " (" + flowLabel(row.flow) + ")");
            }
        }
        return new ArrayList<>(missing.values());
    }

    void mergeTransactions(List<TransactionCsv.Row> rows, boolean createMissingCategories) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, ImportCategory> available = importCategories(db);
            Map<String, String> plannedFlows = new HashMap<>();
            for (TransactionCsv.Row row : rows) {
                String key = row.category.toLowerCase(Locale.ROOT);
                ImportCategory existing = available.get(key);
                if (existing != null && !existing.flow.equals(row.flow)) {
                    throw new IllegalArgumentException(row.category + " already exists as a different type");
                }
                if (existing == null && !createMissingCategories) {
                    throw new IllegalArgumentException("CSV references a missing category: " + row.category);
                }
                if (existing == null) {
                    String plannedFlow = plannedFlows.get(key);
                    if (plannedFlow != null && !plannedFlow.equals(row.flow)) {
                        throw new IllegalArgumentException(row.category + " is used with different types in the CSV");
                    }
                    if (plannedFlow == null) plannedFlows.put(key, row.flow);
                }
            }

            long createdAt = System.currentTimeMillis();
            for (TransactionCsv.Row row : rows) {
                String key = row.category.toLowerCase(Locale.ROOT);
                ImportCategory category = available.get(key);
                if (category == null) {
                    ContentValues categoryValues = new ContentValues(6);
                    categoryValues.put("name", row.category);
                    categoryValues.put("flow", row.flow);
                    categoryValues.put("icon", "dots");
                    categoryValues.put("color", importColor(row.flow));
                    categoryValues.put("active", 1);
                    categoryValues.put("standard", 0);
                    long id = db.insertOrThrow("categories", null, categoryValues);
                    category = new ImportCategory(id, row.flow, true);
                    available.put(key, category);
                } else if (!category.active) {
                    ContentValues activate = new ContentValues(1);
                    activate.put("active", 1);
                    db.update("categories", activate, "id=?", new String[]{Long.toString(category.id)});
                    category.active = true;
                }

                ContentValues entry = new ContentValues(6);
                entry.put("amount_minor", row.amountMinor);
                entry.put("date_key", row.dateKey);
                entry.put("category_id", category.id);
                entry.put("flow", row.flow);
                entry.put("note", row.note);
                entry.put("created_at", createdAt++);
                db.insertOrThrow("entries", null, entry);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void deleteEntry(long id) {
        getWritableDatabase().delete("entries", "id=?", new String[]{Long.toString(id)});
    }

    void eraseTransactions() {
        getWritableDatabase().delete("entries", null, null);
    }

    void eraseAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("entries", null, null);
            db.delete("categories", null, null);
            db.delete("app_settings", null, null);
            seed(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    LedgerModels.Snapshot snapshot(int start, int end, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        String[] args = {Integer.toString(start), Integer.toString(end)};
        LedgerModels.Summary summary;
        try (Cursor cursor = db.rawQuery(
                "SELECT " +
                        "COALESCE(SUM(CASE WHEN flow='EXPENSE' THEN amount_minor ELSE 0 END),0)," +
                        "COALESCE(SUM(CASE WHEN flow='INVESTMENT' THEN amount_minor ELSE 0 END),0)," +
                        "COALESCE(SUM(CASE WHEN flow='CREDIT' THEN amount_minor ELSE 0 END),0)," +
                        "COUNT(*) FROM entries WHERE date_key BETWEEN ? AND ?", args)) {
            cursor.moveToFirst();
            summary = new LedgerModels.Summary(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3));
        }

        ArrayList<LedgerModels.Entry> entries = new ArrayList<>();
        String sql = "SELECT e.id,e.amount_minor,e.date_key,e.note," +
                "c.id,c.name,e.flow,c.icon,c.color,c.standard " +
                "FROM entries e JOIN categories c ON c.id=e.category_id " +
                "WHERE e.date_key BETWEEN ? AND ? ORDER BY e.date_key DESC,e.created_at DESC LIMIT " + Math.max(1, limit);
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                LedgerModels.Category category = new LedgerModels.Category(
                        cursor.getLong(4), cursor.getString(5), cursor.getString(6), cursor.getString(7),
                        cursor.getInt(8), cursor.getInt(9) == 1);
                entries.add(new LedgerModels.Entry(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getString(3), category));
            }
        }
        return new LedgerModels.Snapshot(summary, entries, availableCash(DateRanges.todayKey()));
    }

    List<LedgerModels.CategoryTotal> categoryTotals(int start, int end, String flow) {
        ArrayList<LedgerModels.CategoryTotal> result = new ArrayList<>();
        String[] args = {Integer.toString(start), Integer.toString(end), flow};
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT c.id,c.name,e.flow,c.icon,c.color,c.standard,SUM(e.amount_minor) total " +
                        "FROM entries e JOIN categories c ON c.id=e.category_id " +
                        "WHERE e.date_key BETWEEN ? AND ? AND e.flow=? " +
                        "GROUP BY c.id,c.name,e.flow,c.icon,c.color,c.standard ORDER BY total DESC", args)) {
            while (cursor.moveToNext()) {
                result.add(new LedgerModels.CategoryTotal(readCategory(cursor, 0), cursor.getLong(6)));
            }
        }
        return result;
    }

    long availableCash(int throughDate) {
        long opening = openingBalance();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN flow='CREDIT' THEN amount_minor ELSE -amount_minor END),0) " +
                        "FROM entries WHERE date_key<=?", new String[]{Integer.toString(throughDate)})) {
            cursor.moveToFirst();
            return opening + cursor.getLong(0);
        }
    }

    boolean hasOpeningBalance() {
        return setting("opening_balance_minor") != null;
    }

    long openingBalance() {
        String value = setting("opening_balance_minor");
        if (value == null) return 0L;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    void setOpeningBalance(long amountMinor) {
        putSetting("opening_balance_minor", Long.toString(amountMinor));
        putSetting("opening_prompt_handled", "1");
    }

    boolean openingPromptHandled() {
        return "1".equals(setting("opening_prompt_handled"));
    }

    void markOpeningPromptHandled() {
        putSetting("opening_prompt_handled", "1");
    }

    String createBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "paisaflow-backup");
        root.put("version", 1);
        root.put("created_at", System.currentTimeMillis());
        if (hasOpeningBalance()) root.put("opening_balance_minor", openingBalance());

        JSONArray categoryArray = new JSONArray();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,name,flow,icon,color,active,standard FROM categories ORDER BY id", null)) {
            while (cursor.moveToNext()) {
                JSONObject category = new JSONObject();
                category.put("name", cursor.getString(1));
                category.put("flow", cursor.getString(2));
                category.put("icon", cursor.getString(3));
                category.put("color", cursor.getInt(4));
                category.put("active", cursor.getInt(5) == 1);
                category.put("standard", cursor.getInt(6) == 1);
                categoryArray.put(category);
            }
        }
        root.put("categories", categoryArray);

        JSONArray entryArray = new JSONArray();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT e.amount_minor,e.date_key,c.name,e.flow,e.note,e.created_at " +
                        "FROM entries e JOIN categories c ON c.id=e.category_id ORDER BY e.id", null)) {
            while (cursor.moveToNext()) {
                JSONObject entry = new JSONObject();
                entry.put("amount_minor", cursor.getLong(0));
                entry.put("date_key", cursor.getInt(1));
                entry.put("category", cursor.getString(2));
                entry.put("flow", cursor.getString(3));
                entry.put("note", cursor.getString(4));
                entry.put("created_at", cursor.getLong(5));
                entryArray.put(entry);
            }
        }
        root.put("transactions", entryArray);
        return root.toString(2);
    }

    LedgerModels.BackupInfo inspectBackup(String json) throws Exception {
        JSONObject root = validatedBackup(json);
        JSONArray categoryArray = root.getJSONArray("categories");
        JSONArray entryArray = root.getJSONArray("transactions");
        return new LedgerModels.BackupInfo(categoryArray.length(), entryArray.length(),
                root.has("opening_balance_minor"));
    }

    void restoreBackup(String json) throws Exception {
        JSONObject root = validatedBackup(json);
        JSONArray categoryArray = root.getJSONArray("categories");
        JSONArray entryArray = root.getJSONArray("transactions");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("entries", null, null);
            db.delete("categories", null, null);
            db.delete("app_settings", null, null);
            Map<String, Long> categoryIds = new HashMap<>();
            for (int i = 0; i < categoryArray.length(); i++) {
                JSONObject category = categoryArray.getJSONObject(i);
                ContentValues values = new ContentValues(6);
                String name = category.getString("name").trim();
                values.put("name", name);
                values.put("flow", category.getString("flow"));
                values.put("icon", category.getString("icon"));
                values.put("color", category.getInt("color"));
                values.put("active", category.optBoolean("active", true) ? 1 : 0);
                values.put("standard", isStandardCategory(name, category.getString("flow")) ? 1 : 0);
                long id = db.insertOrThrow("categories", null, values);
                categoryIds.put(name.toLowerCase(Locale.ROOT), id);
            }
            for (int i = 0; i < entryArray.length(); i++) {
                JSONObject entry = entryArray.getJSONObject(i);
                Long categoryId = categoryIds.get(entry.getString("category").trim().toLowerCase(Locale.ROOT));
                if (categoryId == null) throw new IllegalArgumentException("Transaction references a missing category");
                ContentValues values = new ContentValues(6);
                values.put("amount_minor", entry.getLong("amount_minor"));
                values.put("date_key", entry.getInt("date_key"));
                values.put("category_id", categoryId);
                values.put("flow", entry.getString("flow"));
                values.put("note", entry.optString("note", ""));
                values.put("created_at", entry.optLong("created_at", System.currentTimeMillis()));
                db.insertOrThrow("entries", null, values);
            }
            if (root.has("opening_balance_minor")) {
                putSettingInDatabase(db, "opening_balance_minor", Long.toString(root.getLong("opening_balance_minor")));
            }
            putSettingInDatabase(db, "opening_prompt_handled", "1");
            markCurrentStandardCategories(db);
            removeUnusedRetiredCategory(db, "Subscriptions");
            removeUnusedRetiredCategory(db, "Refund");
            updateIcon(db, "Deposit", "piggy");
            updateIcon(db, "Dividends", "dividend");
            seed(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static JSONObject validatedBackup(String json) throws Exception {
        if (json == null || json.length() > 50_000_000) throw new IllegalArgumentException("Backup is empty or too large");
        JSONObject root = new JSONObject(json);
        if (!"paisaflow-backup".equals(root.optString("format")) || root.optInt("version") != 1) {
            throw new IllegalArgumentException("This is not a supported PaisaFlow backup");
        }
        JSONArray categories = root.getJSONArray("categories");
        JSONArray transactions = root.getJSONArray("transactions");
        if (categories.length() == 0 || categories.length() > 1000 || transactions.length() > 1_000_000) {
            throw new IllegalArgumentException("Backup contains an unsupported number of records");
        }
        Map<String, String> flowsByCategory = new HashMap<>();
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.getJSONObject(i);
            String name = category.getString("name").trim();
            String flow = category.getString("flow");
            String icon = category.getString("icon");
            if (name.isEmpty() || name.length() > 80 || icon.isEmpty() || icon.length() > 40 || !validFlow(flow)) {
                throw new IllegalArgumentException("Backup contains an invalid category");
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (flowsByCategory.put(key, flow) != null) throw new IllegalArgumentException("Backup contains duplicate categories");
        }
        for (int i = 0; i < transactions.length(); i++) {
            JSONObject entry = transactions.getJSONObject(i);
            long amount = entry.getLong("amount_minor");
            int date = entry.getInt("date_key");
            String category = entry.getString("category").trim().toLowerCase(Locale.ROOT);
            String flow = entry.getString("flow");
            String note = entry.optString("note", "");
            if (amount <= 0 || date < 19000101 || date > 29991231 || note.length() > 2000
                    || !validFlow(flow) || !flow.equals(flowsByCategory.get(category))) {
                throw new IllegalArgumentException("Backup contains an invalid transaction");
            }
        }
        if (root.has("opening_balance_minor") && root.getLong("opening_balance_minor") < 0) {
            throw new IllegalArgumentException("Backup contains an invalid opening balance");
        }
        return root;
    }

    private static boolean validFlow(String flow) {
        return LedgerModels.EXPENSE.equals(flow) || LedgerModels.INVESTMENT.equals(flow)
                || LedgerModels.CREDIT.equals(flow);
    }

    private static Map<String, ImportCategory> importCategories(SQLiteDatabase db) {
        HashMap<String, ImportCategory> result = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT id,name,flow,active FROM categories", null)) {
            while (cursor.moveToNext()) {
                result.put(cursor.getString(1).toLowerCase(Locale.ROOT),
                        new ImportCategory(cursor.getLong(0), cursor.getString(2), cursor.getInt(3) == 1));
            }
        }
        return result;
    }

    private static String flowLabel(String flow) {
        if (LedgerModels.INVESTMENT.equals(flow)) return "Investment";
        if (LedgerModels.CREDIT.equals(flow)) return "Credit";
        return "Expense";
    }

    private static int importColor(String flow) {
        if (LedgerModels.INVESTMENT.equals(flow)) return 0xFF6C63A8;
        if (LedgerModels.CREDIT.equals(flow)) return 0xFF3F8F74;
        return 0xFF607D8B;
    }

    private static final class ImportCategory {
        final long id;
        final String flow;
        boolean active;

        ImportCategory(long id, String flow, boolean active) {
            this.id = id;
            this.flow = flow;
            this.active = active;
        }
    }

    private static LedgerModels.Category readCategory(Cursor cursor, int offset) {
        return new LedgerModels.Category(cursor.getLong(offset), cursor.getString(offset + 1),
                cursor.getString(offset + 2), cursor.getString(offset + 3), cursor.getInt(offset + 4),
                cursor.getInt(offset + 5) == 1);
    }

    private static void seed(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            addSeed(db, "Food", LedgerModels.EXPENSE, "food", 0xFFE76F51);
            addSeed(db, "Transport", LedgerModels.EXPENSE, "car", 0xFF457B9D);
            addSeed(db, "Shopping", LedgerModels.EXPENSE, "bag", 0xFFF4A261);
            addSeed(db, "Health", LedgerModels.EXPENSE, "health", 0xFFE63946);
            addSeed(db, "Money Transfer", LedgerModels.EXPENSE, "send", 0xFF4361EE);
            addSeed(db, "Electricity", LedgerModels.EXPENSE, "bolt", 0xFFF4B740);
            addSeed(db, "Internet & Broadband", LedgerModels.EXPENSE, "wifi", 0xFF3A86FF);
            addSeed(db, "Mobile", LedgerModels.EXPENSE, "phone", 0xFF5E60CE);
            addSeed(db, "Water", LedgerModels.EXPENSE, "water", 0xFF00B4D8);
            addSeed(db, "Gas", LedgerModels.EXPENSE, "flame", 0xFFF77F00);
            addSeed(db, "Rent", LedgerModels.EXPENSE, "home", 0xFF9B5DE5);
            addSeed(db, "Home Maintenance", LedgerModels.EXPENSE, "tools", 0xFF6C757D);
            addSeed(db, "Insurance", LedgerModels.EXPENSE, "shield", 0xFF2A9D8F);
            addSeed(db, "Credit Card Bill", LedgerModels.EXPENSE, "card", 0xFF7B61A8);
            addSeed(db, "Loan EMI", LedgerModels.EXPENSE, "bank", 0xFF8D6E63);
            addSeed(db, "Other Bills", LedgerModels.EXPENSE, "receipt", 0xFF607D8B);
            addSeed(db, "Miscellaneous Expense", LedgerModels.EXPENSE, "dots", 0xFF607D8B);
            addSeed(db, "Grocery", LedgerModels.EXPENSE, "bag", 0xFF43AA8B);
            addSeed(db, "Learning", LedgerModels.EXPENSE, "layers", 0xFF6C63A8);
            addSeed(db, "Tax", LedgerModels.EXPENSE, "receipt", 0xFFF4A261);
            addSeed(db, "Stocks", LedgerModels.INVESTMENT, "chart", 0xFF2A9D8F);
            addSeed(db, "Bonds", LedgerModels.INVESTMENT, "bond", 0xFF52796F);
            addSeed(db, "Mutual Funds", LedgerModels.INVESTMENT, "layers", 0xFF3A86FF);
            addSeed(db, "F & O", LedgerModels.INVESTMENT, "swap", 0xFF8338EC);
            addSeed(db, "Miscellaneous Investment", LedgerModels.INVESTMENT, "dots", 0xFF6C63A8);
            addSeed(db, "Real Estate", LedgerModels.INVESTMENT, "home", 0xFF9B5DE5);
            addSeed(db, "Fixed Deposit", LedgerModels.INVESTMENT, "bank", 0xFF8D6E63);
            addSeed(db, "Lend", LedgerModels.INVESTMENT, "send", 0xFF4361EE);
            addSeed(db, "Salary", LedgerModels.CREDIT, "rupee", 0xFF20C997);
            addSeed(db, "Bond Interest", LedgerModels.CREDIT, "percent", 0xFF4CAF50);
            addSeed(db, "Dividends", LedgerModels.CREDIT, "dividend", 0xFF06A77D);
            addSeed(db, "Deposit", LedgerModels.CREDIT, "piggy", 0xFF009688);
            addSeed(db, "Miscellaneous Credit", LedgerModels.CREDIT, "dots", 0xFF3F8F74);
            addSeed(db, "Investment deficit", LedgerModels.CREDIT, "chart", 0xFF6C63A8);
            addSeed(db, "FD Interest", LedgerModels.CREDIT, "percent", 0xFF2A9D8F);
            addSeed(db, "Lend Interest", LedgerModels.CREDIT, "percent", 0xFF06A77D);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void addSeed(SQLiteDatabase db, String name, String flow, String icon, int color) {
        ContentValues values = new ContentValues(6);
        values.put("name", name);
        values.put("flow", flow);
        values.put("icon", icon);
        values.put("color", color);
        values.put("active", 1);
        values.put("standard", 1);
        db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void markCurrentStandardCategories(SQLiteDatabase db) {
        String[] expenseNames = {
                "Food", "Transport", "Shopping", "Health", "Money Transfer", "Electricity",
                "Internet & Broadband", "Mobile", "Water", "Gas", "Rent", "Home Maintenance",
                "Insurance", "Credit Card Bill", "Loan EMI", "Other Bills", "Miscellaneous Expense",
                "Grocery", "Learning", "Tax"
        };
        String[] investmentNames = {"Stocks", "Bonds", "Mutual Funds", "F & O",
                "Miscellaneous Investment", "Real Estate", "Fixed Deposit", "Lend"};
        String[] creditNames = {"Salary", "Bond Interest", "Dividends", "Deposit",
                "Miscellaneous Credit", "Investment deficit", "FD Interest", "Lend Interest"};
        ContentValues values = new ContentValues(1);
        values.put("standard", 1);
        markStandardCategories(db, values, expenseNames, LedgerModels.EXPENSE);
        markStandardCategories(db, values, investmentNames, LedgerModels.INVESTMENT);
        markStandardCategories(db, values, creditNames, LedgerModels.CREDIT);
    }

    private static void markStandardCategories(
            SQLiteDatabase db, ContentValues values, String[] names, String flow) {
        for (String name : names) {
            db.update("categories", values, "name=? AND flow=?", new String[]{name, flow});
        }
    }

    private static boolean isStandardCategory(String name, String flow) {
        if (name == null || !validFlow(flow)) return false;
        String[] names;
        if (LedgerModels.EXPENSE.equals(flow)) {
            names = new String[]{"Food", "Transport", "Shopping", "Health", "Money Transfer",
                    "Electricity", "Internet & Broadband", "Mobile", "Water", "Gas", "Rent",
                    "Home Maintenance", "Insurance", "Credit Card Bill", "Loan EMI", "Other Bills",
                    "Miscellaneous Expense", "Grocery", "Learning", "Tax"};
        } else if (LedgerModels.INVESTMENT.equals(flow)) {
            names = new String[]{"Stocks", "Bonds", "Mutual Funds", "F & O",
                    "Miscellaneous Investment", "Real Estate", "Fixed Deposit", "Lend"};
        } else {
            names = new String[]{"Salary", "Bond Interest", "Dividends", "Deposit",
                    "Miscellaneous Credit", "Investment deficit", "FD Interest", "Lend Interest"};
        }
        for (String standardName : names) if (standardName.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static void removeUnusedRetiredCategory(SQLiteDatabase db, String name) {
        db.delete("categories", "name=? AND NOT EXISTS " +
                        "(SELECT 1 FROM entries WHERE entries.category_id=categories.id)",
                new String[]{name});
    }

    private static void createSettingsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }

    private static void updateIcon(SQLiteDatabase db, String name, String icon) {
        ContentValues values = new ContentValues(1);
        values.put("icon", icon);
        db.update("categories", values, "name=?", new String[]{name});
    }

    private String setting(String key) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT value FROM app_settings WHERE key=?", new String[]{key})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private void putSetting(String key, String value) {
        putSettingInDatabase(getWritableDatabase(), key, value);
    }

    private static void putSettingInDatabase(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues(2);
        values.put("key", key);
        values.put("value", value);
        db.insertWithOnConflict(
                "app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
