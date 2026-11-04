package com.kongye.floatball;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.os.Build;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import java.util.function.Consumer;

/** One live WebView survives folding, pinning, and moving the floating window. */
final class FloatingBrowser {
    private final Context context;private final WindowManager wm;private final Runnable dismiss;private final Consumer<Intent> launch;
    private FrameLayout root,pinButton,foldButton;private LinearLayout card,body,addressRow;private FrameLayout.LayoutParams cardAt;private WindowManager.LayoutParams params;
    private WebView web;private EditText address;private TextView status,bing,google;
    private boolean docked;private boolean pinned,folded,closed,attached;private int x,y,imeBottom,barTop,barBottom,loads;
    FloatingBrowser(Context c,Runnable dismiss,Consumer<Intent> launch){context=c;this.dismiss=dismiss;this.launch=launch;wm=OverlayWindows.get(c);y=dp(48);}
    private int dp(float n){return Ui.dp(context,n);}private int screenW(){android.util.DisplayMetrics m=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(m);return m.widthPixels;}private int screenH(){android.util.DisplayMetrics m=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(m);return m.heightPixels;}
    void show(){
        root=new FrameLayout(context){@Override public boolean dispatchTouchEvent(MotionEvent e){if(Boolean.TRUE.equals(getTag(R.id.dock_morph_running)))return true;if(pinned){if(e.getActionMasked()==MotionEvent.ACTION_OUTSIDE){animate().alpha(.45f).setDuration(280).start();return false;}if(e.getActionMasked()==MotionEvent.ACTION_DOWN)animate().alpha(1).setDuration(160).start();}return super.dispatchTouchEvent(e);}@Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP){if(folded)fold(false);else if(!goBack())dismiss.run();}return true;}return super.dispatchKeyEvent(e);}};
        root.setClipChildren(false);root.setClipToPadding(false);root.setBackgroundColor(Color.TRANSPARENT);root.setFocusableInTouchMode(true);root.setOnClickListener(v->{if(!pinned)dismiss.run();});
        card=Ui.card(context);Ui.softShadow(card);card.setBackground(Ui.floatingFrame(context,24));
        // One outer outline around the toolbar and web content, with a straight seam between them.
        Ui.pad(card,2,2,2,2);
        card.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(24));}
        });
        card.setClipToOutline(true);
        card.setContentDescription("浏览器窗口");
        card.setOnClickListener(v->{});cardAt=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);root.addView(card,cardAt);
        card.addView(makeToolbar());
        body=Ui.column(context);card.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        status=Ui.text(context,"",11,Ui.MUTED,false);
        status.setSingleLine();status.setEllipsize(TextUtils.TruncateAt.END);status.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(status,12,0,12,0);
        body.addView(status,new LinearLayout.LayoutParams(-1,dp(20)));
        showStatus("");
        web=new WebView(context);web.setBackgroundColor(Color.WHITE);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setSupportMultipleWindows(false);web.getSettings().setSafeBrowsingEnabled(true);web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);web.getSettings().setUseWideViewPort(true);web.getSettings().setLoadWithOverviewMode(true);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!"https".equalsIgnoreCase(request.getUrl().getScheme())){showStatus("此窗口仅支持 HTTPS 网页");return true;}return false;}
            public void onPageStarted(WebView view,String url,Bitmap icon){if(closed)return;loads++;showStatus("");if(url.startsWith("https://"))address.setText(url);receipt(url);}
            public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(!closed&&request.isForMainFrame())showStatus("加载失败，可切换搜索引擎或更换网址");}
            public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();if(!closed)showStatus("网站证书异常，已停止加载");}
        });body.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.LEFT;params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;params.setTitle("空叶浏览器");if(Build.VERSION.SDK_INT>=30)params.setFitInsetsTypes(0);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(closed)return insets;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());barTop=bars.top;barBottom=bars.bottom;imeBottom=insets.getInsets(WindowInsets.Type.ime()).bottom;}else{barTop=insets.getSystemWindowInsetTop();barBottom=dp(24);int bottom=insets.getSystemWindowInsetBottom();imeBottom=bottom>dp(120)?bottom:0;}layout();return insets;});
        x=dp(8);wm.addView(root,params);attached=true;layout();root.requestFocus();root.requestApplyInsets();web.loadUrl(BrowserSearch.home(Prefs.of(context).getString("browser_engine","bing")));
    }

    private LinearLayout makeToolbar() {
        LinearLayout toolbar = Ui.column(context);
        toolbar.setContentDescription("浏览器工具栏");
        Ui.pad(toolbar, 8, 5, 8, 5);
        LinearLayout controls = Ui.row(context);
        toolbar.addView(controls, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout engines = Ui.row(context);
        engines.setBackground(Ui.bg(Ui.PALE, 12, context));
        Ui.pad(engines, 2, 2, 2, 2);
        bing = engineTab(engines, "必应", "bing", 40);
        google = engineTab(engines, "Google", "google", 54);
        controls.addView(engines);
        engineSelection();

        View drag = new View(context);
        drag.setContentDescription("拖动浏览器窗口");
        controls.addView(drag, new LinearLayout.LayoutParams(0, -1, 1));
        installDrag(drag);
        addTool(controls, "back", "浏览器后退", this::goBack);
        addTool(controls, "forward", "浏览器前进", () -> { if (web.canGoForward()) web.goForward(); });
        addTool(controls, "compass", "在外部浏览器打开", this::openExternal);
        View divider = new View(context);
        divider.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dividerAt = new LinearLayout.LayoutParams(dp(1), dp(12));
        dividerAt.setMargins(dp(3), 0, dp(3), 0);
        controls.addView(divider, dividerAt);
        foldButton = addTool(controls, "collapse", "收起浏览器", () -> fold(!folded));
        pinButton = addTool(controls, "browser_pin", "置顶浏览器", () -> { pinned = !pinned; layout(); });
        addTool(controls, "close", "关闭浏览器", dismiss);

        addressRow = Ui.row(context);
        addressRow.setBackground(Ui.bg(Ui.BG, 16, context));
        Ui.pad(addressRow, 9, 0, 4, 0);
        addressRow.addView(Ui.icon(context, "browser_lock", toolColor(), 14));
        address = Ui.field(context, "搜索或输入 HTTPS 网址");
        address.setContentDescription("浏览器地址或搜索");
        address.setTextSize(12);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setBackground(null);
        Ui.pad(address, 6, 0, 4, 0);
        addressRow.addView(address, new LinearLayout.LayoutParams(0, -1, 1));
        FrameLayout go = toolbarButton("arrow", "前往", this::navigate);
        go.setBackground(Ui.ripple(context, Ui.PALE, 18));
        addressRow.addView(go, new LinearLayout.LayoutParams(dp(28), dp(28)));
        address.setOnEditorActionListener((view, id, event) -> {
            if (id != EditorInfo.IME_ACTION_GO) return false;
            navigate();
            return true;
        });
        LinearLayout.LayoutParams addressAt = new LinearLayout.LayoutParams(-1, dp(36));
        addressAt.topMargin = dp(4);
        toolbar.addView(addressRow, addressAt);
        return toolbar;
    }

    private TextView engineTab(LinearLayout engines, String label, String value, int width) {
        TextView tab = Ui.centeredText(context, label, 12, toolColor(), false);
        tab.setContentDescription("搜索引擎：" + label);
        tab.setOnClickListener(view -> engine(value));
        engines.addView(tab, new LinearLayout.LayoutParams(dp(width), dp(24)));
        return tab;
    }

    private int toolColor() { return Ui.toolTint(); }

    private FrameLayout addTool(LinearLayout row, String icon, String label, Runnable action) {
        FrameLayout view = toolbarButton(icon, label, action);
        int reserved = dp(40 + 16 + 98 + 7 + 12); // Window padding, toolbar padding, tabs, divider and drag space.
        int width = Math.max(dp(20), Math.min(dp(28), (screenW() - reserved) / 6));
        row.addView(view, new LinearLayout.LayoutParams(width, -1));
        return view;
    }

    private FrameLayout toolbarButton(String icon, String label, Runnable action) {
        FrameLayout view = new FrameLayout(context);
        view.setFocusable(true);
        view.setBackground(Ui.ripple(context, 0, 6));
        view.setOnClickListener(button -> action.run());
        button(view, icon, label);
        return view;
    }

    private void openExternal() {
        String url = web.getUrl();
        if (url == null || !url.startsWith("https://")) return;
        dismiss.run();
        launch.accept(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void engine(String value){Prefs.of(context).edit().putString("browser_engine",value).apply();engineSelection();web.loadUrl(BrowserSearch.home(value));}
    private void engineSelection(){boolean useGoogle=Prefs.of(context).getString("browser_engine","bing").equals("google");chip(bing,!useGoogle);chip(google,useGoogle);}
    private void chip(TextView v,boolean on){v.setSelected(on);v.setBackground(on?Ui.bg(Ui.SURFACE,10,context):null);v.setElevation(on?dp(1):0);v.setTextColor(on?Ui.INK:toolColor());}
    private void navigate(){String url=BrowserSearch.url(address.getText().toString(),Prefs.of(context).getString("browser_engine","bing"));if(url==null){if(!address.getText().toString().trim().isEmpty())showStatus("请输入 HTTPS 网址");return;}web.loadUrl(url);hideKeyboard();web.requestFocus();}
    boolean goBack(){if(!closed&&web!=null&&web.canGoBack()){web.goBack();return true;}return false;}
    private void hideKeyboard(){context.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(root.getWindowToken(),0);}
    private void fold(boolean value){if(closed)return;hideKeyboard();root.requestFocus();folded=value;body.setVisibility(value?View.GONE:View.VISIBLE);addressRow.setVisibility(value?View.GONE:View.VISIBLE);if(value)web.onPause();else web.onResume();layout();}
    private void button(FrameLayout target,String icon,String label){if(TextUtils.equals(label,target.getContentDescription()))return;target.setContentDescription(label);target.removeAllViews();target.addView(Ui.icon(context,icon,toolColor(),16),new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.CENTER));}
    private void layout(){if(!attached||closed||docked)return;button(pinButton,pinned?"browser_pinned":"browser_pin",pinned?"取消置顶浏览器":"置顶浏览器");button(foldButton,folded?"expand":"collapse",folded?"展开浏览器":"收起浏览器");int oldW=params.width,oldH=params.height,oldX=params.x,oldY=params.y;int margin=pinned?0:dp(8),cardHeight=folded?-2:-1;if(cardAt.height!=cardHeight||cardAt.leftMargin!=margin){cardAt.height=cardHeight;cardAt.width=-1;cardAt.gravity=Gravity.CENTER;cardAt.setMargins(margin,margin,margin,margin);card.setLayoutParams(cardAt);}
        if(pinned){params.flags|=WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;root.setPadding(dp(12),dp(12),dp(12),dp(12));params.width=screenW()-dp(16);int top=Math.max(dp(32),barTop),bottom=Math.max(barBottom,imeBottom);y=Math.max(top,Math.min(screenH()-dp(100),y));int height=imeBottom>0?Math.min(screenH()-dp(112),Math.max(dp(260),screenH()-y-bottom-dp(16))):screenH()-dp(112);params.height=folded?-2:height;int maxY=screenH()-(folded?dp(84):height)-barBottom-dp(8);y=Math.max(top,Math.min(maxY,y));params.x=x;params.y=y;}else{params.flags&=~(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);root.setPadding(0,barTop,0,Math.max(barBottom,imeBottom));params.width=params.height=-1;params.x=params.y=0;}
        if(oldW!=params.width||oldH!=params.height||oldX!=params.x||oldY!=params.y)wm.updateViewLayout(root,params);receipt(null);
    }
    private void dock(boolean left){docked=true;DockMorph.dock(context,wm,root,left,"browser","展开置顶浏览器标签",()->{docked=false;x=params.x;y=params.y;receipt(null);});}
    private void installDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int beforeX, beforeY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (!pinned) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        startX = event.getRawX();
                        startY = event.getRawY();
                        beforeX = x;
                        beforeY = y;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        x = beforeX + Math.round(event.getRawX() - startX);
                        y = beforeY + Math.round(event.getRawY() - startY);
                        layout();
                    }
                    case MotionEvent.ACTION_UP -> {
                        view.performClick();
                        if (x < -params.width / 2 || x > screenW() - params.width / 2) dock(x < 0);
                    }
                }
                return true;
            }
        });
    }
    private void receipt(String url){if((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0)return;android.content.SharedPreferences.Editor e=Prefs.of(context).edit().putBoolean("qa_browser_pinned",pinned).putBoolean("qa_browser_folded",folded).putInt("qa_browser_loads",loads).putInt("qa_browser_window_x",params==null?0:params.x).putInt("qa_browser_window_y",params==null?0:params.y).putInt("qa_browser_window_h",params==null?0:params.height);if(url!=null)e.putString("qa_browser_url",url);e.apply();}
    void close(){if(closed)return;closed=true;if(root!=null&&attached){hideKeyboard();try{wm.removeViewImmediate(root);}catch(IllegalArgumentException ignored){}attached=false;}if(web!=null){web.stopLoading();web.setWebViewClient(new WebViewClient());web.destroy();web=null;}}
}
