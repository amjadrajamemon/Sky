import com.skycast.pro.WidgetSizing;
public final class WidgetSizingTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(WidgetSizing.iconDimension(12,60,75,20)==8,"Reduced icon visible");
        check(WidgetSizing.iconDimension(12,100,100,20)==12,"Default icon size");
        check(WidgetSizing.iconDimension(12,150,100,20)==18,"Icon grows when slider grows");
        check(WidgetSizing.iconDimension(12,150,150,20)==20,"Bound compact icon by row");
        check(WidgetSizing.iconDimension(27,70,90,41)<WidgetSizing.iconDimension(27,135,130,41),
                "Live icon slider changes size");
        System.out.println("5 icon-resizing tests passed");
    }
}
