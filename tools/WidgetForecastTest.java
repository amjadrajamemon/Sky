import com.skycast.pro.Forecast;
import com.skycast.pro.RainBrief;
import java.time.Instant;
import java.time.LocalDateTime;

public final class WidgetForecastTest {
    private static int passed;
    private static void verify(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        passed++;
    }
    public static void main(String[] args) {
        verify(RainBrief.percent(-1).equals("—%"), "Missing chance not invented");
        verify(RainBrief.compactMm(Double.NaN).equals("—mm"), "Missing mm not invented");
        verify(RainBrief.percent(77).equals("77%"), "Percent precision");
        verify(RainBrief.compactMm(21.4).equals("21mm"), "Compact rainfall rounding");
        Forecast f = new Forecast();
        f.timezone = "Asia/Karachi";
        Forecast.Day today = new Forecast.Day();
        today.date = "2026-11-11";
        today.rainProbability = 77;
        today.rainMm = 21;
        f.days.add(today);
        Instant now = Instant.parse("2026-11-11T05:20:00Z"); // 10:20 Pakistan time
        verify(RainBrief.today(f, now, true, true).contains("77% / 21mm"), "Today combines % and mm");
        verify(RainBrief.today(f, now.plusSeconds(86400), true, true).contains("unavailable"),
                "Tomorrow must not be mislabeled as today");
        verify(RainBrief.timing(f, now).contains("unavailable"), "Missing hours not nowcast");
        LocalDateTime base = LocalDateTime.parse("2026-11-11T10:00");
        for (int i = 0; i < 12; i++) {
            Forecast.Hour h = new Forecast.Hour();
            h.time = base.plusHours(i).toString();
            h.rainProbability = i >= 2 && i <= 4 ? 72 : 10;
            h.rainMm = i >= 2 && i <= 4 ? .8 : 0;
            f.hours.add(h);
        }
        verify(RainBrief.timing(f, now).contains("in ~2h · ~3h window"), "Consecutive hourly window");
        f.hours.get(3).rainProbability = -1;
        f.hours.get(3).rainMm = Double.NaN;
        verify(RainBrief.timing(f, now).contains("~1h window"), "Unknown hourly gap ends window");
        System.out.println(passed + " widget forecast tests passed");
    }
}
