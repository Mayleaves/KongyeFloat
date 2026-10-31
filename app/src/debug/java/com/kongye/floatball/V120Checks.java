package com.kongye.floatball;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.InsetDrawable;
import android.view.*;
import android.widget.*;

/** Pixel measurements stay on-device; only counts and pass/fail leave the phone. */
final class V120Checks {
    private static int count;
    private static void check(boolean good,String message){if(!good)throw new AssertionError(message);count++;}
    private static Bitmap draw(View view,int w,int h){view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(new Canvas(out));return out;}
    static void art(Activity a){
        if(!BallArt.file(a).isFile()){Prefs.of(a).edit().putString("qa_v120_art","No stored art").commit();return;}
        try{Prefs.of(a).edit().putString("qa_v120_parser",GifBounds.read(java.nio.file.Files.readAllBytes(BallArt.file(a).toPath())).toString()).commit();}catch(Throwable e){Prefs.of(a).edit().putString("qa_v120_parser",e.toString()).commit();}
        android.content.SharedPreferences p=Prefs.of(a);boolean custom=p.getBoolean("custom_art",false);int size=p.getInt("size",36);p.edit().putBoolean("custom_art",true).putInt("size",150).remove("art_bounds_file").commit();
        BallArt.prepare(a,()->{try{
            int measured=p.getInt("size",150);RectF b=BallArt.bounds(a);int[] d=BallArt.dimensions(a,Ui.dp(a,36));check(Math.max(d[0],d[1])==Ui.dp(a,36),"normalized visible long edge");check(measured>=36&&measured<=150,"legacy size migrated safely");View view=BallArt.create(a,Ui.dp(a,36));check(view!=null,"stored art decodes");Bitmap bitmap=draw(view,d[0],d[1]);int n=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(Color.alpha(bitmap.getPixel(x,y))>0)n++;check(n>0,"stored GIF visible after normalization");bitmap.recycle();p.edit().putString("qa_v120_art","PASS; migrated="+measured+"; viewport="+d[0]+"x"+d[1]+"; bounds="+b).commit();
        }catch(Throwable error){p.edit().putString("qa_v120_art","FAILED "+error).commit();}finally{p.edit().putBoolean("custom_art",custom).putInt("size",size).commit();}});
    }
    static void run(Activity a){count=0;try{
        ThemePolicy.apply(a);check(Prefs.label("auto_longshot").equals("滚动截图"),"four-character name");
        Bitmap slider=draw(Ui.icon(a,"quick_settings",Ui.GREEN,48),96,96);for(int y:new int[]{20,48,76})check(Color.alpha(slider.getPixel(48,y))>0,"three slider tracks");slider.recycle();
        for(String name:new String[]{"region","longshot","auto_longshot"}){Bitmap b=draw(Ui.icon(a,name,Ui.GREEN,48),240,240);int x=name.equals("region")?160:name.equals("auto_longshot")?63:80,y=name.equals("region")?200:name.equals("auto_longshot")?185:200;check(Color.alpha(b.getPixel(x,y))==0,name+" scissors have hollow rings");b.recycle();}
        for(String name:new String[]{"screenshot","full_screen","resolution","move_area","lock","info"}){Bitmap b=draw(Ui.icon(a,name,Ui.GREEN,48),96,96);int pixels=0;for(int y=0;y<96;y++)for(int x=0;x<96;x++)if(Color.alpha(b.getPixel(x,y))>0)pixels++;check(pixels>80,name+" glyph renders");b.recycle();}
        FrameLayout fixed=QuickBubble.create(a,"app:"+a.getPackageName(),48,Ui.GREEN),recent=QuickBubble.create(a,"app:"+a.getPackageName(),48,Ui.MUTED,true);check(fixed.getChildAt(0).getLayoutParams().width==Ui.dp(a,48),"app icon fills circle");check(fixed.getClipToOutline(),"app icon circle clips corners");check(((ImageView)recent.getChildAt(0)).getColorFilter()!=null,"recent apps use muted tint");check(((ImageView)fixed.getChildAt(0)).getColorFilter()==null,"fixed apps keep full color");
        AlertDialog dialog=new UiDialog(a).setTitle("空叶布局验证").setMessage("仅用于检查按钮间距。").setNegativeButton("取消",null).setPositiveButton("确定",null).show();check(dialog.getButton(-1).getBackground() instanceof InsetDrawable,"dialog button fill is inset");Rect padding=new Rect();dialog.getButton(-1).getBackground().getPadding(padding);check(padding.left>=Ui.dp(a,5)&&padding.right>=Ui.dp(a,5),"dialog buttons have visible gap");dialog.dismiss();
        if(BallArt.file(a).isFile()&&Prefs.of(a).getBoolean("custom_art",false)){RectF bounds=BallArt.bounds(a);check(!bounds.isEmpty()&&bounds.left>=0&&bounds.right<=1,"existing art metadata valid");int[] d=BallArt.dimensions(a,Ui.dp(a,36));check(Math.max(d[0],d[1])==Ui.dp(a,36),"36dp means visible art long edge");View image=BallArt.create(a,Ui.dp(a,36));check(image!=null,"normalized art decodes");Bitmap b=draw(image,d[0],d[1]);int opaque=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(Color.alpha(b.getPixel(x,y))>0)opaque++;check(opaque>0,"normalized GIF paints in viewport");b.recycle();}
        Prefs.of(a).edit().putString("qa_v120_result",count+" checks passed").commit();
    }catch(Throwable e){Prefs.of(a).edit().putString("qa_v120_result",count+" passed; FAILED "+e).commit();}}
}
