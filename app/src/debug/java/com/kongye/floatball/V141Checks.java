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
    private static android.graphics.Bitmap gameBitmap;
    private static int gameMode;
    private static String line = "Hello world";
    private static final String[] STYLE_KEYS = {"frame", "cover", "caption_below", "follow_original", "background", "follow_background", "follow_text_color", "opacity", "cover_opacity", "original_opacity", "font_scale", "alignment", "source", "target"};
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
        if (command.equals("game_fixture")) {
            if (activity == null) throw new IllegalStateException("Open the app first");
            activity.getWindow().getInsetsController().hide(android.view.WindowInsets.Type.systemBars());
            java.io.File file = new java.io.File(activity.getExternalFilesDir("subtitle-fixture"), "game.jpg");
            gameBitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            gameMode = 0;
            if (gameBitmap == null) throw new IllegalStateException("Missing local screenshot fixture");
            fixture = new View(activity) {
                @Override protected void onDraw(Canvas canvas) {
                    canvas.drawColor(Color.BLACK);
                    float scale = Math.min(getWidth() / (float) gameBitmap.getWidth(), getHeight() / (float) gameBitmap.getHeight());
                    float w = gameBitmap.getWidth() * scale, h = gameBitmap.getHeight() * scale;
                    canvas.drawBitmap(gameBitmap, null, new android.graphics.RectF((getWidth()-w)/2, (getHeight()-h)/2,
                            (getWidth()+w)/2, (getHeight()+h)/2), new Paint(3));
                    if (gameMode != 0) {
                        canvas.save(); canvas.translate((getWidth()-w)/2, (getHeight()-h)/2); canvas.scale(scale,scale);
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setColor(gameMode == 5 ? 0xFF625032 : gameMode == 6 ? 0xFF244D69 : 0xFF141414);
                        canvas.drawRect(330,575,1510,715,p);
                        if (gameMode != 3) {
                            p.setTextSize(42); p.setColor(gameMode == 4 ? 0xFF65B9EF : 0xFFC766B8);
                            canvas.drawText("Sarah",gameMode == 4 ? 375 : 344,618,p);
                            p.setColor(Color.WHITE); p.setTextSize(gameMode == 2 ? 27 : 37);
                            if (gameMode == 2) {
                                canvas.drawText("When the rain stops tomorrow, would you like to walk with me along the river,",352,660,p);
                                canvas.drawText("visit the bookstore, and have dinner together before we go home?",352,701,p);
                            } else canvas.drawText("Good morning. We should leave now.",352,666,p);
                        }
                        canvas.restore();
                    }
                }
            };
            activity.setContentView(fixture);
            Prefs.of(context).edit().putString("subtitle_source", "en").putString("subtitle_target", "zh")
                    .remove("subtitle_follow_original").putBoolean("subtitle_cover", true)
                    .remove("subtitle_caption_below")
                    .remove("subtitle_cover_opacity")
                    .putBoolean("subtitle_follow_background", true)
                    .remove("subtitle_follow_text_color")
                    .putBoolean("subtitle_frame", true).putInt("subtitle_opacity", 15)
                    .putInt("subtitle_font_scale", 100).apply();
            activity.startService(new android.content.Intent(activity, FloatService.class));
            return;
        }
        if (command.startsWith("game_") && !command.equals("game_select")) {
            gameMode = switch (command) {
                case "game_next" -> 1;
                case "game_long" -> 2;
                case "game_blank" -> 3;
                case "game_blue" -> 4;
                case "game_warm_background" -> 5;
                case "game_cool_background" -> 6;
                default -> 0;
            };
            fixture.invalidate(); return;
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
        if (command.equals("game_select")) {
            ResizableCropView crop = (ResizableCropView) V126Checks.get(session, "selection");
            float scale = Math.min(fixture.getWidth() / (float) gameBitmap.getWidth(), fixture.getHeight() / (float) gameBitmap.getHeight());
            int[] origin = new int[2], cropOrigin = new int[2];
            fixture.getLocationOnScreen(origin); crop.getLocationOnScreen(cropOrigin);
            float x = origin[0] - cropOrigin[0] + (fixture.getWidth()-gameBitmap.getWidth()*scale)/2;
            float y = origin[1] - cropOrigin[1] + (fixture.getHeight()-gameBitmap.getHeight()*scale)/2;
            if (gameBitmap.getHeight() > gameBitmap.getWidth()) {
                crop.area.set(x+30*scale, y+645*scale, x+816*scale, y+1195*scale);
            } else {
                crop.area.set(x+330*scale, y+575*scale, x+1510*scale, y+715*scale);
            }
            crop.invalidate();
            return;
        }
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
        if (command.equals("opacity") || command.equals("font_size")) {
            View slider = find(root, command.equals("opacity") ? "字幕背景不透明度" : "译文字号");
            long now = android.os.SystemClock.uptimeMillis();
            for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                var event = android.view.MotionEvent.obtain(now, now, action, slider.getWidth() * (command.equals("opacity") ? .4f : .75f), slider.getHeight() / 2f, 0);
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
            SubtitleFrame frame = (SubtitleFrame) V126Checks.get(session, "latestFrame");
            var blocks = new org.json.JSONArray();
            for (SubtitleLayout.Block block : frame.blocks) blocks.put(new JSONObject().put("text", block.text())
                    .put("color", String.format("#%06X", block.color() & 0xFFFFFF)).put("left", block.left())
                    .put("top", block.top()).put("right", block.right()).put("bottom", block.bottom()));
            state.put("blocks", blocks).put("translated_blocks", new org.json.JSONArray((java.util.List<?>) V126Checks.get(session,"translatedBlocks")));
            SubtitleCaptionView styled = (SubtitleCaptionView) V126Checks.get(session,"styledCaption");
            if (styled != null) {
                state.put("styled_visible", styled.getVisibility() == View.VISIBLE).put("styled_height",styled.getHeight());
                var layouts = (java.util.List<android.text.StaticLayout>) V126Checks.get(styled,"layouts");
                var positions = (java.util.List<android.graphics.PointF>) V126Checks.get(styled,"positions");
                var rendered = new org.json.JSONArray();
                for(int i=0;i<layouts.size();i++) rendered.put(new JSONObject().put("left",positions.get(i).x)
                        .put("top",positions.get(i).y).put("height",layouts.get(i).getHeight())
                        .put("size", layouts.get(i).getPaint().getTextSize()).put("lines",layouts.get(i).getLineCount())
                        .put("color", layouts.get(i).getPaint().getColor()).put("shadow", layouts.get(i).getPaint().getShadowLayerRadius()));
                state.put("rendered",rendered);
            }
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
        state.put("cover_opacity", V126Checks.get(session, "coverOpacity"));
        state.put("sampled_background", V126Checks.get(session, "sampledBackground"))
                .put("follow_background", V126Checks.get(session, "followBackground"))
                .put("follow_text_color", V126Checks.get(session, "followTextColor"));
        View preview = (View) V126Checks.get(session, "preview");
        if (preview != null) {
            int[] xy = new int[2];
            preview.getLocationOnScreen(xy);
            state.put("panel_id", System.identityHashCode(root)).put("preview_id", System.identityHashCode(preview))
                    .put("preview", new org.json.JSONArray(new int[]{xy[0], xy[1], xy[0] + preview.getWidth(), xy[1] + preview.getHeight()}));
            View previewCover = (View) V126Checks.get(session, "previewCover");
            View previewOriginal = (View) V126Checks.get(session, "previewOriginal");
            View previewFloating = (View) V126Checks.get(session, "previewFloating");
            state.put("preview_cover", previewCover.getVisibility() == View.VISIBLE)
                    .put("preview_floating", previewFloating.getVisibility() == View.VISIBLE);
            state.put("preview_source_text", new org.json.JSONArray((java.util.List<?>) V126Checks.get(previewOriginal, "translated")));
            state.put("preview_translated_text", new org.json.JSONArray((java.util.List<?>) V126Checks.get(previewCover, "translated")));
            var previewLayouts = (java.util.List<android.text.StaticLayout>) V126Checks.get(previewCover, "layouts");
            var previewColors = new org.json.JSONArray();
            for (var item : previewLayouts) previewColors.put(item.getPaint().getColor());
            state.put("preview_colors", previewColors);
        }
        state.put("font_scale", V126Checks.get(session, "fontScale"));
        state.put("caption_below", V126Checks.get(session, "captionBelow"));
        state.put("cover_switch", find(root, "译文覆盖选区") != null)
                .put("old_switch", find(root, "跟随原文颜色与位置") != null);
        Rect rect = (Rect) V126Checks.get(session, "area");
        state.put("area_top", rect.top).put("area_bottom", rect.bottom)
                .put("area_width", rect.width()).put("area_left", rect.left);
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
            var controls = new JSONObject();
            describeControls(root, controls);
            state.put("controls", controls);
        }
        TextView caption = (TextView) V126Checks.get(session, "caption");
        if (caption != null) {
            int[] at = new int[2];
            caption.getLocationOnScreen(at);
            state.put("caption_left", at[0]).put("caption_top", at[1]).put("caption_height", caption.getHeight())
                    .put("caption_width", caption.getWidth()).put("caption_visible", caption.getVisibility() == View.VISIBLE);
            state.put("caption_shadow", caption.getShadowRadius());
            if (caption.getBackground() instanceof android.graphics.drawable.GradientDrawable bg)
                state.put("caption_radius", bg.getCornerRadius());
            var layout = caption.getLayout();
            if (layout != null) {
                var lines = new org.json.JSONArray();
                CharSequence text = layout.getText();
                for (int i = 0; i < layout.getLineCount(); i++) {
                    lines.put(text.subSequence(layout.getLineStart(i), layout.getLineEnd(i)).toString());
                }
                state.put("caption_lines", lines);
                if (text instanceof android.text.Spanned spans) {
                    var sizes = new org.json.JSONArray();
                    for (var span : spans.getSpans(0, spans.length(), android.text.style.AbsoluteSizeSpan.class)) sizes.put(span.getSize());
                    var colors = new org.json.JSONArray();
                    for (var span : spans.getSpans(0, spans.length(), android.text.style.ForegroundColorSpan.class))
                        colors.put(String.format("#%06X",span.getForegroundColor() & 0xFFFFFF));
                    state.put("caption_sizes",sizes).put("caption_colors",colors);
                }
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

    private static void describeControls(View view, JSONObject out) throws org.json.JSONException {
        if (view.getVisibility() != View.VISIBLE) return;
        CharSequence label = view.getContentDescription();
        if (label == null && view instanceof TextView text) label = text.getText();
        if (!TextUtils.isEmpty(label) && !out.has(label.toString())) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            out.put(label.toString(), new org.json.JSONArray(location));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) describeControls(group.getChildAt(i), out);
        }
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
