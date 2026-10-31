package com.kongye.floatball;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.SeekBar;
/** Native accessible range input with the rail and thumb appearance from apk.html. */
// MainActivity is a framework Activity; this view draws its own theme-aware rail.
@android.annotation.SuppressLint("AppCompatCustomView")
final class LeafSeekBar extends SeekBar {
    private final Paint paint=new Paint(3);
    LeafSeekBar(Context c){super(c);setBackground(null);setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));GradientDrawable thumb=new GradientDrawable();thumb.setColor(Color.TRANSPARENT);thumb.setSize(Ui.dp(c,16),Ui.dp(c,16));setThumb(thumb);setPadding(Ui.dp(c,8),0,Ui.dp(c,8),0);}
    @Override protected void onDraw(Canvas c){float l=getPaddingLeft(),r=getWidth()-getPaddingRight(),y=getHeight()/2f,h=Ui.dp(getContext(),7),radius=h/2;paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.dark?0xFF314238:0xFFEDEDED);c.drawRoundRect(l,y-h/2,r,y+h/2,radius,radius,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),.7f));paint.setColor(Ui.dark?0xFF607568:0xFFBFC2BF);c.drawRoundRect(l,y-h/2,r,y+h/2,radius,radius,paint);float x=l+(r-l)*getProgress()/Math.max(1f,getMax());paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.dark?Ui.GREEN:0xFF28664B);if(x>l)c.drawRoundRect(l,y-h/2,x,y+h/2,radius,radius,paint);c.drawCircle(x,y,Ui.dp(getContext(),8),paint);}
}
