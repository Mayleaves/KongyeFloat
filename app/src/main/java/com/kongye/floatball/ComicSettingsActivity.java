package com.kongye.floatball;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public class ComicSettingsActivity extends Activity {
    private static final String[] CODES = {"ja", "en", "ko"}, NAMES = {"日语", "英语", "韩语"};
    private int language;
    private boolean api;
    private EditText endpoint, model, key;
    private LinearLayout apiSection;
    private TextView engineChip, languageFlowChip;
    private TextView[] langTabs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemePolicy.apply(this);
        setTheme(Ui.dark ? R.style.AppThemeDark : R.style.AppTheme);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(Ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        var prefs = Prefs.of(this);
        api = "api".equals(prefs.getString("comic_engine", "offline"));
        language = Math.max(0, java.util.Arrays.asList(CODES).indexOf(prefs.getString("comic_source", "ja")));

        LinearLayout shell = Ui.column(this);
        shell.setBackgroundColor(Ui.BG);

        LinearLayout header = Ui.row(this);
        Ui.pad(header, 12, 8, 18, 8);
        header.setMinimumHeight(dp(52));
        header.addView(Ui.tool(this, "back", "返回", this::finish), new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView heading = Ui.text(this, "漫画翻译", 20, Ui.INK, true);
        Ui.pad(heading, 6, 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout body = Ui.column(this);
        Ui.pad(body, 18, 10, 18, 22);
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Section 1: Translation Engine
        LinearLayout engineHead = sectionHeader("翻译方式");
        engineChip = Ui.text(this, "", 11.5f, Ui.GREEN, true);
        engineChip.setBackground(Ui.bg(Ui.PALE, 9, this));
        Ui.pad(engineChip, 9, 4, 9, 4);
        engineHead.addView(engineChip);
        body.addView(engineHead);
        Ui.gap(body, 8);

        LinearLayout engine = card(body);
        SelectionCard offline = new SelectionCard(this, "离线翻译", "无需 Key · 首次下载模型需 Wi-Fi");
        SelectionCard online = new SelectionCard(this, "API 翻译", "使用你配置的模型翻译");
        engine.addView(offline, new LinearLayout.LayoutParams(-1, -2));
        Ui.gap(engine, 8);
        engine.addView(online, new LinearLayout.LayoutParams(-1, -2));
        offline.setChecked(!api);
        online.setChecked(api);

        // Section 2: Language & Layout
        Ui.gap(body, 18);
        body.addView(sectionHeader("语言与排版"));
        Ui.gap(body, 8);

        LinearLayout langCard = card(body);
        LinearLayout langHead = Ui.row(this);
        langHead.addView(Ui.text(this, "原文语言", 14.5f, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout flowPill = Ui.row(this);
        flowPill.setBackground(Ui.bg(Ui.PALE, 10, this));
        Ui.pad(flowPill, 10, 5, 10, 5);
        languageFlowChip = Ui.text(this, NAMES[language], 12, Ui.GREEN, true);
        flowPill.addView(languageFlowChip);
        View flowArrow = Ui.icon(this, "update_arrow", Ui.GREEN, 13);
        LinearLayout.LayoutParams arrowAt = new LinearLayout.LayoutParams(dp(13), dp(13));
        arrowAt.leftMargin = dp(6);
        arrowAt.rightMargin = dp(6);
        flowPill.addView(flowArrow, arrowAt);
        flowPill.addView(Ui.text(this, "简体中文", 12, Ui.GREEN, true));
        langHead.addView(flowPill);
        langCard.addView(langHead);
        Ui.gap(langCard, 12);

        LinearLayout segBar = Ui.row(this);
        segBar.setBackground(Ui.bg(Ui.BG, 14, this));
        Ui.pad(segBar, 4, 4, 4, 4);
        langTabs = new TextView[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            final int index = i;
            TextView tab = Ui.centeredText(this, NAMES[i], 13.5f, Ui.MUTED, false);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> {
                language = index;
                syncLanguageTabs();
            });
            langTabs[i] = tab;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(4);
            segBar.addView(tab, lp);
        }
        langCard.addView(segBar, new LinearLayout.LayoutParams(-1, -2));
        syncLanguageTabs();

        Ui.gap(langCard, 14);
        View divider = new View(this);
        divider.setBackgroundColor(Ui.LINE);
        langCard.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.6f))));
        Ui.gap(langCard, 12);

        LinearLayout layoutHint = Ui.row(this);
        layoutHint.addView(Ui.icon(this, "check", Ui.GREEN, 15));
        TextView layoutText = Ui.text(this, "保留横排与竖排气泡，按框体自动调整行距", 12, Ui.MUTED, false);
        Ui.pad(layoutText, 8, 0, 0, 0);
        layoutHint.addView(layoutText, new LinearLayout.LayoutParams(0, -2, 1));
        langCard.addView(layoutHint);

        // Section 3: API Settings (conditionally visible)
        apiSection = Ui.column(this);
        Ui.gap(apiSection, 18);
        apiSection.addView(sectionHeader("API 配置"));
        Ui.gap(apiSection, 8);
        LinearLayout apiFields = card(apiSection);
        endpoint = field(apiFields, "接口地址", "https://服务地址/v1");
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        model = field(apiFields, "模型名称", "服务商提供的模型名称");
        key = field(apiFields, "API Key", "输入 API Key");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        try {
            var saved = ComicApiSettings.load(this);
            endpoint.setText(saved.endpoint());
            model.setText(saved.model());
            key.setText(saved.key());
        } catch (java.io.IOException error) {
            Feedback.show(this, error.getMessage());
        }
        TextView apiNote = Ui.text(this, "兼容 Chat Completions · Key 仅加密保存在本机", 12, Ui.MUTED, false);
        apiNote.setLineSpacing(dp(2), 1);
        apiFields.addView(apiNote);
        Ui.gap(apiFields, 14);
        apiFields.addView(Ui.button(this, "清除 API 配置", false, () -> {
            getSharedPreferences("comic_api", MODE_PRIVATE).edit().clear().apply();
            endpoint.setText("");
            model.setText("");
            key.setText("");
            Feedback.show(this, "API 配置已清除");
        }), new LinearLayout.LayoutParams(-1, dp(44)));
        body.addView(apiSection, new LinearLayout.LayoutParams(-1, -2));

        offline.setOnClickListener(v -> {
            api = false;
            offline.setChecked(true);
            online.setChecked(false);
            syncEngineState();
        });
        online.setOnClickListener(v -> {
            api = true;
            offline.setChecked(false);
            online.setChecked(true);
            syncEngineState();
        });
        syncEngineState();

        // Footnote tip card
        Ui.gap(body, 16);
        LinearLayout tip = Ui.row(this);
        tip.setGravity(Gravity.TOP);
        Ui.pad(tip, 14, 12, 14, 12);
        tip.setBackground(strokedSurface(Ui.dark ? 0xFF161E1A : 0xFFEDF3EE, 16, Ui.LINE, .8f));
        View tipIcon = Ui.icon(this, "info", Ui.MUTED, 16);
        LinearLayout.LayoutParams tipIconAt = new LinearLayout.LayoutParams(dp(16), dp(16));
        tipIconAt.topMargin = dp(2);
        tip.addView(tipIcon, tipIconAt);
        TextView note = Ui.text(this, "翻译加载期间悬浮球会显示旋转进度环；复杂背景与拟声词可能保留原文。", 12, Ui.MUTED, false);
        note.setLineSpacing(dp(3), 1);
        Ui.pad(note, 9, 0, 0, 0);
        tip.addView(note, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(tip, new LinearLayout.LayoutParams(-1, -2));

        // Bottom action bar
        View bottomLine = new View(this);
        bottomLine.setBackgroundColor(Ui.LINE);
        shell.addView(bottomLine, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.6f))));
        LinearLayout bottomBar = Ui.column(this);
        Ui.pad(bottomBar, 18, 12, 18, 16);
        bottomBar.setBackgroundColor(Ui.BG);
        bottomBar.addView(Ui.button(this, "保存设置", true, () -> {
            try {
                if (api) {
                    ComicApiSettings.save(this, new ComicApiSettings.Config(
                            endpoint.getText().toString().trim(),
                            model.getText().toString().trim(),
                            key.getText().toString().trim()));
                }
                prefs.edit().putString("comic_source", CODES[language]).putString("comic_engine", api ? "api" : "offline").apply();
                if (FloatService.instance != null) FloatService.instance.stopComics();
                Feedback.show(this, "漫画翻译设置已保存");
                finish();
            } catch (java.io.IOException error) {
                Feedback.show(this, error.getMessage());
            }
        }), new LinearLayout.LayoutParams(-1, dp(50)));
        shell.addView(bottomBar);

        FrameLayout page = Ui.page(this, shell);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right;
                bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        setContentView(page);
        page.requestApplyInsets();
    }

    private void syncEngineState() {
        if (apiSection != null) apiSection.setVisibility(api ? View.VISIBLE : View.GONE);
        if (engineChip != null) engineChip.setText(api ? "API 引擎" : "离线引擎");
    }

    private void syncLanguageTabs() {
        if (languageFlowChip != null) languageFlowChip.setText(NAMES[language]);
        if (langTabs == null) return;
        for (int i = 0; i < langTabs.length; i++) {
            boolean on = i == language;
            TextView tab = langTabs[i];
            tab.setSelected(on);
            tab.setTextColor(on ? Ui.GREEN : Ui.MUTED);
            tab.setTypeface(Ui.textTypeface(on));
            tab.setContentDescription(NAMES[i] + (on ? "，已选择" : ""));
            if (on) {
                tab.setBackground(strokedSurface(Ui.SURFACE, 11, Ui.dark ? 0xFF355645 : 0xFFC5DDD0, .9f));
                tab.setElevation(dp(1.5f));
            } else {
                tab.setBackground(Ui.ripple(this, 0, 11));
                tab.setElevation(0);
            }
        }
    }

    private LinearLayout sectionHeader(String title) {
        LinearLayout row = Ui.row(this);
        Ui.pad(row, 4, 0, 4, 0);
        row.addView(Ui.text(this, title, 13, Ui.MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = Ui.column(this);
        Ui.pad(box, 16, 16, 16, 16);
        box.setBackground(strokedSurface(Ui.SURFACE, 20, Ui.LINE, .85f));
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private EditText field(LinearLayout parent, String title, String hint) {
        parent.addView(Ui.text(this, title, 12.5f, Ui.MUTED, false));
        Ui.gap(parent, 6);
        EditText field = Ui.field(this, hint);
        field.setSingleLine();
        field.setBackground(strokedSurface(Ui.BG, 14, Ui.LINE, .8f));
        field.setOnFocusChangeListener((v, hasFocus) ->
                field.setBackground(strokedSurface(Ui.BG, 14, hasFocus ? Ui.GREEN : Ui.LINE, hasFocus ? 1.2f : .8f)));
        parent.addView(field, new LinearLayout.LayoutParams(-1, dp(46)));
        Ui.gap(parent, 12);
        return field;
    }

    private GradientDrawable strokedSurface(int fill, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = Ui.bg(fill, radiusDp, this);
        d.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return d;
    }

    private int dp(float n) { return Ui.dp(this, n); }
}
