package com.kongye.floatball;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;

import java.io.File;

public class RecordingResultActivity extends Activity {
    private RecordingPreview preview;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        File file;
        try {
            String path = saved == null ? getIntent().getStringExtra("path") : saved.getString("path");
            file = RecordingFiles.checked(this, path);
        } catch (Exception unavailable) {
            finish();
            return;
        }
        preview = new RecordingPreview(this, file, getIntent().getIntExtra("gif_fps", 10));
        if (saved != null) preview.restore(saved);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, preview::back);
        }
        preview.show(saved == null && getIntent().getBooleanExtra("auto_gif", false));
    }

    @Override protected void onResume() {
        super.onResume();
        String source = getIntent().getStringExtra("path");
        if (preview != null && source != null) RecordingPreviewFallback.activityShown(new File(source));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) preview.back();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (preview != null) preview.store(state);
    }

    @Override protected void onPause() {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Prefs.of(this).edit().putLong("qa_player_pause_at", android.os.SystemClock.uptimeMillis()).apply();
        }
        if (preview != null) preview.pause();
        super.onPause();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == RecordingPreview.SHARE_REQUEST && preview != null) preview.shareReturned();
    }

    @Override protected void onDestroy() {
        if (preview != null) preview.dispose();
        super.onDestroy();
    }
}
