import com.kongye.floatball.GestureRules;

public class GestureRulesCheck {
    private static int checks;
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        check(GestureRules.dragPosition(1118,550,1179)==489,"center press keeps finger on ball horizontally");
        check(GestureRules.dragPosition(1129,1100,1190)==1039,"center press keeps finger on ball vertically");
        check(GestureRules.dragPosition(100,400,145)==355,"off-center press preserves touch offset");
        check(GestureRules.dragPosition(200,100,211)==89,"dragging backwards preserves touch offset");
        check(GestureRules.dragPosition(100,0,130)==-30,"drag may extend ball beyond display edge");
        check(GestureRules.dragPosition(50,70,70)==50,"stationary pointer never relocates ball");
        check(!GestureRules.isDrag(0,0,900,8),"stationary long press must not drag");
        check(!GestureRules.isDrag(0,60,200,8),"quick swipe must not drag");
        check(GestureRules.isDrag(20,0,280,8),"held movement starts dragging");
        check("up".equals(GestureRules.swipe(4,-50,150,28)),"quick upward swipe");
        check("down".equals(GestureRules.swipe(-4,50,150,28)),"quick downward swipe");
        check(GestureRules.swipe(40,30,150,28)==null,"horizontal movement must not invoke vertical gesture");
        check(GestureRules.swipe(0,-60,280,28)==null,"drag must not also invoke swipe");
        check(GestureRules.swipe(0,10,150,28)==null,"jitter must not invoke swipe");
        check(GestureRules.edge(30,1080,120)==0,"left half snaps left");
        check(GestureRules.edge(900,1080,120)==960,"right half snaps right");
        check(GestureRules.clamp(-50,0,960)==0,"drag cannot leave left edge");
        check(GestureRules.clamp(1100,0,960)==960,"drag cannot leave right edge");
        check(GestureRules.clamp(12,32,10)==32,"tiny viewport must not produce negative bounds");
        check(GestureRules.edge(100,40,48)==0,"ball larger than viewport stays nonnegative");
        System.out.println(checks+" gesture and boundary checks passed.");
    }
}
