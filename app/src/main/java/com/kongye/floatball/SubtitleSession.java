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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.function.BooleanSupplier;
import java.util.*;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

/** An explicit, in-memory OCR session over one user-selected screen region. */
final class SubtitleSession {
    private static final String[] LANGUAGES = {"en", "zh", "ja", "ko"};
    private static final String[] LANGUAGE_NAMES = TranslationPane.languageOptions(false);
    private static final String[] SOURCE_NAMES = TranslationPane.languageOptions(true);
    private static final int[] COLORS = {0xFF141414, 0xFF1B4332, 0xFF1B2A4A, 0xFFFFFFFF};
    private static final int PANEL_GREEN = 0xFF24533B;
    private static final int DEFAULT_OPACITY = 50;
    private static final int POLL_MS = 650;
    private static final int PREVIEW_NAME_COLOR = 0xFF82C4A1;
    private static final List<String> PREVIEW_SOURCE = List.of("Kongye Float", "Hello, this is the Kongye Float.");
    private static final List<String> PREVIEW_TRANSLATION = List.of("空叶", "你好，这里是空叶悬浮球。");
    private static final SubtitleFrame PREVIEW_FRAME = new SubtitleFrame(1000, 100, List.of(
            new SubtitleLayout.Block(PREVIEW_SOURCE.get(0), 30, 12, 970, 36, PREVIEW_NAME_COLOR, 24),
            new SubtitleLayout.Block(PREVIEW_SOURCE.get(1), 30, 54, 970, 78, Color.WHITE, 24)));
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
    private FrameLayout preview;
    private SubtitleCaptionView previewOriginal, previewCover;
    private TextView previewFloating;
    private ResizableCropView selection;
    private FrameLayout layer;
    private TextView caption;
    private SubtitleCaptionView styledCaption;
    private SubtitleFrame latestFrame = SubtitleFrame.EMPTY;
    private List<String> translatedBlocks = List.of();
    private String displayedKey = "";
    private final LinkedHashMap<String, String> translatedCache = new LinkedHashMap<>();
    private TextRecognizer reader;
    private String readerLanguage = "";
    private LanguageIdentifier identifier;
    private Translator translator;
    private Runnable translationTimeout;
    private boolean active, live, frameBusy, translating, modelReady;
    private boolean frameVisible, cover, captionBelow, presentationHidden;
    private int epoch, translationJob, background, opacity, coverOpacity, alignment, fontScale;
    private String source, target, clientPair = "";
    private String detectedSource = "";
    private String latest = "", translation = "";
    private String status = "";
    private boolean languageChanged, followBackground, followTextColor;
    private int sampledBackground;
    private long retryAt;
    static final int EXTERNAL_TEXT_LIMIT = 6000;
    record ExternalRequest(String id, SubtitleFrame frame, String packageName, String source, String target) {}
    private boolean freshCaptureRequested, externalCaptureRequested, externalPaused;
    private Runnable freshCaptureTimeout;
    private ExternalRequest externalRequest;
    private String pendingExternalText = "", awaitingSourcePackage = "", overrideKey = "";
    private SubtitleFrame overrideFrame;
    private List<String> overrideBlocks = List.of();

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
        frameVisible = preferences.getBoolean("subtitle_frame", false);
        cover = preferences.getBoolean("subtitle_follow_original", false) || preferences.getBoolean("subtitle_cover", true);
        captionBelow = preferences.getBoolean("subtitle_caption_below", false);
        background = preferences.getInt("subtitle_background", COLORS[0]);
        followBackground = preferences.getBoolean("subtitle_follow_background", background == COLORS[0]);
        followTextColor = preferences.getBoolean("subtitle_follow_text_color", true);
        opacity = Math.max(0, Math.min(100, preferences.getInt("subtitle_opacity", DEFAULT_OPACITY)));
        coverOpacity = Math.max(0, Math.min(100, preferences.getInt("subtitle_cover_opacity", 100)));
        fontScale = Math.max(60, Math.min(160, preferences.getInt("subtitle_font_scale", 90)));
        alignment = Math.max(0, Math.min(2, preferences.getInt("subtitle_alignment", 0)));
        source = preferences.getString("subtitle_source", "auto");
        target = preferences.getString("subtitle_target", "zh");
        detectedSource = "";
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
        caption = newCaption();
        layer.addView(caption);
        styledCaption = new SubtitleCaptionView(context);
        styledCaption.setContentDescription("按原文位置显示的字幕译文");
        layer.addView(styledCaption);
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
        readerLanguage = ocrLanguage();
        reader = switch (readerLanguage) {
            case "en" -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            case "ja" -> TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            case "ko" -> TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
            default -> TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        };
    }

    private String ocrLanguage() {
        String language="auto".equals(source)?detectedSource:source;
        return switch(language){case "en","ja","ko"->language;default->"zh";};
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

    private TextView newCaption() {
        TextView text = Ui.text(context, "", 17, Color.WHITE, false);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
        if (Build.VERSION.SDK_INT >= 33) {
            text.setLineBreakStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_STYLE_STRICT);
            text.setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
        }
        return text;
    }

    private int subtitleBackground(int percent) {
        return (effectiveBackground() & 0xFFFFFF) | (Math.round(percent * 2.55f) << 24);
    }

    private int effectiveBackground() {
        return followBackground && sampledBackground != 0 ? sampledBackground : background;
    }

    private int textColorOverride() {
        if (followTextColor) return 0;
        return Color.luminance(effectiveBackground() | 0xFF000000) >= .179f ? Color.BLACK : Color.WHITE;
    }

    private void render() {
        renderPreview();
        if (layer == null || caption == null) return;
        layer.invalidate();
        boolean manual = overrideFrame != null && overrideKey.equals(latestFrame.key);
        SubtitleFrame displayFrame = manual ? overrideFrame : latestFrame;
        List<String> displayBlocks = manual ? overrideBlocks : translatedBlocks;
        boolean visible = (manual || !translation.isEmpty() && displayedKey.equals(latestFrame.key)
                && translatedBlocks.size() == latestFrame.blocks.size()) && !area.isEmpty();
        int visibility = !visible ? View.GONE : presentationHidden ? View.INVISIBLE : View.VISIBLE;
        int[] origin = new int[2];
        layer.getLocationOnScreen(origin);
        if (cover) {
            caption.setVisibility(View.GONE);
            styledCaption.setVisibility(visibility);
            if (!visible) return;
            FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(area.width(), area.height());
            at.leftMargin = area.left - origin[0]; at.topMargin = area.top - origin[1];
            styledCaption.setLayoutParams(at);
            styledCaption.show(displayFrame, displayBlocks, target, subtitleBackground(coverOpacity), fontScale, textColorOverride());
            return;
        }
        styledCaption.setVisibility(View.GONE);
        caption.setVisibility(visibility);
        if (!visible) return;
        Rect region = new Rect(area);
        region.offset(-origin[0], -origin[1]);
        renderFloating(caption, displayFrame, displayBlocks, region, screen.bottom - origin[1], false);
    }

    private void renderFloating(TextView text, SubtitleFrame frame, List<String> blocks, Rect region,
                                int bottom, boolean small) {
        text.setTextLocale(Locale.forLanguageTag(small ? "zh" : target));
        int horizontal = alignment == 0 ? Gravity.LEFT : alignment == 2 ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL;
        text.setGravity(horizontal | Gravity.TOP);
        text.setBackground(Ui.bg(subtitleBackground(opacity), 4, context));
        text.setLineSpacing(dp(small ? 1 : 3), 1f);
        int width = region.width();
        int side = Math.min(dp(10), width / 5);
        int room = Math.max(0, captionBelow ? bottom - region.bottom : region.top);
        text.setPadding(side, dp(small ? 2 : 4), side, dp(small ? 2 : 4));
        int percent = fontScale;
        while (true) {
            text.setText(floatingText(text, frame, blocks, region.height(), percent));
            text.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (text.getMeasuredHeight() <= room || percent <= 35) break;
            percent = Math.max(35, percent - 5);
        }
        int gap = Math.min(dp(small ? 3 : 6), Math.max(0, room - text.getMeasuredHeight()));
        int height = Math.min(text.getMeasuredHeight(), room - gap);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = region.left;
        params.topMargin = captionBelow ? region.bottom + gap : region.top - gap - height;
        text.setLayoutParams(params);
    }

    private CharSequence floatingText(TextView view, SubtitleFrame frame, List<String> blocks, int height, int percent) {
        android.text.SpannableStringBuilder content = new android.text.SpannableStringBuilder();
        float sourceScale = height / (float) frame.height;
        int textColor = textColorOverride();
        for (int i = 0; i < blocks.size(); i++) {
            String text = blocks.get(i);
            if (text.isBlank()) continue;
            if (content.length() > 0) content.append('\n');
            int start = content.length();
            SubtitleLayout.Block original = frame.blocks.get(i);
            content.append(text);
            int size = Math.round(SubtitleCaptionView.fontSize(view.getPaint(), text, original.lineHeight() * sourceScale, percent));
            content.setSpan(new android.text.style.AbsoluteSizeSpan(size), start, content.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            content.setSpan(new android.text.style.ForegroundColorSpan(textColor == 0 ? original.color() : textColor),
                    start, content.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return content;
    }

    private void schedule(long delay) {
        main.removeCallbacks(poll);
        if (active && live && panel == null && !externalPaused) main.postDelayed(poll, delay);
    }

    private boolean ready() { return active && live && panel == null && !externalPaused && !blocked.getAsBoolean(); }

    private void capture() {
        if (!active || !live) return;
        if (externalPaused) return;
        if (!awaitingSourcePackage.isEmpty()) {
            if (!awaitingSourcePackage.equals(AssistService.foregroundPackage)) { schedule(400); return; }
            awaitingSourcePackage = "";
        }
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
        View presentation = cover ? styledCaption : caption;
        boolean hideCaption = intersects(presentation, selected);
        if (hideCaption) {
            presentationHidden = true;
            presentation.setVisibility(View.INVISIBLE);
        }
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
        if (hideCaption) {
            presentationHidden = false;
            render();
        }
    }

    private void read(Bitmap bitmap, String error, int token) {
        if (bitmap == null || token != epoch || !ready()) {
            if (bitmap != null) bitmap.recycle();
            if (token == epoch && freshCaptureRequested) {
                freshCaptureFailed(error == null ? "未能重新读取选区，请返回原页面重试" : error);
            } else if (error != null && token == epoch && ready()) {
                clearText();
                status = error;
            }
            finishFrame();
            return;
        }
        try {
            if(reader==null||!readerLanguage.equals(ocrLanguage()))replaceReader();
            reader.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener(task -> {
                try {
                if (token == epoch && ready()) {
                    if (task.isSuccessful()) {
                        latestFrame = SubtitleFrame.read(task.getResult(), bitmap);
                        int detected = latestFrame.background;
                        if (detected != 0 && (sampledBackground == 0 || SubtitleLayout.distance(sampledBackground, detected) >= 14))
                            sampledBackground = detected;
                        latest = latestFrame.text;
                        if (!pendingExternalText.isEmpty()) acceptPendingExternal();
                        if (latest.isEmpty()) {
                            clearText();
                            if (freshCaptureRequested) { freshCaptureFailed("选区内未识别到文字，请调整识别区域后重试"); return; }
                            if (!translating) status = "";
                        } else {
                            if (freshCaptureRequested) {
                                boolean external = externalCaptureRequested;
                                clearFreshRequest();
                                if (external) { openExternalTranslation(); return; }
                                Feedback.success(context, "已重新识别，正在翻译");
                            }
                            render();
                            translateLatest();
                        }
                    } else {
                        clearText();
                        status = "字幕识别失败，正在重试";
                        if (freshCaptureRequested) freshCaptureFailed("重新识别失败，请重试");
                    }
                }
                } catch (RuntimeException failure) {
                    clearText(); status = "字幕识别失败，正在重试";
                    if (freshCaptureRequested) freshCaptureFailed("重新识别失败，请重试");
                } finally { bitmap.recycle(); finishFrame(); }
            });
        } catch (RuntimeException errorDuringRead) {
            bitmap.recycle();
            status = "字幕识别失败，正在重试";
            if (freshCaptureRequested) freshCaptureFailed("重新识别失败，请重试");
            finishFrame();
        }
    }

    private void finishFrame() {
        frameBusy = false;
        schedule(POLL_MS);
    }

    private void clearText() {
        overrideKey = ""; overrideFrame = null; overrideBlocks = List.of();
        latest = "";
        latestFrame = SubtitleFrame.EMPTY;
        sampledBackground = 0;
        displayedKey = "";
        translatedBlocks = List.of();
        translation = ""; render();
    }

    private void translateLatest() {
        if (overrideFrame != null && overrideKey.equals(latestFrame.key)) return;
        if (translating || latest.isEmpty() || latestFrame.key.equals(displayedKey) || SystemClock.uptimeMillis() < retryAt) return;
        translating = true;
        int token = ++translationJob;
        SubtitleFrame original = latestFrame;
        if (!source.equals("auto")) { prepareClient(source, original, token); return; }
        try {
            if (identifier == null) identifier = LanguageIdentification.getClient(
                    new LanguageIdentificationOptions.Builder().setConfidenceThreshold(.25f).build());
            Runnable fallback = () -> prepareClient(TranslationPolicy.scriptLanguage(original.text), original, token);
            timeout(fallback, 4000);
            identifier.identifyLanguage(original.text).addOnCompleteListener(task -> {
                if (!valid(token) || translationTimeout != fallback) return;
                String language = task.isSuccessful() ? TranslateLanguage.fromLanguageTag(task.getResult()) : null;
                prepareClient(language == null ? TranslationPolicy.scriptLanguage(original.text) : language, original, token);
            });
        } catch (RuntimeException failure) {
            prepareClient(TranslationPolicy.scriptLanguage(original.text), original, token);
        }
    }

    private boolean valid(int token) { return token == translationJob && active && translating; }

    private void prepareClient(String language, SubtitleFrame original, int token) {
        if (!valid(token)) return;
        if ("auto".equals(source)) detectedSource = language;
        clearTimeout();
        if (language.isEmpty()) { failed(token, "请选择字幕源语言"); return; }
        if (language.equals(target)) {
            ArrayList<String> unchanged = new ArrayList<>();
            for (SubtitleLayout.Block block : original.blocks) unchanged.add(block.text());
            translated(token, original, unchanged); return;
        }
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
                if (!original.key.equals(latestFrame.key)) {
                    translating = false;
                    clearTimeout();
                    translateLatest();
                } else translate(client, original, token);
            });
        } catch (RuntimeException failure) { failed(token, "无法准备字幕翻译"); }
    }

    private void translate(Translator client, SubtitleFrame original, int token) {
        timeout(() -> failed(token, "字幕翻译超时"), 20000);
        try {
            ArrayList<Task<String>> jobs = new ArrayList<>();
            ArrayList<SubtitleTranslation.Plan> plans = new ArrayList<>();
            ArrayList<String> pieces = new ArrayList<>();
            String pair = clientPair;
            String language = pair.substring(0,pair.indexOf(':'));
            for (SubtitleLayout.Block block : original.blocks) {
                SubtitleTranslation.Plan plan = SubtitleTranslation.plan(block.text(),language);
                plans.add(plan);
                for(String piece:plan.pieces()) {
                    pieces.add(piece);
                    String cached = translatedCache.get(pair + "\u0000" + piece);
                    jobs.add(cached == null ? client.translate(piece) : Tasks.forResult(cached));
                }
            }
            Tasks.whenAllComplete(jobs).addOnCompleteListener(task -> {
                if (!valid(token) || client != translator) return;
                ArrayList<String> results = new ArrayList<>();
                for (int i = 0; i < jobs.size(); i++) {
                    Task<String> job = jobs.get(i);
                    if (!job.isSuccessful() || job.getResult() == null || job.getResult().isBlank()) { failed(token, "字幕翻译失败，正在重试"); return; }
                    results.add(job.getResult());
                    translatedCache.put(pair + "\u0000" + pieces.get(i), job.getResult());
                }
                ArrayList<String> paragraphs = new ArrayList<>();
                int cursor=0;
                try {
                    for(SubtitleTranslation.Plan plan:plans) {
                        int end=cursor+plan.pieces().size();
                        paragraphs.add(plan.join(results.subList(cursor,end),target));cursor=end;
                    }
                }catch(IllegalArgumentException incomplete){failed(token,"字幕翻译失败，正在重试");return;}
                while (translatedCache.size() > 128) translatedCache.remove(translatedCache.keySet().iterator().next());
                translated(token, original, paragraphs);
            });
        } catch (RuntimeException failure) { failed(token, "字幕翻译失败，正在重试"); }
    }

    private void translated(int token, SubtitleFrame original, List<String> results) {
        if (!valid(token)) return;
        translating = false;
        clearTimeout();
        if (ready() && original.key.equals(latestFrame.key)) {
            displayedKey = original.key;
            ArrayList<String> formatted = new ArrayList<>();
            for (String value : results) formatted.add(OcrPunctuation.formatTranslatedSubtitle(value, target));
            translatedBlocks = List.copyOf(formatted);
            translation = String.join("\n", translatedBlocks);
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
        clearFreshRequest();
        externalRequest = null; externalPaused = false; pendingExternalText = ""; awaitingSourcePackage = "";
        if (layer != null) layer.setVisibility(View.VISIBLE);
        ++epoch;
        ++translationJob;
        translating = false;
        clearTimeout();
        closeTranslator();
        latest = "";
        displayedKey = "";
        retryAt = 0;
        languageChanged = false;
    }

    void showActions() {
        if (!live) return;
        if (freshCaptureRequested) { clearFreshRequest(); status = ""; }
        if (externalPaused && externalRequest != null) cancelExternal(externalRequest.id());
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
        card.addView(settingsAction());
        Ui.gap(card, 10);
        LinearLayout retries = Ui.row(context);
        LinearLayout recognize = Ui.action(context, "refresh", "重新识别", "重新识别当前选区", () -> recognizeAgain(false));
        LinearLayout external = Ui.action(context, "external", "翻译 App", "用翻译 App 重译当前选区", () -> recognizeAgain(true));
        recognize.setGravity(Gravity.CENTER); external.setGravity(Gravity.CENTER);
        recognize.setBackground(Ui.ripple(context, cardColor(), 14)); external.setBackground(Ui.ripple(context, cardColor(), 14));
        retries.addView(recognize, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams externalAt = new LinearLayout.LayoutParams(0, dp(44), 1); externalAt.leftMargin = dp(8);
        retries.addView(external, externalAt); card.addView(retries);
        Ui.gap(card, 12);
        card.addView(primaryButton("subtitle_exit", "退出翻译", this::stop));
    }

    private void showSettings() {
        populateSettings(makePanel(true));
    }

    private void recognizeAgain(boolean external) {
        if (!active || !live || area.isEmpty()) return;
        removePanel(); invalidateWork(); clearText(); translatedCache.clear();
        freshCaptureRequested = true; externalCaptureRequested = external;
        status = "正在重新识别选区…";
        freshCaptureTimeout = () -> freshCaptureFailed("重新识别超时，请返回原页面重试");
        main.postDelayed(freshCaptureTimeout, 12000); schedule(160);
    }

    private void clearFreshRequest() {
        freshCaptureRequested = externalCaptureRequested = false;
        if (freshCaptureTimeout != null) main.removeCallbacks(freshCaptureTimeout);
        freshCaptureTimeout = null;
    }

    private void freshCaptureFailed(String message) {
        clearFreshRequest(); status = message;
        Feedback.show(context, message);
        if (active && live) showActions();
    }

    private void openExternalTranslation() {
        ++translationJob; translating = false; clearTimeout(); closeTranslator();
        status = "";
        String from = "auto".equals(source) ? detectedSource : source;
        if (from.isEmpty()) from = TranslationPolicy.scriptLanguage(latest);
        externalRequest = new ExternalRequest(UUID.randomUUID().toString(), latestFrame, AssistService.foregroundPackage, from, target);
        externalPaused = true; main.removeCallbacks(poll);
        if (layer != null) layer.setVisibility(View.INVISIBLE);
        try {
            context.startActivity(new android.content.Intent(context, SubtitleExternalActivity.class)
                    .putExtra("request_id", externalRequest.id()).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException unavailable) {
            String id = externalRequest.id(); cancelExternal(id); status = "无法打开翻译面板，请重试";
            showActions(); Feedback.show(context, status);
        }
    }

    ExternalRequest externalRequest(String id) {
        return active && externalPaused && externalRequest != null && externalRequest.id().equals(id) ? externalRequest : null;
    }

    boolean submitExternal(String id, String text) {
        if (externalRequest(id) == null || text == null || text.isBlank() || text.length() > EXTERNAL_TEXT_LIMIT) return false;
        pendingExternalText = text.strip();
        finishExternalPause(); return true;
    }

    void cancelExternal(String id) {
        if (externalRequest(id) == null) return;
        pendingExternalText = ""; finishExternalPause(); externalRequest = null;
    }

    private void finishExternalPause() {
        awaitingSourcePackage = externalRequest.packageName(); externalPaused = false;
        if (layer != null) layer.setVisibility(View.VISIBLE);
        schedule(350);
    }

    private void acceptPendingExternal() {
        String text = pendingExternalText; pendingExternalText = "";
        ExternalRequest request = externalRequest; externalRequest = null;
        if (request == null || !request.frame().key.equals(latestFrame.key) || !request.target().equals(target)) {
            Feedback.show(context, "当前对白已变化，未覆盖旧译文，请重新识别"); return;
        }
        String[] paragraphs = text.strip().split("\\n+");
        overrideKey = latestFrame.key;
        if (paragraphs.length == latestFrame.blocks.size()) {
            ArrayList<String> values = new ArrayList<>();
            for (String paragraph : paragraphs) values.add(OcrPunctuation.formatTranslatedSubtitle(paragraph, target));
            overrideFrame = latestFrame; overrideBlocks = List.copyOf(values);
        } else {
            int left=latestFrame.width, top=latestFrame.height, right=0, bottom=0;
            SubtitleLayout.Block style=latestFrame.blocks.get(0);
            for (SubtitleLayout.Block block:latestFrame.blocks) {
                left=Math.min(left,block.left()); top=Math.min(top,block.top()); right=Math.max(right,block.right()); bottom=Math.max(bottom,block.bottom());
                if (block.text().length()>style.text().length()) style=block;
            }
            overrideFrame=new SubtitleFrame(latestFrame.width,latestFrame.height,List.of(new SubtitleLayout.Block(latestFrame.text,
                    left,top,right,bottom,style.color(),style.lineHeight())));
            overrideBlocks=List.of(OcrPunctuation.formatTranslatedSubtitle(text,target));
        }
        status=""; Feedback.success(context,"译文已显示，下一段对白继续自动翻译");
    }

    private void populateSettings(LinearLayout card) {
        card.removeAllViews();
        LinearLayout header = Ui.row(context);
        View mark = new View(context);
        mark.setBackground(Ui.bg(accentText(), 4, context));
        header.addView(mark, new LinearLayout.LayoutParams(dp(7), dp(20)));
        TextView title = Ui.text(context, "字幕设置", 18, Ui.INK, true);
        Ui.pad(title, 9, 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout reset = Ui.action(context, "reset", "重置", "重置字幕设置", () -> {
            frameVisible = false;
            cover = true;
            captionBelow = false;
            alignment = 0;
            background = COLORS[0];
            followBackground = true;
            followTextColor = true;
            opacity = DEFAULT_OPACITY;
            coverOpacity = 100;
            fontScale = 90;
            populateSettings(card);
            saveStyle();
        });
        reset.setBackground(Ui.ripple(context, cardColor(), 12));
        Ui.pad(reset, 10, 0, 10, 0);
        reset.setMinimumHeight(dp(34));
        header.addView(reset);
        card.addView(header);
        Ui.gap(card, 12);
        addPreview(card);
        Ui.gap(card, 10);
        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        LinearLayout body = Ui.column(context);
        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = section(body, 0, 4);
        addTranslationLanguages(controls);
        divider(controls);
        LinearLayout region = optionRow("subtitle_expand", "调整识别区域");
        region.setMinimumHeight(dp(46));
        TextView adjustRegion = Ui.centeredText(context, "去调整", 11, Color.WHITE, true);
        Ui.pad(adjustRegion, 8, 5, 8, 5);
        adjustRegion.setBackground(Ui.ripple(context, 0xFF25553D, 8));
        adjustRegion.setFocusable(true);
        adjustRegion.setContentDescription("去调整");
        adjustRegion.setOnClickListener(view -> editRegion());
        region.addView(adjustRegion, new LinearLayout.LayoutParams(-2, -2));
        region.setOnClickListener(view -> editRegion());
        controls.addView(region);
        divider(controls);
        LinearLayout positionOptions = Ui.column(context);
        populatePositionOptions(positionOptions);
        positionOptions.setVisibility(cover ? View.GONE : View.VISIBLE);
        Runnable[] syncOpacity = {() -> {}};
        addCheck(controls, "subtitle_cover", "译文覆盖选区", cover, value -> {
            cover = value;
            positionOptions.setVisibility(cover ? View.GONE : View.VISIBLE);
            syncOpacity[0].run();
            saveStyle();
        });
        divider(controls);
        addCheck(controls, "subtitle_frame", "显示虚线框", frameVisible, value -> { frameVisible = value; saveStyle(); });
        LinearLayout options = section(body, 12, 12);
        options.addView(positionOptions);
        LinearLayout textColorRow = addCheck(options, "text", "译文字色跟随画面", followTextColor, value -> {
            followTextColor = value;
            saveStyle();
        });
        Ui.pad(textColorRow, 8, 4, 8, 4);
        textColorRow.setMinimumHeight(dp(40));
        divider(options);
        addFontSize(options);
        syncOpacity[0] = addBackgroundSection(section(body, 12, 12));
        Ui.gap(card, 12);
        card.addView(primaryButton(null, "完成", this::resume));
        renderPreview();
    }

    private void populatePositionOptions(LinearLayout options) {
        LinearLayout position = optionRow("subtitle_position", "译文位置");
        LinearLayout sides = Ui.segmented(context, new String[]{"选区上方", "选区下方"}, captionBelow ? 1 : 0,
                index -> { captionBelow = index == 1; saveStyle(); });
        sides.setBackground(Ui.bg(controlColor(), 12, context));
        position.addView(sides, new LinearLayout.LayoutParams(dp(162), -2));
        options.addView(position);
        divider(options);
        LinearLayout align = optionRow("subtitle_align", "译文对齐");
        LinearLayout segmented = Ui.segmented(context, new String[]{"靠左", "居中", "靠右"}, alignment,
                index -> { alignment = index; saveStyle(); });
        segmented.setBackground(Ui.bg(controlColor(), 12, context));
        align.addView(segmented, new LinearLayout.LayoutParams(dp(162), -2));
        options.addView(align);
        divider(options);
    }

    private void addPreview(LinearLayout card) {
        card.addView(Ui.text(context, "实时预览", 11, Ui.MUTED, true));
        Ui.gap(card, 5);
        preview = new FrameLayout(context) {
            private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (!frameVisible) return;
                Rect r = previewArea(this);
                stroke.setColor(Ui.BRAND);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1));
                stroke.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(3)}, 0));
                canvas.drawRect(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1, stroke);
            }
        };
        preview.setBackground(surface(0xFF303832, 12, 0xFF4F6054));
        preview.setClipToOutline(true);
        previewOriginal = new SubtitleCaptionView(context);
        previewCover = new SubtitleCaptionView(context);
        previewFloating = newCaption();
        preview.addView(previewOriginal);
        preview.addView(previewCover);
        preview.addView(previewFloating);
        preview.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (view == preview && (r - l != or - ol || b - t != ob - ot)) renderPreview();
        });
        card.addView(preview, new LinearLayout.LayoutParams(-1, dp(screen.height() < dp(450) ? 100 : 116)));
    }

    private Rect previewArea(View view) {
        int height = dp(36);
        int top = (view.getHeight() - height) / 2;
        return new Rect(dp(10), top, view.getWidth() - dp(10), top + height);
    }

    private void renderPreview() {
        if (preview == null || preview.getWidth() <= dp(20) || preview.getHeight() <= dp(36)) return;
        Rect region = previewArea(preview);
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(region.width(), region.height());
        at.leftMargin = region.left;
        at.topMargin = region.top;
        previewOriginal.setLayoutParams(new FrameLayout.LayoutParams(at));
        previewOriginal.show(PREVIEW_FRAME, PREVIEW_SOURCE, "en", 0, 100, 0);
        previewCover.setVisibility(cover ? View.VISIBLE : View.GONE);
        previewFloating.setVisibility(cover ? View.GONE : View.VISIBLE);
        if (cover) {
            previewCover.setLayoutParams(at);
            previewCover.show(PREVIEW_FRAME, PREVIEW_TRANSLATION, "zh", subtitleBackground(coverOpacity),
                    fontScale, textColorOverride());
        } else renderFloating(previewFloating, PREVIEW_FRAME, PREVIEW_TRANSLATION, region, preview.getHeight(), true);
        preview.invalidate();
    }

    private void addFontSize(LinearLayout card) {
        LinearLayout row = optionRow("subtitle_font_size", "译文字号");
        TextView value = Ui.text(context, fontScale + "%", 12, accentText(), true);
        row.addView(value);
        card.addView(row);
        SeekBar slider = new LeafSeekBar(context);
        slider.setContentDescription("译文字号");
        slider.setMax(20);
        slider.setProgress((fontScale - 60) / 5);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                fontScale = 60 + progress * 5;
                value.setText(fontScale + "%");
                saveStyle();
            }
        });
        card.addView(slider, new LinearLayout.LayoutParams(-1, dp(32)));
    }

    private Runnable addBackgroundSection(LinearLayout card) {
        LinearLayout heading = optionRow("subtitle_background", "字幕背景颜色");
        TextView colorLabel = Ui.text(context, hexColor(effectiveBackground()), 11, Ui.MUTED, false);
        colorLabel.setTypeface(Typeface.MONOSPACE);
        heading.addView(colorLabel);
        card.addView(heading);
        LinearLayout manual = Ui.column(context);
        Runnable syncBackgroundMode = () -> {
            manual.setVisibility(followBackground ? View.GONE : View.VISIBLE);
            colorLabel.setText(hexColor(effectiveBackground()));
        };
        addCheck(card, "subtitle_color_sample", "跟随画面", followBackground, value -> {
            followBackground = value;
            syncBackgroundMode.run();
            saveStyle();
        });
        LinearLayout colors = Ui.row(context);
        EditText hex = Ui.field(context, "自定义颜色 #RRGGBB");
        hex.setSingleLine(true);
        hex.setFocusableInTouchMode(false);
        hex.setFocusable(false);
        hex.setOnClickListener(view -> editColor(hex));
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setTextSize(12);
        hex.setText(hexColor(background));
        View dot = new View(context);
        LinearLayout[] swatches = new LinearLayout[COLORS.length];
        Runnable syncColor = () -> {
            colorLabel.setText(hexColor(effectiveBackground()));
            hex.setText(hexColor(background));
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
        manual.addView(colors);
        Ui.gap(manual, 8);
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
        manual.addView(colorField);
        card.addView(manual);
        syncColor.run();
        syncBackgroundMode.run();
        Ui.gap(card, 8);
        divider(card);
        LinearLayout opacityRow = optionRow("subtitle_opacity", "背景不透明度");
        TextView opacityLabel = Ui.text(context, "", 12, accentText(), true);
        opacityRow.addView(opacityLabel);
        card.addView(opacityRow);
        SeekBar slider = new LeafSeekBar(context);
        slider.setContentDescription("字幕背景不透明度");
        slider.setMax(100);
        Runnable syncOpacity = () -> {
            int percent = cover ? coverOpacity : opacity;
            opacityLabel.setText(percent + "%");
            slider.setProgress(percent);
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { saveStyle(); }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (cover) coverOpacity = progress;
                else opacity = progress;
                opacityLabel.setText(progress + "%");
                saveStyle();
            }
        });
        card.addView(slider, new LinearLayout.LayoutParams(-1, dp(32)));
        syncOpacity.run();
        return syncOpacity;
    }

    private void saveStyle() {
        preferences.edit().putBoolean("subtitle_frame", frameVisible).putBoolean("subtitle_cover", cover)
                .putBoolean("subtitle_caption_below", captionBelow)
                .remove("subtitle_follow_original").remove("subtitle_original_opacity")
                .putInt("subtitle_background", background).putInt("subtitle_opacity", opacity)
                .putBoolean("subtitle_follow_background", followBackground)
                .putBoolean("subtitle_follow_text_color", followTextColor)
                .putInt("subtitle_cover_opacity", coverOpacity)
                .putInt("subtitle_font_scale", fontScale)
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

    private String hexColor(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); }

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
        TextView text = Ui.text(context, label, 15, Color.WHITE, true);
        if (icon != null) {
            button.addView(Ui.icon(context, icon, Color.WHITE, 18));
            Ui.pad(text, 8, 0, 0, 0);
        }
        button.addView(text);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private LinearLayout settingsAction() {
        LinearLayout row = Ui.row(context);
        Ui.pad(row, 12, 10, 12, 10);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x202E6F4E), surface(cardColor(), 16), null));
        row.addView(badge("subtitle_sliders", 0x1924533B, accentText(), 36));
        TextView title = Ui.text(context, "字幕设置", 14, Ui.INK, true);
        Ui.pad(title, 10, 0, 6, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.icon(context, "forward", Ui.MUTED, 17));
        row.setFocusable(true);
        row.setContentDescription("字幕设置");
        row.setOnClickListener(view -> showSettings());
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

    private LinearLayout optionRow(String icon, String title) {
        LinearLayout row = Ui.row(context);
        Ui.pad(row, 8, 4, 8, 4);
        row.setMinimumHeight(dp(40));
        row.addView(Ui.icon(context, icon, Ui.MUTED, 16));
        TextView label = Ui.text(context, title, 13, Ui.INK, true);
        Ui.pad(label, 8, 0, 4, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private LinearLayout addCheck(LinearLayout parent, String icon, String title, boolean selected, java.util.function.Consumer<Boolean> change) {
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
        return row;
    }

    private void addTranslationLanguages(LinearLayout parent) {
        LinearLayout row = Ui.row(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView from = Ui.centeredText(context, "", 14, Ui.INK, true);
        TextView to = Ui.centeredText(context, "", 14, Ui.INK, true);
        LinearLayout sourceChoices = newLanguageChoices();
        LinearLayout targetChoices = newLanguageChoices();
        Runnable refresh = () -> {
            boolean expanded = sourceChoices.getVisibility() == View.VISIBLE || targetChoices.getVisibility() == View.VISIBLE;
            int fromIndex = indexOf(source) + 1, toIndex = Math.max(0, indexOf(target));
            from.setText(SOURCE_NAMES[fromIndex]);
            to.setText(LANGUAGE_NAMES[toIndex]);
            selectLanguage(sourceChoices, fromIndex);
            selectLanguage(targetChoices, toIndex);
            sourceChoices.setVisibility(View.GONE);
            targetChoices.setVisibility(View.GONE);
            if (expanded) row.post(() -> row.requestRectangleOnScreen(new Rect(0, 0, row.getWidth(), row.getHeight()), true));
        };
        row.addView(TranslationPane.languageChip(from, "源语言", () -> toggleLanguageChoices(sourceChoices, targetChoices)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        FrameLayout swap = new FrameLayout(context);
        swap.setContentDescription("交换翻译语言");
        swap.setFocusable(true);
        swap.setBackground(Ui.ripple(context, Ui.dark ? Ui.PALE : 0xFFE6EFE4, 18));
        swap.addView(Ui.icon(context, "translation_swap", Ui.GREEN, 22),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        swap.setOnClickListener(view -> {
            String original = "auto".equals(source) ? detectedSource : source;
            if (indexOf(original) < 0) {
                toggleLanguageChoices(sourceChoices, targetChoices);
                return;
            }
            setLanguages(target, original);
            refresh.run();
        });
        row.addView(swap, new LinearLayout.LayoutParams(dp(40), dp(32)));
        row.addView(TranslationPane.languageChip(to, "翻译为", () -> toggleLanguageChoices(targetChoices, sourceChoices)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        parent.addView(row);
        addLanguageOptions(sourceChoices, "源语言", SOURCE_NAMES, index -> {
            setLanguages(index == 0 ? "auto" : LANGUAGES[index - 1], target);
            refresh.run();
        });
        addLanguageOptions(targetChoices, "翻译为", LANGUAGE_NAMES, index -> {
            setLanguages(source, LANGUAGES[index]);
            refresh.run();
        });
        parent.addView(sourceChoices);
        parent.addView(targetChoices);
        refresh.run();
    }

    private void setLanguages(String from, String to) {
        boolean sourceChanged = !source.equals(from);
        if (!sourceChanged && target.equals(to)) return;
        source = from;
        target = to;
        languageChanged = true;
        preferences.edit().putString("subtitle_source", source).putString("subtitle_target", target).apply();
        if (sourceChanged) {
            detectedSource = "";
            replaceReader();
        }
    }

    private LinearLayout newLanguageChoices() {
        LinearLayout options = Ui.column(context);
        options.setBackground(surface(controlColor(), 14));
        options.setClipToOutline(true);
        options.setVisibility(View.GONE);
        return options;
    }

    private void toggleLanguageChoices(View selected, View other) {
        other.setVisibility(View.GONE);
        selected.setVisibility(selected.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        if (selected.getVisibility() == View.VISIBLE) {
            selected.post(() -> selected.requestRectangleOnScreen(new Rect(0, 0, selected.getWidth(), selected.getHeight()), false));
        }
    }

    private void addLanguageOptions(LinearLayout options, String title, String[] labels, java.util.function.IntConsumer change) {
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            TextView option = Ui.text(context, labels[index], 15, Ui.INK, false);
            Ui.pad(option, 12, 0, 12, 0);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setBackground(Ui.ripple(context, 0, 0));
            option.setOnClickListener(view -> change.accept(index));
            option.setContentDescription(title + "，" + labels[index]);
            options.addView(option, new LinearLayout.LayoutParams(-1, dp(44)));
        }
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
        // Pause capture while the controls are open, keeping the current translation for preview.
        main.removeCallbacks(poll);
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
                boolean compact = bottom > 0 && height < dp(300);
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
        if (languageChanged) {
            invalidateWork();
            clearText();
        }
        saveStyle();
        schedule(120);
    }

    private void removePanel() {
        removeOverlay(panel);
        panel = null;
        preview = null;
        previewOriginal = null;
        previewCover = null;
        previewFloating = null;
        selection = null;
    }

    void displayChanged() {
        if (!active) return;
        String returnPackage = externalPaused && externalRequest != null ? externalRequest.packageName() : awaitingSourcePackage;
        Rect next = displayBounds();
        if (next.equals(screen)) return;
        float sx = next.width() / (float) screen.width(), sy = next.height() / (float) screen.height();
        area.set(Math.round(area.left * sx), Math.round(area.top * sy), Math.round(area.right * sx), Math.round(area.bottom * sy));
        screen.set(next);
        if (!live || selection != null) { editRegion(); return; }
        removePanel();
        invalidateWork();
        clearText();
        awaitingSourcePackage = returnPackage;
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
        styledCaption = null;
        translatedCache.clear();
    }

    private void removeOverlay(View view) {
        if (view == null || !view.isAttachedToWindow()) return;
        try { overlays.removeViewImmediate(view); } catch (IllegalArgumentException ignored) {}
    }
}
