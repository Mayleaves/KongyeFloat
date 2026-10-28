package com.kongye.floatball;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;
import java.util.function.BiConsumer;

/** A drag starting on a button cancels that button; taps still reach it normally. */
final class DragOverlay extends FrameLayout {
    private final Runnable begin;private final BiConsumer<Float,Float> move;
    private float startX,startY;private boolean dragging;
    DragOverlay(Context c,Runnable begin,BiConsumer<Float,Float> move){super(c);this.begin=begin;this.move=move;}
    private void down(MotionEvent e){startX=e.getRawX();startY=e.getRawY();dragging=false;begin.run();}
    private boolean moved(MotionEvent e){return Math.hypot(e.getRawX()-startX,e.getRawY()-startY)>Ui.dp(getContext(),8);}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)down(e);if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&moved(e))dragging=true;return dragging;}
    @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN->{down(e);return true;}case MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{dragging|=moved(e);if(dragging)move.accept(e.getRawX()-startX,e.getRawY()-startY);if(e.getActionMasked()==MotionEvent.ACTION_UP){dragging=false;performClick();}return true;}case MotionEvent.ACTION_CANCEL->{dragging=false;return true;}default->{return true;}}}
    @Override public boolean performClick(){super.performClick();return true;}
}
