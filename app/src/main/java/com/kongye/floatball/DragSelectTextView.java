package com.kongye.floatball;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.*;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Additive drag/tap selection. No selection handles are drawn or required. */
@SuppressLint("AppCompatCustomView")
final class DragSelectTextView extends TextView {
    private final Handler main=new Handler(Looper.getMainLooper());private final MultiTextSelection selection;
    private final ArrayList<BackgroundColorSpan> highlights=new ArrayList<>();private List<int[]> before;
    private int anchor;private boolean touching,moved,subtract;private float downX,downY,rawX,rawY;
    DragSelectTextView(Context c,String value){super(c);setText(value,BufferType.SPANNABLE);selection=new MultiTextSelection(value);setTextColor(Ui.INK);setTextSize(21);setIncludeFontPadding(false);setFocusable(true);setFocusableInTouchMode(true);setContentDescription("可跳选的识别文字，无选择句柄");}
    String selectedText(){return selection.selectedText();}
    int rangeCount(){return selection.count();}
    void selectAll(){selection.add(0,getText().length());highlight();}
    void clearSelection(){selection.clear();highlight();}
    private int offset(float x,float y){return Math.max(0,Math.min(getText().length(),getOffsetForPosition(x,y)));}
    private void highlight(){Spannable text=(Spannable)getText();for(BackgroundColorSpan span:highlights)text.removeSpan(span);highlights.clear();for(int[] range:selection.ranges()){BackgroundColorSpan span=new BackgroundColorSpan(0x8847C8F4);text.setSpan(span,range[0],range[1],Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);highlights.add(span);}invalidate();}
    private ScrollView viewport(){ViewParent p=getParent();while(p!=null){if(p instanceof ScrollView s)return s;p=p.getParent();}return null;}
    private void update(float x,float y){selection.restore(before);selection.drag(anchor,offset(x,y),subtract);highlight();}
    private final Runnable autoscroll=new Runnable(){public void run(){if(!touching)return;ScrollView scroll=viewport();if(scroll!=null){Rect visible=new Rect();scroll.getGlobalVisibleRect(visible);int edge=Ui.dp(getContext(),36),delta=rawY<visible.top+edge?-Ui.dp(getContext(),14):rawY>visible.bottom-edge?Ui.dp(getContext(),14):0;if(delta!=0){scroll.scrollBy(0,delta);int[] at=new int[2];getLocationOnScreen(at);update(rawX-at[0],rawY-at[1]);}}main.postDelayed(this,40);}};
    @Override public boolean onTouchEvent(MotionEvent event){switch(event.getActionMasked()){
        case MotionEvent.ACTION_DOWN->{touching=true;moved=false;downX=event.getX();downY=event.getY();rawX=event.getRawX();rawY=event.getRawY();anchor=offset(downX,downY);before=selection.ranges();subtract=selection.contains(Math.min(anchor,Math.max(0,getText().length()-1)));requestFocus();getParent().requestDisallowInterceptTouchEvent(true);main.postDelayed(autoscroll,80);return true;}
        case MotionEvent.ACTION_MOVE->{rawX=event.getRawX();rawY=event.getRawY();moved|=Math.hypot(event.getX()-downX,event.getY()-downY)>Ui.dp(getContext(),3);if(moved)update(event.getX(),event.getY());return true;}
        case MotionEvent.ACTION_UP->{if(!moved){selection.restore(before);selection.toggleWord(anchor);highlight();}touching=false;main.removeCallbacks(autoscroll);getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}
        case MotionEvent.ACTION_CANCEL->{touching=false;main.removeCallbacks(autoscroll);selection.restore(before);highlight();getParent().requestDisallowInterceptTouchEvent(false);return true;}
    }return true;}
    @Override protected void onDetachedFromWindow(){touching=false;main.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
}
