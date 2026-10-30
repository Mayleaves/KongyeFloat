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
        Context c=d.getContext();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawable(Ui.bg(Ui.SURFACE,24,c));d.getWindow().setDimAmount(.22f);}
        TextView title=d.findViewById(c.getResources().getIdentifier("alertTitle","id","android"));if(title!=null){title.setTextColor(Ui.INK);title.setTextSize(18);}
        TextView message=d.findViewById(android.R.id.message);if(message!=null){message.setTextColor(Ui.MUTED);message.setTextSize(14);message.setLineSpacing(Ui.dp(c,3),1);}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=d.getButton(which);if(b!=null){b.setAllCaps(false);b.setTextSize(13);b.setTextColor(Ui.GREEN);b.setBackground(Ui.ripple(c,Ui.PALE,14));Ui.pad(b,14,8,14,8);}}
        ListView list=d.getListView();if(list!=null){list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.LINE));list.setDividerHeight(1);list.setSelector(Ui.ripple(c,Ui.PALE,12));}
    }
}
