package com.kongye.floatball;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONObject;

/** Generated subtitle fixture. Only exposes recognized text while the fixture is visible. */
final class V141Checks {
    private static View fixture;
    private static String line = "Hello world";
    private static final String[] STYLE_KEYS = {"frame", "cover", "background", "opacity", "alignment", "source", "target"};
    private static java.util.Map<String, ?> saved;

    static void run(Context context, String command) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        if (command.equals("reset_style") || command.equals("restore_style")) {
            if (command.equals("reset_style")) saved = Prefs.of(context).getAll();
            var edit = Prefs.of(context).edit();
            for (String suffix : STYLE_KEYS) {
                String key = "subtitle_" + suffix;
                Object value = command.equals("restore_style") && saved != null ? saved.get(key) : null;
                if (value instanceof Boolean flag) edit.putBoolean(key, flag);
                else if (value instanceof Integer number) edit.putInt(key, number);
                else if (value instanceof String text) edit.putString(key, text);
                else edit.remove(key);
            }
            edit.commit();
            return;
        }
        MainActivity activity = V1231Checks.main();
        if (command.equals("landscape") || command.equals("restore_orientation")) {
            activity.setRequestedOrientation(command.equals("landscape")
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            return;
        }
        if (command.equals("fixture")) {
            if (activity == null) throw new IllegalStateException("Open the app first");
            var bars = activity.getWindow().getInsetsController();
            bars.hide(android.view.WindowInsets.Type.systemBars());
            line = "Hello world";
            fixture = new View(activity) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas) {
                    canvas.drawColor(Color.WHITE);
                    paint.setColor(Color.BLACK);
                    paint.setTextSize(Ui.dp(activity, 26));
                    paint.setTextAlign(Paint.Align.CENTER);
                    float width = paint.measureText(line);
                    if (width > getWidth() * .76f) paint.setTextSize(paint.getTextSize() * getWidth() * .76f / width);
                    canvas.drawText(line, getWidth() * .5f, getHeight() * .48f, paint);
                    paint.setTextSize(Ui.dp(activity, 26));
                    canvas.drawText("Outside the selected region", getWidth() * .5f, getHeight() * .26f, paint);
                }
            };
            activity.setContentView(fixture);
            return;
        }
        if (command.equals("next")) { line = "Good morning"; fixture.invalidate(); return; }
        if (command.equals("long_caption")) {
            line = "When the rain finally stops tomorrow, would you like to walk with me along the river, "
                    + "visit the little bookstore, and have dinner together before we go home?";
            fixture.invalidate();
            return;
        }
        if (command.equals("punctuation")) {
            String sample = "他说: \"明天晚上 8:30, 带上 3.14 元去 KTV... 行吗?\"\n网址是 https://example.com/a?x=2，邮箱是 a.b@example.com。\nHello, world!";
            String formatted = OcrPunctuation.normalizeChineseSubtitle(sample);
            Prefs.of(context).edit().putString("qa_v141_punctuation", formatted).commit();
            return;
        }
        if (command.equals("blank")) { line = ""; fixture.invalidate(); return; }
        if (command.equals("japanese")) { line = "おはようございます"; fixture.invalidate(); return; }
        if (command.equals("korean")) { line = "안녕하세요"; fixture.invalidate(); return; }
        if (command.equals("secure") || command.equals("unsecure")) {
            if (command.equals("secure")) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            return;
        }
        FloatService service = FloatService.instance;
        SubtitleSession session = (SubtitleSession) V126Checks.get(service, "subtitles");
        if (command.equals("stop")) { session.stop(); return; }
        if (command.equals("menu")) { service.perform("menu"); return; }
        View root = (View) V126Checks.get(session, "panel");
        if (command.equals("start")) { service.perform("subtitle"); return; }
        if (command.equals("color_keyboard")) {
            EditText field = colorField(root);
            field.requestRectangleOnScreen(new Rect(0, 0, field.getWidth(), field.getHeight()), true);
            field.performClick();
            return;
        }
        if (command.equals("open_motion")) { openMotion(context, service); return; }
        if (command.equals("opacity")) {
            View slider = find(root, "字幕背景不透明度");
            long now = android.os.SystemClock.uptimeMillis();
            for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                var event = android.view.MotionEvent.obtain(now, now, action, slider.getWidth() * .4f, slider.getHeight() / 2f, 0);
                try { slider.onTouchEvent(event); } finally { event.recycle(); }
            }
            return;
        }
        if (command.startsWith("click_")) {
            View found = find(root, command.substring(6));
            if (found == null) throw new IllegalStateException("Missing subtitle control");
            found.performClick();
            return;
        }
        JSONObject state = new JSONObject().put("active", session.isActive()).put("panel", root != null);
        if (fixture != null && fixture.isAttachedToWindow() && activity != null) {
            state.put("recognized", V126Checks.get(session, "latest"));
            state.put("translation", V126Checks.get(session, "translation"));
            var insets = fixture.getRootWindowInsets();
            if (insets != null) {
                state.put("status_bar", insets.isVisible(android.view.WindowInsets.Type.statusBars()));
                state.put("nav_bar", insets.isVisible(android.view.WindowInsets.Type.navigationBars()));
            }
            state.put("fixture_focused", fixture.hasWindowFocus());
            int[] at = new int[2];
            fixture.getLocationOnScreen(at);
            state.put("fixture_top", at[1]);
        }
        state.put("frame", V126Checks.get(session, "frameVisible")).put("cover", V126Checks.get(session, "cover"));
        state.put("alignment", V126Checks.get(session, "alignment")).put("background", V126Checks.get(session, "background"));
        state.put("opacity", V126Checks.get(session, "opacity"));
        Rect rect = (Rect) V126Checks.get(session, "area");
        state.put("area_top", rect.top).put("area_width", rect.width()).put("area_left", rect.left);
        state.put("start", find(root, "确认") != null).put("confirm", find(root, "退出翻译") != null);
        if (V126Checks.get(session, "selection") instanceof ResizableCropView crop) {
            int[] at = new int[2];
            crop.getLocationOnScreen(at);
            state.put("crop_top", Math.round(crop.area.top) + at[1]);
            state.put("crop_left", Math.round(crop.area.left) + at[0]);
            state.put("crop_width", Math.round(crop.area.width()));
            state.put("screen_width", crop.getWidth()).put("screen_height", crop.getHeight());
        }
        state.put("action_available", java.util.Arrays.asList(Prefs.gestureActions()).contains("subtitle"));
        state.put("status", V126Checks.get(session, "status"));
        if (root != null) {
            var insets = root.getRootWindowInsets();
            if (insets != null) state.put("ime", insets.isVisible(android.view.WindowInsets.Type.ime()));
        }
        TextView caption = (TextView) V126Checks.get(session, "caption");
        if (caption != null) {
            int[] at = new int[2];
            caption.getLocationOnScreen(at);
            state.put("caption_top", at[1]).put("caption_height", caption.getHeight());
            var layout = caption.getLayout();
            if (layout != null) {
                var lines = new org.json.JSONArray();
                CharSequence text = layout.getText();
                for (int i = 0; i < layout.getLineCount(); i++) {
                    lines.put(text.subSequence(layout.getLineStart(i), layout.getLineEnd(i)).toString());
                }
                state.put("caption_lines", lines);
            }
        }
        Prefs.of(context).edit().putString("qa_v141_state", state.toString()).commit();
    }

    private static void openMotion(Context context, FloatService service) {
        var clock = android.view.Choreographer.getInstance();
        long start = android.os.SystemClock.uptimeMillis();
        var frames = new org.json.JSONArray();
        Prefs.of(context).edit().remove("qa_v141_entry_frames").apply();
        service.perform("settings");
        clock.postFrameCallback(new android.view.Choreographer.FrameCallback() {
            @Override public void doFrame(long time) {
                long elapsed = android.os.SystemClock.uptimeMillis() - start;
                try {
                    MainActivity page = V1231Checks.main();
                    if (page != null) {
                        View content = page.findViewById(android.R.id.content);
                        frames.put(new JSONObject().put("ms", elapsed).put("y", content.getTranslationY())
                                .put("height", content.getHeight()).put("scale", content.getScaleX()));
                    }
                } catch (Exception ignored) {}
                if (elapsed < 1050) clock.postFrameCallback(this);
                else Prefs.of(context).edit().putString("qa_v141_entry_frames", frames.toString()).apply();
            }
        });
    }

    private static View find(View root, String label) {
        if (root == null) return null;
        if (TextUtils.equals(root.getContentDescription(), label) || root instanceof TextView text && root.isClickable() && TextUtils.equals(text.getText(), label)) return root;
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = find(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static EditText colorField(View root) {
        if (root instanceof EditText field) return field;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            EditText field = colorField(group.getChildAt(i));
            if (field != null) return field;
        }
        return null;
    }
}
