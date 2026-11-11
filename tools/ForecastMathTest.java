import com.skycast.pro.Forecast;
import com.skycast.pro.ForecastMath;
import com.skycast.pro.ForecastQuality;
import java.time.Instant;
import java.util.*;

public class ForecastMathTest {
    static int passed;
    static void yes(boolean ok, String name) { if(!ok) throw new AssertionError(name); passed++; }
    public static void main(String[] args) {
        yes(ForecastQuality.probability(-1)==-1, "Missing chance stays missing");
        yes(ForecastQuality.probability(144)==-1, "Invalid probability stays unavailable");
        yes(ForecastMath.median(Arrays.asList(23., 22., 34., 21.))==22.5, "Robust median");
        yes(ForecastMath.condition(95,true).equals("Thunderstorm"), "WMO thunderstorm");
        yes(ForecastMath.temp(25.0,true).equals("77°"), "Fahrenheit");
        Forecast.Hour h=new Forecast.Hour(); h.rainProbability=-1; h.rainMm=0.6;
        ForecastMath.RainWindow w=ForecastMath.rainWindow(Arrays.asList(h));
        yes(!w.probabilityAvailable, "No invented probability");
        yes(w.headline().contains("chance unavailable"), "Missing rain chance attribution");
        Forecast.Hour dry=new Forecast.Hour(); dry.rainProbability=2; dry.rainMm=0;
        yes(ForecastMath.rainWindow(Arrays.asList(dry)).firstHour==-1, "No false alert");
        yes(ForecastMath.temperatureAgreement(26,33).contains("significantly"), "Disagreement detection");
        Forecast.Hour late=new Forecast.Hour();late.time="2026-11-10T03:00";late.rainMm=0.3;
        Forecast.Hour early=new Forecast.Hour();early.time="2026-11-10T00:00";early.rainMm=0;
        ForecastMath.RainWindow sparse=ForecastMath.rainWindow(Arrays.asList(early,late));
        yes(sparse.firstHour==3,"Use actual hour offsets for sparse MET Norway forecast");
        yes(!sparse.completeCoverage,"Sparse provider coverage disclosed");
        yes(sparse.headline().contains("partial"),"Partial rainfall totals labeled");
        Forecast forecast = new Forecast();
        forecast.timezone = "Asia/Karachi";
        Forecast.Hour distant = new Forecast.Hour();
        distant.time = "2026-11-11T00:00";
        distant.rainProbability = 98;
        forecast.hours.add(early);
        forecast.hours.add(late);
        forecast.hours.add(distant);
        Instant now = Instant.parse("2026-11-09T19:20:00Z");
        ForecastMath.RainWindow aligned = ForecastMath.rainWindow(forecast, now);
        yes(aligned.peakChance == 98 && aligned.nextSixHourChance < 70,
                "A strong signal tomorrow cannot trigger a six-hour alert");
        yes(ForecastMath.rainWindow(forecast, now.plusSeconds(20*3600)).nextSixHourChance == 98,
                "The high chance triggers when it actually enters the six-hour window");
        forecast.hours.clear();
        forecast.hours.add(distant);
        yes(ForecastMath.rainWindow(forecast, now).firstHour == 24,
                "Missing early hours do not move tomorrow's rain to the present");
        forecast.hours.clear();
        for (int i = 0; i < 48; i++) forecast.hours.add(new Forecast.Hour());
        yes(ForecastMath.rainWindow(forecast.hours).headline().contains("unavailable"),
                "Missing rainfall values cannot imply a dry forecast");
        forecast.hours.clear();
        forecast.hours.add(late);
        forecast.hours.add(late);
        yes(ForecastMath.rainWindow(forecast, now).coveredHours == 1
                && ForecastMath.rainWindow(forecast, now).totalMm == 0.3,
                "Duplicate time periods cannot double rainfall or coverage");
        System.out.println(passed+" forecast-engine tests passed");
    }
}
