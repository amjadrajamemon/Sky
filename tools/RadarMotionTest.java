import com.skycast.pro.*;
import java.time.Instant;
public class RadarMotionTest {
    static int tests;
    static void verify(boolean condition,String message){tests++;if(!condition)throw new AssertionError(message);}
    static int[][] grid(){return new int[41][41];}
    static void cloud(int[][] v,int x,int y,int strength){for(int yy=y-2;yy<=y+2;yy++)for(int xx=x-3;xx<=x+3;xx++)v[yy][xx]=strength;}
    public static void main(String[] args){
        int[][] a=grid(),b=grid();cloud(a,10,20,3);cloud(b,12,20,3);
        RadarMotion.Motion motion=RadarMotion.forecast(a,b,10);
        verify(motion.coherent,"Shifted radar patch should be tracked");
        verify(motion.dx==2&&motion.dy==0,"Storm dx must be positive two cells: "+motion.dx+","+motion.dy);
        verify(motion.steps[0]==0&&motion.steps[3]>0,"Echo should arrive near center in ~30 min");
        RainNowcast n=new RainNowcast();n.radarCoverage=true;n.radarAvailable=true;n.radarTime=Instant.now().toEpochMilli();
        RainNowcastEngine.radarSignal(n,motion);
        verify(n.arrivalStart>=0&&n.arrivalEnd<=90,"Non-exact arrival window expected");
        verify(n.duration>0&&n.intensity.contains("echoes"),"Qualitative echo intensity only");
        RadarMotion.Motion blank=RadarMotion.forecast(grid(),grid(),10);
        verify(!blank.coherent&&blank.steps[0]==0,"Blank frames must not imply dry");
        int[][] sparse=grid();sparse[20][20]=1;
        verify(!RadarMotion.forecast(sparse,sparse,10).coherent,"Sparse storms have unknown velocity");
        verify(!RadarMotion.forecast(a,b,45).coherent,"Stale interval must fail motion");
        RainNowcast invalid=new RainNowcast();invalid.radarCoverage=false;invalid.radarAvailable=true;invalid.radarTime=Instant.now().toEpochMilli();
        RainNowcastEngine.radarSignal(invalid,motion);
        verify(invalid.arrivalStart<0,"No nowcast without validated coverage");
        System.out.println(tests+" radar nowcast tests passed");
    }
}
