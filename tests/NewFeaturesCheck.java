import com.kongye.floatball.FrameStitcher;
import com.kongye.floatball.ThemeSchedule;
import java.util.Arrays;
import java.util.Random;

public class NewFeaturesCheck {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(ThemeSchedule.isDark(23*60,22*60,7*60),"overnight evening");
        check(ThemeSchedule.isDark(6*60,22*60,7*60),"overnight morning");
        check(!ThemeSchedule.isDark(12*60,22*60,7*60),"overnight daytime");
        check(ThemeSchedule.isDark(22*60,22*60,7*60),"inclusive start");
        check(!ThemeSchedule.isDark(7*60,22*60,7*60),"exclusive end");
        check(ThemeSchedule.isDark(10*60,9*60,17*60),"daytime interval");
        check(!ThemeSchedule.isDark(18*60,9*60,17*60),"after daytime interval");
        check(ThemeSchedule.isDark(800,600,600),"equal times mean all day");
        int width=96,height=240,shift=91;int[] page=new int[width*height*3];Random random=new Random(8421);for(int i=0;i<page.length;i++)page[i]=0xff000000|random.nextInt(0xffffff);
        int[] previous=Arrays.copyOfRange(page,0,width*height),current=Arrays.copyOfRange(page,width*shift,width*(height+shift));
        check(FrameStitcher.findShift(previous,current,width,height)==shift,"detect exact scrolling overlap");
        check(FrameStitcher.findShift(previous,previous.clone(),width,height)==0,"detect unchanged page");
        int[] unrelated=new int[width*height];Arrays.fill(unrelated,0xffeeeeee);
        check(FrameStitcher.findShift(previous,unrelated,width,height)==-1,"reject unrelated frame");
        check(FrameStitcher.findShift(unrelated,unrelated.clone(),width,height)==0,"blank unchanged page stops");
        check(FrameStitcher.findShift(new int[4],new int[4],2,2)==-1,"reject invalid dimensions");
        System.out.println(checks+" schedule and stitching checks passed.");
    }
}
