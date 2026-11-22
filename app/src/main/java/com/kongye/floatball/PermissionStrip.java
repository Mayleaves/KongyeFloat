package com.kongye.floatball;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Scheme C from the supplied HTML; all status values come from Android permissions. */
final class PermissionStrip extends LinearLayout {
    private static final String[] NAMES={"悬浮窗","无障碍","通知"};
    PermissionStrip(Context c,boolean[] granted,Runnable[] actions){
        super(c);setOrientation(VERTICAL);setContentDescription("权限智能紧凑胶囊条");Ui.pad(this,12,12,12,12);
        var assistState=AccessibilityAccess.state(c);
        boolean assistWaiting=assistState==AccessibilityAccess.State.WAITING;
        boolean assistSystemOff=assistState==AccessibilityAccess.State.SYSTEM_OFF;
        GradientDrawable frame=Ui.bg(Ui.SURFACE,22,c);frame.setStroke(Ui.dp(c,.7f),Ui.LINE);setBackground(frame);
        LinearLayout bar=Ui.row(c);int missing=0,first=-1;StringBuilder pending=new StringBuilder();
        for(int i=0;i<3;i++){
            boolean on=granted[i];final Runnable action=actions[i];if(!on){missing++;if(first<0)first=i;if(pending.length()>0)pending.append("、");pending.append(NAMES[i]);}
            String status=on?(i==1?"，已连接":"，已开启"):i==1&&assistWaiting?"，已开启，尚未连接":"，未开启，去开启";
            LinearLayout chip=Ui.row(c);chip.setGravity(Gravity.CENTER);Ui.pad(chip,6,9,6,9);chip.setMinimumHeight(Ui.dp(c,36));chip.setFocusable(true);chip.setContentDescription(NAMES[i]+status);
            int fill=on?(Ui.dark?Ui.PALE:0xFFEEF4EC):(Ui.dark?0xFF3C3529:0xFFF5F2EB),ink=on?(Ui.dark?Ui.INK:0xFF25543D):(Ui.dark?0xFFE3CFAB:0xFF5B4D34);
            GradientDrawable bg=Ui.bg(fill,14,c);if(!on)bg.setStroke(Ui.dp(c,1),0xFFD3C29D,Ui.dp(c,3),Ui.dp(c,3));chip.setBackground(bg);
            TextView badge=Ui.centeredText(c,on?"✓":"!",9,Color.WHITE,true);badge.setBackground(Ui.bg(on?0xFF2B6C4C:0xFFD49B3B,8,c));chip.addView(badge,new LayoutParams(Ui.dp(c,15),Ui.dp(c,15)));
            TextView title=Ui.text(c,NAMES[i],12,ink,true);LayoutParams nameAt=new LayoutParams(-2,-2);nameAt.leftMargin=Ui.dp(c,6);chip.addView(title,nameAt);chip.setOnClickListener(v->action.run());LayoutParams at=new LayoutParams(0,-2,1);if(i>0)at.leftMargin=Ui.dp(c,7);bar.addView(chip,at);
        }
        addView(bar);
        if(missing>0){
            Ui.gap(this,9);
            LinearLayout hint=Ui.row(c);Ui.pad(hint,12,8,12,8);
            hint.setBackground(Ui.bg(Ui.dark?0xFF3C3529:0xFFF7F4EA,12,c));
            String message=assistWaiting?"无障碍尚未连接":assistSystemOff?"系统无障碍已关闭":"还有 "+missing+" 项权限待开启（"+pending+"）";
            TextView text=Ui.text(c,message,11,Ui.dark?0xFFE3CFAB:0xFF6B5835,false);
            hint.addView(text,new LayoutParams(0,-2,1));
            TextView enable=Ui.text(c,assistWaiting?"去检查":"去开启",11,Ui.dark?Ui.INK:0xFF23533D,true);
            enable.setPaintFlags(enable.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            Ui.pad(enable,10,6,0,6);
            enable.setFocusable(true);
            enable.setContentDescription(assistWaiting?"检查无障碍连接":"开启待授权权限");
            Runnable next=actions[assistWaiting||assistSystemOff?1:first];
            enable.setOnClickListener(v->next.run());
            hint.addView(enable,new LayoutParams(-2,-2));
            addView(hint);
        }
    }
}
