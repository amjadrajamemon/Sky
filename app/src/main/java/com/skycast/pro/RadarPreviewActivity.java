package com.skycast.pro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** In-app native RainViewer preview and user-initiated embedded PMD viewer. */
public final class RadarPreviewActivity extends Activity {
    private final int BG=0xff071624,CARD=0xff122c42,WHITE=0xfff3faff,PALE=0xffa9cce5,BLUE=0xff63d3f6;
    private AppPrefs prefs;private LinearLayout body;private TextView heading,detail,observation,trust,frameTime;
    private ImageView radar;private WebView official;private int frame=0;private boolean playing;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable animation=new Runnable(){@Override public void run(){
        if(!playing)return;frame=(frame+1)%2;showFrame();handler.postDelayed(this,1100);
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(dp(radius));return gd;}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String title,Runnable click){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(WHITE);
        b.setTextSize(12);b.setBackground(background(CARD,12));b.setPadding(dp(7),0,dp(7),0);b.setOnClickListener(v->click.run());return b;}
    private void item(LinearLayout holder,View v,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);holder.addView(v,p);}
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=new AppPrefs(this);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(17),dp(16),dp(17),dp(24));scroll.addView(body);setContentView(scroll);
        item(body,text("Rain nowcast & live radar",23,WHITE,true),3);
        item(body,text("0–2 hours · observed radar vs estimated movement",12,PALE,false),5);
        heading=text("Checking evidence…",19,WHITE,true);item(body,heading,20);
        detail=text("",13,PALE,false);item(body,detail,6);
        observation=text("",12,PALE,false);item(body,observation,7);
        trust=text("",11,PALE,false);item(body,trust,6);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);item(body,controls,15);
        add(controls,button("◀ Earlier",()->{playing=false;handler.removeCallbacks(animation);frame=1;showFrame();}));
        add(controls,button("▶ Play",()->{playing=!playing;handler.removeCallbacks(animation);if(playing)handler.post(animation);}));
        add(controls,button("Latest",()->{playing=false;handler.removeCallbacks(animation);frame=0;showFrame();}));
        FrameLayout map=new FrameLayout(this);map.setBackground(background(0xff091522,14));item(body,map,11);
        radar=new ImageView(this);radar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        map.addView(radar,new FrameLayout.LayoutParams(-1,dp(300)));
        TextView center=text("⊕",26,WHITE,true);center.setGravity(Gravity.CENTER);
        map.addView(center,new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.CENTER));
        frameTime=text("Radar timestamp unavailable",12,BLUE,true);item(body,frameTime,9);
        item(body,text("Radar imagery: RainViewer · past frames only. Local point is at the center. Blank tiles may mean no coverage, not no rain.",11,PALE,false),4);
        item(body,button("↻ Check radar & observations",this::refresh),12);
        item(body,button("PMD official radar · in-app preview",this::showOfficial),8);
        item(body,button("Open PMD radar in browser ↗",()->open("https://ffd.pmd.gov.pk/radar?site=islamabad")),7);
        item(body,button("RainViewer source ↗",()->open("https://www.rainviewer.com/")),7);
        item(body,button("NOAA observation source ↗",()->open("https://aviationweather.gov/data/api/")),7);
        item(body,text("RainViewer imagery is for personal / educational use with attribution. PMD frames may be stale. Airport reports may not reflect conditions at your address.",11,PALE,false),12);
        display();
    }
    private void add(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(dp(2),0,dp(2),0);row.addView(b,p);}
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"A browser is needed",Toast.LENGTH_LONG).show();}}
    private void display(){
        RainNowcast n=new RainNowcastRepository(this,prefs).cached();
        if(n==null){heading.setText("No saved nowcast · Refresh to check");detail.setText("Select a city on the main screen first");observation.setText("");trust.setText("");}
        else{
            heading.setText(n.headline);detail.setText(n.detail);
            observation.setText(n.observation);trust.setText("Evidence: "+n.evidence+"\n"+n.radarState+"\n"+n.verification);
            if(!n.current(System.currentTimeMillis()))heading.setText("Earlier outlook · "+n.headline);
        }
        showFrame();
    }
    private void showFrame(){
        if(!prefs.hasLocation()||!RadarNowcastClient.locationKey(prefs.lat(),prefs.lon()).equals(prefs.get("radar_preview_location",""))){
            radar.setImageDrawable(null);frameTime.setText("No radar preview for this location");return;
        }
        long time=prefs.getLong(frame==0?"radar_preview_latest":"radar_preview_previous");
        File image=RadarNowcastClient.previewFile(this,frame);
        long age=System.currentTimeMillis()-time;
        if(!image.exists()||time<=0||age>40*60_000L){
            radar.setImageDrawable(null);frameTime.setText("Radar frame unavailable or older than 40 min");return;
        }
        Bitmap bitmap=BitmapFactory.decodeFile(image.getAbsolutePath());
        if(bitmap==null){radar.setImageDrawable(null);frameTime.setText("Could not decode radar image");return;}
        radar.setImageBitmap(bitmap);
        frameTime.setText((frame==0?"Latest available radar frame: ":"Previous radar frame: ")+
           DateTimeFormatter.ofPattern("dd MMM HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))+
           " · "+Math.max(0,age/60_000L)+" min age (frame generation time)");
    }
    private void refresh(){
        if(!prefs.hasLocation()){heading.setText("Select a city first");return;}
        heading.setText("Checking radar coverage and observation…");
        WeatherCoordinator.refresh(this,false,(f,message)->{
            if(isFinishing())return;display();
            if(f==null)detail.setText(message);
        });
    }
    private void showOfficial(){
        if(official==null){
            official=new WebView(this);official.setBackgroundColor(BG);
            official.getSettings().setJavaScriptEnabled(true);official.getSettings().setAllowFileAccess(false);
            official.getSettings().setDomStorageEnabled(false);
            official.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                    Uri u=r.getUrl();String host=u.getHost();
                    if("https".equals(u.getScheme())&&(host!=null)&&(host.equals("ffd.pmd.gov.pk")||host.equals("ffd.gov.pk")||host.equals("radar.weather.gov.pk")))return false;
                    open(u.toString());return true;
                }
            });
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(440));p.topMargin=dp(10);body.addView(official,p);
        }
        official.setVisibility(View.VISIBLE);
        official.loadUrl("https://ffd.pmd.gov.pk/radar?site=islamabad");
        Toast.makeText(this,"Official PMD page · check the frame timestamp",Toast.LENGTH_LONG).show();
    }
    @Override protected void onPause(){super.onPause();playing=false;handler.removeCallbacks(animation);if(official!=null)official.onPause();}
    @Override protected void onResume(){super.onResume();if(official!=null)official.onResume();display();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(official!=null){official.destroy();official=null;}super.onDestroy();}
}
