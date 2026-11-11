package com.skycast.pro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Ten distinctly constructed surfaces, not ten recolorings of a generic rectangle. */
final class WidgetBackdropPainter {
    private WidgetBackdropPainter() {}

    static Bitmap make(int width,int height,int background,int accent,int text,int theme){
        int w=Math.max(64,Math.min(900,width)),h=Math.max(64,Math.min(520,height));
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(image);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        int alpha=Color.alpha(background);
        float radius=new float[]{24,5,7,28,22,32,6,2,15,1}[theme]*Math.min(w/280f,1.4f);
        RectF area=new RectF(.5f,.5f,w-.5f,h-.5f);
        c.save();
        Path mask=new Path();mask.addRoundRect(area,radius,radius,Path.Direction.CW);c.clipPath(mask);
        int first=background,second=background;
        switch(theme){
            case 0: first=mixWithAlpha(background,accent,.25f); second=mixWithAlpha(background,0xff4771ba,.44f);break; // aurora
            case 1: first=mixWithAlpha(background,Color.WHITE,.12f); second=mixWithAlpha(background,accent,.09f);break; // arctic
            case 2: first=mixWithAlpha(background,0xff5d6572,.10f); second=mixWithAlpha(background,Color.BLACK,.50f);break; // obsidian
            case 3: first=mixWithAlpha(background,0xff0489bd,.36f); second=mixWithAlpha(background,0xff000f27,.22f);break; // deep ocean
            case 4: first=mixWithAlpha(background,0xff67c797,.20f); second=mixWithAlpha(background,Color.BLACK,.28f);break; // forest
            case 5: first=mixWithAlpha(background,0xffad4aff,.30f); second=mixWithAlpha(background,0xff0b153d,.50f);break; // violet
            case 6: first=mixWithAlpha(background,0xffffd891,.14f); second=mixWithAlpha(background,0xffcc955a,.18f);break; // solar
            case 7: first=mixWithAlpha(background,0xff45515a,.18f); second=mixWithAlpha(background,Color.BLACK,.32f);break; // graphite
            case 8: first=mixWithAlpha(background,0xff1e1641,.27f); second=mixWithAlpha(background,Color.BLACK,.32f);break; // radar
            case 9: first=background;second=background;break; // editorial
        }
        p.setShader(new LinearGradient(0,0,w,h,first,second,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        switch(theme){
            case 0: { // Aurora ribbons, soft glass rim
                for(int i=0;i<3;i++){
                    Path wave=new Path();int offset=i*19;
                    wave.moveTo(-w*.15f,h*.12f+offset);
                    wave.cubicTo(w*.16f,h*.58f-offset,w*.53f,-h*.3f+offset,w*1.1f,h*.54f+offset);
                    p.setColor((0x25<<24)|(new int[]{0x83f5dd,0xb181ff,0x83caf5}[i]));
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(6,h*.12f));c.drawPath(wave,p);
                }p.setStyle(Paint.Style.FILL); break;
            }
            case 1: { // white arctic magazine, blue vertical keyline and faint horizontal rules
                p.setColor(argb(alpha,accent));c.drawRect(0,0,Math.max(5,w*.016f),h,p);
                p.setColor(0x180c618e);
                for(int y=Math.round(h*.32f);y<h;y+=Math.max(25,Math.round(h*.34f)))c.drawRect(0,y,w,y+1,p);
                break;
            }
            case 2: { // carved obsidian, metallic upper edge and indented facets
                p.setColor(0x24ffffff);c.drawRect(0,0,w,Math.max(2,h*.018f),p);
                Path facet=new Path();facet.moveTo(w*.76f,0);facet.lineTo(w,0);facet.lineTo(w,h*.6f);facet.close();
                p.setColor(0x17000000);c.drawPath(facet,p);
                break;
            }
            case 3: { // ocean lines and moving wave silhouette
                for(int i=0;i<4;i++){
                    Path wave=new Path();float y=h*(.48f+i*.13f);
                    wave.moveTo(-30,y);
                    wave.cubicTo(w*.2f,y-h*.2f,w*.48f,y+h*.2f,w*.78f,y);
                    wave.quadTo(w*.91f,y-h*.09f,w+30,y+h*.02f);
                    p.setColor(0x1667e6ff);p.setStrokeWidth(3+i);p.setStyle(Paint.Style.STROKE);c.drawPath(wave,p);
                }p.setStyle(Paint.Style.FILL);break;
            }
            case 4: { // botanical diagonals and leaves
                p.setColor(0x156cf5b1);p.setStrokeWidth(Math.max(1,w*.003f));
                for(int i=0;i<6;i++){
                    float x=w*.67f+i*w*.075f;
                    c.drawLine(x,0,x-w*.24f,h,p);
                    p.setStyle(Paint.Style.FILL);c.drawOval(x-5,h*.32f+i*5,x+9,h*.42f+i*5,p);
                    p.setStyle(Paint.Style.STROKE);
                }p.setStyle(Paint.Style.FILL);break;
            }
            case 5: { // twilight sky constellations
                p.setColor(0x23ffffff);
                for(int i=0;i<23;i++){
                    float x=(i*41+17)%w,y=(i*29+13)%h;
                    c.drawCircle(x,y,i%4==0?2f:1f,p);
                }
                p.setColor(0x229f8cff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
                c.drawCircle(w*.88f,h*.21f,h*.38f,p);c.drawCircle(w*.88f,h*.21f,h*.28f,p);
                p.setStyle(Paint.Style.FILL);break;
            }
            case 6: { // warm paper and angled solar stripes
                p.setColor(0x24a66a36);
                for(int i=-h;i<w+h;i+=26)c.drawLine(i,0,i+h,h,p);
                p.setColor(0x10bc7e3a);c.drawRect(0,h*.68f,w,h,p);
                break;
            }
            case 7: { // graphite blueprint grid
                p.setColor(0x1f9ebcdb);
                for(int x=0;x<w;x+=Math.max(17,w/12))c.drawRect(x,0,x+1,h,p);
                for(int y=0;y<h;y+=Math.max(17,h/5))c.drawRect(0,y,w,y+1,p);
                p.setColor(argb(120,accent));c.drawRect(0,0,Math.max(3,w*.01f),h,p);
                break;
            }
            case 8: { // illuminated radar circles and sweeping arm
                float cx=w*.84f,cy=h*.24f;
                p.setColor(0x4490f9e9);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);
                for(int i=1;i<=4;i++)c.drawCircle(cx,cy,h*(.13f*i),p);
                p.setStyle(Paint.Style.FILL);
                Path sweep=new Path();sweep.moveTo(cx,cy);sweep.lineTo(cx+h*.53f,cy-h*.45f);
                sweep.lineTo(cx+h*.57f,cy+h*.24f);sweep.close();p.setColor(0x205cf1d1);c.drawPath(sweep,p);
                break;
            }
            case 9: { // highly restrained editorial with vertical ochre rule
                p.setColor(argb(220,accent));c.drawRect(0,0,Math.max(4,w*.014f),h,p);
                p.setColor(argb(30,text));c.drawRect(w*.57f,0,w*.573f,h,p);
                break;
            }
        }
        c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(theme==2||theme==7?1.5f:1f);
        p.setColor((theme==1||theme==6||theme==9?0x35:0x56)<<24|(accent&0xffffff));
        c.drawRoundRect(area,radius,radius,p);
        p.setStyle(Paint.Style.FILL);
        return image;
    }
    private static int argb(int a,int col){return (a<<24)|(col&0xffffff);}
    private static int mixWithAlpha(int base,int tint,float fraction){
        int rr=Math.round(Color.red(base)*(1-fraction)+Color.red(tint)*fraction);
        int gg=Math.round(Color.green(base)*(1-fraction)+Color.green(tint)*fraction);
        int bb=Math.round(Color.blue(base)*(1-fraction)+Color.blue(tint)*fraction);
        return Color.argb(Color.alpha(base),rr,gg,bb);
    }
}
