package com.kongye.floatball;

import android.graphics.*;
import com.google.mlkit.vision.text.Text;
import java.util.*;

final class ComicFrame {
    record Block(String text,Rect bounds,boolean vertical,float font,int background,Path clip,Rect textBounds) {}
    final int width,height;
    final List<Block> blocks;
    final String key;
    ComicFrame(int w,int h,List<Block> blocks){width=w;height=h;this.blocks=List.copyOf(blocks);StringBuilder k=new StringBuilder();for(Block b:blocks)k.append(b.text().length()).append(':').append(b.text()).append('|');key=k.toString();}
    static ComicFrame read(Text text,Bitmap image,String language){
        ArrayList<Block> result=new ArrayList<>();
        ComicBubbleMap paper=new ComicBubbleMap(image.getWidth(),image.getHeight(),image::getPixel);
        LinkedHashMap<String,ArrayList<Block>> groups=new LinkedHashMap<>();int unique=0;
        for(Text.TextBlock raw:text.getTextBlocks()){
            Rect box=raw.getBoundingBox();if(box==null||box.width()<5||box.height()<8||raw.getText().isBlank())continue;
            ArrayList<Text.Line> lines=new ArrayList<>(raw.getLines());int tall=0;
            for(Text.Line line:lines){Rect r=line.getBoundingBox();if(r!=null&&r.height()>r.width()*1.25f)tall++;}
            boolean vertical=!language.equals("en")&&(tall>0&&tall*2>=lines.size());
            if(vertical)lines.sort((a,b)->{Rect x=a.getBoundingBox(),y=b.getBoundingBox();if(x==null||y==null)return 0;int cmp=Integer.compare(y.centerX(),x.centerX());return cmp!=0?cmp:Integer.compare(x.top,y.top);});
            StringJoiner content=new StringJoiner(vertical?"":" ");float font=0;
            for(Text.Line line:lines){String value=line.getText().trim();content.add(vertical?value.replaceAll("\\s+",""):value);Rect r=line.getBoundingBox();if(r!=null)font=Math.max(font,vertical?r.width():r.height());}
            String value=content.toString().trim();if(value.isEmpty())continue;
            box=new Rect(box);box.inset(-3,-3);if(!box.intersect(0,0,image.getWidth(),image.getHeight()))continue;
            int white=0,black=0,total=0,edgeWhite=0,edgeBlack=0,edgeTotal=0;
            for(int y=box.top;y<box.bottom;y+=3)for(int x=box.left;x<box.right;x+=3){int p=image.getPixel(x,y),r=Color.red(p),g=Color.green(p),b=Color.blue(p);total++;if(Math.min(r,Math.min(g,b))>222)white++;if(Math.max(r,Math.max(g,b))<32)black++;}
            for(int y=box.top;y<box.bottom;y+=2)for(int x:new int[]{box.left,box.right-1}){int p=image.getPixel(x,y);edgeTotal++;if(Math.min(Color.red(p),Math.min(Color.green(p),Color.blue(p)))>222)edgeWhite++;if(Math.max(Color.red(p),Math.max(Color.green(p),Color.blue(p)))<32)edgeBlack++;}
            for(int x=box.left;x<box.right;x+=2)for(int y:new int[]{box.top,box.bottom-1}){int p=image.getPixel(x,y);edgeTotal++;if(Math.min(Color.red(p),Math.min(Color.green(p),Color.blue(p)))>222)edgeWhite++;if(Math.max(Color.red(p),Math.max(Color.green(p),Color.blue(p)))<32)edgeBlack++;}
            // Only erase text on nearly plain light/dark backgrounds; preserve detailed artwork.
            int background=white>total*.45f&&edgeWhite>edgeTotal*.84f?Color.WHITE:black>total*.45f&&edgeBlack>edgeTotal*.84f?Color.BLACK:0;
            int bubble=paper.bubble(box.left+3,box.top+3,box.right-3,box.bottom-3);
            if(background==0&&bubble!=0)background=Color.WHITE;
            if(background==0)continue;
            if(background==Color.WHITE&&bubble==0)continue;
            Block item=new Block(value,box,vertical,Math.max(10,font),background,null,box);
            String group=bubble>0&&background==Color.WHITE?bubble+":"+vertical:"single:"+(unique++);
            groups.computeIfAbsent(group,k->new ArrayList<>()).add(item);if(groups.size()>=80)break;
        }
        for(var entry:groups.entrySet()){
            ArrayList<Block> group=entry.getValue();
            boolean vertical=group.get(0).vertical();
            group.sort((a,b)->vertical?Integer.compare(b.bounds().right,a.bounds().right):Integer.compare(a.bounds().top,b.bounds().top));
            Rect box=new Rect();StringJoiner joined=new StringJoiner(vertical?"":" ");float font=0;
            for(Block block:group){box.union(block.bounds());joined.add(block.text());font=Math.max(font,block.font());}
            Path clip=null;Rect textBounds=new Rect(box);
            if(!entry.getKey().startsWith("single:")){int id=Integer.parseInt(entry.getKey().substring(0,entry.getKey().indexOf(':')));clip=new Path();var spans=paper.spans(id,box.left,box.top,box.right,box.bottom);for(int[] span:spans)clip.addRect(span[0],span[1],span[2],span[3],Path.Direction.CW);int[] inner=ComicBubbleMap.innerBounds(spans);if(inner!=null)textBounds.set(inner[0],inner[1],inner[2],inner[3]);}
            result.add(new Block(joined.toString(),box,vertical,font,group.get(0).background(),clip,textBounds));
        }
        result.sort(Comparator.comparingInt((Block b)->b.bounds().top).thenComparing(Comparator.comparingInt((Block b)->b.bounds().right).reversed()));
        return new ComicFrame(image.getWidth(),image.getHeight(),result);
    }
    static int[] fingerprint(Bitmap bitmap){int[] pixels=new int[40*60];int i=0;for(int y=0;y<60;y++)for(int x=0;x<40;x++)pixels[i++]=bitmap.getPixel(Math.min(bitmap.getWidth()-1,(x*2+1)*bitmap.getWidth()/80),Math.min(bitmap.getHeight()-1,(y*2+1)*bitmap.getHeight()/120));return pixels;}
    static boolean similar(int[] a,int[] b){if(a==null||b==null||a.length!=b.length)return false;int changed=0;for(int i=0;i<a.length;i++){int d=Math.abs(Color.red(a[i])-Color.red(b[i]))+Math.abs(Color.green(a[i])-Color.green(b[i]))+Math.abs(Color.blue(a[i])-Color.blue(b[i]));if(d>60&&++changed>18)return false;}return true;}
}
