package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.*;
import java.util.*;

/** Deterministic layout sample: supplied Chinese text, no OCR or network involved. */
final class ComicLayoutChecks {
    static void render(Context context)throws Exception {
        int width=720,height=740;
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(0xFFE5EAE5);
        Paint paint=new Paint(3);paint.setColor(Color.WHITE);
        ArrayList<ComicFrame.Block> blocks=new ArrayList<>();
        Rect[] areas={new Rect(36,40,344,195),new Rect(376,40,684,255),new Rect(130,310,270,680),new Rect(445,310,585,680)};
        for(int i=0;i<areas.length;i++){
            Rect r=areas[i];canvas.drawRoundRect(new RectF(r),24,24,paint);Rect text=new Rect(r);text.inset(20,20);
            blocks.add(new ComicFrame.Block("sample",r,i>=2,28,Color.WHITE,null,text));
        }
        ComicOverlayView view=new ComicOverlayView(context);view.layout(0,0,width,height);
        view.show(new ComicFrame(width,height,blocks),List.of("哦，强力。","看起来可能发生了一些错误。\n\n请你再说明一下。","为了搞清楚这个字的意思，我查阅了辞典。","原来如此！这样我就明白了。"),new Rect(0,0,width,height));view.draw(canvas);
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir("comic-check"),"typography-preview.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
