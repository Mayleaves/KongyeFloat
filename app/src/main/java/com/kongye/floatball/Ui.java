package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BRAND=0xFF287A59;
    static boolean dark;
    static int BG=0xFFF3F5F2, INK=0xFF18261F, MUTED=0xFF6B7871, SURFACE=Color.WHITE;
    static int GREEN=BRAND, PALE=0xFFE5F0E8, LINE=0xFFE9EDE8;
    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable bg(int color, float radius, Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    static TextView text(Context c, String value, float size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static LinearLayout row(Context c) { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    static TextView centeredText(Context c,String value,float size,int color,boolean bold) { TextView t=text(c,value,size,color,bold);t.setGravity(Gravity.CENTER);return t; }
    static void pad(View v, int a, int b, int d, int e) { Context c=v.getContext(); v.setPadding(dp(c,a),dp(c,b),dp(c,d),dp(c,e)); }
    static void gap(LinearLayout v, int h) { v.addView(new View(v.getContext()),new LinearLayout.LayoutParams(1,dp(v.getContext(),h))); }
    static TextView button(Context c, String title, boolean primary, Runnable action) {
        TextView t=text(c,title,14,primary?(dark?0xFF102E21:Color.WHITE):GREEN,true); t.setGravity(Gravity.CENTER);
        pad(t,16,14,16,14); t.setBackground(ripple(c,primary?GREEN:PALE,16)); t.setOnClickListener(v->action.run());
        t.setFocusable(true); return t;
    }
    static LinearLayout card(Context c) { LinearLayout v=column(c); pad(v,20,20,20,20); v.setBackground(bg(SURFACE,24,c)); return v; }
    static View icon(Context c,String name,int color,int size) { Icon v=new Icon(c,name,color); v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size))); v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return v; }
    static FrameLayout symbol(Context c,String icon,String description,Runnable action){FrameLayout v=new FrameLayout(c);v.setContentDescription(description);v.setFocusable(true);v.setBackground(ripple(c,PALE,14));v.addView(new Icon(c,icon,GREEN),new FrameLayout.LayoutParams(dp(c,22),dp(c,22),Gravity.CENTER));v.setOnClickListener(w->action.run());v.setMinimumWidth(dp(c,42));v.setMinimumHeight(dp(c,42));return v;}
    /** Neutral icon colour for bare header buttons. */
    static int toolTint(){return dark?0xFFC3CFC7:0xFF46544C;}
    /** Bare header button: no tile, only a round touch ripple. */
    static FrameLayout tool(Context c,String icon,String description,Runnable action){FrameLayout v=new FrameLayout(c);v.setContentDescription(description);v.setFocusable(true);v.setBackground(ripple(c,0,20));v.addView(new Icon(c,icon,toolTint()),new FrameLayout.LayoutParams(dp(c,22),dp(c,22),Gravity.CENTER));v.setOnClickListener(w->action.run());v.setMinimumWidth(dp(c,40));v.setMinimumHeight(dp(c,40));return v;}
    /** Tonal pill with an icon and a short label, for action rows under results. */
    static LinearLayout action(Context c,String icon,String label,String description,Runnable action){LinearLayout v=row(c);v.setGravity(Gravity.CENTER);v.setContentDescription(description);v.setFocusable(true);v.setBackground(ripple(c,PALE,14));v.addView(icon(c,icon,GREEN,18));TextView t=text(c,label,13,GREEN,true);t.setSingleLine();pad(t,6,0,0,0);v.addView(t);v.setOnClickListener(w->action.run());v.setMinimumHeight(dp(c,42));return v;}
    /** Single-line input on a soft filled background. */
    static EditText field(Context c,String hint){EditText e=new EditText(c);e.setHint(hint);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(14);e.setSingleLine();e.setBackground(bg(BG,14,c));pad(e,14,0,14,0);e.setGravity(Gravity.CENTER_VERTICAL);return e;}
    /** Segmented choice: the selected option sits on a raised surface chip. */
    static LinearLayout segmented(Context c,String[] labels,int selected,java.util.function.IntConsumer pick){LinearLayout bar=row(c);bar.setBackground(bg(BG,14,c));pad(bar,3,3,3,3);for(int i=0;i<labels.length;i++){int at=i;boolean on=i==selected;TextView t=centeredText(c,labels[i],13,on?INK:MUTED,on);t.setSelected(on);t.setContentDescription(labels[i]+(on?"，已选择":""));if(on){t.setBackground(bg(SURFACE,11,c));t.setElevation(dp(c,1));}t.setOnClickListener(v->pick.accept(at));bar.addView(t,new LinearLayout.LayoutParams(0,dp(c,32),1));}return bar;}
    /** Soft frame that clips media to rounded corners. */
    static void frame(View v,int color,float radius){v.setBackground(bg(color,radius,v.getContext()));v.setClipToOutline(true);}
    /** Ripple over an optional fill; transparent fills keep only the touch feedback. */
    static android.graphics.drawable.RippleDrawable ripple(Context c,int fill,float radius){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(dark?0x33FFFFFF:0x1F000000),fill==0?null:bg(fill,radius,c),bg(Color.WHITE,radius,c));}
    static class Icon extends View {
        final Paint p=new Paint(3); final Path path=new Path(); final String name; final int color;
        public Icon(Context c){this(c,"leaf",GREEN);}
        Icon(Context c,String name,int color){super(c);this.name=name;this.color=color;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/24f,getHeight()/24f);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            switch(name) {
                case "leaf" -> {
                    p.setStyle(Paint.Style.FILL);path.moveTo(5,16);path.cubicTo(3,8,9,4,21,4);path.cubicTo(21,14,15,21,8,19);path.close();canvas.drawPath(path,p);
                    p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);canvas.drawLine(5,21,17,8,p);canvas.drawLine(11,14,10,10,p);
                }
                case "back" -> {canvas.drawLine(16,5,8,12,p);canvas.drawLine(8,12,16,19,p);}
                case "forward" -> {canvas.drawLine(8,5,16,12,p);canvas.drawLine(16,12,8,19,p);}
                case "home" -> {path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);path.moveTo(6,9);path.lineTo(6,21);path.lineTo(18,21);path.lineTo(18,9);canvas.drawPath(path,p);}
                case "recents" -> {for(int x=4;x<=14;x+=10)for(int y=4;y<=14;y+=10)canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,p);}
                case "apps" -> {p.setStyle(Paint.Style.FILL);for(int x=5;x<=19;x+=7)for(int y=5;y<=19;y+=7)canvas.drawCircle(x,y,2.1f,p);}
                case "screenshot" -> {canvas.drawRoundRect(4,6,20,20,3,3,p);canvas.drawCircle(12,13,4,p);canvas.drawLine(9,3,15,3,p);}
                case "region" -> {path.moveTo(8,3);path.lineTo(3,3);path.lineTo(3,8);path.moveTo(16,3);path.lineTo(21,3);path.lineTo(21,8);path.moveTo(3,16);path.lineTo(3,21);path.lineTo(8,21);path.moveTo(21,16);path.lineTo(21,21);path.lineTo(16,21);canvas.drawPath(path,p);canvas.drawRoundRect(8,8,16,16,1,1,p);}
                case "longshot" -> {canvas.drawRoundRect(5,2,19,22,2,2,p);canvas.drawLine(9,7,15,7,p);canvas.drawLine(12,10,12,18,p);canvas.drawLine(9,15,12,18,p);canvas.drawLine(15,15,12,18,p);}
                case "auto_longshot" -> {canvas.drawRoundRect(2,2,15,22,2,2,p);canvas.drawLine(5,7,12,7,p);canvas.drawLine(5,12,12,12,p);canvas.drawLine(5,17,10,17,p);canvas.drawLine(20,3,20,20,p);canvas.drawLine(17,16,20,20,p);canvas.drawLine(20,20,23,16,p);}
                case "gif_record" -> {canvas.drawRoundRect(2,5,17,19,2,2,p);p.setStyle(Paint.Style.FILL);p.setTextSize(6);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));canvas.drawText("GIF",3,14,p);p.setStyle(Paint.Style.STROKE);path.moveTo(17,10);path.lineTo(22,7);path.lineTo(22,17);path.lineTo(17,14);canvas.drawPath(path,p);}
                case "pause" -> {canvas.drawLine(8,5,8,19,p);canvas.drawLine(16,5,16,19,p);}
                case "play" -> {path.moveTo(7,4);path.lineTo(20,12);path.lineTo(7,20);path.close();canvas.drawPath(path,p);}
                case "stop" -> {p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(6,6,18,18,2,2,p);}
                case "resolution" -> {canvas.drawRoundRect(2,5,22,19,2.5f,2.5f,p);path.moveTo(6,12);path.lineTo(6,9);path.lineTo(9,9);path.moveTo(18,12);path.lineTo(18,15);path.lineTo(15,15);canvas.drawPath(path,p);}
                case "watermark" -> {canvas.drawRoundRect(3,4,21,20,2.5f,2.5f,p);canvas.drawLine(8,9,16,9,p);canvas.drawLine(12,9,12,16,p);}
                case "audio" -> {canvas.drawRoundRect(9,3,15,14,3,3,p);canvas.drawArc(6,7,18,18,0,180,false,p);canvas.drawLine(12,18,12,22,p);}
                case "hide" -> {canvas.drawOval(2,6,22,18,p);canvas.drawCircle(12,12,3,p);canvas.drawLine(3,3,21,21,p);}
                case "help" -> {canvas.drawCircle(12,12,10,p);p.setStyle(Paint.Style.FILL);p.setTextSize(17);canvas.drawText("?",8,18,p);}
                case "record", "record_silent", "record_sound" -> {canvas.drawRoundRect(2,5,16,19,3,3,p);path.moveTo(16,10);path.lineTo(22,7);path.lineTo(22,17);path.lineTo(16,14);canvas.drawPath(path,p);if(name.equals("record_sound"))canvas.drawCircle(9,12,2,p);}
                case "record_stop" -> {canvas.drawCircle(12,12,10,p);canvas.drawRoundRect(8,8,16,16,1,1,p);}
                case "recent_apps" -> {canvas.drawCircle(12,12,9,p);canvas.drawLine(12,6,12,12,p);canvas.drawLine(12,12,17,15,p);}
                case "favorite_apps", "star_on" -> {for(int i=0;i<10;i++){double angle=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?10:4.5f;float x=12+(float)Math.cos(angle)*radius,y=12+(float)Math.sin(angle)*radius;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();if(name.equals("star_on"))p.setStyle(Paint.Style.FILL_AND_STROKE);canvas.drawPath(path,p);}
                case "text" -> {canvas.drawLine(4,5,20,5,p);canvas.drawLine(12,5,12,20,p);canvas.drawLine(8,20,16,20,p);canvas.drawLine(4,5,4,8,p);canvas.drawLine(20,5,20,8,p);}
                case "translate" -> {p.setStyle(Paint.Style.FILL);p.setTextSize(10);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));canvas.drawText("A",2,11,p);canvas.drawText("文",12,21,p);p.setStyle(Paint.Style.STROKE);canvas.drawLine(10,4,21,4,p);canvas.drawLine(18,2,21,4,p);canvas.drawLine(21,4,18,6,p);}
                case "notes" -> {canvas.drawRoundRect(4,3,20,21,3,3,p);canvas.drawLine(8,8,16,8,p);canvas.drawLine(8,12,16,12,p);canvas.drawLine(8,16,13,16,p);}
                case "browser" -> {canvas.drawCircle(12,12,9,p);canvas.drawOval(8,3,16,21,p);canvas.drawLine(3,12,21,12,p);}
                case "notifications" -> {path.moveTo(5,17);path.lineTo(7,14);path.lineTo(7,9);path.cubicTo(7,2,17,2,17,9);path.lineTo(17,14);path.lineTo(19,17);path.close();canvas.drawPath(path,p);canvas.drawArc(10,18,14,22,0,180,false,p);}
                case "files" -> {path.moveTo(3,7);path.lineTo(3,20);path.lineTo(21,20);path.lineTo(21,7);path.lineTo(12,7);path.lineTo(10,4);path.lineTo(3,4);path.close();canvas.drawPath(path,p);}
                case "lock" -> {canvas.drawRoundRect(5,10,19,21,2,2,p);canvas.drawArc(8,2,16,15,180,180,false,p);canvas.drawLine(12,14,12,17,p);}
                case "quick_manage" -> {canvas.drawRoundRect(3,3,10,10,2,2,p);canvas.drawRoundRect(14,3,21,10,2,2,p);canvas.drawRoundRect(3,14,10,21,2,2,p);canvas.drawLine(15,21,21,15,p);canvas.drawLine(14,22,17,21,p);}
                case "opacity" -> {canvas.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);canvas.drawArc(3,3,21,21,90,180,true,p);}
                case "pen" -> {canvas.drawLine(4,20,20,4,p);canvas.drawLine(14,4,20,10,p);canvas.drawLine(3,21,8,20,p);}
                case "rect", "crop" -> {canvas.drawRect(4,5,20,19,p);if(name.equals("crop")){canvas.drawLine(2,8,22,8,p);canvas.drawLine(8,2,8,22,p);}}
                case "line" -> canvas.drawLine(4,20,20,4,p);
                case "arrow" -> {canvas.drawLine(4,20,20,4,p);canvas.drawLine(11,4,20,4,p);canvas.drawLine(20,4,20,13,p);}
                case "number" -> {canvas.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);p.setTextSize(13);canvas.drawText("1",8,17,p);}
                case "mosaic" -> {for(int i=0;i<3;i++)for(int j=0;j<3;j++){p.setStyle((i+j)%2==0?Paint.Style.FILL:Paint.Style.STROKE);canvas.drawRect(3+i*6,3+j*6,9+i*6,9+j*6,p);}}
                case "rotate" -> {canvas.drawArc(4,4,20,20,0,285,false,p);canvas.drawLine(13,2,18,5,p);canvas.drawLine(18,5,14,9,p);}
                case "mirror_h", "mirror_v" -> {if(name.equals("mirror_v"))canvas.rotate(90,12,12);canvas.drawLine(12,2,12,22,p);path.moveTo(9,5);path.lineTo(3,19);path.lineTo(9,19);path.close();canvas.drawPath(path,p);path.reset();path.moveTo(15,5);path.lineTo(21,19);path.lineTo(15,19);path.close();canvas.drawPath(path,p);}
                case "reset" -> {canvas.drawArc(4,4,20,20,35,300,false,p);canvas.drawLine(3,3,3,10,p);canvas.drawLine(3,10,10,10,p);}
                case "select_all" -> {canvas.drawRect(3,3,21,21,p);canvas.drawLine(6,8,18,8,p);canvas.drawLine(12,8,12,18,p);}
                case "vertical" -> {canvas.drawRoundRect(5,2,19,22,2,2,p);canvas.drawLine(5,9,19,9,p);canvas.drawLine(5,15,19,15,p);}
                case "horizontal" -> {canvas.drawRoundRect(2,5,22,19,2,2,p);canvas.drawLine(9,5,9,19,p);canvas.drawLine(15,5,15,19,p);}
                case "resize" -> {canvas.drawLine(4,4,20,20,p);canvas.drawLine(4,4,4,11,p);canvas.drawLine(4,4,11,4,p);canvas.drawLine(20,20,13,20,p);canvas.drawLine(20,20,20,13,p);}
                case "reorder" -> {canvas.drawRect(3,3,11,14,p);canvas.drawRect(13,10,21,21,p);canvas.drawLine(15,4,21,4,p);canvas.drawLine(3,20,9,20,p);}
                case "close" -> {canvas.drawLine(6,6,18,18,p);canvas.drawLine(18,6,6,18,p);}
                case "plus" -> {canvas.drawLine(5,12,19,12,p);canvas.drawLine(12,5,12,19,p);}
                case "settings", "manage_quick" -> {canvas.drawCircle(12,12,4,p);for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,12,12);canvas.drawLine(12,2,12,5,p);canvas.restore();}canvas.drawCircle(12,12,8,p);}
                case "copy" -> {canvas.drawRoundRect(7,7,21,21,2,2,p);path.moveTo(17,5);path.lineTo(17,3);path.lineTo(3,3);path.lineTo(3,17);path.lineTo(5,17);canvas.drawPath(path,p);}
                case "save" -> {canvas.drawRoundRect(3,3,21,21,2,2,p);canvas.drawRect(7,3,17,9,p);canvas.drawRect(7,14,17,21,p);}
                case "edit" -> {path.moveTo(4,15);path.lineTo(3,21);path.lineTo(9,20);path.lineTo(21,8);path.lineTo(16,3);path.close();canvas.drawPath(path,p);canvas.drawLine(13,6,18,11,p);}
                case "zoom" -> {canvas.drawCircle(10,10,6,p);canvas.drawLine(14.5f,14.5f,21,21,p);canvas.drawLine(7,10,13,10,p);canvas.drawLine(10,7,10,13,p);}
                case "pin", "pinned" -> {path.moveTo(8,3);path.lineTo(18,3);path.lineTo(16,9);path.lineTo(20,13);path.lineTo(6,13);path.lineTo(10,9);path.close();if(name.equals("pinned"))p.setStyle(Paint.Style.FILL);canvas.drawPath(path,p);p.setStyle(Paint.Style.STROKE);canvas.drawLine(13,13,7,22,p);}
                case "collapse" -> {canvas.drawLine(5,15,12,8,p);canvas.drawLine(12,8,19,15,p);}
                case "expand" -> {canvas.drawLine(5,8,12,15,p);canvas.drawLine(12,15,19,8,p);}
                case "share" -> {canvas.drawCircle(5,12,2.5f,p);canvas.drawCircle(19,5,2.5f,p);canvas.drawCircle(19,19,2.5f,p);canvas.drawLine(7,11,17,6,p);canvas.drawLine(7,13,17,18,p);}
                case "undo" -> {canvas.drawLine(8,4,3,9,p);canvas.drawLine(3,9,9,12,p);canvas.drawArc(3,7,21,21,200,280,false,p);}
                case "check" -> {canvas.drawLine(5,12,10,17,p);canvas.drawLine(10,17,20,6,p);}
                case "up", "down" -> {float d=name.equals("up")?1:-1;canvas.save();canvas.rotate(d<0?180:0,12,12);canvas.drawLine(12,21,12,4,p);canvas.drawLine(5,11,12,4,p);canvas.drawLine(19,11,12,4,p);canvas.restore();}
                case "appearance" -> {canvas.drawCircle(12,12,8,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,12,3,p);}
                case "power" -> {canvas.drawArc(4,4,20,21,-55,290,false,p);canvas.drawLine(12,2,12,11,p);}
                default -> {canvas.drawLine(4,7,20,7,p);canvas.drawLine(4,12,20,12,p);canvas.drawLine(4,17,20,17,p);canvas.drawCircle(8,7,2,p);canvas.drawCircle(16,17,2,p);}
            }
            canvas.restore();
        }
    }
}
