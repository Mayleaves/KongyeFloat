package com.kongye.floatball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The recording card can be hosted by an activity or shown above the current app. */
final class RecordingPreview {
    static final int SHARE_REQUEST = 42;
    private final Context context;
    private final Activity activity;
    private final int gifFps;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private File file;
    private CaptureWindow overlay;
    private AlertDialog dialog;
    private RecordingPlayer video;
    private TextView progress, operationStatus;
    private int floatingX = Integer.MIN_VALUE, floatingY = -1;
    private int playbackPosition;
    private int shortSide;
    private long duration;
    private boolean playbackPlaying, large, pinned, folded, busy;
    private volatile boolean cancelled, closed;
    private String operationMessage = "";

    RecordingPreview(Context context, File file, int gifFps) {
        this.context = context;
        activity = context instanceof Activity host ? host : null;
        this.file = file;
        this.gifFps = gifFps;
        shortSide = Prefs.of(context).getInt("gif_resolution", 240);
    }

    void show(boolean autoGif) {
        if (!isGif()) {
            MediaMetadataRetriever metadata = new MediaMetadataRetriever();
            try {
                metadata.setDataSource(file.getPath());
                duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } catch (Exception ignored) {
                duration = 0;
            } finally {
                try { metadata.release(); } catch (Exception ignored) {}
            }
        }
        if (autoGif && !isGif()) convert(0, 30000);
        else render();
    }

    void store(Bundle state) {
        state.putString("path", file.getPath());
        state.putBoolean("pinned", pinned);
        state.putBoolean("folded", folded);
    }

    void restore(Bundle state) {
        pinned = state.getBoolean("pinned");
        folded = state.getBoolean("folded");
    }

    void back() {
        if (busy) cancelled = true;
        else if (large) setLarge(false);
        else finish();
    }

    void pause() {
        if (video != null && !pinned) video.pause();
    }

    void shareReturned() {
        if (overlay != null) overlay.visible(true);
    }

    private int dp(float size) { return Ui.dp(context, size); }

    private boolean isGif() { return file.getName().endsWith(".gif"); }

    private boolean usesOverlay() { return activity == null || pinned; }

    private void stopVideo() {
        if (video == null) return;
        playbackPosition = video.position();
        playbackPlaying = video.playing();
        video.release();
        video = null;
    }

    private void setLarge(boolean enlarged) {
        large = enlarged;
        render();
    }

