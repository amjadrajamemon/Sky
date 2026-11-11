import com.skycast.pro.Forecast;
import com.skycast.pro.TodayHours;
import java.time.Instant;
import java.util.List;
public final class TodayHoursTest {
    private static void check(boolean actual,String what){if(!actual)throw new AssertionError(what);}
    public static void main(String[] args){
        Forecast f=new Forecast();f.timezone="Asia/Karachi";
        for(String iso:new String[]{"2026-11-11T13:00","2026-11-11T11:00","2026-11-12T00:00",
                "2026-11-11T10:00","2026-11-11T10:00","2026-11-11T12:00"}){
            Forecast.Hour h=new Forecast.Hour();h.time=iso;f.hours.add(h);
        }
        List<Forecast.Hour> today=TodayHours.remaining(f,Instant.parse("2026-11-11T05:45:00Z"),7);
        check(today.size()==4,"No duplicate, past, or next-day hours");
        check(today.get(0).time.equals("2026-11-11T10:00"),"Earliest current hour first");
        check(today.get(3).time.equals("2026-11-11T13:00"),"Last future hour included");
        check(TodayHours.remaining(f,Instant.parse("2026-11-11T17:00:00Z"),7).isEmpty(),
                "After local midnight, previous day must not render");
        check(TodayHours.shortTime("2026-11-11T15:00").equals("3p"),"Short 12-hour caption");
        check(TodayHours.remaining(f,Instant.parse("2026-11-11T05:45:00Z"),2).size()==2,"Slot limit");
        System.out.println("6 hourly-widget tests passed");
    }
}
