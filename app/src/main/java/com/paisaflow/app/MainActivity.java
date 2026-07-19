package com.paisaflow.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final int EXPORT_REQUEST = 42;
    private static final int BACKUP_EXPORT_REQUEST = 43;
    private static final int BACKUP_IMPORT_REQUEST = 44;
    private static final int INK = 0xFF0B1220;
    private static final int INK_SOFT = 0xFF172033;
    private static final int PAPER = 0xFFF6F7F2;
    private static final int EMERALD = 0xFF20C997;
    private static final int MUTED = 0xFF667085;
    private static final int DANGER = 0xFFE85D68;
    private static final int AMBER = 0xFFF4B740;
    private static final int WHITE = Color.WHITE;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LedgerDatabase database;
    private SharedPreferences preferences;
    private LedgerModels.DateWindow dateWindow;
    private DateRanges.Preset activePreset = DateRanges.Preset.MONTH;
    private List<LedgerModels.Category> categories = new ArrayList<>();

    private TextView dateLabel;
    private TextView availableValue;
    private TextView availableHint;
    private Button periodButton;
    private TextView expenseValue;
    private TextView investmentValue;
    private TextView creditValue;
    private TextView expensePercent;
    private TextView investmentPercent;
    private TextView creditPercent;
    private View expenseBar;
    private View investmentBar;
    private View creditBar;
    private TextView transactionHeading;
    private TextView emptyView;
    private EntryAdapter entryAdapter;
    private CategoryTotalAdapter categoryTotalAdapter;
    private CategoryManagementAdapter categoryManagementAdapter;
    private ListView listView;
    private FrameLayout screenHost;
    private View dashboardScreen;
    private View drawerLayer;
    private TextView headerTitle;
    private String activeScreen = "dashboard";
    private LinearLayout summaryPanel;
    private LinearLayout donutPanel;
    private DonutChartView donutChart;
    private Button summaryModeButton;
    private Button categoryModeButton;
    private final List<Button> flowButtons = new ArrayList<>();
    private String breakdownFlow = LedgerModels.EXPENSE;
    private boolean categoryMode;
    private boolean hasOpeningBalance;
    private long openingBalanceMinor;
    private boolean openingPromptChecked;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(INK);
        window.setNavigationBarColor(INK);
        database = new LedgerDatabase(getApplicationContext());
        preferences = getSharedPreferences("display", MODE_PRIVATE);
        restoreDateSelection();
        setContentView(buildScreen());
        reload();
    }

    @Override protected void onDestroy() {
        databaseExecutor.shutdown();
        database.close();
        super.onDestroy();
    }

    private View buildScreen() {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(INK);
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            // Android 15 draws apps edge-to-edge. Keep every screen and the
            // navigation drawer outside status, cutout, and navigation areas.
            applySystemBarInsets(view, insets);
            return insets;
        });
        LinearLayout root = column(this);
        root.setBackgroundColor(PAPER);

        LinearLayout header = row(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(14), dp(8));
        header.setBackgroundColor(INK);
        Button menuButton = new Button(this);
        menuButton.setText("☰");
        menuButton.setTextSize(22);
        menuButton.setTextColor(WHITE);
        menuButton.setAllCaps(false);
        menuButton.setPadding(0, 0, 0, 0);
        menuButton.setBackgroundColor(Color.TRANSPARENT);
        menuButton.setContentDescription("Open navigation menu");
        menuButton.setOnClickListener(v -> openDrawer());
        header.addView(menuButton, size(dp(44), dp(44)));
        TextView mark = text("₹", 20, INK, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(roundRect(EMERALD, dp(12)));
        header.addView(mark, size(dp(40), dp(40)));
        headerTitle = text("Dashboard", 20, WHITE, Typeface.BOLD);
        headerTitle.setPadding(dp(12), 0, 0, 0);
        header.addView(headerTitle, weighted());
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));

        screenHost = new FrameLayout(this);
        LinearLayout dashboard = column(this);
        dashboard.setBackgroundColor(PAPER);
        dashboardScreen = dashboard;

        LinearLayout content = column(this);
        content.setPadding(dp(16), dp(10), dp(16), 0);
        dashboard.addView(content, matchWrap());

        LinearLayout cashCard = column(this);
        cashCard.setPadding(dp(18), dp(13), dp(18), dp(13));
        cashCard.setBackground(roundRect(INK_SOFT, dp(17)));
        cashCard.setOnClickListener(v -> showOpeningBalanceDialog(false));
        cashCard.addView(text("AVAILABLE CASH", 10, 0xFF9BA6B8, Typeface.BOLD));
        availableValue = text("₹0", 28, EMERALD, Typeface.BOLD);
        availableValue.setPadding(0, dp(2), 0, 0);
        cashCard.addView(availableValue);
        availableHint = text("All records through today", 11, 0xFFD5DBE5, Typeface.NORMAL);
        cashCard.addView(availableHint);
        content.addView(cashCard, new LinearLayout.LayoutParams(-1, dp(91)));

        LinearLayout activityRow = row(this);
        activityRow.setGravity(Gravity.CENTER_VERTICAL);
        activityRow.setPadding(0, dp(10), 0, dp(7));
        activityRow.addView(text("Activity", 17, INK, Typeface.BOLD), weighted());

        LinearLayout modeRow = row(this);
        modeRow.setPadding(dp(2), dp(2), dp(2), dp(2));
        modeRow.setBackground(roundRect(0xFFE4E8E1, dp(13)));
        summaryModeButton = modeButton("Summary", false);
        categoryModeButton = modeButton("Breakdown", true);
        modeRow.addView(summaryModeButton, new LinearLayout.LayoutParams(0, dp(32), 1f));
        modeRow.addView(categoryModeButton, new LinearLayout.LayoutParams(0, dp(32), 1f));
        activityRow.addView(modeRow, new LinearLayout.LayoutParams(dp(176), dp(36)));
        content.addView(activityRow, matchWrap());

        LinearLayout periodControl = row(this);
        periodControl.setGravity(Gravity.CENTER_VERTICAL);
        periodControl.setPadding(dp(13), dp(5), dp(10), dp(5));
        periodControl.setBackground(roundRect(WHITE, dp(13), 0xFFDDE1D9, dp(1)));
        periodControl.setOnClickListener(v -> showPeriodSelector());
        TextView calendarMark = text("▣", 18, EMERALD, Typeface.BOLD);
        calendarMark.setGravity(Gravity.CENTER);
        periodControl.addView(calendarMark, size(dp(32), dp(40)));
        LinearLayout periodText = column(this);
        periodText.setPadding(dp(8), 0, 0, 0);
        periodButton = new Button(this);
        periodButton.setText(presetLabel(activePreset) + "  ▾");
        periodButton.setTextSize(14);
        periodButton.setTextColor(INK);
        periodButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        periodButton.setAllCaps(false);
        periodButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        periodButton.setPadding(0, 0, 0, 0);
        periodButton.setMinHeight(0);
        periodButton.setMinimumHeight(0);
        periodButton.setIncludeFontPadding(false);
        periodButton.setBackgroundColor(Color.TRANSPARENT);
        periodButton.setOnClickListener(v -> showPeriodSelector());
        dateLabel = text(dateWindow.label, 11, MUTED, Typeface.NORMAL);
        periodText.addView(periodButton, new LinearLayout.LayoutParams(-1, dp(27)));
        periodText.addView(dateLabel, new LinearLayout.LayoutParams(-1, dp(19)));
        periodControl.addView(periodText, weighted());
        TextView chevron = text("›", 24, MUTED, Typeface.NORMAL);
        chevron.setGravity(Gravity.CENTER);
        periodControl.addView(chevron, size(dp(26), dp(46)));
        content.addView(periodControl, new LinearLayout.LayoutParams(-1, dp(58)));

        summaryPanel = column(this);
        summaryPanel.setPadding(0, dp(8), 0, 0);
        SummaryRow credits = addSummaryRow(summaryPanel, "Credits", EMERALD);
        creditValue = credits.value;
        creditPercent = credits.percentage;
        creditBar = credits.fill;
        SummaryRow expenses = addSummaryRow(summaryPanel, "Expenses", DANGER);
        expenseValue = expenses.value;
        expensePercent = expenses.percentage;
        expenseBar = expenses.fill;
        SummaryRow investments = addSummaryRow(summaryPanel, "Investments", AMBER);
        investmentValue = investments.value;
        investmentPercent = investments.percentage;
        investmentBar = investments.fill;
        content.addView(summaryPanel, matchWrap());

        donutPanel = column(this);
        donutPanel.setVisibility(View.GONE);
        LinearLayout flowRow = row(this);
        flowRow.setPadding(0, dp(7), 0, 0);
        addFlowButton(flowRow, "Expenses", LedgerModels.EXPENSE);
        addFlowButton(flowRow, "Investments", LedgerModels.INVESTMENT);
        addFlowButton(flowRow, "Credits", LedgerModels.CREDIT);
        donutPanel.addView(flowRow, new LinearLayout.LayoutParams(-1, dp(43)));
        donutChart = new DonutChartView(this);
        donutPanel.addView(donutChart, new LinearLayout.LayoutParams(-1, dp(190)));
        content.addView(donutPanel, matchWrap());

        LinearLayout listTitle = row(this);
        listTitle.setGravity(Gravity.CENTER_VERTICAL);
        listTitle.setPadding(dp(18), dp(18), dp(18), dp(10));
        transactionHeading = text("Transactions", 17, INK, Typeface.BOLD);
        listTitle.addView(transactionHeading, weighted());
        TextView hint = text("Hold to delete", 11, MUTED, Typeface.NORMAL);
        listTitle.addView(hint);
        dashboard.addView(listTitle, matchWrap());

        FrameLayout listFrame = new FrameLayout(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(dp(12), 0, dp(12), dp(8));
        listView.setClipToPadding(false);
        listView.setBackgroundColor(PAPER);
        entryAdapter = new EntryAdapter(this);
        categoryTotalAdapter = new CategoryTotalAdapter(this);
        listView.setAdapter(entryAdapter);
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (categoryMode) return false;
            confirmDelete(entryAdapter.getItem(position));
            return true;
        });
        listFrame.addView(listView, frameMatch());

        emptyView = text("No transactions in this period\nTap Add transaction to begin", 15, MUTED, Typeface.NORMAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        listFrame.addView(emptyView, frameMatch());
        dashboard.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button addButton = new Button(this);
        addButton.setText("＋  Add transaction");
        addButton.setTextSize(16);
        addButton.setTextColor(INK);
        addButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addButton.setAllCaps(false);
        addButton.setBackground(roundRect(EMERALD, dp(15)));
        addButton.setOnClickListener(v -> showEntryDialog());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(56));
        addParams.setMargins(dp(16), dp(8), dp(16), dp(14));
        dashboard.addView(addButton, addParams);
        screenHost.addView(dashboard, frameMatch());
        root.addView(screenHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.addView(root, frameMatch());
        addDrawer(shell);
        updateModeAppearance();
        updateFlowAppearance();
        return shell;
    }

    @SuppressWarnings("deprecation")
    private static void applySystemBarInsets(View view, WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets safeArea = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
        } else {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
        }
    }

    private void addDrawer(FrameLayout shell) {
        FrameLayout layer = new FrameLayout(this);
        layer.setVisibility(View.GONE);
        layer.setBackgroundColor(0x66000000);
        layer.setOnClickListener(v -> closeDrawer());

        LinearLayout panel = column(this);
        panel.setPadding(dp(16), dp(24), dp(16), dp(16));
        panel.setBackgroundColor(WHITE);
        panel.setOnClickListener(v -> { });

        LinearLayout brand = row(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("₹", 20, INK, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(roundRect(EMERALD, dp(12)));
        brand.addView(mark, size(dp(42), dp(42)));
        TextView name = text("PaisaFlow", 21, INK, Typeface.BOLD);
        name.setPadding(dp(12), 0, 0, 0);
        brand.addView(name, weighted());
        panel.addView(brand, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView section = text("NAVIGATION", 10, MUTED, Typeface.BOLD);
        section.setPadding(dp(8), dp(18), 0, dp(8));
        panel.addView(section, matchWrap());
        panel.addView(drawerItem("▦", "Dashboard", "dashboard"), new LinearLayout.LayoutParams(-1, dp(54)));
        panel.addView(drawerItem("◫", "Categories", "categories"), new LinearLayout.LayoutParams(-1, dp(54)));
        panel.addView(drawerItem("⚙", "Settings", "settings"), new LinearLayout.LayoutParams(-1, dp(54)));

        TextView privacy = text("Offline only  ·  Your data stays on this device", 11, MUTED, Typeface.NORMAL);
        privacy.setGravity(Gravity.BOTTOM);
        panel.addView(privacy, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(286), -1, Gravity.START);
        layer.addView(panel, panelParams);
        shell.addView(layer, frameMatch());
        drawerLayer = layer;
    }

    private Button drawerItem(String icon, String label, String screen) {
        Button button = new Button(this);
        button.setText(icon + "   " + label);
        button.setTextSize(16);
        button.setTextColor(INK);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(v -> showScreen(screen));
        return button;
    }

    private void openDrawer() {
        drawerLayer.setVisibility(View.VISIBLE);
    }

    private void closeDrawer() {
        drawerLayer.setVisibility(View.GONE);
    }

    private void showScreen(String screen) {
        closeDrawer();
        activeScreen = screen;
        screenHost.removeAllViews();
        switch (screen) {
            case "categories":
                headerTitle.setText("Categories");
                screenHost.addView(buildCategoriesScreen(), frameMatch());
                break;
            case "settings":
                headerTitle.setText("Settings");
                screenHost.addView(buildSettingsScreen(), frameMatch());
                break;
            default:
                activeScreen = "dashboard";
                headerTitle.setText("Dashboard");
                screenHost.addView(dashboardScreen, frameMatch());
                reload();
                break;
        }
    }

    private View buildCategoriesScreen() {
        LinearLayout screen = column(this);
        screen.setPadding(dp(16), dp(14), dp(16), dp(12));
        screen.setBackgroundColor(PAPER);
        LinearLayout heading = row(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column(this);
        copy.addView(text("Manage categories", 20, INK, Typeface.BOLD));
        copy.addView(text("Choose a type, icon, and color for each category", 12, MUTED, Typeface.NORMAL));
        heading.addView(copy, weighted());
        Button add = compactLightButton("＋ Add");
        add.setOnClickListener(v -> showCategoryDialog());
        heading.addView(add, wrap());
        screen.addView(heading, new LinearLayout.LayoutParams(-1, dp(66)));

        ListView categoryList = new ListView(this);
        categoryList.setDivider(null);
        categoryList.setDividerHeight(0);
        categoryList.setPadding(0, dp(8), 0, 0);
        categoryList.setClipToPadding(false);
        categoryManagementAdapter = new CategoryManagementAdapter(this);
        categoryManagementAdapter.replace(categories);
        categoryList.setAdapter(categoryManagementAdapter);
        screen.addView(categoryList, new LinearLayout.LayoutParams(-1, 0, 1f));
        return screen;
    }

    private View buildSettingsScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout screen = column(this);
        screen.setPadding(dp(16), dp(14), dp(16), dp(24));
        screen.setBackgroundColor(PAPER);
        screen.addView(text("Your data", 20, INK, Typeface.BOLD), matchWrap());
        TextView note = text("Manage your starting balance and portable offline backups.", 12, MUTED, Typeface.NORMAL);
        note.setPadding(0, dp(4), 0, dp(12));
        screen.addView(note, matchWrap());

        screen.addView(settingsAction("₹", "Opening balance", "Set or edit the balance from when record-keeping began",
                v -> showOpeningBalanceDialog(false)), settingsParams());
        screen.addView(settingsAction("⇩", "Export transactions", "Save the dashboard's selected period as CSV",
                v -> requestExport()), settingsParams());
        screen.addView(settingsAction("▣", "Create full backup", "Save transactions, categories, and opening balance as JSON",
                v -> requestBackupExport()), settingsParams());
        screen.addView(settingsAction("⇧", "Import full backup", "Review and replace PaisaFlow data from a JSON backup",
                v -> requestBackupImport()), settingsParams());

        TextView privacy = text("PRIVACY", 10, MUTED, Typeface.BOLD);
        privacy.setPadding(dp(4), dp(20), 0, dp(8));
        screen.addView(privacy, matchWrap());
        TextView privacyBody = text("PaisaFlow has no internet permission. Imports and exports use Android's document picker, so the app only accesses the file you choose.",
                13, INK_SOFT, Typeface.NORMAL);
        privacyBody.setPadding(dp(14), dp(12), dp(14), dp(12));
        privacyBody.setBackground(roundRect(WHITE, dp(13), 0xFFE1E5DD, dp(1)));
        screen.addView(privacyBody, matchWrap());
        scroll.addView(screen, new FrameLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private View settingsAction(String icon, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout card = row(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(roundRect(WHITE, dp(14), 0xFFE1E5DD, dp(1)));
        card.setOnClickListener(listener);
        TextView iconView = text(icon, 19, WHITE, Typeface.BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(roundRect(INK_SOFT, dp(12)));
        card.addView(iconView, size(dp(42), dp(42)));
        LinearLayout copy = column(this);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(title, 15, INK, Typeface.BOLD));
        copy.addView(text(subtitle, 11, MUTED, Typeface.NORMAL));
        card.addView(copy, weighted());
        TextView chevron = text("›", 23, MUTED, Typeface.NORMAL);
        card.addView(chevron, wrap());
        return card;
    }

    private LinearLayout.LayoutParams settingsParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(70));
        params.setMargins(0, 0, 0, dp(9));
        return params;
    }

    private Button compactLightButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(roundRect(INK_SOFT, dp(16)));
        return button;
    }

    @Override public void onBackPressed() {
        if (drawerLayer != null && drawerLayer.getVisibility() == View.VISIBLE) {
            closeDrawer();
        } else if (!"dashboard".equals(activeScreen)) {
            showScreen("dashboard");
        } else {
            super.onBackPressed();
        }
    }

    private Button modeButton(String label, boolean categories) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setOnClickListener(v -> {
            setCategoryMode(categories);
            showViewGuideOnce();
        });
        return button;
    }

    private void setCategoryMode(boolean categoriesMode) {
        categoryMode = categoriesMode;
        summaryPanel.setVisibility(categoryMode ? View.GONE : View.VISIBLE);
        donutPanel.setVisibility(categoryMode ? View.VISIBLE : View.GONE);
        listView.setAdapter(categoryMode ? categoryTotalAdapter : entryAdapter);
        updateModeAppearance();
        reload();
    }

    private void updateModeAppearance() {
        if (summaryModeButton == null) return;
        styleModeButton(summaryModeButton, !categoryMode);
        styleModeButton(categoryModeButton, categoryMode);
    }

    private void showViewGuideOnce() {
        if (preferences.getBoolean("view_guide_seen", false)) return;
        preferences.edit().putBoolean("view_guide_seen", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Two ways to understand your activity")
                .setMessage("Summary shows credits, expenses, investments, and how much of the selected period's credits were used.\n\n" +
                        "Breakdown shows the same period as a donut chart grouped by category.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void styleModeButton(Button button, boolean selected) {
        button.setTextColor(selected ? WHITE : MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(roundRect(selected ? INK_SOFT : Color.TRANSPARENT, dp(11)));
    }

    private void addFlowButton(LinearLayout parent, String label, String flow) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setSingleLine(true);
        button.setAllCaps(false);
        button.setTag(flow);
        button.setOnClickListener(v -> {
            breakdownFlow = (String) v.getTag();
            updateFlowAppearance();
            reload();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1f);
        params.setMargins(parent.getChildCount() == 0 ? 0 : dp(3), 0,
                parent.getChildCount() == 0 ? dp(3) : 0, 0);
        parent.addView(button, params);
        flowButtons.add(button);
    }

    private void updateFlowAppearance() {
        for (Button button : flowButtons) {
            boolean selected = breakdownFlow.equals(button.getTag());
            button.setTextColor(selected ? WHITE : INK_SOFT);
            button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackground(roundRect(selected ? flowAccent(breakdownFlow) : 0xFFE8EBE5, dp(16)));
        }
    }

    private static int flowAccent(String flow) {
        if (LedgerModels.CREDIT.equals(flow)) return 0xFF0F9D78;
        if (LedgerModels.INVESTMENT.equals(flow)) return 0xFFD18B00;
        return 0xFFD74D59;
    }

    private SummaryRow addSummaryRow(LinearLayout parent, String label, int accent) {
        LinearLayout card = column(this);
        card.setPadding(dp(13), dp(9), dp(13), dp(9));
        card.setBackground(roundRect(WHITE, dp(14), 0xFFE8EAE4, dp(1)));
        LinearLayout values = row(this);
        values.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(roundRect(accent, dp(5)));
        values.addView(dot, size(dp(9), dp(9)));
        TextView labelView = text(label, 14, INK, Typeface.BOLD);
        labelView.setPadding(dp(9), 0, dp(8), 0);
        values.addView(labelView, weighted());
        TextView value = text("₹0", 18, accent, Typeface.BOLD);
        value.setSingleLine(true);
        value.setGravity(Gravity.END);
        values.addView(value, wrap());
        TextView percentage = text("—", 13, MUTED, Typeface.BOLD);
        percentage.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        percentage.setPadding(dp(8), 0, 0, 0);
        values.addView(percentage, new LinearLayout.LayoutParams(dp(66), dp(32)));
        card.addView(values, new LinearLayout.LayoutParams(-1, dp(34)));

        FrameLayout track = new FrameLayout(this);
        track.setBackground(roundRect(0xFFE6E9E3, dp(2)));
        View fill = new View(this);
        fill.setPivotX(0);
        fill.setScaleX(0f);
        fill.setBackground(roundRect(accent, dp(2)));
        track.addView(fill, frameMatch());
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, dp(4));
        trackParams.setMargins(0, dp(4), 0, 0);
        card.addView(track, trackParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.setMargins(0, 0, 0, dp(7));
        parent.addView(card, params);
        return new SummaryRow(value, percentage, fill);
    }

    private void showPeriodSelector() {
        String[] labels = {"This week", "This month", "This year", "Financial year", "Start till now", "Custom range"};
        DateRanges.Preset[] presets = {
                DateRanges.Preset.WEEK, DateRanges.Preset.MONTH, DateRanges.Preset.YEAR,
                DateRanges.Preset.FINANCIAL_YEAR, DateRanges.Preset.ALL, DateRanges.Preset.CUSTOM};
        new AlertDialog.Builder(this)
                .setTitle("Select reporting period")
                .setItems(labels, (dialog, which) -> {
                    DateRanges.Preset selected = presets[which];
                    if (selected == DateRanges.Preset.CUSTOM) showCustomDateDialog();
                    else selectPreset(selected);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String presetLabel(DateRanges.Preset preset) {
        switch (preset) {
            case WEEK: return "This week";
            case MONTH: return "This month";
            case YEAR: return "This year";
            case FINANCIAL_YEAR: return "Financial year";
            case ALL: return "Start till now";
            case CUSTOM: return "Custom range";
            default: return "Select period";
        }
    }

    private void selectPreset(DateRanges.Preset preset) {
        activePreset = preset;
        dateWindow = DateRanges.forPreset(preset);
        preferences.edit().putString("preset", preset.name()).apply();
        dateLabel.setText(dateWindow.label);
        periodButton.setText(presetLabel(preset) + "  ▾");
        reload();
    }

    private void restoreDateSelection() {
        try {
            activePreset = DateRanges.Preset.valueOf(preferences.getString("preset", DateRanges.Preset.MONTH.name()));
        } catch (IllegalArgumentException ignored) {
            activePreset = DateRanges.Preset.MONTH;
        }
        if (activePreset == DateRanges.Preset.CUSTOM) {
            int start = preferences.getInt("custom_start", DateRanges.todayKey());
            int end = preferences.getInt("custom_end", DateRanges.todayKey());
            dateWindow = DateRanges.custom(start, end);
        } else {
            dateWindow = DateRanges.forPreset(activePreset);
        }
    }

    private void reload() {
        final int start = dateWindow.start;
        final int end = dateWindow.end;
        final String requestedFlow = breakdownFlow;
        databaseExecutor.execute(() -> {
            List<LedgerModels.Category> loadedCategories = database.categories();
            LedgerModels.Snapshot snapshot = database.snapshot(start, end, 250);
            List<LedgerModels.CategoryTotal> totals = database.categoryTotals(start, end, requestedFlow);
            boolean loadedHasOpening = database.hasOpeningBalance();
            long loadedOpening = database.openingBalance();
            boolean promptHandled = database.openingPromptHandled();
            mainHandler.post(() -> {
                categories = loadedCategories;
                if (categoryManagementAdapter != null) categoryManagementAdapter.replace(loadedCategories);
                hasOpeningBalance = loadedHasOpening;
                openingBalanceMinor = loadedOpening;
                render(snapshot, totals, requestedFlow);
                if (!openingPromptChecked) {
                    openingPromptChecked = true;
                    if (!promptHandled) showOpeningBalanceDialog(true);
                }
            });
        });
    }

    private void render(LedgerModels.Snapshot snapshot, List<LedgerModels.CategoryTotal> totals, String totalsFlow) {
        long available = snapshot.availableCash;
        availableValue.setText((available < 0 ? "−" : "") + Money.format(Math.abs(available)));
        availableValue.setTextColor(available < 0 ? DANGER : EMERALD);
        availableHint.setText(hasOpeningBalance
                ? "All records through today  ·  Tap to edit opening balance"
                : "Opening balance not set  ·  Tap to add");
        expenseValue.setText(Money.format(snapshot.summary.expenses));
        investmentValue.setText(Money.format(snapshot.summary.investments));
        creditValue.setText(Money.format(snapshot.summary.credits));
        updateAllocation(creditPercent, creditBar, snapshot.summary.credits, snapshot.summary.credits);
        updateAllocation(expensePercent, expenseBar, snapshot.summary.expenses, snapshot.summary.credits);
        updateAllocation(investmentPercent, investmentBar, snapshot.summary.investments, snapshot.summary.credits);
        entryAdapter.replace(snapshot.entries);
        if (totalsFlow.equals(breakdownFlow)) {
            categoryTotalAdapter.replace(totals);
            donutChart.setData(totals, flowLabel(breakdownFlow) + "S");
        }
        if (categoryMode) {
            transactionHeading.setText("By category  ·  " + categoryTotalAdapter.getCount());
            emptyView.setText("No " + flowLabel(breakdownFlow).toLowerCase(java.util.Locale.ROOT)
                    + " transactions in this period");
            emptyView.setVisibility(categoryTotalAdapter.getCount() == 0 ? View.VISIBLE : View.GONE);
        } else {
            transactionHeading.setText("Transactions  ·  " + snapshot.summary.count);
            emptyView.setText("No transactions in this period\nTap Add transaction to begin");
            emptyView.setVisibility(snapshot.entries.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void updateAllocation(TextView percentage, View bar, long amount, long credits) {
        if (credits <= 0) {
            percentage.setText("—");
            bar.setScaleX(0f);
            return;
        }
        double ratio = amount / (double) credits;
        double value = ratio * 100.0;
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            percentage.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", value));
        } else {
            percentage.setText(String.format(java.util.Locale.getDefault(), "%.1f%%", value));
        }
        bar.setScaleX((float) Math.min(1.0, Math.max(0.0, ratio)));
    }

    private void showOpeningBalanceDialog(boolean firstPrompt) {
        if (firstPrompt) databaseExecutor.execute(database::markOpeningPromptHandled);
        LinearLayout form = dialogForm();
        TextView explanation = text(
                "Enter the total cash you held when record-keeping began. " +
                        "It establishes your Available Cash but is not counted as income.",
                13, MUTED, Typeface.NORMAL);
        explanation.setLineSpacing(0, 1.15f);
        form.addView(explanation, matchWrap());
        EditText amount = field("Opening balance (₹)");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (hasOpeningBalance) amount.setText(Money.inputValue(openingBalanceMinor));
        form.addView(amount, fieldParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(hasOpeningBalance ? "Edit opening balance" : "Set opening balance")
                .setView(form)
                .setNegativeButton(firstPrompt ? "Skip for now" : "Cancel", null)
                .setPositiveButton(hasOpeningBalance ? "Update" : "Save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(0xFF087D5F);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    long minor = Money.parseMinor(amount.getText().toString());
                    databaseExecutor.execute(() -> {
                        database.setOpeningBalance(minor);
                        mainHandler.post(() -> {
                            dialog.dismiss();
                            Toast.makeText(this, "Opening balance saved", Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    });
                } catch (Exception error) {
                    amount.setError(error.getMessage() == null ? "Check the amount" : error.getMessage());
                }
            });
        });
        dialog.show();
    }

    AlertDialog showEntryDialog() {
        if (categories.isEmpty()) {
            Toast.makeText(this, "Categories are loading. Try again in a moment.", Toast.LENGTH_SHORT).show();
            return null;
        }
        LinearLayout form = dialogForm();
        EditText amount = field("Amount (₹)");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(amount, fieldParams());

        form.addView(formLabel("TYPE"), matchWrap());
        String[] selectedFlow = {LedgerModels.EXPENSE};
        String[] flowValues = {LedgerModels.EXPENSE, LedgerModels.INVESTMENT, LedgerModels.CREDIT};
        String[] flowLabels = {"Expense", "Investment", "Credit"};
        LinearLayout typeSelector = row(this);
        typeSelector.setPadding(dp(3), dp(3), dp(3), dp(3));
        typeSelector.setBackground(roundRect(0xFFE4E8E1, dp(12)));

        CategoryOptionAdapter categoryAdapter = new CategoryOptionAdapter();
        Spinner categorySpinner = spinner();
        categorySpinner.setAdapter(categoryAdapter);
        List<Button> typeButtons = new ArrayList<>();
        for (int i = 0; i < flowValues.length; i++) {
            final int index = i;
            Button button = new Button(this);
            button.setText(flowLabels[i]);
            button.setTextSize(13);
            button.setAllCaps(false);
            button.setPadding(dp(4), 0, dp(4), 0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setOnClickListener(v -> {
                selectedFlow[0] = flowValues[index];
                categoryAdapter.replace(categoriesForFlow(selectedFlow[0]));
                for (int position = 0; position < typeButtons.size(); position++) {
                    styleTransactionTypeButton(typeButtons.get(position), position == index);
                }
            });
            typeButtons.add(button);
            typeSelector.addView(button, new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        categoryAdapter.replace(categoriesForFlow(selectedFlow[0]));
        for (int i = 0; i < typeButtons.size(); i++) styleTransactionTypeButton(typeButtons.get(i), i == 0);
        form.addView(typeSelector, new LinearLayout.LayoutParams(-1, dp(46)));

        form.addView(formLabel("CATEGORY"), matchWrap());
        form.addView(categorySpinner, new LinearLayout.LayoutParams(-1, dp(54)));

        final int[] selectedDate = {DateRanges.todayKey()};
        Button dateButton = formButton(DateRanges.format(selectedDate[0]));
        dateButton.setOnClickListener(v -> pickDate(selectedDate[0], key -> {
            selectedDate[0] = key;
            dateButton.setText(DateRanges.format(key));
        }));
        form.addView(dateButton, fieldParams());

        EditText note = field("Optional note");
        note.setSingleLine(true);
        form.addView(note, fieldParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add transaction")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("New category", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                showCategoryDialog(selectedFlow[0]);
            });
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(0xFF087D5F);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    long minor = Money.parseMinor(amount.getText().toString());
                    LedgerModels.Category category = (LedgerModels.Category) categorySpinner.getSelectedItem();
                    saveEntry(dialog, minor, selectedDate[0], category, note.getText().toString());
                } catch (Exception error) {
                    amount.setError(error.getMessage() == null ? "Check the amount" : error.getMessage());
                }
            });
        });
        dialog.show();
        return dialog;
    }

    private List<LedgerModels.Category> categoriesForFlow(String flow) {
        List<LedgerModels.Category> filtered = new ArrayList<>();
        for (LedgerModels.Category category : categories) {
            if (flow.equals(category.flow)) filtered.add(category);
        }
        return filtered;
    }

    private void styleTransactionTypeButton(Button button, boolean selected) {
        button.setTextColor(selected ? WHITE : MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(selected
                ? roundRect(INK_SOFT, dp(10))
                : roundRect(Color.TRANSPARENT, dp(10)));
    }

    private void saveEntry(AlertDialog dialog, long minor, int date, LedgerModels.Category category, String note) {
        databaseExecutor.execute(() -> {
            try {
                database.addEntry(minor, date, category.id, category.flow, note);
                mainHandler.post(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show();
                    reload();
                });
            } catch (RuntimeException error) {
                mainHandler.post(() -> Toast.makeText(this, "Could not save transaction", Toast.LENGTH_LONG).show());
            }
        });
    }

    AlertDialog showCategoryDialog() {
        return showCategoryDialog(LedgerModels.EXPENSE);
    }

    private AlertDialog showCategoryDialog(String initialFlow) {
        LinearLayout form = dialogForm();
        form.addView(formLabel("CATEGORY NAME"), matchWrap());
        EditText name = field("e.g. Pet care");
        form.addView(name, compactFieldParams());

        form.addView(formLabel("TYPE"), matchWrap());
        List<String> flowNames = Arrays.asList("Expense", "Investment", "Credit");
        Spinner flow = spinner();
        flow.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, flowNames));
        if (LedgerModels.INVESTMENT.equals(initialFlow)) flow.setSelection(1);
        else if (LedgerModels.CREDIT.equals(initialFlow)) flow.setSelection(2);
        form.addView(flow, compactFieldParams());

        form.addView(formLabel("ICON"), matchWrap());
        List<String> iconNames = Arrays.asList(
                "Piggy bank", "Dividend payout", "Growth chart", "Payment card", "Money transfer",
                "Home", "Food", "Transport", "Health", "Shopping", "Receipt", "Coin", "Bank");
        List<String> iconKeys = Arrays.asList(
                "piggy", "dividend", "chart", "card", "send", "home", "food", "car", "health", "bag", "receipt", "coin", "bank");
        Spinner icon = spinner();
        IconOptionAdapter iconAdapter = new IconOptionAdapter(iconNames, iconKeys, EMERALD);
        icon.setAdapter(iconAdapter);
        form.addView(icon, compactFieldParams());

        form.addView(formLabel("COLOR"), matchWrap());
        List<String> colorNames = Arrays.asList("Emerald", "Ocean", "Violet", "Amber", "Coral", "Slate");
        int[] colors = {EMERALD, 0xFF3A86FF, 0xFF8338EC, AMBER, 0xFFE76F51, 0xFF52796F};
        Spinner color = spinner();
        color.setAdapter(new ColorOptionAdapter(colorNames, colors));
        form.addView(color, compactFieldParams());

        form.addView(formLabel("PREVIEW"), matchWrap());
        LinearLayout preview = row(this);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(10), dp(7), dp(10), dp(7));
        preview.setBackground(roundRect(0xFFF0F2ED, dp(10), 0xFFDDE1D9, dp(1)));
        CategoryIconView previewIcon = new CategoryIconView(this);
        previewIcon.setContentDescription("Selected category icon preview");
        preview.addView(previewIcon, size(dp(38), dp(38)));
        TextView previewName = text("New category", 15, INK, Typeface.BOLD);
        previewName.setPadding(dp(10), 0, 0, 0);
        preview.addView(previewName, weighted());
        form.addView(preview, new LinearLayout.LayoutParams(-1, dp(54)));

        Runnable refreshPreview = () -> {
            int iconPosition = Math.max(0, icon.getSelectedItemPosition());
            int colorPosition = Math.max(0, color.getSelectedItemPosition());
            int selectedColor = colors[colorPosition];
            previewIcon.setIcon(iconKeys.get(iconPosition), selectedColor);
            iconAdapter.setColor(selectedColor);
        };
        AdapterView.OnItemSelectedListener previewListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshPreview.run();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        icon.setOnItemSelectedListener(previewListener);
        color.setOnItemSelectedListener(previewListener);
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {
                String value = text.toString().trim();
                previewName.setText(value.isEmpty() ? "New category" : value);
            }
        });
        refreshPreview.run();

        ScrollView formScroll = new ScrollView(this);
        formScroll.addView(form, new FrameLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("New category")
                .setView(formScroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String categoryName = name.getText().toString().trim();
            if (categoryName.isEmpty()) {
                name.setError("Enter a category name");
                return;
            }
            String[] flows = {LedgerModels.EXPENSE, LedgerModels.INVESTMENT, LedgerModels.CREDIT};
            databaseExecutor.execute(() -> {
                try {
                    database.addCategory(categoryName, flows[flow.getSelectedItemPosition()],
                            iconKeys.get(icon.getSelectedItemPosition()), colors[color.getSelectedItemPosition()]);
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        Toast.makeText(this, "Category created", Toast.LENGTH_SHORT).show();
                        reload();
                    });
                } catch (RuntimeException error) {
                    mainHandler.post(() -> name.setError("That category already exists"));
                }
            });
        }));
        dialog.show();
        return dialog;
    }

    private void showDeleteCategoryDialog(LedgerModels.Category category) {
        if (category.standard) return;
        databaseExecutor.execute(() -> {
            long recordCount = database.entryCountForCategory(category.id);
            mainHandler.post(() -> {
                if (recordCount == 0) {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete " + category.name + "?")
                            .setMessage("This custom category has no associated records.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (dialog, which) ->
                                    deleteCategory(category, null, false))
                            .show();
                    return;
                }
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("Delete " + category.name + "?")
                        .setMessage(recordCount + (recordCount == 1 ? " record uses " : " records use ")
                                + "this category. Move them to another " + flowLabel(category.flow).toLowerCase()
                                + " category to preserve your totals, or delete the records as well.")
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Delete records", null)
                        .setPositiveButton("Move records", null)
                        .create();
                dialog.setOnShowListener(ignored -> {
                    dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setTextColor(DANGER);
                    dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                        dialog.dismiss();
                        confirmDeleteCategoryRecords(category, recordCount);
                    });
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                        dialog.dismiss();
                        showMoveCategoryDialog(category, recordCount);
                    });
                });
                dialog.show();
            });
        });
    }

    private void confirmDeleteCategoryRecords(LedgerModels.Category category, long recordCount) {
        new AlertDialog.Builder(this)
                .setTitle("Delete records permanently?")
                .setMessage("This will permanently delete " + recordCount
                        + (recordCount == 1 ? " record. " : " records. ")
                        + "Available cash, summaries, and breakdowns will be recalculated. This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete permanently", (dialog, which) ->
                        deleteCategory(category, null, true))
                .show();
    }

    private void showMoveCategoryDialog(LedgerModels.Category category, long recordCount) {
        List<LedgerModels.Category> destinations = new ArrayList<>();
        for (LedgerModels.Category candidate : categories) {
            if (candidate.id != category.id && candidate.flow.equals(category.flow)) destinations.add(candidate);
        }
        if (destinations.isEmpty()) {
            Toast.makeText(this, "No destination category is available", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout form = dialogForm();
        form.addView(text("Choose where to move " + recordCount
                + (recordCount == 1 ? " record." : " records."), 13, MUTED, Typeface.NORMAL));
        Spinner destination = spinner();
        destination.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, destinations));
        form.addView(destination, fieldParams());
        new AlertDialog.Builder(this)
                .setTitle("Move records")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Move and delete", (dialog, which) ->
                        deleteCategory(category,
                                destinations.get(destination.getSelectedItemPosition()).id, false))
                .show();
    }

    private void deleteCategory(LedgerModels.Category category, Long replacementId, boolean deleteRecords) {
        databaseExecutor.execute(() -> {
            try {
                database.deleteCustomCategory(category.id, replacementId, deleteRecords);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Category deleted", Toast.LENGTH_SHORT).show();
                    reload();
                });
            } catch (RuntimeException error) {
                mainHandler.post(() -> Toast.makeText(this,
                        error.getMessage() == null ? "Category could not be deleted" : error.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showCustomDateDialog() {
        final int[] start = {dateWindow.start <= 0 ? DateRanges.todayKey() : dateWindow.start};
        final int[] end = {dateWindow.end};
        LinearLayout form = dialogForm();
        TextView explanation = text("Choose any start and end date.", 13, MUTED, Typeface.NORMAL);
        form.addView(explanation);
        Button startButton = formButton("From  ·  " + DateRanges.format(start[0]));
        Button endButton = formButton("To  ·  " + DateRanges.format(end[0]));
        startButton.setOnClickListener(v -> pickDate(start[0], key -> {
            start[0] = key;
            startButton.setText("From  ·  " + DateRanges.format(key));
        }));
        endButton.setOnClickListener(v -> pickDate(end[0], key -> {
            end[0] = key;
            endButton.setText("To  ·  " + DateRanges.format(key));
        }));
        form.addView(startButton, fieldParams());
        form.addView(endButton, fieldParams());

        new AlertDialog.Builder(this)
                .setTitle("Custom date range")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    activePreset = DateRanges.Preset.CUSTOM;
                    dateWindow = DateRanges.custom(start[0], end[0]);
                    preferences.edit().putString("preset", activePreset.name())
                            .putInt("custom_start", dateWindow.start).putInt("custom_end", dateWindow.end).apply();
                    dateLabel.setText(dateWindow.label);
                    periodButton.setText(presetLabel(activePreset) + "  ▾");
                    reload();
                }).show();
    }

    private interface DatePicked { void accept(int dateKey); }

    private void pickDate(int initial, DatePicked callback) {
        Calendar calendar = DateRanges.calendar(initial);
        DatePickerDialog picker = new DatePickerDialog(this, (DatePicker view, int year, int month, int day) -> {
            Calendar selected = Calendar.getInstance();
            selected.clear();
            selected.set(year, month, day);
            callback.accept(DateRanges.key(selected));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private void confirmDelete(LedgerModels.Entry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Delete transaction?")
                .setMessage(entry.category.name + "  ·  " + Money.format(entry.amountMinor) + "\nThis cannot be undone.")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Delete", (dialog, which) -> databaseExecutor.execute(() -> {
                    database.deleteEntry(entry.id);
                    mainHandler.post(this::reload);
                })).show();
    }

    private void requestExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "paisaflow-" + dateWindow.start + "-" + dateWindow.end + ".csv");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    private void requestBackupExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "paisaflow-full-backup.json");
        startActivityForResult(intent, BACKUP_EXPORT_REQUEST);
    }

    private void requestBackupImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, BACKUP_IMPORT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri document = data.getData();
        if (requestCode == EXPORT_REQUEST) writeCsv(document);
        else if (requestCode == BACKUP_EXPORT_REQUEST) writeFullBackup(document);
        else if (requestCode == BACKUP_IMPORT_REQUEST) inspectImport(document);
    }

    private void writeCsv(Uri destination) {
        final int start = dateWindow.start;
        final int end = dateWindow.end;
        databaseExecutor.execute(() -> {
            try (OutputStream stream = getContentResolver().openOutputStream(destination);
                 OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                LedgerModels.Snapshot export = database.snapshot(start, end, 100000);
                writer.write("Date,Category,Type,Amount INR,Note\n");
                for (LedgerModels.Entry entry : export.entries) {
                    writer.write(csv(DateRanges.format(entry.dateKey)) + ",");
                    writer.write(csv(entry.category.name) + ",");
                    writer.write(csv(flowLabel(entry.category.flow)) + ",");
                    writer.write(Long.toString(entry.amountMinor / 100));
                    writer.write(String.format(java.util.Locale.US, ".%02d", Math.abs(entry.amountMinor % 100)) + ",");
                    writer.write(csv(entry.note) + "\n");
                }
                writer.flush();
                mainHandler.post(() -> Toast.makeText(this, "CSV exported", Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                mainHandler.post(() -> Toast.makeText(this, "Export failed", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void writeFullBackup(Uri destination) {
        databaseExecutor.execute(() -> {
            try (OutputStream stream = getContentResolver().openOutputStream(destination);
                 OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                if (stream == null) throw new IllegalStateException("Could not open destination");
                writer.write(database.createBackupJson());
                writer.flush();
                mainHandler.post(() -> Toast.makeText(this, "Full backup created", Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                mainHandler.post(() -> Toast.makeText(this, "Backup export failed", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void inspectImport(Uri source) {
        databaseExecutor.execute(() -> {
            try {
                String json = readDocument(source);
                LedgerModels.BackupInfo info = database.inspectBackup(json);
                mainHandler.post(() -> confirmImport(json, info));
            } catch (Exception error) {
                mainHandler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Backup could not be imported")
                        .setMessage(error.getMessage() == null ? "The selected file is invalid." : error.getMessage())
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
    }

    private String readDocument(Uri source) throws Exception {
        StringBuilder result = new StringBuilder();
        try (InputStream stream = getContentResolver().openInputStream(source);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            if (stream == null) throw new IllegalStateException("Could not open selected file");
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
                if (result.length() > 50_000_000) throw new IllegalArgumentException("Backup is larger than 50 MB");
            }
        }
        return result.toString();
    }

    private void confirmImport(String json, LedgerModels.BackupInfo info) {
        String message = "This backup contains " + info.transactions + " transactions and " + info.categories
                + " categories" + (info.hasOpeningBalance ? ", including an opening balance." : ".")
                + "\n\nImporting replaces all current PaisaFlow data. This cannot be undone unless you create a backup first.";
        new AlertDialog.Builder(this)
                .setTitle("Replace current data?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Replace data", (dialog, which) -> databaseExecutor.execute(() -> {
                    try {
                        database.restoreBackup(json);
                        mainHandler.post(() -> {
                            Toast.makeText(this, "Backup imported", Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    } catch (Exception error) {
                        mainHandler.post(() -> Toast.makeText(this, "Import failed; current data was kept", Toast.LENGTH_LONG).show());
                    }
                })).show();
    }

    private static String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private LinearLayout dialogForm() {
        LinearLayout form = column(this);
        int horizontal = dp(22);
        form.setPadding(horizontal, dp(8), horizontal, dp(4));
        return form;
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setBackground(roundRect(0xFFF0F2ED, dp(10), 0xFFDDE1D9, dp(1)));
        return field;
    }

    private TextView formLabel(String value) {
        TextView label = text(value, 10, MUTED, Typeface.BOLD);
        label.setPadding(dp(2), dp(12), 0, dp(3));
        return label;
    }

    private Spinner spinner() {
        Spinner spinner = new Spinner(this);
        spinner.setPadding(dp(7), 0, dp(7), 0);
        spinner.setBackground(roundRect(0xFFF0F2ED, dp(10), 0xFFDDE1D9, dp(1)));
        return spinner;
    }

    private Button formButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundRect(0xFFF0F2ED, dp(10), 0xFFDDE1D9, dp(1)));
        return button;
    }

    private Button compactButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(WHITE);
        button.setAllCaps(false);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundRect(INK_SOFT, dp(16), 0xFF33405A, dp(1)));
        return button;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams compactFieldParams() {
        return new LinearLayout.LayoutParams(-1, dp(50));
    }

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1f); }
    private static LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }
    private static LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private static LinearLayout.LayoutParams size(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private static FrameLayout.LayoutParams frameMatch() { return new FrameLayout.LayoutParams(-1, -1); }

    private static GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static GradientDrawable roundRect(int color, int radius, int stroke, int width) {
        GradientDrawable drawable = roundRect(color, radius);
        drawable.setStroke(width, stroke);
        return drawable;
    }

    private final class EntryAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<LedgerModels.Entry> entries = new ArrayList<>();

        EntryAdapter(Context context) { this.context = context; }

        void replace(List<LedgerModels.Entry> replacement) {
            entries.clear();
            entries.addAll(replacement);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return entries.size(); }
        @Override public LedgerModels.Entry getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return entries.get(position).id; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            RowHolder holder;
            if (recycled == null) {
                LinearLayout card = row(context);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(12), dp(9), dp(12), dp(9));
                card.setBackground(roundRect(WHITE, dp(14), 0xFFE8EAE4, dp(1)));

                CategoryIconView icon = new CategoryIconView(context);
                card.addView(icon, size(dp(42), dp(42)));

                LinearLayout center = column(context);
                center.setPadding(dp(12), 0, dp(8), 0);
                TextView title = text("Category", 15, INK, Typeface.BOLD);
                TextView detail = text("Date", 12, MUTED, Typeface.NORMAL);
                detail.setSingleLine(true);
                center.addView(title);
                center.addView(detail);
                card.addView(center, weighted());

                LinearLayout right = column(context);
                right.setGravity(Gravity.END);
                TextView amount = text("₹0", 15, INK, Typeface.BOLD);
                TextView flow = text("EXPENSE", 9, MUTED, Typeface.BOLD);
                amount.setGravity(Gravity.END);
                flow.setGravity(Gravity.END);
                right.addView(amount);
                right.addView(flow);
                card.addView(right, wrap());
                holder = new RowHolder(icon, title, detail, amount, flow);
                card.setTag(holder);
                recycled = card;
            } else {
                holder = (RowHolder) recycled.getTag();
            }

            LedgerModels.Entry entry = getItem(position);
            holder.icon.setIcon(entry.category.icon, entry.category.color);
            holder.icon.setContentDescription(entry.category.name + " icon");
            holder.title.setText(entry.category.name);
            String details = DateRanges.shortFormat(entry.dateKey);
            if (!entry.note.isEmpty()) details += "  ·  " + entry.note;
            holder.detail.setText(details);
            String sign = LedgerModels.CREDIT.equals(entry.category.flow) ? "+" : "−";
            holder.amount.setText(sign + Money.format(entry.amountMinor));
            holder.amount.setTextColor(LedgerModels.CREDIT.equals(entry.category.flow) ? 0xFF0F9D78 : INK);
            holder.flow.setText(flowLabel(entry.category.flow));
            recycled.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(70)));
            return recycled;
        }
    }

    private final class CategoryTotalAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<LedgerModels.CategoryTotal> totals = new ArrayList<>();
        private long grandTotal;

        CategoryTotalAdapter(Context context) { this.context = context; }

        void replace(List<LedgerModels.CategoryTotal> replacement) {
            totals.clear();
            totals.addAll(replacement);
            grandTotal = 0;
            for (LedgerModels.CategoryTotal item : totals) grandTotal += item.amountMinor;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return totals.size(); }
        @Override public LedgerModels.CategoryTotal getItem(int position) { return totals.get(position); }
        @Override public long getItemId(int position) { return totals.get(position).category.id; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            CategoryRowHolder holder;
            if (recycled == null) {
                LinearLayout card = row(context);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(12), dp(9), dp(12), dp(9));
                card.setBackground(roundRect(WHITE, dp(14), 0xFFE8EAE4, dp(1)));
                CategoryIconView icon = new CategoryIconView(context);
                card.addView(icon, size(dp(42), dp(42)));
                TextView name = text("Category", 15, INK, Typeface.BOLD);
                name.setPadding(dp(12), 0, dp(8), 0);
                card.addView(name, weighted());
                LinearLayout right = column(context);
                right.setGravity(Gravity.END);
                TextView amount = text("₹0", 15, INK, Typeface.BOLD);
                TextView percentage = text("0%", 11, MUTED, Typeface.NORMAL);
                amount.setGravity(Gravity.END);
                percentage.setGravity(Gravity.END);
                right.addView(amount);
                right.addView(percentage);
                card.addView(right, wrap());
                holder = new CategoryRowHolder(icon, name, amount, percentage);
                card.setTag(holder);
                recycled = card;
            } else {
                holder = (CategoryRowHolder) recycled.getTag();
            }
            LedgerModels.CategoryTotal item = getItem(position);
            holder.icon.setIcon(item.category.icon, item.category.color);
            holder.icon.setContentDescription(item.category.name + " icon");
            holder.name.setText(item.category.name);
            holder.amount.setText(Money.format(item.amountMinor));
            double percent = grandTotal == 0 ? 0 : item.amountMinor * 100.0 / grandTotal;
            holder.percentage.setText(String.format(java.util.Locale.getDefault(), "%.1f%%", percent));
            recycled.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(70)));
            return recycled;
        }
    }

    private final class CategoryOptionAdapter extends BaseAdapter {
        private final ArrayList<LedgerModels.Category> items = new ArrayList<>();

        void replace(List<LedgerModels.Category> replacement) {
            items.clear();
            items.addAll(replacement);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public LedgerModels.Category getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return getItem(position).id; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            return optionView(position, recycled);
        }
        @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
            return optionView(position, recycled);
        }

        private View optionView(int position, View recycled) {
            IconOptionHolder holder;
            if (recycled == null || !(recycled.getTag() instanceof IconOptionHolder)) {
                LinearLayout option = row(MainActivity.this);
                option.setGravity(Gravity.CENTER_VERTICAL);
                option.setPadding(dp(8), dp(5), dp(8), dp(5));
                CategoryIconView icon = new CategoryIconView(MainActivity.this);
                option.addView(icon, size(dp(38), dp(38)));
                TextView label = text("Category", 15, INK, Typeface.BOLD);
                label.setPadding(dp(10), 0, 0, 0);
                option.addView(label, weighted());
                holder = new IconOptionHolder(icon, label);
                option.setTag(holder);
                recycled = option;
            } else {
                holder = (IconOptionHolder) recycled.getTag();
            }
            LedgerModels.Category category = getItem(position);
            holder.icon.setIcon(category.icon, category.color);
            holder.icon.setContentDescription(category.name + " category icon");
            holder.label.setText(category.name);
            recycled.setMinimumHeight(dp(50));
            return recycled;
        }
    }

    private final class CategoryManagementAdapter extends BaseAdapter {
        private final Context context;
        private final ArrayList<LedgerModels.Category> items = new ArrayList<>();

        CategoryManagementAdapter(Context context) { this.context = context; }

        void replace(List<LedgerModels.Category> replacement) {
            items.clear();
            items.addAll(replacement);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public LedgerModels.Category getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            ManagementRowHolder holder;
            if (recycled == null) {
                LinearLayout card = row(context);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(12), dp(9), dp(12), dp(9));
                card.setBackground(roundRect(WHITE, dp(14), 0xFFE8EAE4, dp(1)));
                CategoryIconView icon = new CategoryIconView(context);
                card.addView(icon, size(dp(42), dp(42)));
                LinearLayout copy = column(context);
                copy.setPadding(dp(12), 0, dp(8), 0);
                TextView name = text("Category", 15, INK, Typeface.BOLD);
                TextView type = text("EXPENSE", 10, MUTED, Typeface.BOLD);
                copy.addView(name);
                copy.addView(type);
                card.addView(copy, weighted());
                Button delete = new Button(context);
                delete.setText("Delete");
                delete.setTextSize(12);
                delete.setTextColor(DANGER);
                delete.setAllCaps(false);
                delete.setPadding(dp(10), 0, dp(10), 0);
                delete.setBackground(roundRect(0xFFFFEFF0, dp(12), 0xFFFFCDD2, dp(1)));
                card.addView(delete, size(dp(72), dp(38)));
                holder = new ManagementRowHolder(icon, name, type, delete);
                card.setTag(holder);
                recycled = card;
            } else {
                holder = (ManagementRowHolder) recycled.getTag();
            }
            LedgerModels.Category category = getItem(position);
            holder.icon.setIcon(category.icon, category.color);
            holder.name.setText(category.name);
            holder.type.setText(flowLabel(category.flow) + (category.standard ? "  ·  STANDARD" : "  ·  CUSTOM"));
            holder.delete.setVisibility(category.standard ? View.GONE : View.VISIBLE);
            holder.delete.setOnClickListener(category.standard ? null : v -> showDeleteCategoryDialog(category));
            recycled.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(68)));
            return recycled;
        }
    }

    private final class IconOptionAdapter extends BaseAdapter {
        private final List<String> labels;
        private final List<String> keys;
        private int color;

        IconOptionAdapter(List<String> labels, List<String> keys, int color) {
            this.labels = labels;
            this.keys = keys;
            this.color = color;
        }

        void setColor(int color) {
            if (this.color == color) return;
            this.color = color;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return labels.size(); }
        @Override public String getItem(int position) { return labels.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            return optionView(position, recycled);
        }
        @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
            return optionView(position, recycled);
        }

        private View optionView(int position, View recycled) {
            IconOptionHolder holder;
            if (recycled == null || !(recycled.getTag() instanceof IconOptionHolder)) {
                LinearLayout row = row(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(5), dp(8), dp(5));
                CategoryIconView icon = new CategoryIconView(MainActivity.this);
                row.addView(icon, size(dp(36), dp(36)));
                TextView label = text("Icon", 15, INK, Typeface.NORMAL);
                label.setPadding(dp(10), 0, 0, 0);
                row.addView(label, weighted());
                holder = new IconOptionHolder(icon, label);
                row.setTag(holder);
                recycled = row;
            } else {
                holder = (IconOptionHolder) recycled.getTag();
            }
            holder.icon.setIcon(keys.get(position), color);
            holder.icon.setContentDescription(labels.get(position) + " icon");
            holder.label.setText(labels.get(position));
            recycled.setMinimumHeight(dp(48));
            return recycled;
        }
    }

    private final class ColorOptionAdapter extends BaseAdapter {
        private final List<String> labels;
        private final int[] colors;

        ColorOptionAdapter(List<String> labels, int[] colors) {
            this.labels = labels;
            this.colors = colors;
        }

        @Override public int getCount() { return labels.size(); }
        @Override public String getItem(int position) { return labels.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            return optionView(position, recycled);
        }
        @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
            return optionView(position, recycled);
        }

        private View optionView(int position, View recycled) {
            ColorOptionHolder holder;
            if (recycled == null || !(recycled.getTag() instanceof ColorOptionHolder)) {
                LinearLayout row = row(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(6), dp(8), dp(6));
                View swatch = new View(MainActivity.this);
                row.addView(swatch, size(dp(24), dp(24)));
                TextView label = text("Color", 15, INK, Typeface.NORMAL);
                label.setPadding(dp(12), 0, 0, 0);
                row.addView(label, weighted());
                holder = new ColorOptionHolder(swatch, label);
                row.setTag(holder);
                recycled = row;
            } else {
                holder = (ColorOptionHolder) recycled.getTag();
            }
            holder.swatch.setBackground(roundRect(colors[position], dp(12)));
            holder.swatch.setContentDescription(labels.get(position) + " color swatch");
            holder.label.setText(labels.get(position));
            recycled.setMinimumHeight(dp(48));
            return recycled;
        }
    }

    private static String flowLabel(String flow) {
        if (LedgerModels.INVESTMENT.equals(flow)) return "INVESTMENT";
        if (LedgerModels.CREDIT.equals(flow)) return "CREDIT";
        return "EXPENSE";
    }

    private static final class RowHolder {
        final CategoryIconView icon;
        final TextView title;
        final TextView detail;
        final TextView amount;
        final TextView flow;

        RowHolder(CategoryIconView icon, TextView title, TextView detail, TextView amount, TextView flow) {
            this.icon = icon;
            this.title = title;
            this.detail = detail;
            this.amount = amount;
            this.flow = flow;
        }
    }

    private static final class CategoryRowHolder {
        final CategoryIconView icon;
        final TextView name;
        final TextView amount;
        final TextView percentage;

        CategoryRowHolder(CategoryIconView icon, TextView name, TextView amount, TextView percentage) {
            this.icon = icon;
            this.name = name;
            this.amount = amount;
            this.percentage = percentage;
        }
    }

    private static final class SummaryRow {
        final TextView value;
        final TextView percentage;
        final View fill;

        SummaryRow(TextView value, TextView percentage, View fill) {
            this.value = value;
            this.percentage = percentage;
            this.fill = fill;
        }
    }

    private static final class ManagementRowHolder {
        final CategoryIconView icon;
        final TextView name;
        final TextView type;
        final Button delete;

        ManagementRowHolder(CategoryIconView icon, TextView name, TextView type, Button delete) {
            this.icon = icon;
            this.name = name;
            this.type = type;
            this.delete = delete;
        }
    }

    private static final class IconOptionHolder {
        final CategoryIconView icon;
        final TextView label;

        IconOptionHolder(CategoryIconView icon, TextView label) {
            this.icon = icon;
            this.label = label;
        }
    }

    private static final class ColorOptionHolder {
        final View swatch;
        final TextView label;

        ColorOptionHolder(View swatch, TextView label) {
            this.swatch = swatch;
            this.label = label;
        }
    }
}
