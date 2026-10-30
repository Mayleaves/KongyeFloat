package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BRAND=0xFF28765A;
    static boolean dark;
    static int BG=0xFFF3F6F1, INK=0xFF1E2E26, MUTED=0xFF8B958B, SURFACE=Color.WHITE;
    static int GREEN=BRAND, PALE=0xFFE8F0E4, LINE=0xFFEDF0E9;
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
    static int toolbar(){return dark?0xFF304B3D:0xFF2C473E;}
    static int toolTint(){return dark?0xFFC3CFC7:0xFF46544C;}
    /** Bare header button: no tile, only a round touch ripple. */
    static FrameLayout tool(Context c,String icon,String description,Runnable action){FrameLayout v=new FrameLayout(c);v.setContentDescription(description);v.setFocusable(true);v.setBackground(ripple(c,0,20));v.addView(new Icon(c,icon,toolTint()),new FrameLayout.LayoutParams(dp(c,22),dp(c,22),Gravity.CENTER));v.setOnClickListener(w->action.run());v.setMinimumWidth(dp(c,40));v.setMinimumHeight(dp(c,40));return v;}
    /** Tonal pill with an icon and a short label, for action rows under results. */
    static LinearLayout action(Context c,String icon,String label,String description,Runnable action){LinearLayout v=row(c);v.setGravity(Gravity.CENTER);v.setContentDescription(description);v.setFocusable(true);v.setBackground(ripple(c,PALE,14));v.addView(icon(c,icon,GREEN,18));TextView t=text(c,label,13,GREEN,true);t.setSingleLine();pad(t,6,0,0,0);v.addView(t);v.setOnClickListener(w->action.run());v.setMinimumHeight(dp(c,42));return v;}
    /** Single-line input on a soft filled background. */
    static EditText field(Context c,String hint){EditText e=new EditText(c);e.setHint(hint);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(14);e.setSingleLine();e.setBackground(bg(BG,14,c));pad(e,14,0,14,0);e.setGravity(Gravity.CENTER_VERTICAL);return e;}
    /** Segmented choice: the selected option sits on a raised surface chip. */
    static LinearLayout segmented(Context c,String[] labels,int selected,java.util.function.IntConsumer pick){LinearLayout bar=row(c);bar.setBackground(bg(BG,14,c));pad(bar,3,3,3,3);for(int i=0;i<labels.length;i++){int at=i;boolean on=i==selected;TextView t=centeredText(c,labels[i],13,on?INK:MUTED,on);t.setSelected(on);t.setContentDescription(labels[i]+(on?"，已选择":""));if(on){t.setBackground(bg(SURFACE,11,c));t.setElevation(dp(c,1));}t.setOnClickListener(v->{for(int j=0;j<bar.getChildCount();j++){TextView option=(TextView)bar.getChildAt(j);boolean active=j==at;option.setSelected(active);option.setContentDescription(labels[j]+(active?"，已选择":""));option.setTextColor(active?INK:MUTED);option.setTypeface(Typeface.create(active?"sans-serif-medium":"sans-serif",Typeface.NORMAL));option.setBackground(active?bg(SURFACE,11,c):null);option.setElevation(active?dp(c,1):0);}pick.accept(at);});bar.addView(t,new LinearLayout.LayoutParams(0,dp(c,32),1));}return bar;}
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
                case "leaf_outline" -> {path.moveTo(5,17);path.cubicTo(2,9,10,3,21,3);path.cubicTo(21,14,15,21,7,19);canvas.drawPath(path,p);canvas.drawLine(3,22,17,8,p);}
                case "nav_home" -> {
                    canvas.save();canvas.scale(24f/64,24f/64);p.setStyle(Paint.Style.FILL);
                    path.moveTo(8,29);path.lineTo(28,10);path.quadTo(32,6,36,10);path.lineTo(56,29);path.quadTo(60,33,55,36);path.lineTo(52,37);path.lineTo(52,53);path.quadTo(52,59,46,59);path.lineTo(18,59);path.quadTo(12,59,12,53);path.lineTo(12,37);path.lineTo(9,36);path.quadTo(4,33,8,29);path.close();
                    Path cuts=new Path();cuts.addRoundRect(27,38,37,65,5,5,Path.Direction.CW);Path vein=new Path();vein.moveTo(17,30);vein.lineTo(31,17);Paint cutter=new Paint(p);cutter.setStyle(Paint.Style.STROKE);cutter.setStrokeWidth(3.4f);Path stroke=new Path();cutter.getFillPath(vein,stroke);cuts.op(stroke,Path.Op.UNION);path.op(cuts,Path.Op.DIFFERENCE);canvas.drawPath(path,p);canvas.restore();
                }
                case "nav_settings", "settings" -> {
                    canvas.save();canvas.rotate(15,12,12);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);
                    for(int i=0;i<32;i++){double a=-Math.PI/2+i*Math.PI/16;float radius=(i%4==1||i%4==2)?9.5f:7.8f;float x=12+(float)Math.cos(a)*radius,y=12+(float)Math.sin(a)*radius;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();canvas.drawPath(path,p);canvas.drawCircle(12,12,3.2f,p);canvas.restore();
                }
                case "screen" -> {canvas.drawRoundRect(4,2,20,22,3,3,p);canvas.drawLine(9,18,15,18,p);}
                case "apps_grid" -> {for(int x=3;x<=14;x+=11)for(int y=3;y<=14;y+=11)canvas.drawRoundRect(x,y,x+7,y+7,2,2,p);}
                case "sun" -> {canvas.drawCircle(12,12,4,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+7*(float)Math.cos(a),12+7*(float)Math.sin(a),12+10*(float)Math.cos(a),12+10*(float)Math.sin(a),p);}}
                case "theme" -> {path.moveTo(20,15);path.cubicTo(12,18,6,10,9,3);path.cubicTo(-3,6,3,27,16,21);path.quadTo(19,19,20,15);canvas.drawPath(path,p);}
                case "image" -> {canvas.drawRoundRect(3,3,21,21,3,3,p);canvas.drawCircle(8,8,2,p);path.moveTo(3,17);path.lineTo(9,12);path.lineTo(13,15);path.lineTo(16,12);path.lineTo(21,17);canvas.drawPath(path,p);}
                case "github" -> {android.graphics.drawable.Drawable mark=getContext().getDrawable(R.drawable.ic_github_mark);if(mark!=null){mark=mark.mutate();mark.setTint(Ui.INK);mark.setBounds(0,0,24,24);mark.draw(canvas);}}
                case "back" -> {canvas.drawLine(16,5,8,12,p);canvas.drawLine(8,12,16,19,p);}
                case "forward" -> {canvas.drawLine(8,5,16,12,p);canvas.drawLine(16,12,8,19,p);}
                case "home" -> {path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);path.moveTo(6,9);path.lineTo(6,21);path.lineTo(18,21);path.lineTo(18,9);canvas.drawPath(path,p);}
                case "layers" -> {path.moveTo(2,8);path.lineTo(12,2);path.lineTo(22,8);path.lineTo(12,14);path.close();path.moveTo(2,13);path.lineTo(12,19);path.lineTo(22,13);path.moveTo(2,18);path.lineTo(12,24);path.lineTo(22,18);canvas.drawPath(path,p);}
                case "recover" -> {path.moveTo(4,9);path.cubicTo(6,3,15,2,19,7);path.cubicTo(26,16,16,26,8,20);path.lineTo(6,18);canvas.drawPath(path,p);path.reset();path.moveTo(4,3);path.lineTo(4,9);path.lineTo(10,9);canvas.drawPath(path,p);}
                case "position" -> {canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2,p);canvas.drawLine(12,1,12,6,p);canvas.drawLine(12,18,12,23,p);canvas.drawLine(1,12,6,12,p);canvas.drawLine(18,12,23,12,p);}
                case "update" -> {canvas.drawLine(12,2,12,16,p);canvas.drawLine(7,11,12,16,p);canvas.drawLine(12,16,17,11,p);path.moveTo(4,17);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,17);canvas.drawPath(path,p);}
                case "info" -> {canvas.drawCircle(12,12,9,p);canvas.drawLine(12,10,12,17,p);canvas.drawCircle(12,6.5f,.6f,p);}
                case "recents" -> {for(int x=4;x<=14;x+=10)for(int y=4;y<=14;y+=10)canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,p);}
                case "apps" -> {p.setStyle(Paint.Style.FILL);for(int x=5;x<=19;x+=7)for(int y=5;y<=19;y+=7)canvas.drawCircle(x,y,2.1f,p);}
                case "screenshot" -> {canvas.drawRoundRect(4,6,20,20,3,3,p);canvas.drawCircle(12,13,4,p);canvas.drawLine(9,3,15,3,p);}
                case "region" -> {
                    path.moveTo(12,20);path.lineTo(6,20);path.quadTo(3,20,3,17);path.lineTo(3,6);path.quadTo(3,3,6,3);path.lineTo(17,3);path.quadTo(20,3,20,6);path.lineTo(20,10);canvas.drawPath(path,p);
                    p.setStrokeWidth(1.6f);canvas.drawCircle(16,20,1.8f,p);canvas.drawCircle(21,20,1.8f,p);canvas.drawLine(15.5f,14.5f,21,20,p);canvas.drawLine(21.5f,14.5f,16,20,p);
                }
                case "longshot", "auto_longshot" -> {
                    canvas.save();if(name.equals("auto_longshot")){canvas.scale(.84f,.84f);canvas.translate(-.5f,2);}
                    path.moveTo(3,7);path.lineTo(3,3);path.lineTo(21,3);path.lineTo(21,7);canvas.drawPath(path,p);
                    for(int y=10;y<=17;y+=3){canvas.drawLine(3,y,3,y+.5f,p);canvas.drawLine(21,y,21,y+.5f,p);}
                    canvas.drawCircle(8,20,2.4f,p);canvas.drawCircle(16,20,2.4f,p);canvas.drawLine(7,11,17.5f,21.5f,p);canvas.drawLine(17,11,6.5f,21.5f,p);canvas.restore();
                    if(name.equals("auto_longshot")){canvas.drawLine(21,4,21,20,p);canvas.drawLine(18.5f,17,21,20,p);canvas.drawLine(21,20,23.5f,17,p);}
                }
                case "gif_record" -> {canvas.drawRoundRect(1.5f,5,17,20,3,3,p);p.setStyle(Paint.Style.FILL);p.setTextSize(6.4f);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));canvas.drawText("GIF",3.1f,14.8f,p);p.setStyle(Paint.Style.STROKE);path.moveTo(17,10);path.lineTo(22,7);path.quadTo(23,6.5f,23,8);path.lineTo(23,17);path.quadTo(23,18.5f,22,18);path.lineTo(17,15);canvas.drawPath(path,p);}
                case "quick_settings", "quick_manage", "manage_quick" -> {canvas.drawLine(3,7,21,7,p);canvas.drawLine(3,17,21,17,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(9,7,3,p);canvas.drawCircle(15,17,3,p);}
                case "gesture_tap", "gesture_double", "gesture_hold" -> {
                    path.moveTo(8,17);path.lineTo(8,9);path.cubicTo(8,5.5f,12,5.5f,12,9);path.lineTo(12,14);path.lineTo(15,12);path.quadTo(16,11.5f,17,13);path.lineTo(20,16);path.quadTo(21,17,19,21);path.lineTo(11,21);path.lineTo(6,17);path.quadTo(4,14,6,14);path.close();canvas.drawPath(path,p);
                    if(name.equals("gesture_hold")){canvas.drawCircle(18,5,3.5f,p);canvas.drawLine(18,3,18,5,p);canvas.drawLine(18,5,20,5,p);}
                    else{canvas.drawLine(3,7,1.5f,6,p);canvas.drawLine(5,3,4,1.5f,p);if(name.equals("gesture_double")){p.setStyle(Paint.Style.FILL);p.setTextSize(7);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));canvas.drawText("2",15,7,p);}else canvas.drawLine(12,3,13,1.5f,p);}
                }
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
                case "recent_apps" -> {canvas.drawCircle(12,12,9,p);canvas.drawLine(12,6,12,12,p);canvas.drawLine(12,12,18,12,p);}
                case "favorite_apps", "star_on" -> {for(int i=0;i<10;i++){double angle=-Math.PI/2+i*Math.PI/5;float radius=i%2==0?10:4.5f;float x=12+(float)Math.cos(angle)*radius,y=12+(float)Math.sin(angle)*radius;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();if(name.equals("star_on"))p.setStyle(Paint.Style.FILL_AND_STROKE);canvas.drawPath(path,p);}
                case "text" -> {canvas.drawLine(4,5,20,5,p);canvas.drawLine(12,5,12,20,p);canvas.drawLine(8,20,16,20,p);canvas.drawLine(4,5,4,8,p);canvas.drawLine(20,5,20,8,p);}
                case "translate" -> {p.setStyle(Paint.Style.FILL);p.setTextSize(10);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));canvas.drawText("A",2,11,p);canvas.drawText("文",12,21,p);p.setStyle(Paint.Style.STROKE);canvas.drawLine(10,4,21,4,p);canvas.drawLine(18,2,21,4,p);canvas.drawLine(21,4,18,6,p);}
                case "notes" -> {canvas.drawRoundRect(4,3,20,21,3,3,p);canvas.drawLine(8,8,16,8,p);canvas.drawLine(8,12,16,12,p);canvas.drawLine(8,16,13,16,p);}
                case "browser" -> {canvas.drawCircle(12,12,9,p);canvas.drawOval(8,3,16,21,p);canvas.drawLine(3,12,21,12,p);}
                case "notifications" -> {path.moveTo(5,17);path.lineTo(7,14);path.lineTo(7,9);path.cubicTo(7,2,17,2,17,9);path.lineTo(17,14);path.lineTo(19,17);path.close();canvas.drawPath(path,p);canvas.drawArc(10,18,14,22,0,180,false,p);}
                case "files" -> {path.moveTo(3,7);path.lineTo(3,20);path.lineTo(21,20);path.lineTo(21,7);path.lineTo(12,7);path.lineTo(10,4);path.lineTo(3,4);path.close();canvas.drawPath(path,p);}
                case "lock" -> {canvas.drawRoundRect(5,10,19,21,2,2,p);canvas.drawArc(8,2,16,15,180,180,false,p);canvas.drawLine(12,14,12,17,p);}
                case "opacity" -> {canvas.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);canvas.drawArc(3,3,21,21,90,180,true,p);}
                case "pen", "edit" -> {path.moveTo(4,15);path.lineTo(4,21);path.lineTo(10,21);path.lineTo(22,9);path.lineTo(16,3);path.close();canvas.drawPath(path,p);canvas.drawLine(13,6,19,12,p);}
                case "highlight" -> {canvas.drawArc(5,3,19,17,145,250,false,p);path.moveTo(8,15);path.lineTo(9,20);path.quadTo(12,24,15,20);path.lineTo(16,15);canvas.drawPath(path,p);canvas.drawLine(9,18,15,18,p);canvas.drawLine(2,6,1,5,p);canvas.drawLine(2,11,1,11,p);canvas.drawLine(22,6,23,5,p);canvas.drawLine(22,11,23,11,p);}
                case "rect" -> canvas.drawRect(4,5,20,19,p);
                case "crop" -> {path.moveTo(5,2);path.lineTo(5,19);path.lineTo(22,19);path.moveTo(2,5);path.lineTo(17,5);path.moveTo(19,8);path.lineTo(19,22);canvas.drawPath(path,p);canvas.drawLine(5,19,22,2,p);}
                case "line" -> canvas.drawLine(4,20,20,4,p);
                case "arrow" -> {canvas.drawLine(4,20,20,4,p);canvas.drawLine(11,4,20,4,p);canvas.drawLine(20,4,20,13,p);}
                case "number" -> {canvas.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);p.setTextSize(13);canvas.drawText("1",8,17,p);}
                case "mosaic" -> {for(int i=0;i<3;i++)for(int j=0;j<3;j++){p.setStyle((i+j)%2==0?Paint.Style.FILL:Paint.Style.STROKE);canvas.drawRect(3+i*6,3+j*6,9+i*6,9+j*6,p);}}
                case "rotate" -> {canvas.drawArc(4,4,20,20,0,285,false,p);canvas.drawLine(13,2,18,5,p);canvas.drawLine(18,5,14,9,p);}
                case "mirror_h", "mirror_v" -> {if(name.equals("mirror_v"))canvas.rotate(90,12,12);canvas.drawLine(12,2,12,22,p);path.moveTo(9,5);path.lineTo(3,19);path.lineTo(9,19);path.close();canvas.drawPath(path,p);path.reset();path.moveTo(15,5);path.lineTo(21,19);path.lineTo(15,19);path.close();canvas.drawPath(path,p);}
                case "reset" -> {canvas.drawArc(3,3,21,21,-90,160,false,p);canvas.drawArc(3,3,21,21,90,160,false,p);path.moveTo(21,15);path.lineTo(21,21);path.lineTo(15,21);path.moveTo(9,3);path.lineTo(3,3);path.lineTo(3,9);canvas.drawPath(path,p);}
                case "select_all" -> {canvas.drawRect(3,3,21,21,p);canvas.drawLine(6,8,18,8,p);canvas.drawLine(12,8,12,18,p);}
                case "vertical" -> {canvas.drawRoundRect(5,2,19,22,2,2,p);canvas.drawLine(5,9,19,9,p);canvas.drawLine(5,15,19,15,p);}
                case "horizontal" -> {canvas.drawRoundRect(2,5,22,19,2,2,p);canvas.drawLine(9,5,9,19,p);canvas.drawLine(15,5,15,19,p);}
                case "resize" -> {path.moveTo(9,3);path.lineTo(3,3);path.lineTo(3,9);path.moveTo(3,3);path.lineTo(21,21);path.moveTo(15,21);path.lineTo(21,21);path.lineTo(21,15);canvas.drawPath(path,p);}
                case "reorder" -> {canvas.drawRect(3,3,11,14,p);canvas.drawRect(13,10,21,21,p);canvas.drawLine(15,4,21,4,p);canvas.drawLine(3,20,9,20,p);}
                case "close" -> {canvas.drawLine(6,6,18,18,p);canvas.drawLine(18,6,6,18,p);}
                case "plus" -> {canvas.drawLine(5,12,19,12,p);canvas.drawLine(12,5,12,19,p);}
                case "copy" -> {canvas.drawRoundRect(7,7,21,21,2,2,p);path.moveTo(17,5);path.lineTo(17,3);path.lineTo(3,3);path.lineTo(3,17);path.lineTo(5,17);canvas.drawPath(path,p);}
                case "save" -> {canvas.drawRoundRect(3,3,21,21,2,2,p);canvas.drawRect(7,3,17,9,p);canvas.drawRect(7,14,17,21,p);}
                case "zoom" -> {canvas.drawCircle(10,10,6,p);canvas.drawLine(14.5f,14.5f,21,21,p);canvas.drawLine(7,10,13,10,p);canvas.drawLine(10,7,10,13,p);}
                case "pin", "pinned" -> {canvas.save();canvas.rotate(45,12,12);path.moveTo(8,3);path.lineTo(17,3);path.lineTo(17,6);path.lineTo(15,6);path.lineTo(15,12);path.lineTo(19,16);path.lineTo(6,16);path.lineTo(10,12);path.lineTo(10,6);path.lineTo(8,6);path.close();if(name.equals("pinned"))p.setStyle(Paint.Style.FILL);canvas.drawPath(path,p);p.setStyle(Paint.Style.STROKE);canvas.drawLine(12.5f,16,12.5f,23,p);canvas.restore();}
                case "collapse" -> {canvas.drawLine(5,15,12,8,p);canvas.drawLine(12,8,19,15,p);}
                case "expand" -> {canvas.drawLine(5,8,12,15,p);canvas.drawLine(12,15,19,8,p);}
                case "share" -> {canvas.drawCircle(5,12,2.5f,p);canvas.drawCircle(19,5,2.5f,p);canvas.drawCircle(19,19,2.5f,p);canvas.drawLine(7,11,17,6,p);canvas.drawLine(7,13,17,18,p);}
                case "undo" -> {p.setStrokeWidth(2.2f);path.moveTo(8,4);path.lineTo(3,9);path.lineTo(8,14);path.moveTo(3,9);path.lineTo(14,9);path.cubicTo(24,9,24,21,14,21);path.lineTo(5,21);canvas.drawPath(path,p);}
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
