package com.skycast.pro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

/** Consistent resolution-independent widget icon illustrations, no system emoji dependencies. */
public final class WeatherIconPainter {
    private static final int WHITE=0xFFF7FCFF,BLUE=0xFFACDFFF,CYAN=0xFF58DEFF,GOLD=0xFFFFD96C;
    private WeatherIconPainter(){}
    /** Ten genuine icon treatments (illustrated, outline, neon, glass, flat, mono,
     *  circular badges, pastel illustration, high-contrast, and minimalist glyphs). */
    public static Bitmap make(int code,boolean daytime,int px,int style,int accent,int ink){
        px=Math.max(10,Math.min(256,px));
        if(style<=1)return make(code,daytime,px);
        Bitmap image=Bitmap.createBitmap(px,px,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(image);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        c.save();c.scale(px/128f,px/128f);
        switch(style){
            case 2: // thin meteorological outline
                drawPictogram(c,code,daytime,accent,ink,4.5f,false);
                break;
            case 3: // luminous neon with actual outline glow
                p.setColor((0x32<<24)|(accent&0xffffff));
                c.drawCircle(64,64,59,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xa059f9fa);
                c.drawCircle(64,64,56,p);p.setStyle(Paint.Style.FILL);
                p.setShadowLayer(10,0,0,accent);
                drawPictogram(c,code,daytime,0xffb3ffff,accent,6f,false);
                p.clearShadowLayer();break;
            case 4: // glass tile with shaded original illustration
                p.setColor((0x36<<24)|(accent&0xffffff));c.drawRoundRect(4,4,124,124,25,25,p);
                p.setStyle(Paint.Style.STROKE);p.setColor(0x7affffff);p.setStrokeWidth(3);
                c.drawRoundRect(4,4,124,124,25,25,p);p.setStyle(Paint.Style.FILL);
                Bitmap glass=make(code,daytime,Math.max(10,px));
                c.drawBitmap(glass,null,new android.graphics.RectF(17,17,111,111),p);break;
            case 5: // simple flat silhouettes without illustrated shadows
                drawPictogram(c,code,daytime,accent,ink,8f,true);break;
            case 6: // monochrome editorial line glyph
                drawPictogram(c,code,daytime,ink,ink,6f,false);break;
            case 7: // circular badge and contrasting glyph
                p.setColor((0xee<<24)|(accent&0xffffff));c.drawCircle(64,64,60,p);
                p.setColor(0x26ffffff);c.drawCircle(46,39,36,p);
                drawPictogram(c,code,daytime,ink,ink,7.5f,true);break;
            case 8: // warm illustrated pastel tile
                p.setColor((0x3c<<24)|(accent&0xffffff));c.drawCircle(53,52,48,p);
                p.setColor(0x34ffffff);c.drawCircle(85,87,39,p);
                Bitmap illustrated=make(code,daytime,px);
                c.drawBitmap(illustrated,null,new android.graphics.RectF(13,13,115,115),p);break;
            case 9: // extremely high contrast alert-tile
                p.setColor(ink);c.drawRoundRect(2,2,126,126,13,13,p);
                drawPictogram(c,code,daytime,accent,accent,8.5f,false);
                break;
            case 10: // industrial geometric minimalist outline
                p.setColor((0x45<<24)|(accent&0xffffff));c.drawRect(10,8,118,10,p);
                drawPictogram(c,code,daytime,accent,accent,3f,false);break;
            default: return make(code,daytime,px);
        }
        c.restore();return image;
    }

    private static void drawPictogram(Canvas c,int code,boolean daytime,int primary,int secondary,float line,boolean filled){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        boolean rain=(code>=51&&code<=67)||(code>=80&&code<=82)||code>=95;
        boolean snow=(code>=71&&code<=77)||(code>=85&&code<=86);
        boolean fog=code==45||code==48;
        boolean cloud=code>=1;
        boolean storm=code>=95;
        if(code<0){
            p.setColor(primary);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(line);
            c.drawCircle(64,64,27,p);p.setStyle(Paint.Style.FILL);p.setTextSize(36);p.setTextAlign(Paint.Align.CENTER);
            c.drawText("?",64,77,p);return;
        }
        p.setStrokeWidth(line);p.setColor(primary);
        p.setStyle(filled?Paint.Style.FILL:Paint.Style.STROKE);
        if(!fog){
            if(!daytime&&code<=2){
                c.drawArc(70,18,114,60,30,280,false,p);
            }else{
                c.drawCircle(cloud?85:62,cloud?39:60,cloud?18:25,p);
                for(int i=0;i<8;i++){
                    double a=i*Math.PI/4;
                    float x=cloud?85:62,y=cloud?39:60;
                    c.drawLine(x+(float)Math.cos(a)*27,y+(float)Math.sin(a)*27,
                            x+(float)Math.cos(a)*34,y+(float)Math.sin(a)*34,p);
                }
            }
        }
        if(cloud){
            p.setColor(secondary);p.setStrokeWidth(line*1.15f);
            Path cloudPath=new Path();
            cloudPath.moveTo(26,87);cloudPath.cubicTo(8,80,19,57,39,58);
            cloudPath.cubicTo(44,38,76,35,85,57);cloudPath.cubicTo(111,54,120,78,106,88);
            cloudPath.close();
            c.drawPath(cloudPath,p);
        }
        p.setStyle(Paint.Style.STROKE);p.setColor(primary);p.setStrokeWidth(line);
        if(rain)for(int i=0;i<3;i++)c.drawLine(35+i*25,98,29+i*25,112,p);
        if(fog)for(int i=0;i<4;i++)c.drawLine(22,40+i*16,110,40+i*16,p);
        if(snow)for(int i=0;i<3;i++){
            float x=34+i*26,y=107;
            c.drawLine(x-5,y,x+5,y,p);c.drawLine(x,y-5,x,y+5,p);
        }
        if(storm){
            p.setStyle(Paint.Style.FILL);
            Path bolt=new Path();bolt.moveTo(73,84);bolt.lineTo(57,107);bolt.lineTo(74,105);
            bolt.lineTo(66,121);bolt.lineTo(94,96);bolt.lineTo(77,98);bolt.close();c.drawPath(bolt,p);
        }
    }
    public static Bitmap make(int code,boolean daytime,int px) {
        Bitmap bitmap=Bitmap.createBitmap(px,px,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);c.scale(px/128f,px/128f);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStrokeCap(Paint.Cap.ROUND);
        if(code<0){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);p.setColor(0xff92a7bb);
            c.drawCircle(64,64,36,p);
            p.setStyle(Paint.Style.FILL);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextSize(52);p.setTextAlign(Paint.Align.CENTER);
            c.drawText("?",64,82,p);return bitmap;
        }
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
