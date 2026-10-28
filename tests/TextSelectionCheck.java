import com.kongye.floatball.TextSelectionRange;
import java.util.Arrays;
public class TextSelectionCheck {
    static int count;
    static void check(boolean ok){count++;if(!ok)throw new AssertionError("Selection check "+count);}
    public static void main(String[] args){
        check(Arrays.equals(TextSelectionRange.normalize("abcdef",5,2),new int[]{2,5}));
        check(Arrays.equals(TextSelectionRange.normalize("abc",-10,99),new int[]{0,3}));
        String emoji="叶😀球";int[] range=TextSelectionRange.normalize(emoji,2,3);check(emoji.substring(range[0],range[1]).equals("😀"));
        range=TextSelectionRange.normalize(emoji,1,2);check(emoji.substring(range[0],range[1]).equals("😀"));
        check(Arrays.equals(TextSelectionRange.word("KONGYE test",2),new int[]{0,6}));
        check(Arrays.equals(TextSelectionRange.word("空叶悬浮球",2),new int[]{2,3}));
        check(Arrays.equals(TextSelectionRange.word("",9),new int[]{0,0}));
        check(Arrays.equals(TextSelectionRange.word("abc",99),new int[]{0,3}));
        check(Arrays.equals(TextSelectionRange.word(emoji,2),new int[]{1,3}));
        System.out.println(count+" partial-text and Unicode selection checks passed.");
    }
}
