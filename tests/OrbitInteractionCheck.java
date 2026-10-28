import com.kongye.floatball.QuickOrbitLayout;
import com.kongye.floatball.GestureRules;
public class OrbitInteractionCheck {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        float[][] left=QuickOrbitLayout.positions(1080,2520,63,874,2.625f),right=QuickOrbitLayout.positions(1080,2520,1017,874,2.625f);
        check(left.length==13,"12 configurable slots and 1 recent slot");
        float[][] reference=QuickOrbitLayout.positions(510,953,0,182,1);
        check(reference[0][0]==40&&reference[0][1]==40,"reference top circle");
        check(reference[12][0]==35&&reference[12][1]==269,"single recent slot matches red circle");
        check(reference[10][0]==91&&reference[10][1]==302,"lower editable position");
        check(reference[11][0]==40&&reference[11][1]==326,"last editable position");
        check(QuickOrbitLayout.diameter(510,953,1)==44,"reference icon diameter");
        for(int i=0;i<left.length;i++){check(Math.abs(left[i][0]+right[i][0]-1080)<.01,"mirrored slot "+i);check(left[i][1]==right[i][1],"same vertical anchor "+i);}
        for(float[][] positions:new float[][][]{left,QuickOrbitLayout.positions(1080,2520,63,100,2.625f),QuickOrbitLayout.positions(1080,2520,1017,2400,2.625f)}){
            boolean fits=true,separate=true;for(int i=0;i<positions.length;i++){fits&=positions[i][0]>0&&positions[i][0]<1080&&positions[i][1]>0&&positions[i][1]<2520;for(int j=0;j<i;j++)separate&=Math.hypot(positions[i][0]-positions[j][0],positions[i][1]-positions[j][1])>=QuickOrbitLayout.diameter(1080,2520,2.625f);}
            check(fits,"fan stays within display");check(separate,"circles do not overlap");
        }
        check(GestureRules.followOffset(100,64)==45,"downwards proportional follow");check(GestureRules.followOffset(-100,64)==-45,"upwards proportional follow");check(GestureRules.followOffset(400,64)==64,"follow distance bounded");check(GestureRules.followOffset(0,64)==0,"stationary ball has no offset");
        System.out.println(checks+" orbit and follow-animation checks passed.");
    }
}
