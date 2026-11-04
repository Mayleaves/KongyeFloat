package com.kongye.floatball;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.*;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import java.util.function.BooleanSupplier;

/** An explicit, in-memory OCR session over one user-selected screen region. */
final class SubtitleSession {
    private static final String[] LANGUAGES = {"en", "zh", "ja", "ko"};
    private static final String[] LANGUAGE_NAMES = TranslationPane.languageOptions(false);
    private static final String[] SOURCE_NAMES = TranslationPane.languageOptions(true);
    private static final int[] COLORS = {0xFF141414, 0xFF1B4332, 0xFF1B2A4A, 0xFFFFFFFF};
    private static final int PANEL_GREEN = 0xFF24533B;
    private static final int DEFAULT_OPACITY = 78;
    private static final int POLL_MS = 650;
    private final Context context;
    private final SharedPreferences preferences;
    private final BooleanSupplier blocked;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Rect screen = new Rect();
    private final Rect area = new Rect();
    private final Runnable poll = this::capture;
    private WindowManager overlays;
    private AssistService assist;
    private FrameLayout panel;
    private ResizableCropView selection;
    private FrameLayout layer;
    private TextView caption;
    private TextRecognizer reader;
    private LanguageIdentifier identifier;
    private Translator translator;
    private Runnable translationTimeout;
    private boolean active, live, frameBusy, translating, modelReady;
    private boolean frameVisible, cover;
    private int epoch, translationJob, background, opacity, alignment;
    private String source, target, clientPair = "";
    private String latest = "", displayed = "", translation = "";
    private String status = "";
    private long retryAt;

    SubtitleSession(Context context, BooleanSupplier blocked) {
        this.context = context;
        this.blocked = blocked;
        preferences = Prefs.of(context);
    }

    boolean isActive() { return active; }

    void start() {
        if (active) { showActions(); return; }
        assist = AssistService.instance;
        if (Build.VERSION.SDK_INT < 30 || assist == null) {
            Feedback.show(context, "字幕翻译需要 Android 11 或以上，并开启空叶无障碍服务");
            return;
        }
        overlays = assist.getSystemService(WindowManager.class);
        frameVisible = preferences.getBoolean("subtitle_frame", true);
        cover = preferences.getBoolean("subtitle_cover", false);
        background = preferences.getInt("subtitle_background", COLORS[0]);
        opacity = Math.max(0, Math.min(100, preferences.getInt("subtitle_opacity", DEFAULT_OPACITY)));
        alignment = Math.max(0, Math.min(2, preferences.getInt("subtitle_alignment", 1)));
        source = preferences.getString("subtitle_source", "auto");
        target = preferences.getString("subtitle_target", "zh");
        screen.set(displayBounds());
        area.setEmpty();
        active = true;
        editRegion();
    }

    private int dp(float value) { return Ui.dp(context, value); }

    private Rect displayBounds() {
        DisplayMetrics metrics = new DisplayMetrics();
        overlays.getDefaultDisplay().getRealMetrics(metrics);
        return new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
    }

