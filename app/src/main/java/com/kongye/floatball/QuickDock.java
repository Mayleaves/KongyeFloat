package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Exists only while the original pointer is held. Releasing over an icon executes it. */
final class QuickDock {
    private final Context context;private final WindowManager wm;private final Consumer<String> action;
    private FrameLayout root,previewIcon;private LinearLayout preview;private TextView previewName;
    private final LinkedHashMap<View,String> targets=new LinkedHashMap<>();private String hovered;
    QuickDock(Context c,Consumer<String> action){context=c;wm=OverlayWindows.get(c);this.action=action;}
    boolean isVisible(){return root!=null;}
    private int dp(float value){return Ui.dp(context,value);}
    void show(int anchorX,int anchorY){
        close();DisplayMetrics m=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(m);
        int w=m.widthPixels,h=m.heightPixels;boolean left=anchorX<w/2;int diameter=Math.round(QuickOrbitLayout.diameter(w,h,m.density)),bubbleDp=Math.round(diameter/m.density);
        root=new FrameLayout(context);root.setContentDescription("按住显示的环形快捷菜单");root.setBackgroundColor(0x66081526);
        float[][] points=QuickOrbitLayout.positions(w,h,anchorX,anchorY,m.density);
        ArrayList<String> entries=QuickActions.load(context);entries.addAll(QuickActions.recent(context));
        for(int i=0;i<entries.size();i++){
            String id=entries.get(i);if(id.isEmpty()&&i==12)id="recent_apps";String target=id.isEmpty()?"edit_slot:"+i:id;
            FrameLayout bubble=QuickBubble.create(context,id,bubbleDp,i<12?(id.isEmpty()?Color.WHITE:QuickBubble.COLORS[i%6]):0xFF536E76);
            bubble.setContentDescription(i<12?(id.isEmpty()?"空白快捷位置"+(i+1):"快捷："+Prefs.label(context,id)):"最近："+Prefs.label(context,id));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(diameter,diameter);p.leftMargin=Math.round(points[i][0])-diameter/2;p.topMargin=Math.round(points[i][1])-diameter/2;
            root.addView(bubble,p);targets.put(bubble,target);
        }
        FrameLayout manage=QuickBubble.create(context,"quick_manage",40,0xEE304F58);manage.setContentDescription("管理快捷");
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(44),dp(44));mp.leftMargin=left?w-dp(58):dp(14);mp.topMargin=Math.max(dp(36),Math.min(h-dp(90),anchorY-dp(22)));root.addView(manage,mp);targets.put(manage,"manage_quick");
        preview=Ui.column(context);preview.setGravity(Gravity.CENTER);preview.setContentDescription("快捷操作放大预览");
        previewIcon=new FrameLayout(context);preview.addView(previewIcon,new LinearLayout.LayoutParams(dp(80),dp(80)));Ui.gap(preview,10);
        previewName=Ui.centeredText(context,"",17,Color.WHITE,true);preview.addView(previewName,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(210),dp(150),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);pp.bottomMargin=dp(100);root.addView(preview,pp);preview.setVisibility(View.INVISIBLE);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.alpha=Math.max(.2f,Prefs.of(context).getInt("quick_opacity",90)/100f);if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);p.setTitle("空叶按住快捷");wm.addView(root,p);Prefs.of(context).edit().putBoolean("quick_visible",true).putString("last_quick_hover","").apply();
    }
    void hover(float x,float y){
        String next=null;for(Map.Entry<View,String> entry:targets.entrySet()){
            View v=entry.getKey();int[] at=new int[2];v.getLocationOnScreen(at);
            boolean hit=x>=at[0]-dp(5)&&x<at[0]+v.getWidth()+dp(5)&&y>=at[1]-dp(5)&&y<at[1]+v.getHeight()+dp(5);
            v.setScaleX(hit?1.16f:1);v.setScaleY(hit?1.16f:1);if(hit)next=entry.getValue();
        }
        if(Objects.equals(next,hovered))return;hovered=next;
        if(next==null){preview.setVisibility(View.INVISIBLE);return;}
        previewIcon.removeAllViews();previewIcon.addView(QuickBubble.create(context,next.equals("manage_quick")||next.startsWith("edit_slot:")?"quick_manage":next,80,0xFF2F7890),new FrameLayout.LayoutParams(-1,-1));
        previewName.setText(next.equals("manage_quick")?"管理快捷":next.startsWith("edit_slot:")?"设置快捷位置":Prefs.label(context,next));preview.setVisibility(View.VISIBLE);
        Prefs.of(context).edit().putString("last_quick_hover",next).apply();
    }
    void release(){String selected=hovered;close();if(selected!=null)action.accept(selected);}
    void close(){if(root!=null){try{wm.removeView(root);}catch(IllegalArgumentException ignored){}root=null;Prefs.of(context).edit().putBoolean("quick_visible",false).apply();}targets.clear();hovered=null;}
}
