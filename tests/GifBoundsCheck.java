package com.kongye.floatball;

import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;

/** Desktop-only fixtures: transparent padding, animation frames, palettes and LZW growth. */
public final class GifBoundsCheck {
    private static int checks;
    private static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);checks++;}
    private static IIOMetadataNode node(IIOMetadataNode root,String name){for(int i=0;i<root.getLength();i++)if(root.item(i).getNodeName().equals(name))return (IIOMetadataNode)root.item(i);IIOMetadataNode n=new IIOMetadataNode(name);root.appendChild(n);return n;}
    static byte[] fixture(int w,int h,int frames,boolean interlace,boolean noisy)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageWriter writer=ImageIO.getImageWritersByFormatName("gif").next();try(ImageOutputStream stream=ImageIO.createImageOutputStream(bytes)){writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int f=0;f<frames;f++){
                byte[] r={0,(byte)(30+f*15)},g={0,(byte)180},b={0,(byte)220},alpha={0,(byte)255};IndexColorModel colors=new IndexColorModel(8,2,r,g,b,alpha);BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_BYTE_INDEXED,colors);
                int left=noisy?0:w/3+(f%7)*3,top=noisy?0:h/3+(f%7)*2,right=noisy?w:left+21,bottom=noisy?h:top+36;
                Random random=new Random(f);for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)image.getRaster().setSample(x,y,0,noisy?(random.nextBoolean()?1:0):1);
                ImageWriteParam param=writer.getDefaultWriteParam();IIOMetadata meta=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),param);String format=meta.getNativeMetadataFormatName();IIOMetadataNode root=(IIOMetadataNode)meta.getAsTree(format),control=node(root,"GraphicControlExtension");control.setAttribute("disposalMethod","restoreToBackgroundColor");control.setAttribute("userInputFlag","FALSE");control.setAttribute("transparentColorFlag","TRUE");control.setAttribute("delayTime","5");control.setAttribute("transparentColorIndex","0");node(root,"ImageDescriptor").setAttribute("interlaceFlag",interlace?"TRUE":"FALSE");meta.setFromTree(format,root);writer.writeToSequence(new IIOImage(image,null,meta),param);
            }writer.endWriteSequence();
        }finally{writer.dispose();}return bytes.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        for(boolean interlace:new boolean[]{false,true})for(int frames:new int[]{1,2,7,240}){
            byte[] bytes=fixture(800,600,frames,interlace,false);GifBounds.Bounds b=GifBounds.read(bytes);check(b.width()==800&&b.height()==600,"canvas dimensions");check(b.left()==266&&b.top()==200&&b.right()==287+Math.min(6,frames-1)*3&&b.bottom()==236+Math.min(6,frames-1)*2,"union of every frame, interlace="+interlace+", frames="+frames);
            Files.createDirectories(Path.of(".tools/art-v120"));Files.write(Path.of(".tools/art-v120/fixture-"+interlace+"-"+frames+".gif"),bytes);
        }
        GifBounds.Bounds noisy=GifBounds.read(fixture(256,256,3,true,true));check(noisy.left()==0&&noisy.top()==0&&noisy.right()==256&&noisy.bottom()==256,"LZW dictionary growth and noise");
        byte[] bytes=fixture(80,100,2,false,false);for(int len:new int[]{0,10,25,bytes.length/2}){boolean failed=false;try{GifBounds.read(Arrays.copyOf(bytes,len));}catch(IOException expected){failed=true;}check(failed,"truncated stream rejected");}
        System.out.println(checks+" GIF bounds checks passed");
    }
}
