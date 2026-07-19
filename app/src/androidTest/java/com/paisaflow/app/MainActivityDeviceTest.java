package com.paisaflow.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class MainActivityDeviceTest {
    @Test public void coreDashboardControlsAreMeasuredAndVisible() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertVisibleText(root, "AVAILABLE CASH");
                assertVisibleText(root, "Activity");
                assertVisibleText(root, "Summary");
                assertVisibleText(root, "Breakdown");
                assertVisibleText(root, "Credits");
                assertVisibleText(root, "Expenses");
                assertVisibleText(root, "Investments");
                assertVisibleContentDescription(root, "Date filter calendar");
                assertTrue("A compact period selector should be visible", hasVisibleTextContaining(root, "This ")
                        || hasVisibleTextContaining(root, "Financial year")
                        || hasVisibleTextContaining(root, "Start till now")
                        || hasVisibleTextContaining(root, "Custom range"));
            });
        }
    }

    @Test public void drawerNavigatesToCategoriesAndSettings() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> findText(activity.getWindow().getDecorView(), "☰").performClick());
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertVisibleText(root, "Dashboard");
                assertTrue(hasVisibleTextContaining(root, "Categories"));
                assertTrue(hasVisibleTextContaining(root, "Settings"));
                findTextContaining(root, "Categories").performClick();
            });
            scenario.onActivity(activity -> assertVisibleText(activity.getWindow().getDecorView(), "Manage categories"));
            scenario.onActivity(activity -> findText(activity.getWindow().getDecorView(), "☰").performClick());
            scenario.onActivity(activity -> findTextContaining(activity.getWindow().getDecorView(), "Settings").performClick());
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertVisibleText(root, "Create full backup");
                assertVisibleText(root, "Import full backup");
            });
        }
    }

    @Test public void newCategoryShowsVisualIconAndColorSelectors() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<AlertDialog> dialogReference = new AtomicReference<>();
            scenario.onActivity(activity -> dialogReference.set(activity.showCategoryDialog()));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                AlertDialog dialog = dialogReference.get();
                View root = dialog.getWindow().getDecorView();
                assertVisibleText(root, "ICON");
                assertVisibleText(root, "COLOR");
                assertVisibleText(root, "PREVIEW");
                assertVisibleContentDescription(root, "Deposit icon");
                assertVisibleContentDescription(root, "Emerald color swatch");
                assertVisibleContentDescription(root, "Selected category icon preview");
                dialog.dismiss();
            });
        }
    }

    @Test public void addTransactionFiltersCategoriesBySelectedType() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<AlertDialog> dialogReference = new AtomicReference<>();
            for (int attempt = 0; attempt < 20 && dialogReference.get() == null; attempt++) {
                scenario.onActivity(activity -> {
                    if (dialogReference.get() == null) dialogReference.set(activity.showEntryDialog());
                });
                if (dialogReference.get() == null) {
                    SystemClock.sleep(50);
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                }
            }
            assertNotNull("Transaction dialog did not open", dialogReference.get());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                View root = dialogReference.get().getWindow().getDecorView();
                assertVisibleText(root, "Expense");
                assertVisibleText(root, "Credit Card Bill");
                findText(root, "Investment").performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                View root = dialogReference.get().getWindow().getDecorView();
                assertVisibleText(root, "Bonds");
                findText(root, "Credit").performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                View root = dialogReference.get().getWindow().getDecorView();
                assertVisibleText(root, "Bond Interest");
                dialogReference.get().dismiss();
            });
        }
    }

    private static TextView findText(View root, String expected) {
        if (root instanceof TextView && expected.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextOrNull(group.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing text: " + expected);
    }

    private static TextView findTextContaining(View root, String expected) {
        if (root instanceof TextView && ((TextView) root).getText().toString().contains(expected)) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextContainingOrNull(group.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Missing text containing: " + expected);
    }

    private static TextView findTextContainingOrNull(View root, String expected) {
        if (root instanceof TextView && ((TextView) root).getText().toString().contains(expected)) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextContainingOrNull(group.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findTextOrNull(View root, String expected) {
        if (root instanceof TextView && expected.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextOrNull(group.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertVisibleText(View root, String expected) {
        List<TextView> matches = new ArrayList<>();
        collectTextViews(root, expected, matches);
        assertFalse("Missing text: " + expected, matches.isEmpty());
        boolean visible = false;
        StringBuilder diagnostic = new StringBuilder();
        for (TextView view : matches) {
            Rect rect = new Rect();
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            boolean hasGlobalRect = view.getGlobalVisibleRect(rect);
            diagnostic.append(" [shown=").append(view.isShown())
                    .append(", visibility=").append(view.getVisibility())
                    .append(", size=").append(view.getWidth()).append('x').append(view.getHeight())
                    .append(", location=").append(location[0]).append(',').append(location[1])
                    .append(", global=").append(hasGlobalRect).append(':').append(rect)
                    .append(", parents=").append(parentChain(view)).append(']');
            if (view.getWidth() > 0 && view.getHeight() > 0
                    && hasGlobalRect && rect.width() > 0 && rect.height() > 0) {
                visible = true;
                break;
            }
        }
        assertTrue("Text exists but is not visible: " + expected + diagnostic, visible);
    }

    private static void assertVisibleContentDescription(View root, String expected) {
        View match = findContentDescription(root, expected);
        Rect rect = new Rect();
        assertTrue("View is not visible: " + expected,
                match.getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0);
    }

    private static View findContentDescription(View root, String expected) {
        CharSequence description = root.getContentDescription();
        if (description != null && expected.contentEquals(description)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                try {
                    return findContentDescription(group.getChildAt(i), expected);
                } catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Missing content description: " + expected);
    }

    private static String parentChain(View view) {
        StringBuilder result = new StringBuilder();
        android.view.ViewParent parent = view.getParent();
        int depth = 0;
        while (parent instanceof View && depth++ < 8) {
            View parentView = (View) parent;
            int[] location = new int[2];
            parentView.getLocationOnScreen(location);
            if (result.length() > 0) result.append(" <- ");
            result.append(parentView.getClass().getSimpleName())
                    .append('(').append(parentView.getWidth()).append('x').append(parentView.getHeight())
                    .append('@').append(location[0]).append(',').append(location[1]).append(')');
            parent = parentView.getParent();
        }
        return result.toString();
    }

    private static boolean hasVisibleTextContaining(View root, String expected) {
        if (root instanceof TextView) {
            TextView text = (TextView) root;
            Rect rect = new Rect();
            return text.getText().toString().contains(expected)
                    && text.getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasVisibleTextContaining(group.getChildAt(i), expected)) return true;
            }
        }
        return false;
    }

    private static void collectTextViews(View root, String expected, List<TextView> result) {
        if (root instanceof TextView && expected.contentEquals(((TextView) root).getText())) {
            result.add((TextView) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTextViews(group.getChildAt(i), expected, result);
            }
        }
    }
}
