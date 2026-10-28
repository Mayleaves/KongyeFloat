package com.kongye.floatball;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** Four stable columns with bounded rows; empty cells never request viewport height. */
final class HomeToolGrid extends LinearLayout {
    private final ArrayList<String> ids;private final Consumer<ArrayList<String>> save;private final Handler main=new Handler(Looper.getMainLooper());
    private int direction;private ScrollView scroller;
    HomeToolGrid(Context c,List<String> source,Function<String,View> create,Consumer<ArrayList<String>> save){super(c);this.ids=new ArrayList<>(source);this.save=save;setOrientation(VERTICAL);
        for(int row=0;row<(ids.size()+3)/4;row++){LinearLayout line=Ui.row(c);for(int column=0;column<4;column++){int index=row*4+column;View tile=index<ids.size()?create.apply(ids.get(index)):new View(c);line.addView(tile,new LinearLayout.LayoutParams(0,index<ids.size()?-1:0,1));if(index<ids.size())bind(tile,ids.get(index));}addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(c,110)));}
    }
    private void bind(View tile,String id){tile.setTag("home_tool:"+id);tile.setOnLongClickListener(view->{boolean started=view.startDragAndDrop(ClipData.newPlainText("空叶首页工具",id),new View.DragShadowBuilder(view),this,0);if(started){view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);active=id;}return started;});tile.setOnDragListener((view,event)->{if(event.getLocalState()!=this)return false;switch(event.getAction()){case DragEvent.ACTION_DRAG_STARTED:return true;case DragEvent.ACTION_DRAG_ENTERED:view.setAlpha(.55f);return true;case DragEvent.ACTION_DRAG_LOCATION:edge(view,event.getY());return true;case DragEvent.ACTION_DRAG_EXITED:view.setAlpha(1);return true;case DragEvent.ACTION_DROP:{stopScroll();int from=ids.indexOf(active),to=ids.indexOf(id);if(from>=0&&to>=0&&from!=to){CompositionLayout.move(ids,from,to);ArrayList<String> order=new ArrayList<>(ids);post(()->save.accept(order));}return true;}case DragEvent.ACTION_DRAG_ENDED:stopScroll();view.setAlpha(1);return true;default:return true;}});}
    private String active;
    private void edge(View tile,float y){ViewParent parent=getParent();while(parent!=null&&!(parent instanceof ScrollView))parent=parent.getParent();if(!(parent instanceof ScrollView scroll))return;scroller=scroll;int[] here=new int[2],at=new int[2];tile.getLocationOnScreen(here);scroll.getLocationOnScreen(at);float screenY=here[1]+y;int next=screenY<at[1]+Ui.dp(getContext(),64)?-1:screenY>at[1]+scroll.getHeight()-Ui.dp(getContext(),64)?1:0;if(direction!=next){direction=next;main.removeCallbacks(advance);if(next!=0)main.post(advance);}}
    private final Runnable advance=new Runnable(){public void run(){if(direction==0||scroller==null)return;scroller.scrollBy(0,direction*Ui.dp(getContext(),9));main.postDelayed(this,24);}};
    private void stopScroll(){direction=0;main.removeCallbacks(advance);}
    @Override protected void onDetachedFromWindow(){stopScroll();super.onDetachedFromWindow();}
}
