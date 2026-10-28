package com.kongye.floatball;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.*;
import android.widget.FrameLayout;

final class CaptureWindow {
    final Context context;final WindowManager manager;private View root;
    CaptureWindow(Context c){context=c;manager=OverlayWindows.get(c);}
    FrameLayout frame(Runnable back){return new FrameLayout(context){@Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)back.run();return true;}return super.dispatchKeyEvent(event);}};}
    void show(View view,boolean focusable){
        remove();root=view;int flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if(!focusable)flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.LEFT;
        params.setTitle("空叶截图窗口");params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        if(Build.VERSION.SDK_INT>=30)params.setFitInsetsTypes(0);
        if(Build.VERSION.SDK_INT>=28)params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if(focusable){view.setFocusableInTouchMode(true);view.setOnApplyWindowInsetsListener((v,insets)->{int bottom=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.ime()).bottom:insets.getSystemWindowInsetBottom();v.setPadding(0,0,0,bottom);if(v instanceof android.view.ViewGroup group){for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if("capture_editor".equals(child.getTag())){android.util.DisplayMetrics m=new android.util.DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(m);android.view.ViewGroup.LayoutParams p=child.getLayoutParams();int height=Math.max(Ui.dp(context,240),m.heightPixels-bottom-Ui.dp(context,80));if(p.height!=height){p.height=height;child.setLayoutParams(p);}}}}return insets;});}
        manager.addView(view,params);if(focusable)view.requestFocus();view.requestApplyInsets();
    }
    void visible(boolean visible){if(root!=null)root.setVisibility(visible?View.VISIBLE:View.INVISIBLE);}
    View root(){return root;}
    void showFloating(View view,int width,int maximumHeight,int x,int y){
        remove();root=view;WindowManager.LayoutParams params=new WindowManager.LayoutParams(width,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.x=x;params.y=y;params.setTitle("空叶置顶截图结果");params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        manager.addView(view,params);view.setFocusableInTouchMode(true);view.requestFocus();
    }
    void move(int x,int y){if(root!=null){WindowManager.LayoutParams p=(WindowManager.LayoutParams)root.getLayoutParams();p.x=x;p.y=y;manager.updateViewLayout(root,p);}}
    int[] position(){if(root==null)return new int[]{0,0};WindowManager.LayoutParams p=(WindowManager.LayoutParams)root.getLayoutParams();return new int[]{p.x,p.y};}
    private void clearImages(View view){if(view instanceof android.widget.ImageView image)image.setImageDrawable(null);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)clearImages(group.getChildAt(i));}
    void remove(){if(root!=null){clearImages(root);context.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(root.getWindowToken(),0);try{manager.removeView(root);}catch(IllegalArgumentException ignored){}root=null;}}
}
