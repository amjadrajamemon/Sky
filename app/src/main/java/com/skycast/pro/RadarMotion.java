package com.skycast.pro;

import java.util.Arrays;

/** Small-grid frame registration and extrapolation. Units are relative echo levels, not mm/h. */
public final class RadarMotion {
    private RadarMotion(){}
    public static final class Motion {
        public int dx,dy,frameMinutes;
        public boolean coherent,hasEcho;
        public double agreement;
        public final int[] steps = new int[13];
        Motion(){Arrays.fill(steps,-1);}
    }
    public static Motion forecast(int[][] previous,int[][] current,int frameMinutes){
        Motion result=new Motion();result.frameMinutes=frameMinutes;
        if(!square(previous)||!square(current)||previous.length!=current.length||frameMinutes<3||frameMinutes>30)return result;
        int n=current.length;int count=0,prior=0;
        // Current center echo is an observation even when velocity cannot be estimated.
        int center=n/2,observed=0;
        for(int yy=center-1;yy<=center+1;yy++)for(int xx=center-1;xx<=center+1;xx++)
            observed=Math.max(observed,current[yy][xx]);
        result.steps[0]=observed;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){if(current[y][x]>0)count++;if(previous[y][x]>0)prior++;}
        result.hasEcho=count>0;
        if(count<5||prior<5)return result; // no fabricated velocity from blank/sparse imagery
        double best=-1;int bx=0,by=0;
        for(int dy=-4;dy<=4;dy++)for(int dx=-4;dx<=4;dx++){
            int intersect=0,union=0;
            for(int y=4;y<n-4;y++)for(int x=4;x<n-4;x++){
                int a=previous[y-dy][x-dx],b=current[y][x];
                boolean aw=a>0,bw=b>0;
                if(aw||bw)union++;
                if(aw&&bw)intersect++;
            }
            double score=union==0?0:(double)intersect/union-0.006*(Math.abs(dx)+Math.abs(dy));
            if(score>best){best=score;bx=dx;by=dy;}
        }
        result.agreement=Math.max(0,best);
        result.coherent=best>=0.28;
        if(!result.coherent)return result;
        result.dx=bx;result.dy=by;
        int cx=n/2,cy=n/2;
        for(int step=0;step<result.steps.length;step++){
            double factor=step*10.0/frameMinutes;
            int xx=(int)Math.round(cx-bx*factor),yy=(int)Math.round(cy-by*factor);
            int peak=0;
            for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){
                int px=xx+ox,py=yy+oy;
                if(px>=0&&py>=0&&px<n&&py<n)peak=Math.max(peak,current[py][px]);
            }
            result.steps[step]=peak;
        }
        return result;
    }
    private static boolean square(int[][] v){if(v==null||v.length<15)return false;for(int[] row:v)if(row==null||row.length!=v.length)return false;return true;}
    public static String intensity(int v){return v>=4?"Strong radar echoes":v==3?"Moderate radar echoes":v==2?"Light radar echoes":v==1?"Weak radar echoes":"No detected echoes";}
}
