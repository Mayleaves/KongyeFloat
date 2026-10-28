package com.kongye.floatball;
import android.graphics.*;
import java.io.*;
/** Streaming RGB332 GIF89a encoder. Frequent clear codes keep the literal LZW stream bounded to 9 bits. */
final class GifWriter implements Closeable {
    private final OutputStream out;private final int w,h;private final int[] pixels;private boolean closed;
    GifWriter(OutputStream out,int width,int height)throws IOException{this.out=out;w=width;h=height;pixels=new int[w*h];out.write("GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII));word(w);word(h);out.write(0xF7);out.write(0);out.write(0);for(int i=0;i<256;i++){out.write(((i>>5)&7)*255/7);out.write(((i>>2)&7)*255/7);out.write((i&3)*255/3);}out.write(new byte[]{0x21,(byte)0xFF,11});out.write("NETSCAPE2.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(new byte[]{3,1,0,0,0});}
    private void word(int n)throws IOException{out.write(n&255);out.write((n>>8)&255);}
    void frame(Bitmap image,int delayMs)throws IOException{if(image.getWidth()!=w||image.getHeight()!=h)throw new IOException("GIF frame size changed");out.write(new byte[]{0x21,(byte)0xF9,4,4});word(Math.max(2,delayMs/10));out.write(0);out.write(0);out.write(0x2C);word(0);word(0);word(w);word(h);out.write(0);out.write(8);image.getPixels(pixels,0,w,0,0,w,h);ByteArrayOutputStream bytes=new ByteArrayOutputStream(w*h*2);int bits=0,used=0,group=0;int[] codes=new int[2];for(int i=0;i<pixels.length;i++){codes[0]=group==0?256:-1;int p=pixels[i];codes[1]=((p>>16)&0xE0)|((p>>11)&0x1C)|((p>>6)&3);for(int code:codes)if(code>=0){bits|=code<<used;used+=9;while(used>=8){bytes.write(bits&255);bits>>>=8;used-=8;}}if(++group==200)group=0;}bits|=257<<used;used+=9;while(used>0){bytes.write(bits&255);bits>>>=8;used-=8;}byte[] data=bytes.toByteArray();for(int i=0;i<data.length;i+=255){int count=Math.min(255,data.length-i);out.write(count);out.write(data,i,count);}out.write(0);}
    @Override public void close()throws IOException{if(!closed){closed=true;try{out.write(0x3B);}finally{out.close();}}}
}
