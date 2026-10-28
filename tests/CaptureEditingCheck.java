import com.kongye.floatball.CompositionLayout;
import com.kongye.floatball.CropGeometry;
import java.util.*;

public class CaptureEditingCheck {
    static int count;
    static void check(boolean success,String message){count++;if(!success)throw new AssertionError(message);}
    public static void main(String[] args){
        float[] r={100,200,500,800};
        float[] moved=CropGeometry.adjust(r,CropGeometry.MOVE,-300,400,1080,1000,40);
        check(moved[0]==0&&moved[2]==400,"move clamps at left while retaining width");
        check(moved[1]==400&&moved[3]==1000,"move clamps bottom while retaining height");
        float[] left=CropGeometry.adjust(r,CropGeometry.LEFT,900,0,1080,1000,40);
        check(left[0]==460&&left[2]==500,"left resize cannot cross right edge");
        float[] bottom=CropGeometry.adjust(r,CropGeometry.BOTTOM,0,-900,1080,1000,40);
        check(bottom[3]==240&&bottom[1]==200,"bottom resize retains minimum height");
        float[] corner=CropGeometry.adjust(r,CropGeometry.RIGHT|CropGeometry.TOP,800,-500,1080,1000,40);
        check(corner[2]==1080&&corner[1]==0,"corner resize clamps both dimensions");
        check(Arrays.equals(r,new float[]{100,200,500,800}),"resizing never mutates original drag anchor");
        CompositionLayout vertical=CompositionLayout.create(new int[]{200,100},new int[]{300,400},false);
        check(vertical.width==200&&vertical.height==700,"vertical concatenation dimensions");
        check(vertical.left[1]==50&&vertical.top[1]==300,"unequal image widths are centered without distortion");
        CompositionLayout horizontal=CompositionLayout.create(new int[]{200,100},new int[]{300,400},true);
        check(horizontal.width==300&&horizontal.height==400,"horizontal concatenation dimensions");
        check(horizontal.left[1]==200&&horizontal.top[0]==50,"horizontal images retain aspect and center vertically");
        ArrayList<String> order=new ArrayList<>(List.of("first","second","third"));CompositionLayout.move(order,2,0);
        check(order.equals(List.of("third","first","second")),"move later screenshot to first");
        CompositionLayout.move(order,0,2);check(order.equals(List.of("first","second","third")),"move first screenshot to end");
        order.remove(1);check(order.equals(List.of("first","third")),"deletion preserves other screenshot ordering");
        CompositionLayout.move(order,-1,0);check(order.equals(List.of("first","third")),"out-of-range reorder is harmless");
        boolean oversized=false;try{CompositionLayout.create(new int[]{4000,4000},new int[]{4000,4000},false);}catch(IllegalArgumentException expected){oversized=true;}check(oversized,"oversized output rejected before allocating bitmap");
        boolean empty=false;try{CompositionLayout.create(new int[0],new int[0],false);}catch(IllegalArgumentException expected){empty=true;}check(empty,"empty compositions cannot be exported");
        System.out.println(count+" crop, concatenation and ordering checks passed.");
    }
}
