package com.skycast.pro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Animated only while in-app, visible, and animations are enabled. Widgets remain static. */
public final class WeatherSceneView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;
    private int code=2;
    private boolean daytime=true;
    private int artStyle=1,artAccent=0xff58deff,artInk=0xfff7fcff;
    private android.graphics.Bitmap styledCache;
    private int styledCacheSize;
    public void setArtStyle(int style,int accent,int ink){
        artStyle=style;artAccent=accent;artInk=ink;clearCache();invalidate();
    }
    public WeatherSceneView(Context c){super(c);setContentDescription("Animated weather illustration");}
    public void setCondition(int weatherCode,boolean isDay){
        if(code!=weatherCode||daytime!=isDay)clearCache();
        code=weatherCode;daytime=isDay;invalidate();
    }
    private void clearCache(){
        if(styledCache!=null){styledCache.recycle();styledCache=null;}
        styledCacheSize=0;
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();start();}
    @Override protected void onDetachedFromWindow(){stop();clearCache();super.onDetachedFromWindow();}
    @Override protected void onVisibilityChanged(View changed,int visibility){
        super.onVisibilityChanged(changed,visibility);
        if(visibility==VISIBLE)start();else stop();
    }
    private void start(){
        if(animator!=null||!ValueAnimator.areAnimatorsEnabled()||!isShown())return;
        animator=ValueAnimator.ofFloat(0,1);animator.setDuration(3200);
        animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();
    }
    private void stop(){if(animator!=null){animator.cancel();animator=null;}}
    private void circle(Canvas c,float x,float y,float r,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);}
    private void stroke(Canvas c,float x,float y,float xx,float yy,int color,float width){
        p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
        c.drawLine(x,y,xx,yy,p);p.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(artStyle!=1){
            int size=Math.max(12,Math.min(getWidth(),getHeight()));
            if(styledCache==null||styledCacheSize!=size){
                clearCache();styledCache=WeatherIconPainter.make(code,daytime,size,artStyle,artAccent,artInk);
                styledCacheSize=size;
            }
            float x=(getWidth()-size)/2f,y=(getHeight()-size)/2f;
            float bounce=(float)Math.sin(phase*2*Math.PI)*Math.min(3,size*.012f);
            Paint iconPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(styledCache,x,y+bounce,iconPaint);
            return;
        }
        canvas.save();float scale=Math.min(getWidth(),getHeight())/130f;
        canvas.translate((getWidth()-130*scale)/2f,(getHeight()-130*scale)/2f);canvas.scale(scale,scale);
        int gold=Color.rgb(255,213,103),white=Color.rgb(234,246,255),shade=Color.rgb(183,222,245),rain=Color.rgb(75,212,250);
        float bob=(float)Math.sin(phase*2*Math.PI)*2.5f;
        boolean storm=code>=95;
        boolean wet=(code>=51&&code<=67)||(code>=80&&code<=82)||storm;
        boolean snowy=(code>=71&&code<=77)||(code>=85&&code<=86);
        boolean fog=code==45||code==48;
        boolean cloud=code>=1||wet||snowy||fog;
        if(!daytime&&code<=2){
            circle(canvas,92,40,23,gold);circle(canvas,101,30,22,0xFF193458);
        }else if(!fog&&!storm){
            float sunRadius=code==0?24:20;
            circle(canvas,88,39+0.8f*bob,sunRadius,gold);
            for(int i=0;i<8;i++){
                float angle=(float)(i*Math.PI/4+phase*0.25);
                stroke(canvas,88+(float)Math.cos(angle)*29,39+(float)Math.sin(angle)*29,
                    88+(float)Math.cos(angle)*35,39+(float)Math.sin(angle)*35,gold,3);
            }
        }
        if(cloud){
            canvas.save();canvas.translate(bob,0);
            p.setColor(shade);canvas.drawRoundRect(21,60,108,89,17,17,p);
            circle(canvas,46,60,22,white);circle(canvas,69,50,29,white);circle(canvas,92,63,19,white);
            p.setColor(white);canvas.drawRoundRect(23,62,103,83,10,10,p);
            canvas.restore();
        }
        if(wet){
            for(int i=0;i<4;i++){
                float yy=88+((phase*17+i*6)%23);
                stroke(canvas,35+i*18,yy,30+i*18,yy+8,rain,3);
            }
        }
        if(snowy){for(int i=0;i<4;i++){
            float yy=90+((phase*12+i*6)%22);
            circle(canvas,36+i*18,yy,3,white);
        }}
        if(fog){for(int i=0;i<3;i++)stroke(canvas,24,96+i*8,104,96+i*8,shade,3);}
        if(storm){
            Path flash=new Path();flash.moveTo(75,80);flash.lineTo(62,105);flash.lineTo(77,103);flash.lineTo(68,123);flash.lineTo(94,94);flash.lineTo(78,96);flash.close();
            p.setColor(gold);canvas.drawPath(flash,p);
        }
        canvas.restore();
    }
}