    private void render() {
        if (closed) return;
        stopVideo();
        if (overlay != null) {
            overlay.remove();
            overlay = null;
        }
        progress = operationStatus = null;
        ThemePolicy.apply(context);
        if (usesOverlay()) overlay = new CaptureWindow(context);
        FrameLayout root = overlay != null ? overlay.frame(this::back) : new FrameLayout(context);
        if (large) {
            showLarge(root);
            return;
        }
        root.setOnClickListener(view -> { if (!pinned && !busy) finish(); });
        LinearLayout card = Ui.card(context);
        Ui.pad(card, 16, 12, 16, 16);
        Ui.softShadow(card);
        card.setOnClickListener(view -> {});
        card.addView(heading());
        if (busy) {
            Ui.gap(card, 20);
            progress = Ui.text(context, "正在转换 GIF…", 15, Ui.INK, false);
            card.addView(progress);
            Ui.gap(card, 20);
            card.addView(Ui.button(context, "取消转换", false, () -> cancelled = true));
        } else if (!folded) {
            addMediaAndActions(card);
        }

        int width = context.getResources().getDisplayMetrics().widthPixels - dp(pinned ? 40 : 16);
        if (pinned) {
            root.setPadding(dp(12), dp(12), dp(12), dp(12));
            root.addView(card, new FrameLayout.LayoutParams(-1, -2));
            if (floatingX == Integer.MIN_VALUE) {
                floatingX = dp(8);
                floatingY = dp(100);
            }
        } else {
            root.addView(card, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER));
        }
        showRoot(root, width);
        Prefs.of(context).edit().putString("record_result_kind", isGif() ? "gif" : "video")
                .putString("record_result_path", file.getPath()).putBoolean("record_result_ocr", false).apply();
    }

    private LinearLayout heading() {
        LinearLayout row = Ui.row(context);
        TextView title = Ui.text(context, isGif() ? "GIF 预览" : "录屏预览", 18, Ui.INK, true);
        title.setContentDescription("拖动录制结果窗口");
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        title.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (!pinned || overlay == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        int[] position = overlay.position();
                        startX = position[0];
                        startY = position[1];
                        downX = event.getRawX();
                        downY = event.getRawY();
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        floatingX = startX + Math.round(event.getRawX() - downX);
                        int bottom = context.getResources().getDisplayMetrics().heightPixels - dp(60);
                        floatingY = Math.max(dp(24), Math.min(bottom, startY + Math.round(event.getRawY() - downY)));
                        overlay.move(floatingX, floatingY);
                    }
                    case MotionEvent.ACTION_UP -> {
                        view.performClick();
                        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                        int width = screenWidth - dp(16);
                        if (floatingX < -width / 2 || floatingX > screenWidth - width / 2) dock(floatingX < 0);
                    }
                }
                return true;
            }
        });
        addIcon(row, folded ? "expand" : "collapse", folded ? "展开录制结果" : "收起录制结果", () -> {
            if (busy) return;
            folded = !folded;
            render();
        });
        addIcon(row, pinned ? "pinned" : "pin", pinned ? "取消置顶录制结果" : "置顶录制结果", () -> {
            if (busy) return;
            pinned = !pinned;
            if (!pinned && activity != null) bringHostToFront();
            render();
        });
        addIcon(row, "close", "关闭录制结果", () -> { if (!busy) finish(); });
        return row;
    }

    private void addIcon(LinearLayout row, String icon, String description, Runnable action) {
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(40), dp(40));
        at.leftMargin = dp(2);
        row.addView(Ui.tool(context, icon, description, action), at);
    }

    private void addMediaAndActions(LinearLayout card) {
        Ui.gap(card, 12);
        int height = Math.min(dp(230), context.getResources().getDisplayMetrics().heightPixels / 3);
        if (isGif()) {
            View gif = RecordingMediaView.decode(context, file, 720);
            if (gif != null) {
                gif.setContentDescription("GIF 动态预览，点击放大");
                gif.setOnClickListener(view -> setLarge(true));
                Ui.frame(gif, Ui.BG, 14);
                card.addView(gif, new LinearLayout.LayoutParams(-1, height));
            }
        } else {
            video = new RecordingPlayer(context, file, false, playbackPosition, playbackPlaying, () -> setLarge(true));
            card.addView(video, new LinearLayout.LayoutParams(-1, height));
        }
        Ui.gap(card, 12);
        LinearLayout actions = Ui.row(context);
        addAction(actions, "save", "保存", "保存录制", this::save);
        addAction(actions, "share", "分享", "分享录制", this::share);
        if (!isGif()) addAction(actions, "gif_record", "转 GIF", "转为 GIF", this::conversionOptions);
        card.addView(actions);
        Ui.gap(card, 6);
        String message = operationMessage.isEmpty() ? "预览保存在临时空间；点保存后才写入相册。" : operationMessage;
        operationStatus = Ui.text(context, message, 11, Ui.MUTED, false);
        operationStatus.setContentDescription("录制操作状态");
        card.addView(operationStatus);
    }

    private void addAction(LinearLayout row, String icon, String label, String description, Runnable action) {
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(0, dp(44), 1);
        at.setMargins(dp(4), 0, dp(4), 0);
        row.addView(Ui.action(context, icon, label, description, action), at);
    }

    private void showRoot(FrameLayout root, int width) {
        if (activity != null) {
            activity.setContentView(overlay == null ? root : new FrameLayout(context));
            WindowManager.LayoutParams at = activity.getWindow().getAttributes();
            at.flags &= ~(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            if (overlay == null) {
                at.width = at.height = -1;
                at.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                at.gravity = Gravity.CENTER;
            } else {
                at.width = at.height = 1;
                at.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                at.gravity = Gravity.TOP | Gravity.LEFT;
            }
            at.x = at.y = 0;
            activity.getWindow().setAttributes(at);
        }
        if (overlay == null) return;
        try {
            if (pinned && !large) overlay.showFloating(root, width + dp(24),
                    context.getResources().getDisplayMetrics().heightPixels - dp(80), floatingX, floatingY);
            else overlay.show(root, true);
        } catch (RuntimeException unavailable) {
            android.util.Log.w("KongyeRecorder", "Cannot show recording preview", unavailable);
            finish();
            Feedback.show(context, "无法显示录制预览");
        }
    }

    private void bringHostToFront() {
        try {
            activity.startActivity(new Intent(context, RecordingResultActivity.class)
                    .putExtra("path", file.getPath())
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            activity.overridePendingTransition(0, 0);
        } catch (RuntimeException ignored) {}
    }

    private void dock(boolean left) {
        if (video != null) video.pause();
        CaptureWindow window = overlay;
        DockMorph.dock(context, window.manager, (FrameLayout) window.root(), left,
                isGif() ? "gif_record" : "record", "展开置顶录制标签", () -> {
                    if (overlay != window) return;
                    int[] position = window.position();
                    floatingX = position[0];
                    floatingY = position[1];
                });
    }

    private void showLarge(FrameLayout root) {
        root.setBackgroundColor(0xED101A16);
        root.setContentDescription("录制大图预览暗色背景");
        root.setOnClickListener(view -> setLarge(false));
        if (isGif()) {
            var metrics = context.getResources().getDisplayMetrics();
            View gif = RecordingMediaView.decode(context, file, metrics.widthPixels);
            if (gif != null) {
                int height = metrics.heightPixels - dp(140);
                if (gif instanceof ImageView image && image.getDrawable() != null) {
                    var drawable = image.getDrawable();
                    height = Math.min(height, Math.round(metrics.widthPixels * drawable.getIntrinsicHeight()
                            / (float) Math.max(1, drawable.getIntrinsicWidth())));
                }
                root.addView(gif, new FrameLayout.LayoutParams(-1, height, Gravity.CENTER));
                gif.setOnClickListener(view -> {});
                gif.setOnTouchListener((view, event) -> {
                    if (event.getActionMasked() != MotionEvent.ACTION_UP || !(view instanceof ImageView image)
                            || image.getDrawable() == null) return false;
                    var drawable = image.getDrawable();
                    float fit = Math.min(view.getWidth() / (float) Math.max(1, drawable.getIntrinsicWidth()),
                            view.getHeight() / (float) Math.max(1, drawable.getIntrinsicHeight()));
                    float left = (view.getWidth() - drawable.getIntrinsicWidth() * fit) / 2;
                    float top = (view.getHeight() - drawable.getIntrinsicHeight() * fit) / 2;
                    if (event.getX() >= left && event.getX() <= view.getWidth() - left
                            && event.getY() >= top && event.getY() <= view.getHeight() - top) return false;
                    setLarge(false);
                    return true;
                });
            }
        } else {
            video = new RecordingPlayer(context, file, true, playbackPosition, playbackPlaying, () -> {});
            FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(-1, -1);
            at.setMargins(dp(8), dp(48), dp(8), dp(40));
            root.addView(video, at);
        }
        Ui.addPreviewBack(root, () -> setLarge(false));
        showRoot(root, 0);
    }

    private void report(String message) {
        operationMessage = message;
        if (operationStatus != null) operationStatus.setText(message);
        Feedback.show(context, message);
    }

    private void save() {
        if (busy) return;
        busy = true;
        boolean gif = isGif();
        if (operationStatus != null) operationStatus.setText(gif ? "正在保存 GIF…" : "正在保存视频…");
        stopVideo();
        File source = file;
        worker.execute(() -> {
            try {
                Uri saved = RecordingFiles.save(context, source);
                Prefs.of(context).edit().putString("last_record_saved_uri", saved.toString()).apply();
                main.post(() -> {
                    if (closed) return;
                    Feedback.success(context, gif ? "GIF 已保存到相册" : "视频已保存到相册");
                    finish();
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (closed) return;
                    busy = false;
                    report("保存失败：" + (failure.getMessage() == null ? "请检查剩余空间或权限" : failure.getMessage()));
                    render();
                });
            }
        });
    }

    private void share() {
        if (busy) return;
        busy = true;
        stopVideo();
        File source = file;
        worker.execute(() -> {
            Uri uri = null;
            try { uri = ShareImageProvider.prepareMedia(context, source); } catch (Exception ignored) {}
            Uri target = uri;
            main.post(() -> {
                busy = false;
                if (closed) return;
                if (target == null) report("无法准备分享文件");
                else {
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType(isGif() ? "image/gif" : "video/mp4")
                            .putExtra(Intent.EXTRA_STREAM, target)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri("空叶录制", target));
                    Intent chooser = Intent.createChooser(send, "分享录制");
                    try {
                        if (activity == null) {
                            context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            finish();
                            return;
                        }
                        activity.startActivityForResult(chooser, SHARE_REQUEST);
                        render();
                        if (overlay != null) overlay.visible(false);
                        return;
                    } catch (RuntimeException unavailable) {
                        report("无法打开分享面板");
                    }
                }
                render();
            });
        });
    }

    private void conversionOptions() {
        if (busy || dialog != null) return;
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.column(context);
        Ui.pad(body, 20, 20, 20, 18);
        body.setFocusableInTouchMode(true);
        scroll.addView(body);

        LinearLayout header = Ui.row(context);
        FrameLayout badge = new FrameLayout(context);
        badge.setBackground(Ui.bg(Ui.PALE, 12, context));
        badge.addView(Ui.icon(context, "gif_record", Ui.GREEN, 22),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        header.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView title = Ui.text(context, "转为 GIF", 18, Ui.INK, true);
        Ui.pad(title, 10, 0, 0, 0);
        header.addView(title);
        body.addView(header);

        Ui.gap(body, 18);
        LinearLayout times = Ui.row(context);
        times.setGravity(Gravity.TOP);
        EditText start = numberField(times, "起始时间", "GIF 起始秒", "0", 0);
        EditText length = numberField(times, "持续时间", "GIF 持续秒数",
                Long.toString(Math.max(1, Math.min(30, duration / 1000))), 10);
        body.addView(times);

        Ui.gap(body, 14);
        body.addView(Ui.text(context, "分辨率", 12, Ui.INK, true));
        Ui.gap(body, 6);
        int[] sizes = {240, 480, 720, 1080};
        RadioGroup resolutions = resolutionOptions(sizes);
        body.addView(resolutions);
        Ui.gap(body, 9);
        TextView hint = Ui.text(context, "最多 30 秒 · GIF 无声", 11, Ui.MUTED, false);
        body.addView(hint);

        AlertDialog options = new UiDialog(context).setView(scroll).create();
        Ui.gap(body, 16);
        LinearLayout actions = Ui.row(context);
        actions.addView(Ui.button(context, "取消", false, options::dismiss),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView confirm = Ui.button(context, "转换", true, () -> {
            try {
                long offset = Math.multiplyExact(Long.parseLong(start.getText().toString()), 1000L);
                long milliseconds = Math.multiplyExact(Long.parseLong(length.getText().toString()), 1000L);
                if (offset < 0 || offset >= duration || milliseconds < 200 || milliseconds > 30000) {
                    throw new NumberFormatException();
                }
                View resolution = resolutions.findViewById(resolutions.getCheckedRadioButtonId());
                shortSide = sizes[resolutions.indexOfChild(resolution)];
                options.dismiss();
                convert(offset, milliseconds);
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                hint.setText("请输入视频内的起点和 1–30 秒时长。");
            }
        });
        LinearLayout.LayoutParams confirmAt = new LinearLayout.LayoutParams(0, -2, 1);
        confirmAt.leftMargin = dp(8);
        actions.addView(confirm, confirmAt);
        body.addView(actions);

        if (activity == null) options.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        options.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        CaptureWindow underneath = overlay;
        if (underneath != null) underneath.visible(false);
        options.setOnDismissListener(dismissed -> {
            dialog = null;
            if (overlay == underneath && underneath != null) underneath.visible(true);
        });
        dialog = options;
        try {
            body.requestFocus();
            options.show();
        }
        catch (RuntimeException unavailable) {
            dialog = null;
            if (underneath != null) underneath.visible(true);
            report("无法打开 GIF 转换设置");
        }
    }

    private RadioGroup resolutionOptions(int[] sizes) {
        RadioGroup row = new RadioGroup(context);
        row.setOrientation(RadioGroup.HORIZONTAL);
        int selected = shortSide <= 240 ? 0 : shortSide <= 480 ? 1 : shortSide <= 720 ? 2 : 3;
        int[] checkedState = {android.R.attr.state_checked};
        int[][] states = {checkedState, new int[0]};
        for (int index = 0; index < sizes.length; index++) {
            RadioButton option = new RadioButton(context);
            option.setId(View.generateViewId());
            option.setButtonDrawable(null);
            option.setText(sizes[index] + "p");
            Ui.textSize(option, 13);
            option.setGravity(Gravity.CENTER);
            option.setPadding(0, 0, 0, 0);
            option.setMinHeight(dp(44));
            option.setTextColor(new ColorStateList(states, new int[]{Ui.GREEN, Ui.INK}));

            GradientDrawable checked = Ui.bg(Ui.PALE, 12, context);
            checked.setStroke(dp(1), Ui.GREEN);
            GradientDrawable idle = Ui.bg(Ui.BG, 12, context);
            idle.setStroke(dp(1), Ui.LINE);
            StateListDrawable background = new StateListDrawable();
            background.addState(checkedState, checked);
            background.addState(new int[0], idle);
            option.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.GREEN & 0x00FFFFFF | 0x24000000),
                    background, null));

            RadioGroup.LayoutParams at = new RadioGroup.LayoutParams(0, -2, 1);
            if (index > 0) at.setMarginStart(dp(6));
            row.addView(option, at);
            if (index == selected) row.check(option.getId());
        }
        return row;
    }

    private EditText numberField(LinearLayout row, String label, String description, String value, int startMargin) {
        LinearLayout column = Ui.column(context);
        column.addView(Ui.text(context, label, 12, Ui.INK, true));
        Ui.gap(column, 6);
        LinearLayout input = Ui.row(context);
        input.setBackground(Ui.bg(Ui.BG, 12, context));
        EditText field = Ui.field(context, label);
        field.setBackground(null);
        field.setContentDescription(description);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        field.setText(value);
        input.addView(field, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView unit = Ui.text(context, "秒", 12, Ui.MUTED, false);
        Ui.pad(unit, 0, 0, 12, 0);
        unit.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        input.addView(unit);
        column.addView(input);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(0, -2, 1);
        at.leftMargin = dp(startMargin);
        row.addView(column, at);
        return field;
    }

    private void convert(long start, long milliseconds) {
        if (busy) return;
        busy = true;
        cancelled = false;
        render();
        if (closed) return;
        File source = file;
        worker.execute(() -> {
            File gif = null;
            String error = null;
            try {
                gif = GifConversion.convert(context, source, shortSide, start, milliseconds, gifFps,
                        () -> cancelled || closed, percent -> main.post(() -> {
                            if (progress != null) progress.setText("正在转换 GIF… " + percent + "%");
                        }));
            } catch (Exception | OutOfMemoryError failure) {
                error = failure.getMessage();
            }
            File converted = gif;
            String failure = error;
            main.post(() -> {
                if (closed) return;
                busy = false;
                if (converted != null) {
                    file = converted;
                    large = false;
                    playbackPosition = 0;
                    Prefs.of(context).edit().putString("last_gif_path", file.getPath()).apply();
                    report("GIF 已生成，可保存或分享");
                } else report(cancelled ? "已取消 GIF 转换" : "GIF 未生成：" + failure);
                render();
            });
        });
    }

    private void finish() {
        dispose();
        if (activity != null) activity.finish();
        else RecordingPreviewFallback.overlayClosed(this);
    }

    void dispose() {
        if (closed) return;
        closed = true;
        if (dialog != null) dialog.dismiss();
        stopVideo();
        if (overlay != null) overlay.remove();
        main.removeCallbacksAndMessages(null);
        worker.shutdown();
    }
}
