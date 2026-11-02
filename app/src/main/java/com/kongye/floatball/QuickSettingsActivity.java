package com.kongye.floatball;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The same orbit as the held menu. White slots are configurable; grey slots are recent apps. */
public class QuickSettingsActivity extends Activity {
    private ArrayList<String> slots;
    @Override public void onCreate(Bundle state){setTheme(R.style.TransparentTheme);super.onCreate(state);slots=QuickActions.load(this);render();int edit=getIntent().getIntExtra("edit_slot",-1);if(edit>=0&&edit<12)new android.os.Handler().postDelayed(()->choose(edit),250);}
    @Override protected void onResume(){super.onResume();if(slots!=null)render();}
    private int dp(float n){return Ui.dp(this,n);}
    private void render(){
        ThemePolicy.apply(this);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0x66304134);root.setOnClickListener(v->finish());
        DisplayMetrics m=new DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(m);
        int[] size=BallArt.dimensions(this,BallSettings.pixels(this));int[] live=FloatService.ballCenter();int x=live==null?Prefs.of(this).getInt("x",m.widthPixels-size[0])+size[0]/2:live[0],y=live==null?Prefs.of(this).getInt("y",m.heightPixels/3)+size[1]/2:live[1];int diameter=Math.max(1,Math.round(QuickOrbitLayout.diameter(m.widthPixels,m.heightPixels,x,y,m.density))),bubbleDp=Math.max(1,Math.round(diameter/m.density));
        float[][] positions=QuickOrbitLayout.positions(m.widthPixels,m.heightPixels,x,y,m.density);
        for(int i=0;i<12;i++){
            int index=i;String id=slots.get(i);FrameLayout bubble=QuickBubble.create(this,id,bubbleDp,Color.WHITE);bubble.setContentDescription("设置快捷位置"+(i+1)+(id.isEmpty()?"，空白":"："+Prefs.label(this,id)));bubble.setOnClickListener(v->choose(index));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(diameter,diameter);p.leftMargin=Math.round(positions[i][0])-diameter/2;p.topMargin=Math.round(positions[i][1])-diameter/2;root.addView(bubble,p);
        }
        ArrayList<String> recents=QuickActions.recent(this);
        for(int i=0;i<1;i++){
            String id=recents.get(i);FrameLayout bubble=QuickBubble.create(this,id.isEmpty()?"recent_apps":id,bubbleDp,0xFF536E76,true);bubble.setContentDescription("最近应用位置"+(i+1)+(id.isEmpty()?"，自动更新":"："+Prefs.label(this,id)));
            bubble.setOnClickListener(v->{if(!AppCatalog.hasUsage(this))startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));});
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(diameter,diameter);p.leftMargin=Math.round(positions[12+i][0])-diameter/2;p.topMargin=Math.round(positions[12+i][1])-diameter/2;root.addView(bubble,p);
        }
        LinearLayout controls=Ui.column(this);controls.setOnClickListener(v->{});LinearLayout buttons=Ui.row(this);
        buttons.addView(Ui.symbol(this,"check","完成",this::finish),new LinearLayout.LayoutParams(dp(44),dp(44)));
        SeekBar opacity=new SeekBar(this);opacity.setMax(80);opacity.setProgress(Math.max(0,Prefs.of(this).getInt("quick_opacity",90)-20));opacity.setContentDescription("快捷面不透明度");opacity.setVisibility(View.GONE);
        View buttonSpace=new View(this);buttons.addView(buttonSpace,new LinearLayout.LayoutParams(dp(8),1));buttons.addView(Ui.symbol(this,"opacity","调整快捷面透明度",()->opacity.setVisibility(opacity.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)),new LinearLayout.LayoutParams(dp(44),dp(44)));controls.addView(buttons);controls.addView(opacity,new LinearLayout.LayoutParams(dp(170),dp(40)));
        Runnable apply=()->{int value=Prefs.of(this).getInt("quick_opacity",90);root.setBackgroundColor(android.graphics.Color.argb(value*102/100,48,65,52));for(int i=0;i<13;i++)root.getChildAt(i).setAlpha(value/100f);};
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){Prefs.of(QuickSettingsActivity.this).edit().putInt("quick_opacity",value+20).apply();apply.run();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);cp.setMargins(dp(12),dp(40),dp(12),0);root.addView(controls,cp);apply.run();
        root.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{int[] origin=new int[2];root.getLocationOnScreen(origin);float ax=x-origin[0],ay=y-origin[1];int width=root.getWidth(),height=root.getHeight();if(width<=0||height<=0)return;float[][] centers=QuickOrbitLayout.positions(width,height,ax,ay,m.density);int d=Math.max(1,Math.round(QuickOrbitLayout.diameter(width,height,ax,ay,m.density)));for(int i=0;i<13;i++){FrameLayout bubble=(FrameLayout)root.getChildAt(i);FrameLayout.LayoutParams at=(FrameLayout.LayoutParams)bubble.getLayoutParams();int bx=Math.round(centers[i][0])-d/2,by=Math.round(centers[i][1])-d/2;if(at.leftMargin!=bx||at.topMargin!=by||at.width!=d||at.height!=d){at.leftMargin=bx;at.topMargin=by;at.width=at.height=d;bubble.setLayoutParams(at);}View icon=bubble.getChildAt(0);if(icon!=null){ViewGroup.LayoutParams ip=icon.getLayoutParams();int side=icon instanceof ImageView?d:Math.round(d*.57f);if(ip.width!=side){ip.width=ip.height=side;icon.setLayoutParams(ip);}}}});
        setContentView(root);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);if(android.os.Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams window=getWindow().getAttributes();window.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;getWindow().setAttributes(window);}
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request>=100&&request<112&&result==RESULT_OK&&data!=null)set(request-100,data.getStringExtra("selected"));}
    private void set(int index,String id){slots.set(index,id);QuickActions.save(this,slots);render();}
    private void choose(int index){new UiDialog(this).setTitle("快捷位置 "+(index+1)).setItems(new String[]{"本地应用","软件功能","清空此位置","与前一个位置交换","与后一个位置交换"},(d,which)->{switch(which){case 0->pick(index,true);case 1->pick(index,false);case 2->set(index,"");case 3,4->{CompositionLayout.move(slots,index,index+(which==3?-1:1));QuickActions.save(this,slots);render();}}}).setNegativeButton("取消",null).show();}
    private void pick(int index,boolean apps){
        ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();
        if(apps){startActivityForResult(new Intent(this,AppPickerActivity.class),index+100);return;}
        else for(String id:Prefs.quickFunctions()){ids.add(id);names.add(Prefs.label(id));}
        AlertDialog dialog=new UiDialog(this).setTitle(apps?"选择本地应用":"选择软件功能").setItems(names.toArray(new String[0]),(d,position)->set(index,ids.get(position))).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->{UiDialog.style(dialog);ListView list=dialog.getListView();list.setFastScrollEnabled(true);});dialog.show();
    }
}
