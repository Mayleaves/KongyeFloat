package com.kongye.floatball;
import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;import java.util.function.*;
/** Stable home grid: same source fade/scale and drag shadow as shortcuts, save without recreating Main. */
final class HomeToolGrid extends LinearLayout {
 private final ArrayList<String> ids;private final Map<String,View> tiles=new LinkedHashMap<>();private final Consumer<ArrayList<String>> save;private final Handler main=new Handler(Looper.getMainLooper());private int direction;private ScrollView scroller;private String active;private boolean reordered;
 private int columns = 4;
 HomeToolGrid(Context c,List<String> source,Function<String,View> create,Consumer<ArrayList<String>> save){super(c);this.ids=new ArrayList<>(source);this.save=save;setOrientation(VERTICAL);setClipChildren(false);setClipToPadding(false);for(String id:ids){View tile=create.apply(id);tiles.put(id,tile);bind(tile,id);}rebuild();}

 @Override protected void onMeasure(int widthSpec, int heightSpec) {
     if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && active == null) {
         int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
         int minimumTileWidth = Math.max(Ui.dp(getContext(), 68), Math.round(4.5f * Ui.sp(getContext(), 12)));
         int next = ResponsiveLayout.gridColumns(width, minimumTileWidth);
         if (next != columns) {
             columns = next;
             rebuild();
         }
     }
     super.onMeasure(widthSpec, heightSpec);
 }

 private void rebuild() {
     Context context = getContext();
     for (View tile : tiles.values()) {
         if (tile.getParent() instanceof ViewGroup group) group.removeView(tile);
     }
     removeAllViews();
     setContentDescription("首页" + "一二三四".charAt(columns - 1) + "列工具网格");
     for (int first = 0; first < ids.size(); first += columns) {
         LinearLayout line = Ui.row(context);
         line.setGravity(Gravity.TOP);
         line.setClipChildren(false);
         for (int col = 0; col < columns; col++) {
             int index = first + col;
             View tile = index < ids.size() ? tiles.get(ids.get(index)) : new View(context);
             line.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
         }
         addView(line, new LinearLayout.LayoutParams(-1, -2));
     }
 }
 private void bind(View tile,String id){tile.setTag("home_tool:"+id);tile.setOnLongClickListener(view->{view.setPressed(false);view.jumpDrawablesToCurrentState();boolean started=view.startDragAndDrop(ClipData.newPlainText("空叶首页工具",id),new View.DragShadowBuilder(view),this,0);if(started){view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);active=id;view.animate().alpha(.28f).scaleX(.92f).scaleY(.92f).setDuration(150).start();}return started;});tile.setOnDragListener((view,event)->{if(event.getLocalState()!=this)return false;switch(event.getAction()){case DragEvent.ACTION_DRAG_STARTED:return true;case DragEvent.ACTION_DRAG_LOCATION:edge(view,event.getY());return true;case DragEvent.ACTION_DROP:{stopScroll();int from=ids.indexOf(active),to=ids.indexOf(id);if(from>=0&&to>=0&&from!=to){CompositionLayout.move(ids,from,to);reordered=true;}return true;}case DragEvent.ACTION_DRAG_ENDED:{active=null;stopScroll();requestLayout();for(View item:tiles.values())item.animate().alpha(1).scaleX(1).scaleY(1).setDuration(150).start();if(reordered){reordered=false;post(()->{rebuild();save.accept(new ArrayList<>(ids));});}return true;}default:return true;}});}
 private void edge(View tile,float y){ViewParent parent=getParent();while(parent!=null&&!(parent instanceof ScrollView))parent=parent.getParent();if(!(parent instanceof ScrollView scroll))return;scroller=scroll;int[] here=new int[2],at=new int[2];tile.getLocationOnScreen(here);scroll.getLocationOnScreen(at);float screenY=here[1]+y;int next=screenY<at[1]+Ui.dp(getContext(),64)?-1:screenY>at[1]+scroll.getHeight()-Ui.dp(getContext(),64)?1:0;if(direction!=next){direction=next;main.removeCallbacks(advance);if(next!=0)main.post(advance);}}
 private final Runnable advance=new Runnable(){public void run(){if(direction==0||scroller==null)return;scroller.scrollBy(0,direction*Ui.dp(getContext(),9));main.postDelayed(this,24);}};
 private void stopScroll(){direction=0;main.removeCallbacks(advance);}@Override protected void onDetachedFromWindow(){stopScroll();super.onDetachedFromWindow();}
}
