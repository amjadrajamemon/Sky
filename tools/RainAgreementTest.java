import com.skycast.pro.Forecast;
import com.skycast.pro.RainAgreement;
import java.time.LocalDateTime;

public class RainAgreementTest {
    private static void require(boolean result, String msg) {if(!result)throw new AssertionError(msg);}
    static Forecast forecast(int rainyStart,int rainyEnd) {
        Forecast f=new Forecast();
        LocalDateTime base=LocalDateTime.parse("2026-11-11T11:00:00");
        for(int i=0;i<12;i++) {
            Forecast.Hour h=new Forecast.Hour();h.time=base.plusHours(i).toString();
            h.rainProbability=i>=rainyStart&&i<rainyEnd?75:10;
            h.rainMm=i>=rainyStart&&i<rainyEnd?.7:0;
            f.hours.add(h);
        }
        return f;
    }
    public static void main(String[] args) {
        LocalDateTime time=LocalDateTime.parse("2026-11-11T11:00:00");
        Forecast a=forecast(2,5), b=forecast(2,5);
        require(RainAgreement.compare(a,b,time).equals("Strong model agreement"),"strong rain signal");
        require(RainAgreement.compare(a,forecast(8,11),time).equals("Rain models disagree"),"disagreement");
        require(RainAgreement.compare(a,forecast(3,6),time).equals("Strong model agreement"),"partial hour shift");
        b.stale=true;
        require(RainAgreement.compare(a,b,time).isEmpty(),"stale comparison rejected");
        Forecast sparse=new Forecast();sparse.hours.add(a.hours.get(0));
        require(RainAgreement.compare(a,sparse,time).equals("Limited model evidence"),"minimum coverage");
        System.out.println("Rain agreement checks passed");
    }
}
