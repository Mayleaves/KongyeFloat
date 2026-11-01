import com.kongye.floatball.BallScale;
import com.kongye.floatball.PlantCycle;

public final class BallScaleCheck {
    private static int count;
    private static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);count++;}
    public static void main(String[] args){
        check(Math.abs(BallScale.dp(30)-10.8f)<.001,"30 percent = 10.8dp");check(BallScale.dp(0)==BallScale.dp(30),"size clamps below 30 percent");check(BallScale.dp(900)==180,"size clamps above 500 percent");check(BallScale.dp(100)==36,"100 percent = 36dp");check(BallScale.dp(500)==180,"500 percent = 180dp");
        check(BallScale.sizePercent(36)==100,"old default preserved");check(Math.abs(BallScale.dp(BallScale.sizePercent(150))-150)<.2,"old 150dp preserved");
        check(BallScale.inset(100,100)==0,"100 percent fully shown");check(BallScale.inset(30,100)==-70,"30 percent visible");
        for(int extent:new int[]{49,107,268,536}){int last=Integer.MIN_VALUE;for(int percent=30;percent<=150;percent++){int inset=BallScale.inset(percent,extent);if(inset<last||inset>Math.round(extent*.5f)||extent+inset<extent*.3-1)throw new AssertionError("bounded monotonic edge inset");last=inset;}check(true,"visibility at "+extent);}
        check(BallScale.inset(150,100)==50,"150 percent leaves half-width inward gap");check(BallScale.edgePercent(18,36)==150,"positive legacy inset migrates to 150 percent");check(BallScale.edgePercent(0,36)==100,"old 0dp becomes 100 percent");check(BallScale.edgePercent(-18,36)==50,"old inset preserves visible fraction");
        for(int hour=0;hour<24;hour++){int expected=hour<6||hour>=23?5:hour>=19?4:hour>=16?3:hour>=12?2:(hour-6)/3;check(PlantCycle.stage(hour)==expected,"plant stage hour "+hour);}
        check(PlantCycle.stage(30)==0,"daily reset");check(PlantCycle.stage(-1)==5,"negative clock normalization");
        System.out.println(count+" percentage and plant clock checks passed");
    }
}
