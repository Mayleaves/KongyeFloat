import com.kongye.floatball.GestureRules;

/** Capture endpoints must cover the display while normal movement stays above the finger. */
public final class CapturePointerCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(float density:new float[]{1f,2.625f,2.975f,4f}){
            int width=Math.round(411*density),height=Math.round(960*density);float offset=Math.round(56*density),reach=Math.round(96*density),inset=Math.round(8*density);
            for(int extent:new int[]{width,height}){
                check(GestureRules.capturePointer(200*density,extent,offset,reach,inset)==200*density-offset,"central offset");
                check(GestureRules.capturePointer(1,extent,offset,reach,inset)==0,"near edge reaches zero");
                check(GestureRules.capturePointer(extent-inset,extent,offset,reach,inset)==extent,"far edge is reachable inside display");
                check(GestureRules.capturePointer(extent+30,extent,offset,reach,inset)==extent,"outside pointer clamps to display");
                float previous=-1;
                for(int x=0;x<=extent;x++){float current=GestureRules.capturePointer(x,extent,offset,reach,inset);if(current<previous||current<0||current>extent)throw new AssertionError("edge transition must be bounded and monotonic");previous=current;}
                check(true,"bounded monotonic edge transition");
            }
        }
        check(GestureRules.capturePointer(10,0,56,96,8)==0,"empty display");
        System.out.println(checks+" capture pointer checks passed.");
    }
}
