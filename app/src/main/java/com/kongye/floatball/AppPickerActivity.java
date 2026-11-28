package com.kongye.floatball;

import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.*;
import android.text.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.File;
import java.util.*;

/** Application picker dialog (default) and full-screen per-app floating ball settings (ruleMode). */
public class AppPickerActivity extends Activity {
    private static final class Item {
        final AppCatalog.Entry entry;
        final String group, size, date;
        Item(AppCatalog.Entry e, String g, String s, String d) { entry = e; group = g; size = s; date = d; }
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Object> rows = new ArrayList<>();
    private final Set<String> collapsed = new HashSet<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private EditText search;
    private ListView list;
    private BaseAdapter adapter;
    private String selected;
    private TextView confirm;
    private TextView configuredBadge;
    private LinearLayout usageBanner;
    private LeafSwitch masterSkipSwitch;
    private TextView masterSkipSub;
    private Dialog ruleSheet;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemePolicy.apply(this);
        setTheme(Ui.dark ? R.style.AppThemeDark : R.style.AppTheme);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (ruleMode()) buildRulePage(); else buildPickerModal();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { collapsed.clear(); populate(); }
            public void afterTextChanged(Editable e) {}
        });
        String query = getIntent().getStringExtra("query");
        if (query != null) search.setText(query);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= rows.size()) return;
            Object item = rows.get(position);
            if (item instanceof String group) {
                if (!collapsed.remove(group)) collapsed.add(group);
                populate();
            } else {
                Item app = (Item) item;
                if (ruleMode()) {
                    hideKeyboard();
                    chooseRule(app.entry.pkg, app.entry.label);
                    return;
                }
                selected = app.entry.pkg;
                if (confirm != null) { confirm.setEnabled(true); confirm.setAlpha(1f); }
                adapter.notifyDataSetChanged();
            }
        });
        loadApps();
    }

    protected boolean ruleMode() { return false; }

    @Override protected void onResume() {
        super.onResume();
        if (ruleMode()) syncRuleHeaderState();
    }

    @Override protected void onDestroy() {
        dismissRuleSheet();
        super.onDestroy();
    }

    private void buildRulePage() {
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(Ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        LinearLayout shell = Ui.column(this);
        Ui.pad(shell, 16, 8, 16, 0);

        LinearLayout header = Ui.row(this);
        header.setMinimumHeight(dp(48));
        header.addView(Ui.tool(this, "back", "返回", this::finish), new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView heading = Ui.text(this, "应用内悬浮球", 20, Ui.INK, true);
        Ui.pad(heading, 6, 0, 8, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        configuredBadge = Ui.text(this, "", 11.5f, Ui.GREEN, true);
        configuredBadge.setBackground(Ui.bg(Ui.PALE, 10, this));
        Ui.pad(configuredBadge, 9, 4, 9, 4);
        configuredBadge.setVisibility(View.GONE);
        header.addView(configuredBadge, new LinearLayout.LayoutParams(-2, -2));
        shell.addView(header);
        Ui.gap(shell, 8);

        // Global ad-skip switch card
        LinearLayout adCard = Ui.row(this);
        Ui.pad(adCard, 14, 12, 12, 12);
        adCard.setBackground(cardRipple(Ui.SURFACE, 18, Ui.LINE, .8f));
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.bg(Ui.dark ? Ui.PALE : 0xFFEEF4EC, 12, this));
        badge.addView(Ui.icon(this, "app_ball", Ui.GREEN, 20), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        adCard.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout adTexts = Ui.column(this);
        Ui.pad(adTexts, 12, 0, 10, 0);
        adTexts.addView(Ui.text(this, "跳过开屏广告", 15, Ui.INK, true));
        Ui.gap(adTexts, 3);
        masterSkipSub = Ui.text(this, "", 12, Ui.MUTED, false);
        adTexts.addView(masterSkipSub);
        adCard.addView(adTexts, new LinearLayout.LayoutParams(0, -2, 1));
        masterSkipSwitch = new LeafSwitch(this);
        masterSkipSwitch.setContentDescription("跳过开屏广告总开关");
        masterSkipSwitch.setChecked(Prefs.of(this).getBoolean("ad_skip_enabled", false));
        masterSkipSwitch.setOnCheckedChangeListener((v, on) -> {
            Prefs.of(this).edit().putBoolean("ad_skip_enabled", on).apply();
            if (AssistService.instance != null) AssistService.instance.adSkipSettingsChanged();
            if (on && AssistService.instance == null) Feedback.show(this, "请先开启无障碍服务");
            syncRuleHeaderState();
            if (adapter != null) adapter.notifyDataSetChanged();
        });
        adCard.addView(masterSkipSwitch, new LinearLayout.LayoutParams(dp(44), dp(40)));
        adCard.setOnClickListener(v -> masterSkipSwitch.toggle());
        shell.addView(adCard, new LinearLayout.LayoutParams(-1, -2));

        // Usage access hint banner (shown only when permission is missing)
        usageBanner = Ui.row(this);
        LinearLayout.LayoutParams bannerAt = new LinearLayout.LayoutParams(-1, -2);
        bannerAt.topMargin = dp(10);
        Ui.pad(usageBanner, 14, 10, 10, 10);
        usageBanner.setBackground(strokedSurface(Ui.dark ? 0xFF1D2922 : 0xFFEBF3EC, 16, Ui.dark ? 0xFF2D4336 : 0xFFCDE0D3, .8f));
        usageBanner.addView(Ui.icon(this, "info", Ui.GREEN, 18));
        TextView bannerText = Ui.text(this, "开启使用记录权限，以识别当前应用", 12.5f, Ui.INK, false);
        Ui.pad(bannerText, 8, 0, 8, 0);
        usageBanner.addView(bannerText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView grantBtn = Ui.centeredText(this, "去开启", 12, Ui.dark ? 0xFF102E21 : Color.WHITE, true);
        grantBtn.setBackground(Ui.ripple(this, Ui.GREEN, 10));
        Ui.pad(grantBtn, 11, 6, 11, 6);
        grantBtn.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))));
        usageBanner.addView(grantBtn);
        shell.addView(usageBanner, bannerAt);

        Ui.gap(shell, 10);
        shell.addView(buildSearchCapsule(true), new LinearLayout.LayoutParams(-1, -2));
        Ui.gap(shell, 6);

        list = new ListView(this);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(8));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setFastScrollEnabled(true);
        Ui.pad(list, 0, 2, 0, 20);
        adapter = createRuleAdapter();
        list.setAdapter(adapter);
        shell.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout page = Ui.page(this, shell);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right;
                bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });
        setContentView(page);
        page.setFocusableInTouchMode(true);
        page.requestFocus();
        page.requestApplyInsets();
        syncRuleHeaderState();
    }

    private void buildPickerModal() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x77000000);
        LinearLayout card = Ui.card(this);
        Ui.pad(card, 16, 10, 16, 14);
        card.setElevation(dp(12));
        LinearLayout titleRow = Ui.row(this);
        titleRow.addView(Ui.tool(this, "back", "取消选择", this::finish), new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView heading = Ui.text(this, "选择应用", 19, Ui.INK, true);
        Ui.pad(heading, 6, 0, 0, 0);
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(titleRow);
        Ui.gap(card, 10);
        card.addView(buildSearchCapsule(false), new LinearLayout.LayoutParams(-1, -2));
        Ui.gap(card, 8);

        list = new ListView(this);
        list.setDivider(null);
        list.setFastScrollEnabled(true);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        adapter = createPickerAdapter();
        list.setAdapter(adapter);
        card.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = Ui.row(this);
        Ui.pad(bottom, 0, 10, 0, 0);
        LinearLayout.LayoutParams cancelAt = new LinearLayout.LayoutParams(0, -2, 1);
        cancelAt.rightMargin = dp(5);
        bottom.addView(Ui.button(this, "取消", false, this::finish), cancelAt);
        confirm = Ui.button(this, "确定", true, () -> {
            if (selected != null) {
                setResult(RESULT_OK, new Intent().putExtra("selected", "app:" + selected));
                finish();
            }
        });
        confirm.setEnabled(false);
        confirm.setAlpha(.45f);
        LinearLayout.LayoutParams confirmAt = new LinearLayout.LayoutParams(0, -2, 1);
        confirmAt.leftMargin = dp(5);
        bottom.addView(confirm, confirmAt);
        card.addView(bottom);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        cp.setMargins(dp(16), dp(24), dp(16), dp(16));
        root.addView(card, cp);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, insetBottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                insetBottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                insetBottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, insetBottom);
            return insets;
        });
        root.setFocusableInTouchMode(true);
        root.requestFocus();
    }

    private LinearLayout buildSearchCapsule(boolean fullScreen) {
        int fill = fullScreen ? Ui.SURFACE : Ui.BG;
        LinearLayout capsule = Ui.row(this);
        capsule.setMinimumHeight(dp(46));
        Ui.pad(capsule, 14, 3, 6, 3);
        capsule.setBackground(strokedSurface(fill, 16, Ui.LINE, .9f));

        FrameLayout searchIconHolder = new FrameLayout(this);
        searchIconHolder.addView(Ui.icon(this, "search", Ui.MUTED, 18), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        capsule.addView(searchIconHolder, new LinearLayout.LayoutParams(dp(22), dp(22)));

        search = new EditText(this);
        search.setId(R.id.app_search);
        search.setHint("搜索本地应用");
        search.setTextColor(Ui.INK);
        search.setHintTextColor(Ui.MUTED);
        Ui.textSize(search, 14);
        search.setTypeface(Ui.textTypeface(false));
        search.setSingleLine();
        search.setBackground(null);
        Ui.pad(search, 10, 0, 6, 0);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setMinHeight(dp(40));
        capsule.addView(search, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout clearBtn = new FrameLayout(this);
        clearBtn.setContentDescription("清空搜索");
        clearBtn.setFocusable(true);
        clearBtn.setBackground(Ui.ripple(this, 0, 16));
        clearBtn.addView(Ui.icon(this, "close", Ui.MUTED, 16), new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> { search.setText(""); hideKeyboard(); });
        capsule.addView(clearBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));

        search.setOnFocusChangeListener((v, hasFocus) -> {
            capsule.setBackground(strokedSurface(fill, 16, hasFocus ? Ui.GREEN : Ui.LINE, hasFocus ? 1.2f : .9f));
            searchIconHolder.removeAllViews();
            searchIconHolder.addView(Ui.icon(this, "search", hasFocus ? Ui.GREEN : Ui.MUTED, 18), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            public void afterTextChanged(Editable e) {}
        });
        return capsule;
    }

    private BaseAdapter createRuleAdapter() {
        return new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int p) { return rows.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int position, View convert, ViewGroup parent) {
                Object item = rows.get(position);
                if (item instanceof String group) {
                    LinearLayout head = Ui.row(AppPickerActivity.this);
                    Ui.pad(head, 4, position == 0 ? 4 : 10, 4, 2);
                    TextView letter = Ui.centeredText(AppPickerActivity.this, group, 11.5f, Ui.GREEN, true);
                    letter.setBackground(Ui.bg(Ui.PALE, 7, AppPickerActivity.this));
                    head.addView(letter, new LinearLayout.LayoutParams(dp(22), dp(22)));
                    TextView count = Ui.text(AppPickerActivity.this, counts.getOrDefault(group, 0) + " 个应用", 12, Ui.MUTED, false);
                    Ui.pad(count, 8, 0, 0, 0);
                    head.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
                    head.addView(Ui.icon(AppPickerActivity.this, collapsed.contains(group) ? "expand" : "collapse", Ui.MUTED, 16));
                    head.setContentDescription("应用分组 " + group);
                    return head;
                }
                Item app = (Item) item;
                String pkg = app.entry.pkg;
                String mode = AppBehavior.mode(AppPickerActivity.this, pkg);
                boolean globalSkip = Prefs.of(AppPickerActivity.this).getBoolean("ad_skip_enabled", false);
                boolean appSkip = Prefs.of(AppPickerActivity.this).getBoolean("app_skip_" + pkg, true);
                boolean customMode = !mode.equals("normal");
                boolean customSkip = globalSkip && !appSkip;
                boolean customized = customMode || customSkip;

                LinearLayout row = Ui.row(AppPickerActivity.this);
                Ui.pad(row, 12, 11, 10, 11);
                int stroke = customized ? (Ui.dark ? 0xFF335242 : 0xFFC8DED1) : Ui.LINE;
                row.setBackground(cardRipple(Ui.SURFACE, 16, stroke, customized ? 1f : .8f));

                FrameLayout iconWrap = new FrameLayout(AppPickerActivity.this);
                Ui.frame(iconWrap, Color.TRANSPARENT, 10);
                ImageView icon = new ImageView(AppPickerActivity.this);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                icon.setImageDrawable(loadIcon(app.entry));
                iconWrap.addView(icon, new FrameLayout.LayoutParams(-1, -1));
                row.addView(iconWrap, new LinearLayout.LayoutParams(dp(42), dp(42)));

                LinearLayout titles = Ui.column(AppPickerActivity.this);
                Ui.pad(titles, 12, 0, 8, 0);
                TextView name = Ui.text(AppPickerActivity.this, app.entry.label, 15, Ui.INK, true);
                name.setSingleLine();
                name.setEllipsize(TextUtils.TruncateAt.END);
                titles.addView(name);
                Ui.gap(titles, 4);
                String detail = switch (mode) {
                    case "disabled" -> "在此应用中关闭悬浮球";
                    case "hidden" -> "隐藏外观，手势仍可用";
                    default -> "正常显示，手势可用";
                };
                if (customSkip) detail += " · 不跳过广告";
                int detailColor = mode.equals("disabled") ? (Ui.dark ? 0xFFE6B76C : 0xFF9E6220)
                        : mode.equals("hidden") ? Ui.GREEN : Ui.MUTED;
                TextView sub = Ui.text(AppPickerActivity.this, detail, 12, detailColor, false);
                sub.setSingleLine();
                sub.setEllipsize(TextUtils.TruncateAt.END);
                titles.addView(sub);
                row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

                TextView pill = statusPill(mode);
                row.addView(pill, new LinearLayout.LayoutParams(-2, -2));
                LinearLayout.LayoutParams arrowAt = new LinearLayout.LayoutParams(dp(15), dp(15));
                arrowAt.leftMargin = dp(4);
                row.addView(Ui.icon(AppPickerActivity.this, "forward", Ui.MUTED, 15), arrowAt);
                row.setContentDescription("应用悬浮球状态：" + app.entry.label + "：" + AppBehavior.label(mode));
                return row;
            }
        };
    }

    private BaseAdapter createPickerAdapter() {
        return new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int p) { return rows.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int position, View convert, ViewGroup parent) {
                Object item = rows.get(position);
                if (item instanceof String group) {
                    LinearLayout head = Ui.row(AppPickerActivity.this);
                    Ui.pad(head, 4, 14, 4, 8);
                    head.addView(Ui.text(AppPickerActivity.this, group + " (" + counts.getOrDefault(group, 0) + ")", 12, Ui.MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
                    head.addView(Ui.icon(AppPickerActivity.this, collapsed.contains(group) ? "expand" : "collapse", Ui.MUTED, 18));
                    head.setContentDescription("应用分组 " + group);
                    return head;
                }
                Item app = (Item) item;
                LinearLayout row = Ui.row(AppPickerActivity.this);
                Ui.pad(row, 8, 10, 8, 10);
                if (app.entry.pkg.equals(selected)) row.setBackground(Ui.bg(Ui.PALE, 14, AppPickerActivity.this));
                FrameLayout iconWrap = new FrameLayout(AppPickerActivity.this);
                Ui.frame(iconWrap, Color.TRANSPARENT, 10);
                ImageView icon = new ImageView(AppPickerActivity.this);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                icon.setImageDrawable(loadIcon(app.entry));
                iconWrap.addView(icon, new FrameLayout.LayoutParams(-1, -1));
                row.addView(iconWrap, new LinearLayout.LayoutParams(dp(42), dp(42)));
                LinearLayout titles = Ui.column(AppPickerActivity.this);
                Ui.pad(titles, 12, 0, 6, 0);
                TextView name = Ui.text(AppPickerActivity.this, app.entry.label, 15.5f, Ui.INK, false);
                name.setSingleLine();
                name.setEllipsize(TextUtils.TruncateAt.END);
                titles.addView(name);
                Ui.gap(titles, 3);
                titles.addView(Ui.text(AppPickerActivity.this, app.size, 11.5f, Ui.MUTED, false));
                row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
                if (app.entry.pkg.equals(selected)) row.addView(Ui.icon(AppPickerActivity.this, "check", Ui.GREEN, 18));
                row.setContentDescription("选择应用：" + app.entry.label);
                return row;
            }
        };
    }

    private TextView statusPill(String mode) {
        String text = switch (mode) {
            case "disabled" -> "禁用";
            case "hidden" -> "隐身";
            default -> "正常";
        };
        int fg = switch (mode) {
            case "disabled" -> Ui.dark ? 0xFFF2CE72 : 0xFF965515;
            case "hidden" -> Ui.GREEN;
            default -> Ui.MUTED;
        };
        int bg = switch (mode) {
            case "disabled" -> Ui.dark ? 0xFF3A311C : 0xFFFFF2C6;
            case "hidden" -> Ui.dark ? 0xFF264033 : 0xFFDFF0E5;
            default -> Ui.dark ? 0xFF24312B : 0xFFEDF3EE;
        };
        TextView chip = Ui.text(this, text, 11, fg, true);
        chip.setSingleLine();
        Ui.pad(chip, 8, 4, 8, 4);
        chip.setBackground(Ui.bg(bg, 8, this));
        return chip;
    }

    private void syncRuleHeaderState() {
        if (!ruleMode()) return;
        boolean skipOn = Prefs.of(this).getBoolean("ad_skip_enabled", false);
        if (masterSkipSwitch != null && masterSkipSwitch.isChecked() != skipOn) masterSkipSwitch.setChecked(skipOn);
        if (masterSkipSub != null) {
            masterSkipSub.setText(skipOn
                    ? (AssistService.instance == null ? "已开启 · 需先启用无障碍服务" : "已开启 · 自动识别应用开屏跳过按钮")
                    : "开启后，进入应用时自动跳过开屏广告");
        }
        if (usageBanner != null) {
            usageBanner.setVisibility(AppCatalog.hasUsage(this) ? View.GONE : View.VISIBLE);
        }
        updateConfiguredCount();
    }

    private void updateConfiguredCount() {
        if (configuredBadge == null) return;
        boolean globalSkip = Prefs.of(this).getBoolean("ad_skip_enabled", false);
        int custom = 0;
        for (Item item : items) {
            String pkg = item.entry.pkg;
            boolean customMode = !AppBehavior.mode(this, pkg).equals("normal");
            boolean customSkip = globalSkip && !Prefs.of(this).getBoolean("app_skip_" + pkg, true);
            if (customMode || customSkip) custom++;
        }
        if (custom > 0) {
            configuredBadge.setText("已自定义 " + custom);
            configuredBadge.setVisibility(View.VISIBLE);
        } else {
            configuredBadge.setVisibility(View.GONE);
        }
    }

    private void chooseRule(String pkg, String label) {
        dismissRuleSheet();
        Dialog sheet = new Dialog(this, Ui.dark ? R.style.KongyeDialogDark : R.style.KongyeDialog);
        ruleSheet = sheet;
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout body = Ui.column(this);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Ui.SURFACE);
        float topRadius = dp(26);
        sheetBg.setCornerRadii(new float[]{topRadius, topRadius, topRadius, topRadius, 0, 0, 0, 0});
        body.setBackground(sheetBg);
        Ui.pad(body, 20, 10, 20, 20);

        // Top drag handle pill
        View handle = new View(this);
        handle.setBackground(Ui.bg(Ui.dark ? 0xFF394A41 : 0xFFD5E0D7, 3, this));
        LinearLayout.LayoutParams handleAt = new LinearLayout.LayoutParams(dp(38), dp(4));
        handleAt.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(handle, handleAt);
        Ui.gap(body, 14);

        // App header
        LinearLayout head = Ui.row(this);
        FrameLayout iconWrap = new FrameLayout(this);
        Ui.frame(iconWrap, Color.TRANSPARENT, 11);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try { icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg)); }
        catch (PackageManager.NameNotFoundException ignored) {}
        iconWrap.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        head.addView(iconWrap, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = Ui.column(this);
        Ui.pad(titles, 12, 0, 8, 0);
        TextView appTitle = Ui.text(this, label, 18, Ui.INK, true);
        appTitle.setSingleLine();
        appTitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(appTitle);
        Ui.gap(titles, 3);
        titles.addView(Ui.text(this, "应用专属设置", 12, Ui.MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout closeBtn = Ui.tool(this, "close", "关闭应用设置", sheet::dismiss);
        closeBtn.setMinimumWidth(0);
        closeBtn.setMinimumHeight(0);
        closeBtn.setBackground(Ui.ripple(this, Ui.dark ? Ui.PALE : 0xFFEEF3EE, 17));
        head.addView(closeBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));
        body.addView(head);

        Ui.gap(body, 16);
        View headLine = new View(this);
        headLine.setBackgroundColor(Ui.LINE);
        body.addView(headLine, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.6f))));
        Ui.gap(body, 16);

        body.addView(Ui.text(this, "悬浮球显示", 13, Ui.MUTED, true));
        Ui.gap(body, 10);
        String[] ids = {"normal", "hidden", "disabled"};
        String[] labels = {"正常显示", "隐身操作", "禁用悬浮球"};
        String[] details = {"显示悬浮球，手势可用", "隐藏外观，手势仍可用", "在此应用中关闭悬浮球"};
        SelectionCard[] choices = new SelectionCard[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final int selectedIndex = i;
            SelectionCard option = new SelectionCard(this, labels[i], details[i]);
            choices[i] = option;
            option.setChecked(ids[i].equals(AppBehavior.mode(this, pkg)));
            option.setOnClickListener(v -> {
                String mode = ids[selectedIndex];
                SharedPreferences.Editor edit = Prefs.of(this).edit();
                if (mode.equals("normal")) edit.remove("app_mode_" + pkg);
                else edit.putString("app_mode_" + pkg, mode);
                edit.apply();
                for (int j = 0; j < choices.length; j++) choices[j].setChecked(j == selectedIndex);
                if (FloatService.instance != null) FloatService.instance.foregroundChanged(AssistService.foregroundPackage);
                updateConfiguredCount();
                adapter.notifyDataSetChanged();
            });
            if (i > 0) Ui.gap(body, 8);
            body.addView(option, new LinearLayout.LayoutParams(-1, -2));
        }

        Ui.gap(body, 14);
        boolean globalSkip = Prefs.of(this).getBoolean("ad_skip_enabled", false);
        LinearLayout row = Ui.row(this);
        Ui.pad(row, 14, 10, 10, 10);
        row.setBackground(strokedSurface(Ui.BG, 16, Ui.LINE, .8f));
        LinearLayout adText = Ui.column(this);
        adText.addView(Ui.text(this, "跳过开屏广告", 14.5f, Ui.INK, true));
        Ui.gap(adText, 2);
        adText.addView(Ui.text(this, globalSkip ? "进入此应用时自动识别跳过" : "需先开启页面顶部总开关", 11.5f, Ui.MUTED, false));
        row.addView(adText, new LinearLayout.LayoutParams(0, -2, 1));
        LeafSwitch skip = new LeafSwitch(this);
        skip.setContentDescription(label + "跳过开屏广告");
        skip.setChecked(Prefs.of(this).getBoolean("app_skip_" + pkg, true));
        skip.setOnCheckedChangeListener((v, on) -> {
            Prefs.of(this).edit().putBoolean("app_skip_" + pkg, on).apply();
            if (AssistService.instance != null) AssistService.instance.adSkipSettingsChanged();
            updateConfiguredCount();
            adapter.notifyDataSetChanged();
        });
        row.addView(skip, new LinearLayout.LayoutParams(dp(44), dp(40)));
        row.setOnClickListener(v -> skip.toggle());
        if (!globalSkip) row.setAlpha(.76f);
        body.addView(row);

        Ui.gap(body, 18);
        body.addView(Ui.button(this, "完成", true, sheet::dismiss), new LinearLayout.LayoutParams(-1, dp(48)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        sheet.setContentView(scroll);
        sheet.setCanceledOnTouchOutside(true);
        sheet.setOnDismissListener(d -> { if (ruleSheet == sheet) ruleSheet = null; });

        Window w = sheet.getWindow();
        if (w != null) {
            DisplayMetrics screen = new DisplayMetrics();
            w.getWindowManager().getDefaultDisplay().getRealMetrics(screen);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(Ui.dark ? .62f : .44f);
            w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            w.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
            int width = Math.min(screen.widthPixels, dp(560));
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            View decor = w.getDecorView();
            decor.setPadding(0, 0, 0, 0);
            body.setOnApplyWindowInsetsListener((view, insets) -> {
                int navBottom = Build.VERSION.SDK_INT >= 30
                        ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                        : insets.getSystemWindowInsetBottom();
                view.setPadding(dp(20), dp(10), dp(20), dp(20) + navBottom);
                return insets;
            });
            body.requestApplyInsets();
        }
        sheet.show();
    }

    private void dismissRuleSheet() {
        if (ruleSheet != null) {
            try { ruleSheet.dismiss(); } catch (Exception ignored) {}
            ruleSheet = null;
        }
    }

    private Drawable loadIcon(AppCatalog.Entry entry) {
        Drawable cached = iconCache.get(entry.pkg);
        if (cached != null) return cached;
        Drawable loaded = entry.info.loadIcon(getPackageManager());
        iconCache.put(entry.pkg, loaded);
        return loaded;
    }

    private void loadApps() {
        new Thread(() -> {
            ArrayList<Item> loaded = new ArrayList<>();
            android.icu.text.AlphabeticIndex.ImmutableIndex<String> index =
                    new android.icu.text.AlphabeticIndex<String>(Locale.CHINA).addLabels(Locale.ENGLISH).buildImmutableIndex();
            java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            for (AppCatalog.Entry entry : AppCatalog.all(this)) {
                String group;
                char first = entry.label.isEmpty() ? '#' : entry.label.charAt(0);
                if (Character.isDigit(first)) group = String.valueOf(first);
                else {
                    group = index.getBucket(index.getBucketIndex(entry.label)).getLabel();
                    if (group.length() != 1 || !Character.isLetter(group.charAt(0))) group = "#";
                }
                String size = "—", installed = "";
                try {
                    ApplicationInfo info = getPackageManager().getApplicationInfo(entry.pkg, 0);
                    long bytes = new File(info.sourceDir).length();
                    if (info.splitSourceDirs != null) for (String split : info.splitSourceDirs) bytes += new File(split).length();
                    size = String.format(Locale.US, "%.2f MB", bytes / 1048576d);
                    installed = date.format(new Date(getPackageManager().getPackageInfo(entry.pkg, 0).firstInstallTime));
                } catch (Exception ignored) {}
                loaded.add(new Item(entry, group, size, installed));
            }
            loaded.sort((a, b) -> {
                int g = a.group.compareTo(b.group);
                return g != 0 ? g : java.text.Collator.getInstance(Locale.CHINA).compare(a.entry.label, b.entry.label);
            });
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    items.addAll(loaded);
                    updateConfiguredCount();
                    populate();
                }
            });
        }, "Kongye-AppList").start();
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private GradientDrawable strokedSurface(int fill, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = Ui.bg(fill, radiusDp, this);
        d.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return d;
    }

    private RippleDrawable cardRipple(int fill, float radiusDp, int strokeColor, float strokeDp) {
        return new RippleDrawable(
                ColorStateList.valueOf(Ui.dark ? 0x26FFFFFF : 0x1623533D),
                strokedSurface(fill, radiusDp, strokeColor, strokeDp),
                Ui.bg(Color.WHITE, radiusDp, this)
        );
    }

    private int dp(float value) { return Ui.dp(this, value); }

    private void populate() {
        String key = search.getText().toString().toLowerCase(Locale.ROOT);
        rows.clear();
        counts.clear();
        ArrayList<Item> filtered = new ArrayList<>();
        for (Item item : items) {
            if (item.entry.label.toLowerCase(Locale.ROOT).contains(key) || item.entry.pkg.toLowerCase(Locale.ROOT).contains(key)) {
                filtered.add(item);
                counts.merge(item.group, 1, Integer::sum);
            }
        }
        String group = null;
        for (Item item : filtered) {
            if (!item.group.equals(group)) { group = item.group; rows.add(group); }
            if (!collapsed.contains(group)) rows.add(item);
        }
        adapter.notifyDataSetChanged();
    }
}
