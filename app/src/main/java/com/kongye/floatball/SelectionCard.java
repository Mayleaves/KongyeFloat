package com.kongye.floatball;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Compact selectable row shared by settings that have one active choice. */
final class SelectionCard extends LinearLayout implements Checkable {
    private boolean checked;
    private final View mark;
    SelectionCard(Context c,String title,String detail){
        super(c);setOrientation(HORIZONTAL);setGravity(android.view.Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(c,54));Ui.pad(this,14,11,12,11);setClickable(true);setFocusable(true);
        LinearLayout labels=Ui.column(c);labels.addView(Ui.text(c,title,15,Ui.INK,true));
        if(detail!=null&&!detail.isEmpty()){Ui.gap(labels,3);labels.addView(Ui.text(c,detail,12,Ui.MUTED,false));}
        addView(labels,new LayoutParams(0,-2,1));mark=Ui.icon(c,"check",Ui.GREEN,20);addView(mark);setChecked(false);
    }
    @Override public boolean isChecked(){return checked;}
    @Override public void toggle(){setChecked(!checked);}
    @Override public void setChecked(boolean value){
        checked=value;setSelected(value);mark.setVisibility(value?VISIBLE:INVISIBLE);
        android.graphics.drawable.GradientDrawable background=Ui.bg(value?Ui.PALE:Ui.SURFACE,16,getContext());
        background.setStroke(Ui.dp(getContext(),1),value?Ui.GREEN:Ui.LINE);setBackground(background);invalidate();
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.RadioButton");info.setCheckable(true);info.setChecked(checked);}
}
