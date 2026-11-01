package com.kongye.floatball;
import android.content.Context;import android.view.*;import android.widget.*;import java.util.function.IntConsumer;
/** Choices shown in the same managed overlay layer as their parent, including enhanced mode. */
final class OverlayPicker {
 final CaptureWindow window;final LinearLayout card;private final Context context;
 OverlayPicker(Context c,String title){context=c;window=new CaptureWindow(c);card=Ui.card(c);Ui.softShadow(card);card.addView(Ui.text(c,title,18,Ui.INK,true));Ui.gap(card,12);}
 void show(){FrameLayout root=window.frame(this::close);root.setBackgroundColor(0x55304134);root.setOnClickListener(v->close());card.setOnClickListener(v->{});FrameLayout.LayoutParams at=new FrameLayout.LayoutParams(Math.min(context.getResources().getDisplayMetrics().widthPixels-Ui.dp(context,32),Ui.dp(context,420)),Math.min(context.getResources().getDisplayMetrics().heightPixels-Ui.dp(context,140),Ui.dp(context,520)),Gravity.CENTER);root.addView(card,at);window.show(root,true);}
 void choices(String[] names,int selected,IntConsumer callback){ScrollView scroll=new ScrollView(context);LinearLayout list=Ui.column(context);scroll.addView(list);for(int i=0;i<names.length;i++){int index=i;TextView option=Ui.text(context,names[i],15,selected==i?Ui.GREEN:Ui.INK,selected==i);Ui.pad(option,12,0,12,0);option.setGravity(Gravity.CENTER_VERTICAL);option.setContentDescription("语言选择："+names[i]);option.setBackground(Ui.ripple(context,selected==i?Ui.PALE:0,12));option.setOnClickListener(v->{close();callback.accept(index);});list.addView(option,new LinearLayout.LayoutParams(-1,Ui.dp(context,44)));}card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));Ui.gap(card,8);card.addView(Ui.button(context,"取消",false,this::close));show();}
 void close(){window.remove();}
}
