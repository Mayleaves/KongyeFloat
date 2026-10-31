package com.kongye.floatball;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** One visual language for every app-owned alert, choice and confirmation. */
final class UiDialog extends AlertDialog.Builder {
    UiDialog(Context c){super(c,ThemePolicy.isDark(c)?R.style.KongyeDialogDark:R.style.KongyeDialog);ThemePolicy.apply(c);}
    @Override public AlertDialog create(){AlertDialog d=super.create();d.setOnShowListener(v->style(d));return d;}
    @Override public AlertDialog show(){AlertDialog d=super.show();style(d);return d;}
    static void style(AlertDialog d){
        Context c=d.getContext();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawable(Ui.bg(Ui.SURFACE,24,c));d.getWindow().setDimAmount(.22f);d.getWindow().setGravity(Gravity.CENTER);}
        TextView title=d.findViewById(c.getResources().getIdentifier("alertTitle","id","android"));if(title!=null){title.setTextColor(Ui.INK);title.setTextSize(18);}
        TextView message=d.findViewById(android.R.id.message);if(message!=null){message.setTextColor(Ui.MUTED);message.setTextSize(14);message.setLineSpacing(Ui.dp(c,3),1);}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=d.getButton(which);if(b!=null){b.setAllCaps(false);b.setTextSize(13);b.setSingleLine();b.setGravity(Gravity.CENTER);b.setTextColor(Ui.GREEN);b.setBackground(new android.graphics.drawable.InsetDrawable(Ui.ripple(c,Ui.PALE,14),Ui.dp(c,5),0,Ui.dp(c,5),0));b.setMinimumHeight(0);b.setMinHeight(0);b.setMinWidth(Ui.dp(c,60));Ui.pad(b,14,0,14,0);ViewGroup.LayoutParams p=b.getLayoutParams();p.height=Ui.dp(c,36);b.setLayoutParams(p);if(b.getParent() instanceof ViewGroup bar){bar.setMinimumHeight(0);Ui.pad(bar,10,8,10,12);}}}
        ListView list=d.getListView();if(list!=null){list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));list.setDividerHeight(1);list.setSelector(Ui.ripple(c,Ui.PALE,12));}
        // Cap the first frame before it is drawn, so tall choice lists keep breathing room.
        Window w=d.getWindow();if(w!=null){View decor=w.getDecorView();int key=R.id.dialog_layout_guard;if(decor.getTag(key)==null){decor.setTag(key,true);decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(!d.isShowing())return true;android.util.DisplayMetrics screen=new android.util.DisplayMetrics();w.getWindowManager().getDefaultDisplay().getRealMetrics(screen);int available=screen.heightPixels;WindowInsets insets=decor.getRootWindowInsets();if(insets!=null){if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());available-=safe.top+safe.bottom;}else available-=insets.getSystemWindowInsetTop()+insets.getSystemWindowInsetBottom();}int maximum=Math.max(Ui.dp(c,200),Math.min(available-Ui.dp(c,56),Math.round(screen.heightPixels*.84f)));if(decor.getMeasuredHeight()>maximum){WindowManager.LayoutParams p=w.getAttributes();p.height=maximum;w.setAttributes(p);return false;}decor.getViewTreeObserver().removeOnPreDrawListener(this);return true;}});}}
    }
}
