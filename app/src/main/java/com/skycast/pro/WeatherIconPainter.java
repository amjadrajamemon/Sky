package com.skycast.pro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/** Consistent resolution-independent widget icon illustrations, no system emoji dependencies. */
public final class WeatherIconPainter {
    private static final int WHITE=0xFFF7FCFF,BLUE=0xFFACDFFF,CYAN=0xFF58DEFF,GOLD=0xFFFFD96C;
    private WeatherIconPainter(){}
    public static Bitmap make(int code,boolean daytime,int px) {
        Bitmap bitmap=Bitmap.createBitmap(px,px,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);c.scale(px/128f,px/128f);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStrokeCap(Paint.Cap.ROUND);
        boolean rain=code>=51&&code<=67 || code>=80&&code<=82 || code>=95;
        boolean snow=code>=71&&code<=77 || code==85||code==86;
        boolean fog=code==45||code==48;
        boolean cloud=code>=1||rain||snow||fog;
        boolean storm=code>=95;
        if(!daytime && code<=2) {
            p.setColor(GOLD);c.drawCircle(75,42,26,p);p.setColor(0xFF132C51);c.drawCircle(85,31,25,p);
        }else if(!fog&&!storm){
            p.setColor(GOLD);c.drawCircle(cloud?85:64,cloud?45:63,cloud?23:29,p);
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4;float x=cloud?85:64,y=cloud?45:63;
                p.setColor(GOLD);p.setStrokeWidth(4);p.setStyle(Paint.Style.STROKE);
                c.drawLine(x+(float)Math.cos(a)*31,y+(float)Math.sin(a)*31,x+(float)Math.cos(a)*38,y+(float)Math.sin(a)*38,p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        if(cloud){
            p.setShader(new LinearGradient(22,35,90,94,WHITE,BLUE,Shader.TileMode.CLAMP));
            c.drawRoundRect(15,56,112,95,20,20,p);
            c.drawCircle(40,59,23,p);c.drawCircle(72,47,33,p);c.drawCircle(99,65,20,p);
            p.setShader(null);
            p.setColor(0xFFD9F2FF);c.drawRoundRect(20,76,108,92,10,10,p);
        }
        p.setColor(CYAN);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);
        if(rain)for(int i=0;i<4;i++)c.drawLine(33+i*20,100,27+i*20,115,p);
        if(fog)for(int i=0;i<3;i++)c.drawLine(18,96+i*10,109,96+i*10,p);
        p.setStyle(Paint.Style.FILL);
        if(snow){p.setColor(WHITE);for(int i=0;i<3;i++)c.drawCircle(39+i*23,109,4,p);}
        if(storm){
            Path z=new Path();z.moveTo(72,84);z.lineTo(56,106);z.lineTo(74,106);z.lineTo(64,125);
            z.lineTo(94,95);z.lineTo(76,95);z.close();p.setColor(GOLD);c.drawPath(z,p);
        }
        return bitmap;
    }
}
