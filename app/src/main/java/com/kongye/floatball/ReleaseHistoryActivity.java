package com.kongye.floatball;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.LinearInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Local-first, paginated official-release browser. Syncs from GitHub only when uncached or manually refreshed. */
public final class ReleaseHistoryActivity extends Activity {
    /** No extras, network tokens, or downloaded executables are accepted by this entry point. */
    public static Intent intent(Context context) { return new Intent(context, ReleaseHistoryActivity.class); }
    private static final String FROM_UPDATE = "from_update";
    static Intent fromUpdate(Context context) { return intent(context).putExtra(FROM_UPDATE, true); }

    private static final int PAGE_SIZE = ReleaseHistory.PAGE_SIZE;

    private static final class Model {
        List<ReleaseHistory.Release> catalog = Collections.emptyList();
        List<ReleaseHistory.Release> searchMatches = Collections.emptyList();
        final Map<String, String> defaultExcerpts = new HashMap<>();
        boolean catalogLoaded, catalogComplete;
        int page = 1, position, top;
        ReleaseHistory.Release selected;
        long retryAt;
        String error = "";
        String query = "", draft = "";
        int searchPage = 1, searchPosition, searchTop, detailTop;
        boolean resumeSync;
    }

    private Model model;
    private String installedVersion;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ReleaseHistory.Request request;
    private Future<?> task;
    private LinearLayout statusBanner, footer;
    private FrameLayout stateWrap, refreshTool;
    private View refreshIcon;
    private ObjectAnimator refreshAnimator;
    private boolean refreshSettling;
    private float spinAngle;
    private ListView list;
    private ScrollView detail;
    private boolean loading, initialDiskLoad, stopped, animateTransition;
    private int generation;
    private android.window.OnBackInvokedCallback backCallback;
    private final Runnable countdown = new Runnable() {
        @Override public void run() { if (!stopped && !isFinishing() && !isDestroyed()) syncFeedChrome(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThemePolicy.apply(this); setTheme(Ui.dark ? R.style.AppMotionThemeDark : R.style.AppMotionTheme);
        if(Build.VERSION.SDK_INT>=34&&getIntent().getBooleanExtra(FROM_UPDATE,false)){
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,R.anim.history_page_enter_left,R.anim.history_page_exit_right);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::back;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        Object retained = getLastNonConfigurationInstance();
        model = retained instanceof Model ? (Model) retained : new Model();
        if (!model.catalogLoaded) {
            ReleaseHistoryCache.CatalogSnapshot mem = ReleaseHistoryCache.peekMemory();
            if (mem != null) {
                model.catalog = mem.releases;
                model.catalogLoaded = true;
                model.catalogComplete = mem.complete;
            }
        }
        if (retained == null && state != null && state.containsKey("tag")) {
            model.selected = new ReleaseHistory.Release(state.getString("tag"), state.getString("title"), state.getString("published"), state.getString("notes"));
        }
        if (state != null) {
            model.retryAt = Math.max(model.retryAt, state.getLong("retry_at", 0));
            if (retained == null) {
                model.query = state.getString("query", ""); model.draft = state.getString("draft", model.query);
                model.searchPage = state.getInt("search_page", 1);
                model.searchPosition = state.getInt("search_position", 0); model.searchTop = state.getInt("search_top", 0);
                model.page = Math.max(1, state.getInt("feed_page", 1));
                model.position = state.getInt("feed_position", 0); model.top = state.getInt("feed_top", 0);
                model.detailTop = state.getInt("detail_top", 0);
                applyLocalSearch();
            }
        }
        render();
    }

    @Override public Object onRetainNonConfigurationInstance() { rememberPosition(); return model; }

    @Override protected void onSaveInstanceState(Bundle state) {
        rememberPosition(); super.onSaveInstanceState(state); state.putLong("retry_at", model.retryAt);
        state.putString("query", model.query); state.putString("draft", model.draft);
        state.putInt("search_page", model.searchPage); state.putInt("search_position", model.searchPosition); state.putInt("search_top", model.searchTop);
        state.putInt("feed_page", model.page); state.putInt("feed_position", model.position); state.putInt("feed_top", model.top);
        state.putInt("detail_top", model.detailTop);
        if (model.selected != null) {
            state.putString("tag", model.selected.tag); state.putString("title", model.selected.title);
            state.putString("published", model.selected.published); state.putString("notes", model.selected.notes);
        }
    }

    private int dp(float value) { return Ui.dp(this, value); }

    private String installedVersion() {
        if (installedVersion == null) {
            try { installedVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (Exception ignored) { installedVersion = "0"; }
        }
        return installedVersion;
    }

    private int compareInstalled(String tag) {
        try { return ReleaseVersion.compare(tag, installedVersion()); }
        catch (IllegalArgumentException invalid) { return Integer.MIN_VALUE; }
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

    private TextView versionPill(String version, boolean onHero) {
        TextView pill = Ui.text(this, version, 12, Ui.GREEN, true);
        pill.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        pill.setSingleLine(); pill.setEllipsize(TextUtils.TruncateAt.END); pill.setMaxWidth(dp(168));
        Ui.pad(pill, 9, 3, 9, 3);
        if (onHero) {
            pill.setBackground(strokedSurface(Ui.SURFACE, 8, Ui.LINE, .8f));
        } else {
            pill.setBackground(Ui.bg(Ui.PALE, 8, this));
        }
        return pill;
    }

    private TextView statusBadge(String label, boolean amber, boolean accent) {
        int fg = amber ? (Ui.dark ? 0xFFF2CE72 : 0xFF965515)
                : accent ? Ui.GREEN : Ui.MUTED;
        int bg = amber ? (Ui.dark ? 0xFF3A311C : 0xFFFFF2C6)
                : accent ? (Ui.dark ? 0xFF264033 : 0xFFDFF0E5)
                : (Ui.dark ? 0xFF24312B : 0xFFEDF3EE);
        TextView chip = Ui.text(this, label, 10.5f, fg, true);
        chip.setSingleLine();
        Ui.pad(chip, 7, 3, 7, 3);
        chip.setBackground(Ui.bg(bg, 7, this));
        return chip;
    }

    private TextView inlineActionPill(String label, boolean primary, Runnable action) {
        int fg = primary ? (Ui.dark ? 0xFF102E21 : Color.WHITE) : Ui.GREEN;
        int bg = primary ? Ui.GREEN : (Ui.dark ? 0xFF253A2F : 0xFFDDEBE1);
        TextView btn = Ui.centeredText(this, label, 12, fg, true);
        btn.setSingleLine();
        Ui.pad(btn, 11, 5, 11, 5);
        btn.setBackground(Ui.ripple(this, bg, 10));
        btn.setFocusable(true);
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private TextView pagerButton(String label, boolean enabled, Runnable action) {
        TextView button = Ui.centeredText(this, label, 13, enabled ? Ui.GREEN : Ui.MUTED, true);
        Ui.pad(button, 16, 0, 16, 0);
        button.setMinHeight(dp(40));
        if (enabled) {
            button.setBackground(cardRipple(Ui.SURFACE, 13, Ui.LINE, .8f));
            button.setFocusable(true);
            button.setOnClickListener(view -> action.run());
        } else {
            button.setBackground(strokedSurface(Ui.dark ? 0xFF151C19 : 0xFFEEF2EE, 13, Ui.LINE, .6f));
            button.setAlpha(.5f);
            button.setEnabled(false);
        }
        return button;
    }

    private CharSequence highlightMatch(String value, String rawQuery, int highlightFg) {
        if (value == null || value.isEmpty() || rawQuery == null || rawQuery.trim().isEmpty()) return value == null ? "" : value;
        String needle = rawQuery.trim().toLowerCase(Locale.ROOT);
        String hay = value.toLowerCase(Locale.ROOT);
        int at = hay.indexOf(needle);
        if (at < 0) return value;
        SpannableString span = new SpannableString(value);
        int bg = Ui.dark ? 0xFF3B331D : 0xFFFFF0BF;
        while (at >= 0 && at + needle.length() <= value.length()) {
            int end = at + needle.length();
            span.setSpan(new BackgroundColorSpan(bg), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(highlightFg), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            at = hay.indexOf(needle, end);
        }
        return span;
    }

    private String cachedExcerpt(ReleaseHistory.Release release) {
        if (searching()) return ReleaseSummary.excerpt(release, model.query);
        String cached = model.defaultExcerpts.get(release.tag);
        if (cached == null) {
            cached = ReleaseSummary.excerpt(release, "");
            if (model.defaultExcerpts.size() > 128) model.defaultExcerpts.clear();
            model.defaultExcerpts.put(release.tag, cached);
        }
        return cached;
    }

    private void openReleasePage(String tag) {
        String url = ReleaseSummary.releaseUrl(tag);
        if (url == null) return;
        openWeb(url);
    }

    private void openWeb(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Feedback.show(this, "没有可用的浏览器");
        }
    }

    private boolean searching() { return !model.query.isEmpty(); }

    private int feedPageCount() { return Math.max(1, (model.catalog.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private int searchPageCount() { return Math.max(1, (model.searchMatches.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private static List<ReleaseHistory.Release> slicePage(List<ReleaseHistory.Release> source, int pageNumber) {
        if (source.isEmpty()) return Collections.emptyList();
        int totalPages = Math.max(1, (source.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clamped = Math.max(1, Math.min(pageNumber, totalPages));
        int start = (clamped - 1) * PAGE_SIZE;
        return source.subList(start, Math.min(start + PAGE_SIZE, source.size()));
    }

    private List<ReleaseHistory.Release> visibleReleases() {
        return searching() ? slicePage(model.searchMatches, model.searchPage) : slicePage(model.catalog, model.page);
    }

    private void applyLocalSearch() {
        String q = model.query == null ? "" : model.query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            model.searchMatches = Collections.emptyList();
            return;
        }
        ArrayList<ReleaseHistory.Release> hits = new ArrayList<>();
        for (ReleaseHistory.Release r : model.catalog) {
            if (r.tag.toLowerCase(Locale.ROOT).contains(q)
                    || r.title.toLowerCase(Locale.ROOT).contains(q)
                    || r.notes.toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(r);
            }
        }
        model.searchMatches = Collections.unmodifiableList(hits);
        model.searchPage = Math.max(1, Math.min(model.searchPage, searchPageCount()));
    }

    private void rememberPosition() {
        if (detail != null && model.selected != null) model.detailTop = detail.getScrollY();
        if (list != null && model.selected == null && list.getVisibility() == View.VISIBLE) {
            View first = list.getChildAt(0); int position = list.getFirstVisiblePosition(), top = first == null ? 0 : first.getTop();
            if (searching()) { model.searchPosition = position; model.searchTop = top; }
            else { model.position = position; model.top = top; }
        }
    }

    private void back() {
        if (model.selected == null) finish();
        else { model.selected = null; animateTransition = true; render(); }
    }

    @Override public void finish(){
        super.finish();
        if(getIntent().getBooleanExtra(FROM_UPDATE,false)){
            overridePendingTransition(R.anim.history_page_enter_left,R.anim.history_page_exit_right);
        }
    }

    // API 33+ uses the registered native callback; this override supports older devices.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { back(); }

    private void syncRefreshSpin() {
        if (refreshTool == null) {
            stopRefreshSpin();
            return;
        }
        if (refreshIcon == null || refreshIcon.getParent() != refreshTool) {
            refreshIcon = new Ui.Icon(this, "refresh", Ui.GREEN);
            refreshIcon.setRotation(spinAngle);
            refreshTool.addView(refreshIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        }
        if (stopped || !ValueAnimator.areAnimatorsEnabled()) {
            stopRefreshSpin();
            return;
        }
        if (loading) {
            if (refreshAnimator != null && !refreshSettling) return;
            cancelRefreshAnimation();
            refreshAnimator = ObjectAnimator.ofFloat(refreshIcon, View.ROTATION, spinAngle, spinAngle + 360f);
            refreshAnimator.setDuration(720);
            refreshAnimator.setInterpolator(new LinearInterpolator());
            refreshAnimator.setRepeatCount(ValueAnimator.INFINITE);
            refreshAnimator.start();
            return;
        }
        if (refreshSettling || refreshAnimator == null && spinAngle == 0f) return;
        cancelRefreshAnimation();
        float remaining = 360f - spinAngle;
        if (remaining < 45f) remaining += 360f;
        refreshSettling = true;
        ObjectAnimator settle = ObjectAnimator.ofFloat(refreshIcon, View.ROTATION, spinAngle, spinAngle + remaining);
        refreshAnimator = settle;
        settle.setDuration(Math.round(remaining * 4));
        settle.setInterpolator(new DecelerateInterpolator());
        settle.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (refreshAnimator != animation) return;
                refreshAnimator = null;
                refreshSettling = false;
                spinAngle = 0f;
                refreshIcon.setRotation(0f);
            }
        });
        settle.start();
    }

    private void cancelRefreshAnimation() {
        if (refreshIcon != null) spinAngle = (refreshIcon.getRotation() % 360f + 360f) % 360f;
        if (refreshAnimator != null) {
            ObjectAnimator old = refreshAnimator;
            refreshAnimator = null;
            old.cancel();
        }
        refreshSettling = false;
    }

    private void stopRefreshSpin() {
        cancelRefreshAnimation();
        spinAngle = 0f;
        if (refreshIcon != null) refreshIcon.setRotation(0f);
    }

    private void renderSearch(LinearLayout content) {
        LinearLayout capsule = Ui.row(this);
        capsule.setMinimumHeight(dp(48));
        Ui.pad(capsule, 14, 4, 6, 4);
        capsule.setBackground(strokedSurface(Ui.SURFACE, 18, searching() ? Ui.GREEN : Ui.LINE, searching() ? 1.1f : .9f));

        FrameLayout searchIconHolder = new FrameLayout(this);
        searchIconHolder.addView(Ui.icon(this, "search", searching() ? Ui.GREEN : Ui.MUTED, 18), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        capsule.addView(searchIconHolder, new LinearLayout.LayoutParams(dp(22), dp(22)));

        EditText input = new EditText(this);
        input.setHint("搜索版本、标题或更新说明");
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);
        input.setTextSize(14);
        input.setSingleLine();
        input.setBackground(null);
        Ui.pad(input, 10, 0, 6, 0);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setContentDescription("搜索全部更新历史：版本、标题、更新说明");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setText(model.draft);
        input.setMinHeight(dp(40));
        input.setVerticalScrollBarEnabled(false);
        input.setHorizontalScrollBarEnabled(false);
        capsule.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout clearBtn = new FrameLayout(this);
        clearBtn.setContentDescription("清空");
        clearBtn.setFocusable(true);
        clearBtn.setBackground(Ui.ripple(this, 0, 16));
        clearBtn.addView(Ui.icon(this, "close", Ui.MUTED, 16), new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        clearBtn.setVisibility(!model.draft.isEmpty() || searching() ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams clearAt = new LinearLayout.LayoutParams(dp(32), dp(32));
        clearAt.rightMargin = dp(4);
        capsule.addView(clearBtn, clearAt);

        Runnable submit = () -> {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            startSearch(input.getText().toString());
        };
        clearBtn.setOnClickListener(v -> {
            input.setText("");
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            clearSearch();
        });

        input.setOnFocusChangeListener((v, hasFocus) -> {
            boolean emphasis = hasFocus || searching();
            capsule.setBackground(strokedSurface(Ui.SURFACE, 18, emphasis ? Ui.GREEN : Ui.LINE, emphasis ? 1.2f : .9f));
            searchIconHolder.removeAllViews();
            searchIconHolder.addView(Ui.icon(this, "search", emphasis ? Ui.GREEN : Ui.MUTED, 18), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        });
        input.setOnEditorActionListener((view, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { submit.run(); return true; } return false; });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                model.draft = text.toString();
                clearBtn.setVisibility(!model.draft.isEmpty() || searching() ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable text) { }
        });

        content.addView(capsule);
    }

    private void startSearch(String text) {
        String query = text.trim();
        if (query.isEmpty()) { clearSearch(); return; }
        rememberPosition();
        model.query = query;
        model.draft = text;
        model.searchPage = 1;
        model.searchPosition = model.searchTop = 0;
        applyLocalSearch();
        render();
        if (!model.catalogComplete && !loading) refreshCatalog(false);
    }

    private void clearSearch() {
        boolean changed = searching() || !model.draft.isEmpty();
        model.query = model.draft = "";
        model.searchMatches = Collections.emptyList();
        if (changed || list == null) render();
    }

    private void feedPage(int next) {
        int clamped = Math.max(1, Math.min(next, feedPageCount()));
        if (clamped == model.page) return;
        model.page = clamped;
        model.position = model.top = 0;
        if (list != null) {
            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
            list.setSelectionFromTop(0, 0);
            syncFeedChrome();
        } else {
            render();
        }
    }

    private void searchPage(int next) {
        int clamped = Math.max(1, Math.min(next, searchPageCount()));
        if (clamped == model.searchPage) return;
        model.searchPage = clamped;
        model.searchPosition = model.searchTop = 0;
        if (list != null) {
            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
            list.setSelectionFromTop(0, 0);
            syncFeedChrome();
        } else {
            render();
        }
    }

    private void render() {
        if (isFinishing() || isDestroyed()) return;
        ThemePolicy.apply(this);
        boolean playTransition = animateTransition;
        animateTransition = false;

        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Ui.BG);
        LinearLayout content = Ui.column(this); Ui.pad(content, 16, 10, 16, 14);

        LinearLayout header = Ui.row(this);
        header.setMinimumHeight(dp(48));
        header.addView(Ui.tool(this, "back", model.selected == null ? "返回设置" : "返回更新历史", this::back), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout titleRow = Ui.row(this);
        Ui.pad(titleRow, 6, 0, 6, 0);
        TextView title = Ui.text(this, model.selected == null ? "更新历史" : "版本详情", 20, Ui.INK, true);
        titleRow.addView(title);
        header.addView(titleRow, new LinearLayout.LayoutParams(0, -2, 1));

        cancelRefreshAnimation();
        refreshTool = null;
        refreshIcon = null;
        if (model.selected == null) {
            header.addView(Ui.tool(this, "github", "GitHub", () -> openWeb(GitHubUpdate.PROJECT)), new LinearLayout.LayoutParams(dp(42), dp(42)));
            FrameLayout btn = new FrameLayout(this);
            btn.setContentDescription("刷新更新历史");
            btn.setFocusable(true);
            btn.setBackground(Ui.ripple(this, 0, 20));
            btn.setMinimumWidth(dp(42));
            btn.setMinimumHeight(dp(42));
            btn.setOnClickListener(v -> refreshCatalog(true));
            refreshTool = btn;
            header.addView(btn, new LinearLayout.LayoutParams(dp(42), dp(42)));
            syncRefreshSpin();
        } else if (ReleaseSummary.releaseUrl(model.selected.tag) != null) {
            ReleaseHistory.Release current = model.selected;
            header.addView(Ui.tool(this, "external", "在浏览器打开此版本发布页", () -> openReleasePage(current.tag)), new LinearLayout.LayoutParams(dp(42), dp(42)));
        }
        content.addView(header);
        Ui.gap(content, 8);

        list = null; statusBanner = null; stateWrap = null; footer = null; detail = null;

        if (model.selected != null) {
            ReleaseHistory.Release release = model.selected;
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(false);
            scroll.setClipToPadding(false);
            Ui.pad(scroll, 0, 2, 0, 12);
            detail = scroll;
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setHorizontalScrollBarEnabled(false);

            LinearLayout card = Ui.column(this);
            card.setBackground(strokedSurface(Ui.SURFACE, 22, Ui.LINE, .9f));
            card.setClipToOutline(true);

            // Botanical Specimen Hero Band
            HeroHeaderBand hero = new HeroHeaderBand(this);
            LinearLayout heroContent = Ui.column(this);
            Ui.pad(heroContent, 20, 18, 20, 18);

            LinearLayout meta = Ui.row(this);
            meta.addView(versionPill(release.tag, true));
            int cmp = compareInstalled(release.tag);
            if (cmp == 0) {
                LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2); at.leftMargin = dp(7);
                meta.addView(statusBadge("当前安装", false, true), at);
            } else if (cmp > 0) {
                LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2); at.leftMargin = dp(7);
                meta.addView(statusBadge("新版本", true, false), at);
            }
            TextView published = Ui.text(this, release.date(), 12, Ui.MUTED, false);
            published.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            published.setGravity(Gravity.END);
            meta.addView(published, new LinearLayout.LayoutParams(0, -2, 1));
            heroContent.addView(meta);
            Ui.gap(heroContent, 12);

            TextView heading = Ui.text(this, ReleaseSummary.headline(release), 20, Ui.INK, true);
            heading.setLineSpacing(dp(3.5f), 1);
            if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
            heroContent.addView(heading);
            hero.addView(heroContent, new FrameLayout.LayoutParams(-1, -2));
            card.addView(hero, new LinearLayout.LayoutParams(-1, -2));

            View sep = new View(this); sep.setBackgroundColor(Ui.LINE);
            card.addView(sep, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.8f))));

            LinearLayout article = Ui.column(this);
            Ui.pad(article, 20, 18, 20, 24);
            article.addView(ReleaseMarkdownView.create(this, ReleaseSummary.detailNotes(release), 14.5f, Ui.INK), new LinearLayout.LayoutParams(-1, -2));
            card.addView(article, new LinearLayout.LayoutParams(-1, -2));

            scroll.addView(card, new FrameLayout.LayoutParams(-1, -2));
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            scroll.post(() -> scroll.scrollTo(0, model.detailTop));
            if (playTransition) settleView(scroll);
        } else {
            renderSearch(content);
            statusBanner = Ui.column(this);
            content.addView(statusBanner, new LinearLayout.LayoutParams(-1, -2));
            Ui.gap(content, 8);

            FrameLayout viewport = new FrameLayout(this);
            list = new ListView(this);
            list.setBackgroundColor(Color.TRANSPARENT);
            list.setVerticalScrollBarEnabled(false);
            list.setHorizontalScrollBarEnabled(false);
            list.setDivider(new ColorDrawable(Color.TRANSPARENT));
            list.setDividerHeight(dp(10));
            list.setHeaderDividersEnabled(false);
            list.setFooterDividersEnabled(false);
            list.setSelector(new ColorDrawable(Color.TRANSPARENT));
            list.setClipToPadding(false);
            Ui.pad(list, 0, 2, 0, 10);
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return visibleReleases().size(); }
                @Override public Object getItem(int position) { return visibleReleases().get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    return buildReleaseCard(visibleReleases().get(position), position);
                }
            });
            list.setOnItemClickListener((parent, view, position, id) -> {
                List<ReleaseHistory.Release> items = visibleReleases();
                if (position < 0 || position >= items.size()) return;
                rememberPosition();
                model.selected = items.get(position);
                model.detailTop = 0;
                animateTransition = true;
                render();
            });
            viewport.addView(list, new FrameLayout.LayoutParams(-1, -1));

            stateWrap = new FrameLayout(this);
            viewport.addView(stateWrap, new FrameLayout.LayoutParams(-1, -1));
            content.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));

            list.setSelectionFromTop(searching() ? model.searchPosition : model.position, searching() ? model.searchTop : model.top);
            footer = Ui.column(this);
            content.addView(footer, new LinearLayout.LayoutParams(-1, -2));
            syncFeedChrome();
            if (playTransition) settleView(viewport);
        }

        root.addView(content, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(Ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) { android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); }
            else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        }); root.requestApplyInsets();
    }

    private void settleView(View target) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        target.setAlpha(0f);
        target.setTranslationY(dp(8));
        target.animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new PathInterpolator(.22f, 1f, .36f, 1f)).start();
    }

    private View buildReleaseCard(ReleaseHistory.Release release, int position) {
        int cmp = compareInstalled(release.tag);
        boolean isLatestFeedTop = !searching() && model.page == 1 && position == 0;
        boolean emphasized = cmp == 0 || isLatestFeedTop;
        int borderColor = emphasized ? (Ui.dark ? 0xFF355645 : 0xFFC5DDD0) : Ui.LINE;

        LinearLayout card = Ui.column(this);
        Ui.pad(card, 16, 14, 14, 14);
        card.setBackground(cardRipple(Ui.SURFACE, 18, borderColor, emphasized ? 1f : .8f));
        card.setClipToOutline(true);

        LinearLayout meta = Ui.row(this);
        meta.addView(versionPill(release.tag, false));
        if (cmp == 0) {
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2); at.leftMargin = dp(6);
            meta.addView(statusBadge("当前安装", false, true), at);
        } else if (isLatestFeedTop && cmp > 0) {
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2); at.leftMargin = dp(6);
            meta.addView(statusBadge("新版本", true, false), at);
        } else if (isLatestFeedTop) {
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2); at.leftMargin = dp(6);
            meta.addView(statusBadge("最新正式版", false, false), at);
        }

        TextView published = Ui.text(this, release.date(), 11.5f, Ui.MUTED, false);
        published.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        published.setGravity(Gravity.END);
        Ui.pad(published, 8, 0, 0, 0);
        meta.addView(published, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams arrowAt = new LinearLayout.LayoutParams(dp(15), dp(15));
        arrowAt.leftMargin = dp(5);
        meta.addView(Ui.icon(this, "forward", Ui.MUTED, 15), arrowAt);
        card.addView(meta);

        Ui.gap(card, 8);
        String headline = ReleaseSummary.headline(release);
        int matchInk = Ui.dark ? 0xFFF5D87E : 0xFF6E4708;
        TextView titleView = Ui.text(this, "", 15.5f, Ui.INK, true);
        titleView.setText(searching() ? highlightMatch(headline, model.query, matchInk) : headline);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setLineSpacing(dp(2), 1);
        card.addView(titleView);

        String excerpt = cachedExcerpt(release);
        if (!excerpt.isEmpty()) {
            Ui.gap(card, 5);
            TextView summaryView = Ui.text(this, "", 13, Ui.MUTED, false);
            summaryView.setText(searching() ? highlightMatch(excerpt, model.query, matchInk) : excerpt);
            summaryView.setMaxLines(2);
            summaryView.setEllipsize(TextUtils.TruncateAt.END);
            summaryView.setLineSpacing(dp(2.5f), 1);
            card.addView(summaryView);
        }
        return card;
    }

    private void syncFeedChrome() {
        main.removeCallbacks(countdown);
        syncRefreshSpin();
        if (footer == null || list == null || stateWrap == null || statusBanner == null) return;
        long now = System.currentTimeMillis();
        boolean limited = model.retryAt > now;

        List<ReleaseHistory.Release> items = visibleReleases();
        boolean hasItems = !items.isEmpty();
        list.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        stateWrap.setVisibility(hasItems ? View.GONE : View.VISIBLE);

        statusBanner.removeAllViews();
        stateWrap.removeAllViews();
        footer.removeAllViews();

        if (searching()) {
            renderSearchChrome(hasItems);
        } else {
            renderFeedStateAndFooter(limited, hasItems);
        }

        if (limited && !stopped && model.catalog.isEmpty()) {
            main.postDelayed(countdown, Math.min(1000, Math.max(1, model.retryAt - now)));
        }
    }

    private void renderSearchChrome(boolean hasItems) {
        LinearLayout bar = Ui.row(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, -2);
        barLp.topMargin = dp(8);
        Ui.pad(bar, 12, 9, 10, 9);
        bar.setBackground(strokedSurface(Ui.dark ? 0xFF192620 : 0xFFEAF2EC, 14, Ui.dark ? 0xFF2B3F33 : 0xFFD5E4D9, .8f));

        View dot = new View(this);
        dot.setBackground(Ui.bg(Ui.GREEN, 4, this));
        bar.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));

        String primaryStatus = "共检索 " + model.catalog.size() + " 个版本 · 找到 " + model.searchMatches.size() + " 条";
        TextView summary = Ui.text(this, primaryStatus, 12.5f, Ui.INK, false);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Ui.pad(summary, 8, 0, 8, 0);
        bar.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(inlineActionPill("退出搜索", false, this::clearSearch));
        statusBanner.addView(bar, barLp);

        if (!hasItems) {
            if (loading && model.catalog.isEmpty()) {
                stateWrap.addView(buildStateCard(
                        null, true,
                        "正在获取历史版本…",
                        "首次同步全部正式版本记录，完成后即可本地检索。",
                        null, false, false, null
                ), stateLayoutParams());
            } else {
                stateWrap.addView(buildStateCard(
                        "search", false,
                        "未找到匹配的版本",
                        "在 " + model.catalog.size() + " 个正式版本中没有包含「" + model.query + "」的记录。",
                        "显示全部版本", false, true, this::clearSearch
                ), stateLayoutParams());
            }
        } else {
            int total = searchPageCount();
            renderPagerRow(
                    model.searchPage > 1, () -> searchPage(model.searchPage - 1),
                    "第 " + model.searchPage + " / " + total + " 页",
                    model.searchPage < total, () -> searchPage(model.searchPage + 1)
            );
        }
    }

    private void renderFeedStateAndFooter(boolean limited, boolean hasItems) {
        if (!hasItems) {
            if (loading || initialDiskLoad) {
                stateWrap.addView(buildStateCard(
                        null, true,
                        "正在获取更新历史…",
                        "首次拉取后将保存在本地，后续打开与翻页无需重新加载。",
                        null, false, false, null
                ), stateLayoutParams());
            } else if (!model.error.isEmpty() || limited) {
                String err = limited ? "GitHub 请求次数受限，可在 " + date(model.retryAt) + " 后重试。" : model.error;
                stateWrap.addView(buildStateCard(
                        "history", false,
                        limited ? "请求次数暂受限制" : "暂时无法加载记录",
                        err,
                        limited ? "请稍后重试" : "重新加载",
                        !limited,
                        !limited,
                        () -> { if (!limited) refreshCatalog(true); }
                ), stateLayoutParams());
            } else if (model.catalogLoaded) {
                stateWrap.addView(buildStateCard(
                        "history", false,
                        "暂无正式发布记录",
                        "点击右上角刷新按钮即可获取最新发布的正式版本。",
                        "立即刷新",
                        true,
                        true,
                        () -> refreshCatalog(true)
                ), stateLayoutParams());
            }
        } else {
            int total = feedPageCount();
            renderPagerRow(
                    model.page > 1, () -> feedPage(model.page - 1),
                    "第 " + model.page + " / " + total + " 页",
                    model.page < total, () -> feedPage(model.page + 1)
            );
        }
    }

    private FrameLayout.LayoutParams stateLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.leftMargin = dp(12);
        lp.rightMargin = dp(12);
        return lp;
    }

    private View buildStateCard(String iconName, boolean spinning, String heading, String detailText,
                                String primaryLabel, boolean primaryFilled, boolean primaryEnabled, Runnable primaryAction) {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(box, 24, 28, 24, 24);
        box.setBackground(strokedSurface(Ui.SURFACE, 22, Ui.LINE, .8f));

        if (spinning) {
            ProgressBar spinner = new ProgressBar(this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
            box.addView(spinner, new LinearLayout.LayoutParams(dp(30), dp(30)));
        } else if (iconName != null) {
            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(Ui.bg(Ui.PALE, 22, this));
            badge.addView(Ui.icon(this, iconName, Ui.GREEN, 22), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            box.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }

        Ui.gap(box, 14);
        TextView titleView = Ui.centeredText(this, heading, 15.5f, Ui.INK, true);
        box.addView(titleView);

        if (detailText != null && !detailText.isEmpty()) {
            Ui.gap(box, 7);
            TextView descView = Ui.centeredText(this, detailText, 13, Ui.MUTED, false);
            descView.setLineSpacing(dp(3), 1);
            descView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            box.addView(descView);
        }

        if (primaryLabel != null && primaryAction != null) {
            Ui.gap(box, 18);
            TextView btn = Ui.button(this, primaryLabel, primaryFilled, primaryAction);
            Ui.pad(btn, 22, 11, 22, 11);
            btn.setEnabled(primaryEnabled);
            btn.setAlpha(primaryEnabled ? 1f : .5f);
            box.addView(btn, new LinearLayout.LayoutParams(-2, -2));
        }
        return box;
    }

    private void renderPagerRow(boolean prevEnabled, Runnable onPrev, String centerLabel, boolean nextEnabled, Runnable onNext) {
        LinearLayout pages = Ui.row(this);
        Ui.pad(pages, 0, 8, 0, 2);
        pages.addView(pagerButton("‹  上一页", prevEnabled, onPrev), new LinearLayout.LayoutParams(0, -2, 1));
        TextView indicator = Ui.centeredText(this, centerLabel, 12.5f, Ui.MUTED, true);
        indicator.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        pages.addView(indicator, new LinearLayout.LayoutParams(dp(108), -2));
        pages.addView(pagerButton("下一页  ›", nextEnabled, onNext), new LinearLayout.LayoutParams(0, -2, 1));
        footer.addView(pages);
    }

    /** Specimen hero wash with a quiet, native botanical leaf watermark in the upper-right corner. */
    private static final class HeroHeaderBand extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path leaf = new Path();
        HeroHeaderBand(Context c) {
            super(c);
            setWillNotDraw(false);
            setBackgroundColor(Ui.dark ? 0xFF202E26 : 0xFFEEF5EC);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Ui.dp(getContext(), 92);
            canvas.save();
            canvas.translate(getWidth() - size * 0.82f, -size * 0.14f);
            canvas.scale(size / 24f, size / 24f);
            paint.setColor(Ui.dark ? 0x1683CCA8 : 0x1223533D);
            paint.setStyle(Paint.Style.FILL);
            leaf.reset();
            leaf.moveTo(5, 16);
            leaf.cubicTo(3, 8, 9, 4, 21, 4);
            leaf.cubicTo(21, 14, 15, 21, 8, 19);
            leaf.close();
            canvas.drawPath(leaf, paint);
            paint.setColor(Ui.dark ? 0x20202E26 : 0x99EEF5EC);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(5, 21, 17, 8, paint);
            canvas.drawLine(11, 14, 10, 10, paint);
            canvas.restore();
        }
    }

    private static String date(long time) { return time <= 0 ? "之前" : new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(time)); }

    private boolean alive(int token) { return token == generation && !stopped && !isFinishing() && !isDestroyed(); }

    /** Reads disk cache once without touching the network unless no complete local catalog exists yet. */
    private void ensureLocalCatalog() {
        if (model.catalogLoaded) {
            if (!model.catalogComplete && !loading) refreshCatalog(false);
            else syncFeedChrome();
            return;
        }
        if (initialDiskLoad || loading) return;
        initialDiskLoad = true;
        syncFeedChrome();
        int token = generation;
        worker.submit(() -> {
            ReleaseHistoryCache.CatalogSnapshot snap = ReleaseHistoryCache.readCatalog(getCacheDir());
            main.post(() -> {
                if (!alive(token)) return;
                initialDiskLoad = false;
                if (snap != null && !snap.releases.isEmpty()) {
                    model.catalog = snap.releases;
                    model.catalogLoaded = true;
                    model.catalogComplete = snap.complete;
                    model.page = Math.max(1, Math.min(model.page, feedPageCount()));
                    applyLocalSearch();
                    if (list != null) ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                    syncFeedChrome();
                    if (!snap.complete) refreshCatalog(false);
                } else {
                    refreshCatalog(false);
                }
            });
        });
    }

    /** Fetches the latest full release catalog from GitHub; spins only the top-right refresh icon. */
    private void refreshCatalog(boolean manual) {
        if (loading) return;
        long now = System.currentTimeMillis();
        if (model.retryAt > now) {
            if (manual) Feedback.show(this, "GitHub 请求受限，可在 " + date(model.retryAt) + " 后重试");
            syncFeedChrome();
            return;
        }
        cancel();
        loading = true;
        model.resumeSync = false;
        model.error = "";
        syncFeedChrome();

        int token = generation;
        ReleaseHistory.Request active = new ReleaseHistory.Request();
        request = active;
        task = worker.submit(() -> {
            ReleaseHistory.Catalog answer = null;
            IOException failure = null;
            try {
                answer = active.loadCatalog();
                ReleaseHistoryCache.writeCatalog(getCacheDir(), answer.releases, answer.complete);
            } catch (IOException error) {
                failure = error;
            } catch (RuntimeException error) {
                failure = new IOException("发布记录暂时无法加载，请重试。", error);
            }
            ReleaseHistory.Catalog result = answer;
            IOException error = failure;
            main.post(() -> {
                if (!alive(token)) return;
                loading = false;
                request = null;
                task = null;
                if (result != null) {
                    model.catalog = result.releases;
                    model.catalogLoaded = true;
                    model.catalogComplete = result.complete;
                    model.defaultExcerpts.clear();
                    model.error = "";
                    model.retryAt = result.retryAt;
                    if (manual && !searching()) {
                        model.page = 1;
                        model.position = model.top = 0;
                    } else {
                        model.page = Math.max(1, Math.min(model.page, feedPageCount()));
                    }
                    applyLocalSearch();
                    if (list != null) {
                        ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                        if (manual && !searching()) list.setSelectionFromTop(0, 0);
                    }
                    syncFeedChrome();
                    if (manual) {
                        if (result.complete) Feedback.success(this, "更新历史已刷新");
                        else Feedback.show(this, "已刷新，部分历史暂未加载完整");
                    }
                } else {
                    String errMsg = error == null || error.getMessage() == null ? "无法连接 GitHub，请检查网络后重试。" : error.getMessage();
                    model.error = errMsg;
                    if (error instanceof ReleaseHistory.RateLimited) model.retryAt = ((ReleaseHistory.RateLimited) error).retryAt;
                    if (manual) {
                        Feedback.show(this, "刷新失败：" + errMsg);
                    }
                    syncFeedChrome();
                }
            });
        });
    }

    private void cancel() {
        generation++;
        if (request != null) { request.cancel(); request = null; }
        if (task != null) { task.cancel(true); task = null; }
        loading = false;
    }

    @Override protected void onStart() {
        super.onStart();
        stopped = false;
        if (model.resumeSync) refreshCatalog(false);
        else ensureLocalCatalog();
    }

    @Override protected void onStop() {
        stopped = true;
        model.resumeSync = loading;
        rememberPosition();
        cancel();
        stopRefreshSpin();
        main.removeCallbacks(countdown);
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        cancel();
        stopRefreshSpin();
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
