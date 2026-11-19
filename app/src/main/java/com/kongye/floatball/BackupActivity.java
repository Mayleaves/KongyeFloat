package com.kongye.floatball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackupActivity extends Activity {
    private static final int EXPORT = 81, IMPORT = 82, FOLDER = 83;
    private static final int SETTING_ROW_HEIGHT = 52;
    private Work work;
    private boolean exportNotes = true;
    private int section;
    private final int[] visibleBackups = {20, 20};
    private final int[] savedScroll = new int[2];
    private final ScrollView[] pages = new ScrollView[2];
    private final LinearLayout[] contents = new LinearLayout[2];
    private final TextView[] tabs = new TextView[2];
    private ViewPager pager;
    private FrameLayout tabBar;
    private View tabIndicator;
    private float tabPosition;
    private LinearLayout body, remoteHistory;
    private AlertDialog confirmation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        work = getLastNonConfigurationInstance() instanceof Work retained ? retained : new Work(getApplicationContext());
        if (state != null) {
            exportNotes = state.getBoolean("export_notes", true);
            section = Math.max(0, Math.min(1, state.getInt("section", 0)));
            savedScroll[0] = state.getInt("scroll_local", 0);
            savedScroll[1] = state.getInt("scroll_remote", 0);
        }
        render();
    }

    @Override protected void onResume() { super.onResume(); work.observer = this::updated; updated(); }
    @Override protected void onPause() { work.observer = null; super.onPause(); }
    @Override public Object onRetainNonConfigurationInstance() { return work; }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("export_notes", exportNotes); state.putInt("section", section);
        state.putInt("scroll_local", pages[0].getScrollY()); state.putInt("scroll_remote", pages[1].getScrollY());
    }
    @Override protected void onDestroy() {
        if (confirmation != null) confirmation.dismiss();
        super.onDestroy();
    }

    private int dp(float n) { return Ui.dp(this, n); }

    private void render() {
        ThemePolicy.apply(this); setTheme(Ui.dark ? R.style.AppMotionThemeDark : R.style.AppMotionTheme);
        LinearLayout page = Ui.column(this); page.setBackgroundColor(Ui.BG);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout header = Ui.row(this); Ui.pad(header, 12, 10, 20, 10);
        header.addView(Ui.tool(this, "back", "返回", this::finish), new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = Ui.text(this, "备份与恢复", 20, Ui.INK, true); Ui.pad(title, 10, 0, 0, 0); header.addView(title);
        page.addView(header);
        tabBar = new FrameLayout(this); tabBar.setBackground(Ui.bg(Ui.PALE, 16, this)); Ui.pad(tabBar, 4, 4, 4, 4);
        tabIndicator = new View(this); tabIndicator.setBackground(Ui.bg(Ui.SURFACE, 12, this));
        tabBar.addView(tabIndicator, new FrameLayout.LayoutParams(1, dp(36), Gravity.CENTER_VERTICAL));
        LinearLayout labels = Ui.row(this);
        String[] names = {"本地备份", "WebDAV 备份"};
        for (int i = 0; i < 2; i++) {
            final int index = i;
            tabs[i] = Ui.centeredText(this, names[i], 14, Ui.GREEN, true);
            tabs[i].setBackground(Ui.ripple(this, 0, 12)); tabs[i].setFocusable(true);
            tabs[i].setOnClickListener(v -> { if (!work.busy) pager.setCurrentItem(index, true); });
            labels.addView(tabs[i], new LinearLayout.LayoutParams(0, dp(36), 1));
        }
        tabBar.addView(labels, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams tabAt = new LinearLayout.LayoutParams(-1, dp(44));
        tabAt.setMargins(dp(20), dp(4), dp(20), dp(6)); page.addView(tabBar, tabAt);
        tabPosition = section;
        tabBar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> moveIndicator(tabPosition));
        pager = new ViewPager(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent event) { return !work.busy && super.onInterceptTouchEvent(event); }
            @Override public boolean onTouchEvent(MotionEvent event) { return !work.busy && super.onTouchEvent(event); }
        };
        pager.setContentDescription("备份方式，可左右滑动切换"); pager.setOffscreenPageLimit(1);
        for (int i = 0; i < 2; i++) {
            ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
            scroll.setFillViewport(true); scroll.setClipToPadding(false);
            contents[i] = Ui.column(this); Ui.pad(contents[i], 20, 12, 20, 24); scroll.addView(contents[i]); pages[i] = scroll;
        }
        pager.setAdapter(new PagerAdapter() {
            @Override public int getCount() { return 2; }
            @Override public boolean isViewFromObject(View view, Object object) { return view == object; }
            @Override public Object instantiateItem(ViewGroup parent, int position) { parent.addView(pages[position]); return pages[position]; }
            @Override public void destroyItem(ViewGroup parent, int position, Object object) { parent.removeView((View)object); }
        });
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageScrolled(int position, float offset, int pixels) { moveIndicator(position + offset); }
            @Override public void onPageSelected(int position) {
                section = position; selectTabs(); hideKeyboard();
                View focused = getCurrentFocus(); if (focused instanceof EditText) focused.clearFocus();
            }
        });
        pager.setCurrentItem(section, false); selectTabs();
        page.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        page.setFocusableInTouchMode(true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(page); page.requestFocus(); page.requestApplyInsets(); renderBody();
    }

    private void moveIndicator(float position) {
        tabPosition = position;
        int width = (tabBar.getWidth() - tabBar.getPaddingLeft() - tabBar.getPaddingRight()) / 2;
        if (width < 1) return;
        ViewGroup.LayoutParams at = tabIndicator.getLayoutParams();
        if (at.width != width) { at.width = width; tabIndicator.setLayoutParams(at); }
        tabIndicator.setTranslationX(position * width);
    }

    private void selectTabs() {
        for (int i = 0; i < 2; i++) {
            boolean selected = section == i;
            tabs[i].setSelected(selected); tabs[i].setTextColor(selected ? Ui.GREEN : Ui.MUTED);
            tabs[i].setContentDescription(tabs[i].getText() + (selected ? "，已选择" : ""));
            tabs[i].setEnabled(!work.busy);
        }
    }

    private void renderBody() {
        remoteHistory = null;
        for (int i = 0; i < 2; i++) {
            ScrollView scroll = pages[i]; int y = Math.max(savedScroll[i], scroll.getScrollY()); savedScroll[i] = 0;
            body = contents[i]; body.removeAllViews();
            if (i == 0) localBody(); else webDavBody();
            Ui.gap(body, 14);
            TextView hint = Ui.text(this, BackupSchedule.notes(this, i == 1)
                    ? "备份文件包含便签原文，请妥善保管。" : "备份文件名以 KongyeFloat 开头。", 11, Ui.MUTED, false);
            hint.setLineSpacing(dp(3), 1); body.addView(hint);
            if (work.busy) {
                Ui.gap(body, 14); LinearLayout progress = Ui.row(this);
                ProgressBar spinner = new ProgressBar(this); spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.GREEN));
                progress.addView(spinner, new LinearLayout.LayoutParams(dp(18), dp(18)));
                TextView status = Ui.text(this, work.status, 12, Ui.MUTED, false); Ui.pad(status, 10, 0, 0, 0); progress.addView(status); body.addView(progress);
            }
            if (y > 0) scroll.post(() -> scroll.scrollTo(0, y));
        }
        selectTabs();
    }

    private void noteChoice(LinearLayout parent, boolean remote) {
        LinearLayout row = Ui.row(this); row.setMinimumHeight(dp(SETTING_ROW_HEIGHT));
        row.addView(Ui.icon(this, "notes", Ui.MUTED, 18));
        TextView label = Ui.text(this, "包含便签", 14, Ui.INK, false); Ui.pad(label, 10, 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        LeafSwitch notes = new LeafSwitch(this); notes.setContentDescription("包含便签");
        notes.setChecked(BackupSchedule.notes(this, remote)); notes.setEnabled(!work.busy);
        notes.setOnCheckedChangeListener((v, checked) -> BackupSchedule.saveNotes(this, remote, checked));
        row.addView(notes, new LinearLayout.LayoutParams(dp(48), dp(40)));
        row.setOnClickListener(v -> { if (!work.busy) notes.toggle(); }); parent.addView(row);
    }

    private void localBody() {
        LinearLayout actions = card("backup_archive", "备份内容", "手势、外观、快捷工具与字幕设置");
        noteChoice(actions, false); Ui.gap(actions, 8);
        LinearLayout buttons = Ui.row(this);
        TextView save = actionButton("导出备份", true, this::export);
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams restoreAt = new LinearLayout.LayoutParams(0, dp(44), 1); restoreAt.leftMargin = dp(10);
        buttons.addView(actionButton("选择备份文件", false, this::chooseBackup), restoreAt); actions.addView(buttons);
        Ui.gap(body, 14);
        Uri folder = BackupSchedule.folder(this);
        LinearLayout automatic = settingsCard("backup_auto", "自动备份");
        option(automatic, "files", "备份目录", folder == null ? "选择目录" : folderName(folder),
                folder == null ? "选择目录" : "更换目录", this::chooseFolder);
        divider(automatic); frequency(automatic, false);
        Ui.gap(body, 14);
        LinearLayout history = historyCard("本地备份", "刷新本地列表", () -> work.local(false, notes(false)), folder != null);
        if (folder == null) {
            empty(history, "选择备份目录后，可在这里管理文件"); return;
        }
        Ui.gap(history, 12); button(history, "备份到此目录", true, () -> work.local(true, notes(false)));
        if (!work.localListed) { empty(history, "刷新查看备份文件"); return; }
        if (work.localEntries.isEmpty()) empty(history, "还没有本地备份");
        for (int i = 0; i < Math.min(visibleBackups[0], work.localEntries.size()); i++) {
            LocalBackupFolder.Entry entry = work.localEntries.get(i);
            fileRow(history, entry.name(), entry.size(), () -> work.inspect(entry.uri()), entry.deletable() ? () -> work.deleteLocal(entry) : null);
        }
        more(history, work.localEntries.size(), 0);
    }

    private boolean notes(boolean remote) { return BackupSchedule.notes(this, remote); }

    private void webDavBody() {
        LinearLayout server = card("cloud_backup", "WebDAV 服务器", null);
        WebDavSettings.Config config = work.config;
        field(server, "服务器地址", "https://dav.example.com/", config.server(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                text -> work.config = new WebDavSettings.Config(text, work.config.username(), work.config.password(), WebDavSettings.FOLDER));
        field(server, "用户名", "WebDAV 用户名", config.username(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                text -> work.config = new WebDavSettings.Config(work.config.server(), text, work.config.password(), WebDavSettings.FOLDER));
        field(server, "密码 / 应用密码", "WebDAV 密码或应用专用密码", config.password(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                text -> work.config = new WebDavSettings.Config(work.config.server(), work.config.username(), text, WebDavSettings.FOLDER));
        Ui.gap(server, 12); TextView directory = Ui.text(this, "云端目录  KongyeFloat", 12, Ui.MUTED, false); server.addView(directory);
        if (config.server().trim().startsWith("http://")) {
            Ui.gap(server, 6); server.addView(Ui.text(this, "HTTP 连接以未加密方式传输", 11, Ui.MUTED, false));
        }
        Ui.gap(server, 14); button(server, "保存并测试", false, () -> work.remote("test", notes(true)));
        Ui.gap(body, 14);
        LinearLayout options = settingsCard("save", "备份选项");
        noteChoice(options, true); divider(options); frequency(options, true); Ui.gap(options, 10);
        button(options, "立即备份", true, () -> work.remote("upload", notes(true)));
        Ui.gap(body, 14);
        LinearLayout history = historyCard("云端备份", "刷新备份列表", () -> work.remote("list", notes(true)), true);
        remoteHistory = Ui.column(this); history.addView(remoteHistory);
        if (!work.listed) { empty(remoteHistory, "刷新查看云端备份"); return; }
        if (work.entries.isEmpty()) empty(remoteHistory, "还没有云端备份");
        for (int i = 0; i < Math.min(visibleBackups[1], work.entries.size()); i++) {
            WebDavBackup.Entry entry = work.entries.get(i);
            fileRow(remoteHistory, entry.name(), entry.size(), () -> work.download(entry), () -> work.deleteRemote(entry));
        }
        more(remoteHistory, work.entries.size(), 1);
    }

    private void frequency(LinearLayout parent, boolean remote) {
        String value = BackupSchedule.LABELS[BackupSchedule.frequency(this, remote)];
        option(parent, "backup_calendar", "备份频率", value, "备份频率 · " + value, () -> new UiDialog(this).setTitle("备份频率")
                .setSingleChoiceItems(BackupSchedule.LABELS, BackupSchedule.frequency(this, remote), (dialog, choice) -> {
                    dialog.dismiss(); work.schedule(remote, choice, notes(remote));
                }).setNegativeButton("取消", null).show());
        String last = BackupSchedule.result(this, remote);
        if (!last.isEmpty()) { Ui.gap(parent, 4); parent.addView(Ui.text(this, last, 11, Ui.MUTED, false)); }
    }

    private void option(LinearLayout parent, String icon, String title, String value, String description, Runnable action) {
        LinearLayout row = Ui.row(this); row.setMinimumHeight(dp(SETTING_ROW_HEIGHT)); row.setBackground(Ui.ripple(this, 0, 12));
        row.setContentDescription(description); row.setFocusable(true); row.setEnabled(!work.busy); row.setOnClickListener(v -> action.run());
        row.addView(Ui.icon(this, icon, Ui.MUTED, 18));
        TextView label = Ui.text(this, title, 14, Ui.INK, false); Ui.pad(label, 10, 0, 8, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView current = Ui.text(this, value, 12, Ui.MUTED, false); current.setSingleLine(); current.setMaxWidth(dp(140));
        current.setEllipsize(android.text.TextUtils.TruncateAt.END); row.addView(current);
        View arrow = Ui.icon(this, "forward", Ui.MUTED, 14);
        LinearLayout.LayoutParams arrowAt = new LinearLayout.LayoutParams(dp(14), dp(14)); arrowAt.leftMargin = dp(5); row.addView(arrow, arrowAt);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void fileRow(LinearLayout parent, String name, long bytes, Runnable restore, Runnable delete) {
        divider(parent); LinearLayout row = Ui.row(this);
        LinearLayout text = Ui.column(this); Ui.pad(text, 0, 14, 8, 14);
        text.setContentDescription("恢复 " + name); text.setBackground(Ui.ripple(this, 0, 12));
        TextView title = Ui.text(this, name, 13, Ui.INK, true); title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END); text.addView(title); Ui.gap(text, 5);
        text.addView(Ui.text(this, bytes < 0 ? "查看备份" : String.format(Locale.getDefault(), "%.1f KB", bytes / 1024d), 11, Ui.MUTED, false));
        text.setEnabled(!work.busy); text.setOnClickListener(v -> restore.run()); row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (delete != null) {
            View remove = Ui.tool(this, "trash", "删除 " + name, () -> confirmDelete(name, delete)); remove.setEnabled(!work.busy);
            row.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void more(LinearLayout parent, int total, int index) {
        if (visibleBackups[index] < total) button(parent, "加载更多", false, () -> { visibleBackups[index] += 20; renderBody(); });
    }

    private void confirmDelete(String name, Runnable delete) {
        if (work.busy) return;
        new UiDialog(this).setTitle("删除备份？").setMessage(name + "\n\n删除后将无法使用这份文件恢复。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> delete.run()).show();
    }

    private static String folderName(Uri uri) {
        String id = DocumentsContract.getTreeDocumentId(uri);
        return id.substring(Math.max(id.lastIndexOf('/'), id.lastIndexOf(':')) + 1);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    private TextView actionButton(String text, boolean primary, Runnable action) {
        TextView button = Ui.button(this, text, primary, () -> { hideKeyboard(); action.run(); });
        button.setTextSize(13); button.setEnabled(!work.busy); return button;
    }

    private void button(LinearLayout parent, String text, boolean primary, Runnable action) {
        parent.addView(actionButton(text, primary, action), new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void field(LinearLayout parent, String label, String hint, String value, int type, java.util.function.Consumer<String> change) {
        Ui.gap(parent, 12); parent.addView(Ui.text(this, label, 12, Ui.MUTED, false)); Ui.gap(parent, 7);
        EditText input = new EditText(this); input.setContentDescription(label); input.setTextSize(14);
        input.setTextColor(Ui.INK); input.setHintTextColor(Ui.MUTED); input.setSingleLine(true); input.setInputType(type);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI); input.setSaveEnabled(false);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); input.setBackground(Ui.bg(Ui.BG, 12, this));
        Ui.pad(input, 12, 10, 12, 10); input.setHint(hint); input.setText(value); input.setEnabled(!work.busy);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                change.accept(text.toString()); work.entries = List.of(); work.listed = false;
                if (remoteHistory != null) remoteHistory.setVisibility(View.GONE);
            }
            public void afterTextChanged(Editable text) { }
        });
        parent.addView(input, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private LinearLayout card(String icon, String title, String description) {
        return card(icon, title, description, false);
    }

    private LinearLayout settingsCard(String icon, String title) {
        return card(icon, title, null, true);
    }

    private LinearLayout card(String icon, String title, String description, boolean settings) {
        LinearLayout card = Ui.column(this); Ui.pad(card, 18, settings ? 6 : 16, 18, settings ? 6 : 16);
        card.setBackground(Ui.bg(Ui.SURFACE, 20, this));
        LinearLayout heading = Ui.row(this); heading.addView(Ui.icon(this, icon, Ui.GREEN, settings ? 18 : 20));
        if (settings) heading.setMinimumHeight(dp(SETTING_ROW_HEIGHT));
        TextView name = Ui.text(this, title, 15, Ui.INK, true); Ui.pad(name, settings ? 10 : 9, 0, 0, 0);
        heading.addView(name); card.addView(heading);
        if (settings) divider(card);
        if (description != null) {
            Ui.gap(card, 10); TextView detail = Ui.text(this, description, 12, Ui.MUTED, false);
            detail.setLineSpacing(dp(3), 1); card.addView(detail);
        }
        body.addView(card, new LinearLayout.LayoutParams(-1, -2)); return card;
    }

    private LinearLayout historyCard(String title, String refreshLabel, Runnable refresh, boolean enabled) {
        LinearLayout card = Ui.column(this); Ui.pad(card, 18, 10, 18, 14); card.setBackground(Ui.bg(Ui.SURFACE, 20, this));
        LinearLayout heading = Ui.row(this); heading.addView(Ui.text(this, title, 15, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        View action = Ui.tool(this, "refresh", refreshLabel, () -> { hideKeyboard(); refresh.run(); });
        action.setEnabled(enabled && !work.busy); heading.addView(action, new LinearLayout.LayoutParams(dp(40), dp(40))); card.addView(heading);
        body.addView(card, new LinearLayout.LayoutParams(-1, -2)); return card;
    }

    private void empty(LinearLayout parent, String text) {
        TextView empty = Ui.centeredText(this, text, 12, Ui.MUTED, false); Ui.pad(empty, 0, 18, 0, 18);
        parent.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private void divider(LinearLayout parent) {
        View line = new View(this); line.setBackgroundColor(Ui.LINE); parent.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.5f))));
    }

    private void export() {
        if (work.busy) return;
        exportNotes = notes(false);
        try {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip").putExtra(Intent.EXTRA_TITLE, BackupStore.filename()), EXPORT);
        } catch (ActivityNotFoundException | SecurityException failure) { Feedback.show(this, "没有可用的系统文件选择器"); }
    }

    private void chooseBackup() {
        if (work.busy) return;
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"), IMPORT);
        } catch (ActivityNotFoundException | SecurityException failure) { Feedback.show(this, "没有可用的系统文件选择器"); }
    }

    private void chooseFolder() {
        if (work.busy) return;
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), FOLDER);
        } catch (ActivityNotFoundException | SecurityException failure) { Feedback.show(this, "没有可用的系统目录选择器"); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || work.busy) return;
        Uri uri = data.getData();
        if (request == EXPORT) work.export(uri, exportNotes);
        else if (request == IMPORT) work.inspect(uri);
        else if (request == FOLDER) work.folder(uri, data.getFlags());
    }

    private void updated() {
        if (isFinishing() || isDestroyed()) return;
        if (work.restored) { work.restored = false; render(); }
        else renderBody();
        if (work.message != null) {
            String message = work.message; work.message = null;
            if (work.success) Feedback.success(this, message); else Feedback.show(this, message);
        }
        if (work.pending != null && confirmation == null) {
            BackupStore.Snapshot backup = work.pending;
            String summary = "备份时间  " + backup.createdAt() + "\n应用版本  " + backup.appVersion()
                    + "\n设置项目  " + backup.settings().size() + " 项"
                    + (backup.image() != null ? "\n包含自定义悬浮球图片" : "")
                    + (backup.includesNotes() ? "\n包含便签，将覆盖当前便签" : "\n当前便签会保留")
                    + "\n\n恢复将覆盖当前设置。";
            confirmation = new UiDialog(this).setTitle("恢复这份备份？").setMessage(summary)
                    .setNegativeButton("取消", (dialog, which) -> work.pending = null)
                    .setPositiveButton("恢复", (dialog, which) -> work.restore()).create();
            confirmation.setOnCancelListener(dialog -> work.pending = null);
            confirmation.setOnDismissListener(dialog -> confirmation = null); confirmation.show();
        }
    }

    /** Retained across rotation. Background work only holds the application context. */
    private static final class Work {
        private static final ExecutorService IO = Executors.newSingleThreadExecutor();
        private final Context context;
        private final Handler main = new Handler(Looper.getMainLooper());
        private Runnable observer;
        private boolean busy, success, restored;
        private String message, status;
        private BackupStore.Snapshot pending;
        private WebDavSettings.Config config;
        private List<WebDavBackup.Entry> entries = List.of();
        private boolean listed;
        private WebDavBackup listedClient;
        private List<LocalBackupFolder.Entry> localEntries = List.of();
        private boolean localListed;

        Work(Context context) {
            this.context = context;
            try { config = WebDavSettings.load(context); }
            catch (IOException failure) { config = WebDavSettings.Config.empty(); message = failure.getMessage(); }
        }
        private void notifyUi() { if (observer != null) observer.run(); }
        private void begin(String value) { busy = true; status = value; notifyUi(); }
        private void finish(String value, boolean ok) { busy = false; message = value; success = ok; notifyUi(); }

        void folder(Uri next, int flags) {
            Uri previous = BackupSchedule.folder(context);
            int required = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            try {
                if ((flags & required) != required) throw new SecurityException();
                context.getContentResolver().takePersistableUriPermission(next, required);
                BackupSchedule.saveFolder(context, next);
                if (previous != null && !previous.equals(next)) {
                    try { context.getContentResolver().releasePersistableUriPermission(previous, required); }
                    catch (SecurityException ignored) { }
                }
                localEntries = List.of(); localListed = false; local(false, false);
            } catch (SecurityException failure) { finish("无法使用此目录，请选择允许写入的目录", false); }
        }

        void local(boolean save, boolean notes) {
            if (busy) return;
            Uri directory = BackupSchedule.folder(context);
            begin(save ? "正在保存本地备份…" : "正在读取本地目录…");
            IO.execute(() -> {
                try {
                    LocalBackupFolder folder = new LocalBackupFolder(context, directory);
                    if (save) folder.save(context, notes);
                    List<LocalBackupFolder.Entry> result = folder.list();
                    main.post(() -> { localEntries = result; localListed = true; finish(save ? "本地备份已保存" : "本地列表已刷新", true); });
                } catch (IOException | RuntimeException failure) {
                    main.post(() -> finish(error(failure, "无法访问本地备份目录，请重新选择"), false));
                }
            });
        }

        void schedule(boolean remote, int index, boolean notes) {
            if (busy) return;
            WebDavSettings.Config selected = config;
            begin("正在保存备份频率…");
            IO.execute(() -> {
                try {
                    if (index != 0) {
                        if (remote) {
                            new WebDavBackup(selected).prepareDirectory();
                            WebDavSettings.save(context, selected);
                        } else new LocalBackupFolder(context, BackupSchedule.folder(context));
                    }
                    BackupSchedule.saveNotes(context, remote, notes);
                    BackupSchedule.configure(context, remote, index);
                    main.post(() -> finish(index == 0 ? "自动备份已关闭" : "已设置为" + BackupSchedule.LABELS[index] + "自动备份", true));
                } catch (IOException | RuntimeException failure) {
                    main.post(() -> finish(error(failure, "无法保存备份频率"), false));
                }
            });
        }

        void deleteLocal(LocalBackupFolder.Entry entry) {
            if (busy || !localEntries.contains(entry)) return;
            Uri directory = BackupSchedule.folder(context);
            begin("正在删除本地备份…");
            IO.execute(() -> {
                try {
                    LocalBackupFolder folder = new LocalBackupFolder(context, directory);
                    if (!folder.list().contains(entry)) throw new IOException("文件已变化，请刷新列表后重试");
                    folder.delete(entry);
                    main.post(() -> { localEntries = without(localEntries, entry); finish("已删除本地备份", true); });
                } catch (IOException | RuntimeException failure) {
                    main.post(() -> finish(error(failure, "无法删除本地备份"), false));
                }
            });
        }

        void deleteRemote(WebDavBackup.Entry entry) {
            if (busy || listedClient == null || !entries.contains(entry)) return;
            WebDavBackup client = listedClient;
            begin("正在删除 WebDAV 备份…");
            IO.execute(() -> {
                try {
                    client.delete(entry);
                    main.post(() -> { entries = without(entries, entry); finish("已删除 WebDAV 备份", true); });
                } catch (IOException | RuntimeException failure) {
                    main.post(() -> finish(error(failure, "无法删除 WebDAV 备份"), false));
                }
            });
        }

        private static <T> List<T> without(List<T> entries, T entry) {
            List<T> remaining = new ArrayList<>(entries); remaining.remove(entry); return List.copyOf(remaining);
        }

        private static String error(Exception failure, String fallback) {
            return failure instanceof IOException && failure.getMessage() != null ? failure.getMessage() : fallback;
        }

        void remote(String operation, boolean notes) {
            if (busy) return;
            WebDavSettings.Config selected = config;
            begin(operation.equals("upload") ? "正在上传备份…" : operation.equals("list") ? "正在读取备份列表…" : "正在测试连接…");
            IO.execute(() -> {
                try {
                    WebDavBackup client = new WebDavBackup(selected);
                    WebDavSettings.save(context, selected);
                    if (!operation.equals("list")) client.prepareDirectory();
                    if (operation.equals("test")) {
                        main.post(() -> finish("WebDAV 连接成功", true)); return;
                    }
                    if (operation.equals("upload")) {
                        BackupSchedule.upload(context, client, notes);
                        main.post(() -> { entries = List.of(); listed = false; finish("WebDAV 备份已上传", true); });
                    } else {
                        List<WebDavBackup.Entry> result = client.list();
                        main.post(() -> { entries = result; listed = true; listedClient = client; finish("备份列表已刷新", true); });
                    }
                } catch (IOException | RuntimeException failure) {
                    String reason = failure instanceof IOException ? failure.getMessage() : "WebDAV 操作失败，请检查配置后重试";
                    main.post(() -> finish(reason, false));
                }
            });
        }

        void download(WebDavBackup.Entry entry) {
            if (busy || listedClient == null || !entries.contains(entry)) return;
            WebDavBackup client = listedClient;
            begin("正在下载备份…");
            IO.execute(() -> {
                File temporary = null;
                try {
                    temporary = File.createTempFile("KongyeFloat-", ".zip", context.getCacheDir());
                    client.download(entry, temporary);
                    BackupStore.Snapshot value = BackupStore.inspect(context, Uri.fromFile(temporary));
                    main.post(() -> { pending = value; busy = false; notifyUi(); });
                } catch (IOException | RuntimeException failure) {
                    String reason = failure instanceof IOException ? failure.getMessage() : "备份下载失败，请重试";
                    main.post(() -> finish(reason, false));
                } finally { if (temporary != null) temporary.delete(); }
            });
        }

        void export(Uri uri, boolean notes) {
            begin("正在导出备份…");
            IO.execute(() -> {
                try {
                    BackupStore.export(context, uri, notes);
                    main.post(() -> finish("备份已导出", true));
                } catch (IOException | RuntimeException failure) {
                    // ACTION_CREATE_DOCUMENT creates a new file, so remove an incomplete export if possible.
                    try { android.provider.DocumentsContract.deleteDocument(context.getContentResolver(), uri); }
                    catch (Exception ignored) { }
                    main.post(() -> finish("导出失败，请检查所选位置的可用空间后重试", false));
                }
            });
        }

        void inspect(Uri uri) {
            begin("正在读取备份…");
            IO.execute(() -> {
                try {
                    BackupStore.Snapshot value = BackupStore.inspect(context, uri);
                    main.post(() -> { pending = value; busy = false; notifyUi(); });
                } catch (IOException | RuntimeException failure) {
                    String reason = failure instanceof IOException ? failure.getMessage() : "无法读取备份文件";
                    main.post(() -> finish(reason, false));
                }
            });
        }

        void restore() {
            BackupStore.Snapshot value = pending; pending = null;
            if (value == null || busy) return;
            begin("正在恢复备份…");
            IO.execute(() -> {
                try {
                    BackupStore.restore(context, value);
                    main.post(() -> {
                        BallSettings.migrate(context); QuickActions.migrate(context);
                        try {
                            if (FloatService.running) context.startService(new Intent(context, FloatService.class).setAction(FloatService.REFRESH));
                        } catch (IllegalStateException | SecurityException ignored) { }
                        restored = true; finish("备份已恢复", true);
                    });
                } catch (IOException | RuntimeException failure) {
                    main.post(() -> finish("恢复失败，请检查手机存储空间后重试", false));
                }
            });
        }
    }
}