    private void editRegion() {
        removePanel();
        invalidateWork();
        clearText();
        FrameLayout root = new FrameLayout(context);
        ResizableCropView crop = new ResizableCropView(context, null, false);
        selection = crop;
        crop.useSubtitleFrame();
        crop.setContentDescription("字幕识别区域，可框选、移动或拖动边角");
        if (!area.isEmpty()) crop.setInitial(area, screen.width(), screen.height());
        root.addView(crop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout buttons = Ui.row(context);
        Ui.pad(buttons, 2, 2, 2, 2);
        android.graphics.drawable.GradientDrawable capsule = Ui.bg(Ui.toolbar(), 24, context);
        buttons.setBackground(capsule);
        addRegionButton(buttons, "close", "取消", this::cancelEditor);
        addRegionButton(buttons, "reset", "重置", crop::resetSubtitleFrame);
        addRegionButton(buttons, "check", "确认", () -> {
            int[] origin = new int[2];
            crop.getLocationOnScreen(origin);
            Rect selected = crop.sourceRect(crop.getWidth(), crop.getHeight());
            selected.offset(origin[0], origin[1]);
            if (!selected.intersect(screen) || selected.width() < dp(24) || selected.height() < dp(24)) {
                Feedback.show(context, "请框选更大的字幕区域");
                return;
            }
            area.set(selected);
            removePanel();
            if (!live && !attachPresentation()) return;
            live = true;
            status = "";
            render();
            schedule(120);
        });
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(dp(174), dp(44), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        at.bottomMargin = Math.max(dp(54), Math.round(screen.height() * .14f));
        root.addView(buttons, at);
        show(root, "空叶字幕选区");
    }

    private void addRegionButton(LinearLayout row, String icon, String label, Runnable action) {
        FrameLayout button = Ui.tool(context, icon, label, action);
        button.removeAllViews();
        Ui.Icon glyph = new Ui.Icon(context, icon, Color.WHITE);
        if (icon.equals("reset")) glyph.setScaleX(-1);
        button.addView(glyph, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        row.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void cancelEditor() {
        if (live) resume();
        else stop();
    }

    private boolean attachPresentation() {
        layer = new FrameLayout(context) {
            private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final int[] origin = new int[2];
            private final DashPathEffect dashed = new DashPathEffect(new float[]{dp(7), dp(4)}, 0);
            @Override protected void onDraw(Canvas canvas) {
                if (!frameVisible) return;
                getLocationOnScreen(origin);
                stroke.setColor(Ui.GREEN);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(2));
                stroke.setPathEffect(dashed);
                // Draw outside the OCR rectangle so the border is never recognized as text.
                canvas.drawRect(area.left - origin[0] - dp(2), area.top - origin[1] - dp(2),
                        area.right - origin[0] + dp(2), area.bottom - origin[1] + dp(2), stroke);
            }
        };
        layer.setWillNotDraw(false);
        caption = Ui.text(context, "", 17, Color.WHITE, true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED);
        caption.setLineSpacing(dp(3), 1f);
        if (Build.VERSION.SDK_INT >= 33) {
            caption.setLineBreakStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_STYLE_STRICT);
            caption.setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
        }
        layer.addView(caption);
        layer.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (view == layer && (l != ol || t != ot || r != or || b != ob)) render();
        });
        try {
            overlays.addView(layer, window(-1, -1, Gravity.TOP | Gravity.LEFT, false, "空叶字幕译文"));
            replaceReader();
            return true;
        } catch (RuntimeException failure) {
            stop();
            Feedback.show(context, "无法显示字幕翻译窗口，请检查无障碍服务");
            return false;
        }
    }

    private void replaceReader() {
        if (reader != null) reader.close();
        reader = switch (source) {
            case "ja" -> TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            case "ko" -> TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
            default -> TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        };
    }

