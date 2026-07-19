package com.paisaflow.app;

import java.util.ArrayList;
import java.util.List;

final class LedgerModels {
    private LedgerModels() {}

    static final String EXPENSE = "EXPENSE";
    static final String INVESTMENT = "INVESTMENT";
    static final String CREDIT = "CREDIT";

    static final class Category {
        final long id;
        final String name;
        final String flow;
        final String icon;
        final int color;
        final boolean standard;

        Category(long id, String name, String flow, String icon, int color, boolean standard) {
            this.id = id;
            this.name = name;
            this.flow = flow;
            this.icon = icon;
            this.color = color;
            this.standard = standard;
        }

        @Override public String toString() { return name; }
    }

    static final class Entry {
        final long id;
        final long amountMinor;
        final int dateKey;
        final String note;
        final Category category;

        Entry(long id, long amountMinor, int dateKey, String note, Category category) {
            this.id = id;
            this.amountMinor = amountMinor;
            this.dateKey = dateKey;
            this.note = note;
            this.category = category;
        }
    }

    static final class Summary {
        final long expenses;
        final long investments;
        final long credits;
        final int count;

        Summary(long expenses, long investments, long credits, int count) {
            this.expenses = expenses;
            this.investments = investments;
            this.credits = credits;
            this.count = count;
        }
    }

    static final class Snapshot {
        final Summary summary;
        final List<Entry> entries;
        final long availableCash;

        Snapshot(Summary summary, List<Entry> entries, long availableCash) {
            this.summary = summary;
            this.entries = entries == null ? new ArrayList<>() : entries;
            this.availableCash = availableCash;
        }
    }

    static final class CategoryTotal {
        final Category category;
        final long amountMinor;

        CategoryTotal(Category category, long amountMinor) {
            this.category = category;
            this.amountMinor = amountMinor;
        }
    }

    static final class DateWindow {
        final int start;
        final int end;
        final String label;

        DateWindow(int start, int end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
    }

    static final class BackupInfo {
        final int categories;
        final int transactions;
        final boolean hasOpeningBalance;

        BackupInfo(int categories, int transactions, boolean hasOpeningBalance) {
            this.categories = categories;
            this.transactions = transactions;
            this.hasOpeningBalance = hasOpeningBalance;
        }
    }
}
