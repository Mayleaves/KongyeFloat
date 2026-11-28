package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;
import java.util.*;

final class ComicOverlayView extends View {
    private record Drawing(RectF bounds,RectF textBounds,StaticLayout horizontal,int[] vertical,float font,int background,Path clip){}
    private final ArrayList<Drawing> drawings=new ArrayList<>();
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    ComicOverlayView(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    void clear(){drawings.clear();invalidate();}
    void show(ComicFrame frame,List<String> translations,Rect area){
        drawings.clear();if(translations.size()!=frame.blocks.size())return;
        float sx=area.width()/(float)frame.width,sy=area.height()/(float)frame.height;
        for(int i=0;i<frame.blocks.size();i++){
            ComicFrame.Block b=frame.blocks.get(i);Rect box=b.bounds();RectF erase=new RectF(area.left+box.left*sx,area.top+box.top*sy,area.left+box.right*sx,area.top+box.bottom*sy);
            Rect safe=b.textBounds();RectF r=new RectF(area.left+safe.left*sx,area.top+safe.top*sy,area.left+safe.right*sx,area.top+safe.bottom*sy);
            float padding=Math.min(3*Math.min(sx,sy),Math.min(r.width(),r.height())*.045f);r.inset(padding,padding);
            int[] vertical=b.vertical()?ComicTypography.vertical(translations.get(i)):null;
            float font=Math.min(b.font()*Math.min(sx,sy),Math.min(r.width(),r.height()));StaticLayout layout=null;
            for(;font>2;font-=.5f){
                paint.setTextSize(font);
                Paint.FontMetrics metrics=paint.getFontMetrics();
                if(vertical!=null){if(ComicTypography.fits(vertical.length,r.width(),r.height(),font,metrics.descent-metrics.ascent))break;}
                else{String value=translations.get(i);layout=StaticLayout.Builder.obtain(value,0,value.length(),new TextPaint(paint),Math.max(1,(int)r.width())).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(font*.10f,1.08f).build();if(layout.getHeight()<=r.height())break;}
            }
            Path clip=null;if(b.clip()!=null){Matrix transform=new Matrix();transform.setScale(sx,sy);transform.postTranslate(area.left,area.top);clip=new Path();b.clip().transform(transform,clip);}
            drawings.add(new Drawing(erase,r,layout,vertical,font,b.background(),clip));
        }invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        for(Drawing d:drawings){RectF r=d.textBounds();canvas.save();canvas.clipRect(d.bounds());if(d.clip()!=null)canvas.clipPath(d.clip());fill.setColor(d.background());canvas.drawRect(d.bounds(),fill);canvas.restore();canvas.save();canvas.clipRect(r);paint.setColor(d.background()==Color.BLACK?Color.WHITE:Color.BLACK);paint.setTextSize(d.font());
            if(d.vertical()!=null){
                Paint.FontMetrics metrics=paint.getFontMetrics();float glyphHeight=metrics.descent-metrics.ascent;
                int capacity=Math.max(1,ComicTypography.rows(r.height(),d.font(),glyphHeight));
                int columns=Math.max(1,(d.vertical().length+capacity-1)/capacity),rows=Math.max(1,(d.vertical().length+columns-1)/columns);
                float step=ComicTypography.columnStep(d.font()),pitch=ComicTypography.characterStep(d.font()),right=r.centerX()+(columns-1)*step/2f;
                float baseline=r.centerY()-((rows-1)*pitch+glyphHeight)/2f-metrics.ascent;paint.setTextAlign(Paint.Align.CENTER);
                for(int i=0;i<d.vertical().length;i++){float x=right-(i/rows)*step,y=baseline+(i%rows)*pitch;canvas.drawText(new String(Character.toChars(d.vertical()[i])),x,y,paint);}
                paint.setTextAlign(Paint.Align.LEFT);
            }else if(d.horizontal()!=null){d.horizontal().getPaint().setColor(paint.getColor());canvas.translate(r.left,r.centerY()-d.horizontal().getHeight()/2f);d.horizontal().draw(canvas);}
            canvas.restore();
        }
    }
}