    private WindowManager.LayoutParams window(int width, int height, int gravity, boolean touchable, String title) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        flags |= touchable ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        params.gravity = gravity;
        params.setTitle(title);
        if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
        if (Build.VERSION.SDK_INT >= 30) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return params;
    }

    private void render() {
        if (layer == null || caption == null) return;
        layer.invalidate();
        caption.setText(translation);
        caption.setVisibility(translation.isEmpty() ? View.GONE : View.VISIBLE);
        caption.setTextLocale(java.util.Locale.forLanguageTag(target));
        int horizontal = alignment == 0 ? Gravity.LEFT : alignment == 2 ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL;
        caption.setGravity(horizontal | Gravity.CENTER_VERTICAL);
        caption.setBackground(Ui.bg((background & 0xFFFFFF) | (Math.round(opacity * 2.55f) << 24), 8, context));
        boolean light = Color.luminance(background | 0xFF000000) > .6f && opacity >= 45;
        caption.setTextColor(light ? Color.BLACK : Color.WHITE);
        caption.setShadowLayer(light ? 0 : dp(2), 0, dp(1), Color.BLACK);
        int[] origin = new int[2];
        layer.getLocationOnScreen(origin);
        int width = Math.min(area.width(), dp(560));
        int extra = area.width() - width;
        int offset = alignment == 0 ? 0 : alignment == 2 ? extra : extra / 2;
        int side = Math.min(dp(10), width / 5);
        caption.setPadding(side + (cover ? offset : 0), dp(5), side + (cover ? extra - offset : 0), dp(5));
        int available = cover ? area.height() : area.top - origin[1] - dp(6);
        if (!cover && available < 2 * caption.getLineHeight() + dp(10)) available = screen.height();
        caption.setMaxLines(Math.max(1, (available - dp(10) + dp(3)) / caption.getLineHeight()));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(cover ? area.width() : width, cover ? area.height() : -2);
        params.leftMargin = area.left - origin[0] + (cover ? 0 : offset);
        int top = area.top;
        if (!cover) {
            caption.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            top = Math.max(origin[1], area.top - caption.getMeasuredHeight() - dp(6));
        }
        params.topMargin = top - origin[1];
        caption.setLayoutParams(params);
    }

    private void schedule(long delay) {
        main.removeCallbacks(poll);
        if (active && live && panel == null) main.postDelayed(poll, delay);
    }

    private boolean ready() { return active && live && panel == null && !blocked.getAsBoolean(); }

    private void capture() {
        if (!active || !live) return;
        if (assist != AssistService.instance || context.getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            stop();
            return;
        }
        if (!ready() || frameBusy) { schedule(POLL_MS); return; }
        int token = epoch;
        Rect selected = new Rect(area);
        frameBusy = true;
        if (assist.captureSubtitleWindow(selected, (bitmap, error) -> read(bitmap, error, token))) return;
        // Older versions capture the display. Blank only overlays that intersect the crop.
        boolean hideCaption = intersects(caption, selected);
        if (hideCaption) caption.setVisibility(View.INVISIBLE);
        main.postDelayed(() -> {
            if (token != epoch || !ready()) {
                restoreAfterFrame(hideCaption);
                read(null, null, token);
                return;
            }
            try {
                assist.captureBitmap((bitmap, error) -> {
                    restoreAfterFrame(hideCaption);
                    Bitmap cropped = null;
                    try {
                        if (bitmap != null && token == epoch) cropped = AssistService.cropSubtitle(bitmap, selected, screen);
                    } catch (RuntimeException | OutOfMemoryError ignored) {
                    } finally {
                        if (bitmap != null && bitmap != cropped) bitmap.recycle();
                    }
                    read(cropped, error, token);
                });
            } catch (RuntimeException failure) {
                restoreAfterFrame(hideCaption);
                read(null, "无法读取字幕区域", token);
            }
        }, hideCaption ? 45 : 0);
    }

    private static boolean intersects(View view, Rect selected) {
        Rect bounds = new Rect();
        int[] origin = new int[2];
        if (view.getVisibility() != View.VISIBLE || view.getWidth() == 0) return false;
        view.getLocationOnScreen(origin);
        bounds.set(origin[0], origin[1], origin[0] + view.getWidth(), origin[1] + view.getHeight());
        return Rect.intersects(bounds, selected);
    }

    private void restoreAfterFrame(boolean hideCaption) {
        if (hideCaption && caption != null) caption.setVisibility(translation.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void read(Bitmap bitmap, String error, int token) {
        if (bitmap == null || token != epoch || !ready()) {
            if (bitmap != null) bitmap.recycle();
            if (error != null && token == epoch && ready()) {
                clearText();
                status = error;
            }
            finishFrame();
            return;
        }
        try {
            reader.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener(task -> {
                bitmap.recycle();
                if (token == epoch && ready()) {
                    if (task.isSuccessful()) {
                        latest = task.getResult().getText().trim();
                        if (latest.isEmpty()) {
                            clearText();
                            if (!translating) status = "";
                        } else translateLatest();
                    } else {
                        clearText();
                        status = "字幕识别失败，正在重试";
                    }
                }
                finishFrame();
            });
        } catch (RuntimeException errorDuringRead) {
            bitmap.recycle();
            status = "字幕识别失败，正在重试";
            finishFrame();
        }
    }

    private void finishFrame() {
        frameBusy = false;
        schedule(POLL_MS);
    }

    private void clearText() {
        latest = "";
        displayed = "";
        if (!translation.isEmpty()) { translation = ""; render(); }
    }

    private void translateLatest() {
        if (translating || latest.isEmpty() || latest.equals(displayed) || SystemClock.uptimeMillis() < retryAt) return;
        translating = true;
        int token = ++translationJob;
        String original = latest;
        if (!source.equals("auto")) { prepareClient(source, original, token); return; }
        try {
            if (identifier == null) identifier = LanguageIdentification.getClient(
                    new LanguageIdentificationOptions.Builder().setConfidenceThreshold(.25f).build());
            Runnable fallback = () -> prepareClient(TranslationPolicy.scriptLanguage(original), original, token);
            timeout(fallback, 4000);
            identifier.identifyLanguage(original).addOnCompleteListener(task -> {
                if (!valid(token) || translationTimeout != fallback) return;
                String language = task.isSuccessful() ? TranslateLanguage.fromLanguageTag(task.getResult()) : null;
                prepareClient(language == null ? TranslationPolicy.scriptLanguage(original) : language, original, token);
            });
        } catch (RuntimeException failure) {
            prepareClient(TranslationPolicy.scriptLanguage(original), original, token);
        }
    }

    private boolean valid(int token) { return token == translationJob && active && translating; }

    private void prepareClient(String language, String original, int token) {
        if (!valid(token)) return;
        clearTimeout();
        if (language.isEmpty()) { failed(token, "请选择字幕源语言"); return; }
        if (language.equals(target)) { translated(token, original, original); return; }
        String pair = language + ":" + target;
        try {
            if (!pair.equals(clientPair) || translator == null) {
                closeTranslator();
                translator = Translation.getClient(new TranslatorOptions.Builder()
                        .setSourceLanguage(language).setTargetLanguage(target).build());
                clientPair = pair;
            }
            Translator client = translator;
            if (modelReady) { translate(client, original, token); return; }
            status = "正在准备翻译模型（首次需 Wi-Fi）";
            timeout(() -> failed(token, "翻译模型准备超时"), 45000);
            ConnectivityManager network = context.getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = network == null ? null : network.getNetworkCapabilities(network.getActiveNetwork());
            DownloadConditions.Builder conditions = new DownloadConditions.Builder();
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) conditions.requireWifi();
            client.downloadModelIfNeeded(conditions.build()).addOnCompleteListener(task -> {
                if (!valid(token) || client != translator) return;
                if (!task.isSuccessful()) { failed(token, "模型下载失败，请检查 Wi-Fi"); return; }
                modelReady = true;
                if (!original.equals(latest)) {
                    translating = false;
                    clearTimeout();
                    translateLatest();
                } else translate(client, original, token);
            });
        } catch (RuntimeException failure) { failed(token, "无法准备字幕翻译"); }
    }

    private void translate(Translator client, String original, int token) {
        timeout(() -> failed(token, "字幕翻译超时"), 20000);
        try {
            client.translate(original).addOnCompleteListener(task -> {
                if (!valid(token) || client != translator) return;
                if (task.isSuccessful()) translated(token, original, task.getResult());
                else failed(token, "字幕翻译失败，正在重试");
            });
        } catch (RuntimeException failure) { failed(token, "字幕翻译失败，正在重试"); }
    }

    private void translated(int token, String original, String result) {
        if (!valid(token)) return;
        translating = false;
        clearTimeout();
        if (ready() && original.equals(latest)) {
            displayed = original;
            translation = "zh".equals(target) ? OcrPunctuation.normalizeChineseSubtitle(result) : result;
            status = "";
            render();
        }
        if (ready()) translateLatest();
    }

    private void failed(int token, String message) {
        if (!valid(token)) return;
        translating = false;
        clearTimeout();
        closeTranslator();
        retryAt = SystemClock.uptimeMillis() + 8000;
        status = message;
    }

    private void timeout(Runnable task, long milliseconds) {
        clearTimeout();
        translationTimeout = task;
        main.postDelayed(task, milliseconds);
    }

    private void clearTimeout() {
        if (translationTimeout != null) main.removeCallbacks(translationTimeout);
        translationTimeout = null;
    }

    private void closeTranslator() {
        if (translator != null) translator.close();
        translator = null;
        modelReady = false;
        clientPair = "";
    }

    private void invalidateWork() {
        ++epoch;
        ++translationJob;
        translating = false;
        clearTimeout();
        closeTranslator();
        latest = "";
        displayed = "";
        retryAt = 0;
    }

    void showActions() {
        if (!live) return;
        LinearLayout card = makePanel(false);
        LinearLayout header = Ui.row(context);
        header.addView(badge("translate", PANEL_GREEN, Color.WHITE, 40));
        LinearLayout titleBlock = Ui.column(context);
        LinearLayout.LayoutParams titleAt = new LinearLayout.LayoutParams(0, -2, 1);
        titleAt.leftMargin = dp(12);
        header.addView(titleBlock, titleAt);
        LinearLayout titleRow = Ui.row(context);
        titleRow.addView(Ui.text(context, "字幕翻译", 17, Ui.INK, true));
        TextView liveTag = Ui.centeredText(context, "● 实时译文中", 10, accentText(), true);
        liveTag.setBackground(surface(Ui.dark ? 0x3334D399 : 0x2634D399, 12));
        Ui.pad(liveTag, 6, 3, 6, 3);
        LinearLayout.LayoutParams tagAt = new LinearLayout.LayoutParams(-2, -2);
        tagAt.leftMargin = dp(6);
        titleRow.addView(liveTag, tagAt);
        titleBlock.addView(titleRow);
        Ui.gap(titleBlock, 4);
        String from = SOURCE_NAMES[indexOf(source) + 1];
        titleBlock.addView(Ui.text(context, from + " → " + LANGUAGE_NAMES[Math.max(0, indexOf(target))], 12, Ui.MUTED, false));
        FrameLayout close = Ui.tool(context, "close", "收起弹窗", this::resume);
        close.setMinimumWidth(0);
        close.setMinimumHeight(0);
        close.setBackground(Ui.ripple(context, cardColor(), 18));
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        card.addView(header);
        Ui.gap(card, 16);
        if (!status.isEmpty()) {
            card.addView(Ui.text(context, status, 13, Ui.MUTED, false));
            Ui.gap(card, 8);
        }
        card.addView(featureRow("subtitle_sliders", "字幕设置", "识别区域、对齐、背景色与透明度", false, this::showSettings));
        Ui.gap(card, 12);
        card.addView(primaryButton("subtitle_exit", "退出翻译", this::stop));
    }

    private void showSettings() {
        LinearLayout card = makePanel(true);
        LinearLayout header = Ui.row(context);
        View mark = new View(context);
        mark.setBackground(Ui.bg(accentText(), 4, context));
        header.addView(mark, new LinearLayout.LayoutParams(dp(7), dp(20)));
        TextView title = Ui.text(context, "字幕设置", 18, Ui.INK, true);
        Ui.pad(title, 9, 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout reset = Ui.action(context, "reset", "重置", "重置字幕设置", () -> {
            frameVisible = true;
            cover = false;
            alignment = 1;
            background = COLORS[0];
            opacity = DEFAULT_OPACITY;
            saveStyle();
            showSettings();
        });
        reset.setBackground(Ui.ripple(context, cardColor(), 12));
        Ui.pad(reset, 10, 0, 10, 0);
        reset.setMinimumHeight(dp(34));
        header.addView(reset);
        card.addView(header);
        Ui.gap(card, 12);
        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        LinearLayout body = Ui.column(context);
        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(featureRow("subtitle_expand", "调整识别区域", "拖动选区框，调整字幕扫描范围", true, this::editRegion));
        LinearLayout switches = section(body, 12, 2);
        addCheck(switches, "subtitle_frame", "显示虚线框", frameVisible, value -> { frameVisible = value; saveStyle(); });
        divider(switches);
        addCheck(switches, "subtitle_cover", "译文覆盖选区", cover, value -> { cover = value; saveStyle(); });
        LinearLayout options = section(body, 12, 4);
        LinearLayout align = optionRow("译文对齐");
        LinearLayout segmented = Ui.segmented(context, new String[]{"靠左", "居中", "靠右"}, alignment,
                index -> { alignment = index; saveStyle(); });
        segmented.setBackground(Ui.bg(controlColor(), 12, context));
        align.addView(segmented, new LinearLayout.LayoutParams(dp(162), -2));
        options.addView(align);
        divider(options);
        addChoices(options, "源语言", SOURCE_NAMES,
                indexOf(source) + 1, index -> {
                    String selected = index == 0 ? "auto" : LANGUAGES[index - 1];
                    if (selected.equals(source)) return;
                    source = selected;
                    preferences.edit().putString("subtitle_source", source).apply();
                    replaceReader();
                });
        divider(options);
        addChoices(options, "翻译为", LANGUAGE_NAMES, Math.max(0, indexOf(target)), index -> {
            target = LANGUAGES[index];
            preferences.edit().putString("subtitle_target", target).apply();
        });
        addBackgroundSection(section(body, 12, 12));
        Ui.gap(card, 12);
        card.addView(primaryButton("check", "完成", this::resume));
    }

    private void addBackgroundSection(LinearLayout card) {
        LinearLayout heading = optionRow("字幕背景颜色");
        TextView colorLabel = Ui.text(context, hexColor(), 11, Ui.MUTED, false);
        colorLabel.setTypeface(Typeface.MONOSPACE);
        heading.addView(colorLabel);
        card.addView(heading);
        LinearLayout colors = Ui.row(context);
        EditText hex = Ui.field(context, "自定义颜色 #RRGGBB");
        hex.setSingleLine(true);
        hex.setFocusableInTouchMode(false);
        hex.setFocusable(false);
        hex.setOnClickListener(view -> editColor(hex));
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setTextSize(12);
        hex.setText(hexColor());
        View dot = new View(context);
        LinearLayout[] swatches = new LinearLayout[COLORS.length];
        Runnable syncColor = () -> {
            colorLabel.setText(hexColor());
            hex.setText(hexColor());
            dot.setBackground(swatch(background, 4));
            for (int i = 0; i < COLORS.length; i++) {
                boolean selected = background == COLORS[i];
                swatches[i].setSelected(selected);
                swatches[i].setBackground(selected ? surface(controlColor(), 11, accentText())
                        : Ui.ripple(context, controlColor(), 11));
            }
        };
        String[] names = {"黑色", "深绿", "深蓝", "白色"};
        for (int i = 0; i < COLORS.length; i++) {
            int color = COLORS[i];
            LinearLayout choice = Ui.row(context);
            choice.setGravity(Gravity.CENTER);
            choice.setContentDescription(names[i]);
            View sample = new View(context);
            sample.setBackground(swatch(color, 10));
            choice.addView(sample, new LinearLayout.LayoutParams(dp(13), dp(13)));
            TextView name = Ui.text(context, names[i], 11, Ui.INK, false);
            Ui.pad(name, 4, 0, 0, 0);
            choice.addView(name);
            choice.setOnClickListener(view -> {
                background = color;
                syncColor.run();
                saveStyle();
            });
            swatches[i] = choice;
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) at.leftMargin = dp(5);
            colors.addView(choice, at);
        }
        card.addView(colors);
        Ui.gap(card, 8);
        LinearLayout colorField = Ui.row(context);
        LinearLayout input = Ui.row(context);
        Ui.pad(input, 10, 0, 0, 0);
        input.setBackground(surface(controlColor(), 11));
        input.addView(dot, new LinearLayout.LayoutParams(dp(15), dp(15)));
        hex.setBackground(null);
        Ui.pad(hex, 8, 0, 6, 0);
        input.addView(hex, new LinearLayout.LayoutParams(0, -1, 1));
        colorField.addView(input, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView apply = Ui.button(context, "应用颜色", false, () -> {
            String value = hex.getText().toString().trim();
            if (!value.matches("#[0-9a-fA-F]{6}")) { hex.setError("请输入 #RRGGBB 颜色"); return; }
            background = Color.parseColor(value);
            syncColor.run();
            saveStyle();
        });
        Ui.pad(apply, 10, 0, 10, 0);
        apply.setTextSize(12);
        LinearLayout.LayoutParams applyAt = new LinearLayout.LayoutParams(-2, dp(38));
        applyAt.leftMargin = dp(8);
        colorField.addView(apply, applyAt);
        card.addView(colorField);
        Ui.gap(card, 8);
        divider(card);
        LinearLayout opacityRow = optionRow("背景不透明度");
        TextView opacityLabel = Ui.centeredText(context, opacity + "%", 12, accentText(), true);
        opacityLabel.setBackground(surface(controlColor(), 7));
        Ui.pad(opacityLabel, 7, 3, 7, 3);
        opacityRow.addView(opacityLabel);
        card.addView(opacityRow);
        SeekBar slider = new LeafSeekBar(context);
        slider.setContentDescription("字幕背景不透明度");
        slider.setMax(100);
        slider.setProgress(opacity);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { saveStyle(); }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                opacity = progress;
                opacityLabel.setText(opacity + "%");
                render();
            }
        });
        card.addView(slider, new LinearLayout.LayoutParams(-1, dp(32)));
        syncColor.run();
    }

    private void saveStyle() {
        preferences.edit().putBoolean("subtitle_frame", frameVisible).putBoolean("subtitle_cover", cover)
                .putInt("subtitle_background", background).putInt("subtitle_opacity", opacity)
                .putInt("subtitle_alignment", alignment).apply();
        render();
    }

    private int indexOf(String language) {
        for (int i = 0; i < LANGUAGES.length; i++) if (LANGUAGES[i].equals(language)) return i;
        return -1;
    }

    private int accentText() { return Ui.dark ? 0xFF9DE2BB : PANEL_GREEN; }
    private int cardColor() { return Ui.dark ? 0xBF222F28 : 0xFFF3F7F5; }
    private int controlColor() { return Ui.dark ? 0xFF1C2721 : 0xFFE5ECE8; }

    private android.graphics.drawable.GradientDrawable surface(int fill, float radius) {
        return surface(fill, radius, Ui.dark ? 0x18FFFFFF : 0x1413241B);
    }

    private android.graphics.drawable.GradientDrawable surface(int fill, float radius, int stroke) {
        var shape = Ui.bg(fill, radius, context);
        shape.setStroke(Math.max(1, dp(1)), stroke);
        return shape;
    }

    private android.graphics.drawable.GradientDrawable swatch(int color, int radius) {
        return surface(color, radius, 0x44838D87);
    }

    private String hexColor() { return String.format(java.util.Locale.ROOT, "#%06X", background & 0xFFFFFF); }

    private FrameLayout badge(String icon, int fill, int tint, int size) {
        FrameLayout badge = new FrameLayout(context);
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        badge.setBackground(Ui.bg(fill, 12, context));
        badge.addView(Ui.icon(context, icon, tint, 19),
                new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        return badge;
    }

    private LinearLayout primaryButton(String icon, String label, Runnable action) {
        LinearLayout button = Ui.row(context);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(46));
        button.setContentDescription(label);
        button.setFocusable(true);
        var gradient = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PANEL_GREEN, 0xFF2E6F4E});
        gradient.setCornerRadius(dp(16));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), gradient, null));
        button.addView(Ui.icon(context, icon, Color.WHITE, 18));
        TextView text = Ui.text(context, label, 15, Color.WHITE, true);
        Ui.pad(text, 8, 0, 0, 0);
        button.addView(text);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private LinearLayout featureRow(String icon, String label, String detail, boolean highlight, Runnable action) {
        LinearLayout row = Ui.row(context);
        Ui.pad(row, 12, 10, 12, 10);
        int tint = accentText();
        var fill = highlight ? surface(0x1924533B, 16, 0x3324533B) : surface(cardColor(), 16);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x202E6F4E), fill, null));
        row.addView(badge(icon, highlight ? PANEL_GREEN : 0x1924533B, highlight ? Color.WHITE : tint, 36));
        LinearLayout texts = Ui.column(context);
        Ui.pad(texts, 10, 0, 6, 0);
        texts.addView(Ui.text(context, label, 14, highlight ? tint : Ui.INK, true));
        Ui.gap(texts, 4);
        texts.addView(Ui.text(context, detail, 11, Ui.MUTED, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        if (highlight) {
            TextView adjust = Ui.centeredText(context, "去调整", 11, Color.WHITE, true);
            Ui.pad(adjust, 8, 5, 8, 5);
            adjust.setBackground(Ui.bg(PANEL_GREEN, 8, context));
            row.addView(adjust);
        } else row.addView(Ui.icon(context, "forward", Ui.MUTED, 17));
        row.setFocusable(true);
        row.setContentDescription(label);
        row.setOnClickListener(view -> action.run());
        return row;
    }

    private LinearLayout section(LinearLayout parent, int gap, int padding) {
        Ui.gap(parent, gap);
        LinearLayout section = Ui.column(context);
        Ui.pad(section, padding, padding, padding, padding);
        section.setBackground(surface(cardColor(), 16));
        parent.addView(section);
        return section;
    }

    private void divider(LinearLayout group) {
        View line = new View(context);
        line.setBackgroundColor(Ui.dark ? 0x12FFFFFF : 0x1413241B);
        group.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
    }

    private LinearLayout optionRow(String title) {
        LinearLayout row = Ui.row(context);
        Ui.pad(row, 8, 4, 8, 4);
        row.setMinimumHeight(dp(40));
        row.addView(Ui.text(context, title, 13, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void addCheck(LinearLayout parent, String icon, String title, boolean selected, java.util.function.Consumer<Boolean> change) {
        LinearLayout row = Ui.row(context);
        Ui.pad(row, 10, 5, 10, 5);
        row.setMinimumHeight(dp(46));
        row.addView(Ui.icon(context, icon, Ui.MUTED, 16));
        TextView label = Ui.text(context, title, 13, Ui.INK, true);
        Ui.pad(label, 8, 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        LeafSwitch toggle = new LeafSwitch(context);
        toggle.setContentDescription(title);
        toggle.setChecked(selected);
        toggle.setOnCheckedChangeListener((view, checked) -> change.accept(checked));
        row.addView(toggle);
        row.setOnClickListener(view -> toggle.toggle());
        parent.addView(row);
    }

    private void addChoices(LinearLayout parent, String title, String[] labels, int selected, java.util.function.IntConsumer change) {
        LinearLayout row = optionRow(title);
        LinearLayout options = Ui.column(context);
        options.setBackground(surface(controlColor(), 14));
        options.setClipToOutline(true);
        options.setVisibility(View.GONE);
        TextView current = Ui.centeredText(context, labels[selected], 14, Ui.INK, true);
        LinearLayout chip = TranslationPane.languageChip(current, title,
                () -> options.setVisibility(options.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        row.addView(chip, new LinearLayout.LayoutParams(dp(145), dp(40)));
        parent.addView(row);
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            TextView option = Ui.text(context, labels[index], 15, Ui.INK, false);
            Ui.pad(option, 12, 0, 12, 0);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setBackground(Ui.ripple(context, 0, 0));
            option.setOnClickListener(view -> {
                current.setText(labels[index]);
                selectLanguage(options, index);
                options.setVisibility(View.GONE);
                change.accept(index);
            });
            option.setContentDescription(title + "，" + labels[index]);
            options.addView(option, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        selectLanguage(options, selected);
        parent.addView(options);
    }

    private void selectLanguage(LinearLayout options, int selected) {
        for (int i = 0; i < options.getChildCount(); i++) {
            TextView option = (TextView) options.getChildAt(i);
            boolean active = i == selected;
            option.setSelected(active);
            option.setTextColor(active ? accentText() : Ui.INK);
            option.setTypeface(Typeface.create(active ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
    }

    private int settingsHeight(int imeBottom) {
        return Math.max(dp(90), Math.min(screen.height() - imeBottom - dp(26), dp(650)));
    }

    private LinearLayout makePanel(boolean settings) {
        removePanel();
        invalidateWork();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x4D0A1A12);
        root.setOnClickListener(view -> resume());
        LinearLayout card = Ui.column(context);
        card.setClickable(true);
        Ui.pad(card, 18, 16, 18, 16);
        card.setBackground(surface(Ui.dark ? 0xEA161F1B : 0xF5FCFEFD, settings ? 30 : 28,
                Ui.dark ? 0x33FFFFFF : 0xB3FFFFFF));
        card.setElevation(dp(16));
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(Math.min(screen.width() - dp(24), dp(settings ? 420 : 370)),
                settings ? settingsHeight(0) : -2, Gravity.CENTER);
        root.addView(card, at);
        show(root, settings ? "空叶字幕设置" : "空叶字幕操作");
        return card;
    }

    private void show(FrameLayout root, String title) {
        panel = root;
        // Keep the immersive app focused. A focusable overlay can reveal the system bars
        // and change the position of the underlying content between selection and capture.
        try { overlays.addView(root, window(-1, -1, Gravity.TOP | Gravity.LEFT, true, title)); }
        catch (RuntimeException failure) { stop(); Feedback.show(context, "无法显示字幕翻译窗口"); }
    }

    private void editColor(EditText field) {
        if (Build.VERSION.SDK_INT < 30) return;
        FrameLayout root = panel;
        WindowInsets insets = root.getRootWindowInsets();
        WindowInsetsController controller = root.getWindowInsetsController();
        if (insets == null || controller == null) return;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) root.getLayoutParams();
        if ((params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
            int hiddenBars = 0;
            int visibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            if (!insets.isVisible(WindowInsets.Type.statusBars())) {
                hiddenBars |= WindowInsets.Type.statusBars();
                visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
            if (!insets.isVisible(WindowInsets.Type.navigationBars())) {
                hiddenBars |= WindowInsets.Type.navigationBars();
                visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }
            root.setSystemUiVisibility(visibility);
            if (hiddenBars != 0) controller.hide(hiddenBars);
            root.setOnApplyWindowInsetsListener((view, applied) -> {
                int bottom = applied.getInsets(WindowInsets.Type.ime()).bottom;
                LinearLayout card = (LinearLayout) root.getChildAt(0);
                FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) card.getLayoutParams();
                int height = settingsHeight(bottom);
                if (layout.height != height) {
                    layout.height = height;
                    card.setLayoutParams(layout);
                }
                boolean compact = bottom > 0 && height < dp(210);
                for (int i = 0; i < card.getChildCount(); i++) {
                    View child = card.getChildAt(i);
                    if (!(child instanceof ScrollView)) child.setVisibility(compact ? View.GONE : View.VISIBLE);
                }
                root.setPadding(0, 0, 0, bottom);
                if (bottom > 0) field.post(() -> field.requestRectangleOnScreen(
                        new Rect(0, 0, field.getWidth(), field.getHeight())));
                return applied;
            });
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            overlays.updateViewLayout(root, params);
        }
        field.setFocusableInTouchMode(true);
        field.requestFocus();
        field.post(() -> {
            if (panel == root) controller.show(WindowInsets.Type.ime());
        });
    }

    private void resume() {
        removePanel();
        saveStyle();
        schedule(120);
    }

    private void removePanel() {
        removeOverlay(panel);
        panel = null;
        selection = null;
    }

    void displayChanged() {
        if (!active) return;
        Rect next = displayBounds();
        if (next.equals(screen)) return;
        float sx = next.width() / (float) screen.width(), sy = next.height() / (float) screen.height();
        area.set(Math.round(area.left * sx), Math.round(area.top * sy), Math.round(area.right * sx), Math.round(area.bottom * sy));
        screen.set(next);
        if (!live || selection != null) { editRegion(); return; }
        removePanel();
        invalidateWork();
        clearText();
        render();
        schedule(150);
    }

    void stop() {
        if (!active) return;
        active = false;
        live = false;
        removePanel();
        main.removeCallbacks(poll);
        invalidateWork();
        clearText();
        if (reader != null) reader.close();
        if (identifier != null) identifier.close();
        reader = null;
        identifier = null;
        removeOverlay(layer);
        layer = null;
        caption = null;
    }

    private void removeOverlay(View view) {
        if (view == null || !view.isAttachedToWindow()) return;
        try { overlays.removeViewImmediate(view); } catch (IllegalArgumentException ignored) {}
    }
}
