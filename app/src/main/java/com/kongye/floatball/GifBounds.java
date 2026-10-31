package com.kongye.floatball;

import java.io.*;
import java.util.*;

/** Visible union of every GIF frame, without changing pixels, timing or disposal. */
final class GifBounds {
    record Bounds(int width,int height,int left,int top,int right,int bottom) {
        boolean empty(){return right<=left||bottom<=top;}
    }
    private final byte[] data;private int at,w,h,left,top,right,bottom;private long decoded;
    private GifBounds(byte[] data){this.data=data;}
    static Bounds read(byte[] bytes)throws IOException{return new GifBounds(bytes).read();}
    private int byteValue()throws IOException{if(at>=data.length)throw new EOFException();return data[at++]&255;}
    private int word()throws IOException{return byteValue()|byteValue()<<8;}
    private void skip(int count)throws IOException{if(count<0||at+count>data.length)throw new EOFException();at+=count;}
    private byte[] blocks()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int n;(n=byteValue())!=0;){if(at+n>data.length)throw new EOFException();out.write(data,at,n);at+=n;}return out.toByteArray();}
    private Bounds read()throws IOException{
        if(data.length<13||!(new String(data,0,6,java.nio.charset.StandardCharsets.US_ASCII)).matches("GIF8[79]a"))throw new IOException("Not GIF");
        at=6;w=word();h=word();if(w<1||h<1||w>4096||h>4096)throw new IOException("GIF dimensions");
        int packed=byteValue();skip(2);if((packed&128)!=0)skip(3*(2<<(packed&7)));left=w;top=h;int transparent=-1,frames=0;
        while(true){int marker=byteValue();if(marker==0x3b)break;
            if(marker==0x21){int extension=byteValue();byte[] value=blocks();if(extension==0xf9){if(value.length!=4)throw new IOException("GIF control");transparent=(value[0]&1)!=0?value[3]&255:-1;}continue;}
            if(marker!=0x2c)throw new IOException("GIF frame marker");
            int x=word(),y=word(),fw=word(),fh=word(),flags=byteValue();
            if(fw<1||fh<1||x+fw>w||y+fh>h)throw new IOException("GIF frame exceeds canvas");if(++frames>8192||(decoded+=(long)fw*fh)>1_000_000_000)throw new IOException("GIF analysis limit: frames="+frames+", pixels="+decoded);
            if((flags&128)!=0)skip(3*(2<<(flags&7)));int min=byteValue();byte[] compressed=blocks();
            // An opaque delta frame paints its own rectangle, not the entire canvas.
            // Only an opaque first frame can introduce a logical canvas background.
            if(transparent<0&&frames==1)return new Bounds(w,h,0,0,w,h);
            scan(compressed,min,x,y,fw,fh,(flags&64)!=0,transparent);transparent=-1;
        }
        if(frames==0)throw new IOException("Empty GIF");
        return right>left&&bottom>top?new Bounds(w,h,left,top,right,bottom):new Bounds(w,h,0,0,w,h);
    }
    private void scan(byte[] input,int min,int x,int y,int fw,int fh,boolean interlaced,int transparent)throws IOException{
        if(min<2||min>8)throw new IOException("GIF code size");int clear=1<<min,end=clear+1,next=end+1,size=min+1,old=-1,first=0,bit=0,out=0;
        int[] prefix=new int[4096],suffix=new int[4096],stack=new int[4097],rows=new int[fh],length=new int[4096],solid=new int[4096],firsts=new int[4096];for(int i=0;i<clear;i++){suffix[i]=i;length[i]=1;solid[i]=firsts[i]=i;}
        if(interlaced){int index=0;int[] starts={0,4,2,1},steps={8,8,4,2};for(int pass=0;pass<4;pass++)for(int row=starts[pass];row<fh;row+=steps[pass])rows[index++]=row;}else for(int i=0;i<fh;i++)rows[i]=i;
        while(bit+size<=input.length*8){int code=0;for(int i=0;i<size;i++)code|=((input[(bit+i)>>3]>>((bit+i)&7))&1)<<i;bit+=size;
            if(code==clear){next=end+1;size=min+1;old=-1;continue;}if(code==end)break;if(code>next||code>=4096)throw new IOException("GIF invalid code");
            int current=code,runLength,runColor;
            if(code==next){if(old<0)throw new IOException("GIF missing prefix");first=firsts[old];runLength=length[old]+1;runColor=solid[old]==first?first:-1;}else{first=firsts[code];runLength=length[code];runColor=solid[code];}
            if(runLength<1||out+runLength>fw*fh)throw new IOException("GIF excess pixels");
            if(runColor>=0){if(runColor!=transparent)includeRun(out,runLength,x,y,fw,rows);out+=runLength;}
            else{int depth=0;if(code==next){stack[depth++]=first;code=old;}while(code>=clear){if(code>=next||depth>=4096)throw new IOException("GIF prefix cycle");stack[depth++]=suffix[code];code=prefix[code];}stack[depth++]=suffix[code];while(depth>0){int value=stack[--depth];if(value!=transparent)includeRun(out,1,x,y,fw,rows);out++;}}
            if(old>=0&&next<4096){prefix[next]=old;suffix[next]=first;length[next]=length[old]+1;firsts[next]=firsts[old];solid[next]=solid[old]==first?first:-1;next++;if(next==(1<<size)&&size<12)size++;}old=current;

        }
        if(out!=fw*fh)throw new IOException("GIF incomplete frame");
    }
    private void includeRun(int offset,int count,int x,int y,int width,int[] rows){while(count>0){int column=offset%width,n=Math.min(count,width-column),row=y+rows[offset/width];left=Math.min(left,x+column);right=Math.max(right,x+column+n);top=Math.min(top,row);bottom=Math.max(bottom,row+1);offset+=n;count-=n;}}

}
