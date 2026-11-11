package com.skycast.pro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native responsive main screen. No external UI frameworks, trackers or API keys. */
public final class MainActivity extends Activity {
    private int NAVY=0xff09172f,CARD=0xff142b50,INK=0xfff5faff,
            MUTED=0xffa8cef0,TEAL=0xff4de0c1,GOLD=0xffffd69c;
    private static final int[] BACKGROUNDS={0xff09172f,0xffe1f4fe,0xff0b111b,0xff05283d,0xff0b2821,0xff231b3c,0xfff3e3c0,0xff171c26,0xff08091d,0xfffcfaf6};
    private static final int[] PANELS={0xff142b50,0xffcbe7f9,0xff202932,0xff104565,0xff21473b,0xff413366,0xffe6d0a5,0xff2b3541,0xff24254c,0xfff0ebe4};
    private static final int[] TEXTS={0xfff5faff,0xff132d43,0xfff5f9ff,0xfff1fdff,0xfff0fff5,0xfffcf5ff,0xff33231a,0xfff2f7ff,0xfff8f8ff,0xff22252b};
    private static final int[] ACCENTS={0xff4de0c1,0xff126ea1,0xff85caef,0xff45aeea,0xff74dab0,0xffd4aaff,0xffb66d20,0xffc5dcef,0xfff633e6,0xffd75c38};
    private void applyTheme(int theme){
        NAVY=BACKGROUNDS[theme];CARD=PANELS[theme];INK=TEXTS[theme];TEAL=ACCENTS[theme];
        MUTED=blend(INK,TEAL,.50f);GOLD=blend(TEAL,INK,.27f);
    }
    private static int blend(int a,int b,float f){
        return 0xff000000|(Math.round(((a>>16)&255)*(1-f)+((b>>16)&255)*f)<<16)
                |(Math.round(((a>>8)&255)*(1-f)+((b>>8)&255)*f)<<8)
                |Math.round((a&255)*(1-f)+(b&255)*f);
    }
    private final java.util.concurrent.ExecutorService background=java.util.concurrent.Executors.newSingleThreadExecutor();
    private AppPrefs prefs;
    private LinearLayout root,currentHolder,forecastRow;
    private TextView status,locationLabel,temperatureLabel,conditionLabel,metricsLabel,rainHead,rainSub,sourceLabel,aqiLabel;
    private WeatherSceneView scene;
    private TextView comparisonLabel, nowcastHead, nowcastDetail, nowcastTrust;
    private Button refreshButton;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=new AppPrefs(this);applyTheme(prefs.appTheme());
        getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(NAVY);
        createUi();RefreshJob.schedule(this);render(new ForecastStore(this).load(),"");
        if(!prefs.hasLocation())locationDialog();
        else if(System.currentTimeMillis()-prefs.lastSuccess()>55*60_000L || new RainNowcastRepository(this,prefs).cached()==null ||
            !new RainNowcastRepository(this,prefs).cached().current(System.currentTimeMillis()))refresh(false);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(dp(radius));return gd;
    }
    private TextView label(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setIncludeFontPadding(true);return t;
    }
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private LinearLayout row(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);return c;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams cardParams(int top){LinearLayout.LayoutParams p=matchWrap();p.topMargin=dp(top);return p;}
    private Button button(String title,Runnable click){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(INK);b.setTextSize(12);b.setPadding(dp(4),0,dp(4),0);
        b.setMinHeight(dp(42));b.setBackground(bg(CARD,12));b.setOnClickListener(v->click.run());return b;
    }
    private void addButton(LinearLayout r,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1);p.setMargins(dp(2),dp(3),dp(2),dp(3));r.addView(b,p);}
    private void createUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(NAVY);
        root=column();root.setPadding(dp(16),dp(16),dp(16),dp(28));root.setFitsSystemWindows(true);
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
        root.addView(label("SkyCast Pro",25,INK,true));
        locationLabel=label("Location",15,MUTED,true);LinearLayout.LayoutParams lp=cardParams(16);root.addView(locationLabel,lp);
        LinearLayout controls=row();root.addView(controls,cardParams(5));
        refreshButton=button("↻ Refresh",()->refresh(true));addButton(controls,refreshButton);
        addButton(controls,button("⌖ Location",this::locationDialog));
        addButton(controls,button("≡ Source",this::sourceDialog));
        addButton(controls,button("⚙ Options",this::settingsDialog));
        status=label("Loading weather data…",11,MUTED,false);root.addView(status,cardParams(5));
        root.addView(button("Customize widgets",()->WidgetConfigActivity.openPicker(this)),cardParams(6));

        currentHolder=row();currentHolder.setPadding(dp(16),dp(10),dp(10),dp(10));
        currentHolder.setBackground(bg(CARD,18));root.addView(currentHolder,cardParams(12));
        LinearLayout left=column();currentHolder.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        temperatureLabel=label("—°",50,INK,true);left.addView(temperatureLabel);
        conditionLabel=label("Weather information",16,MUTED,false);left.addView(conditionLabel);
        metricsLabel=label("",12,MUTED,false);LinearLayout.LayoutParams met=cardParams(7);left.addView(metricsLabel,met);
        scene=new WeatherSceneView(this);
        int iconSize=getResources().getConfiguration().screenWidthDp<350?dp(108):dp(136);
        currentHolder.addView(scene,new LinearLayout.LayoutParams(iconSize,iconSize));

        LinearLayout rain=column();rain.setPadding(dp(15),dp(12),dp(15),dp(12));
        rain.setBackground(bg(0xff55374d,16));root.addView(rain,cardParams(11));
        rain.addView(label("RAIN OUTLOOK · NEXT 48 HOURS",12,GOLD,true));
        rainHead=label("—",16,INK,true);rain.addView(rainHead,cardParams(7));
        rainSub=label("Forecast period",12,GOLD,false);rain.addView(rainSub,cardParams(3));
        LinearLayout now=column();now.setPadding(dp(14),dp(12),dp(14),dp(12));
        now.setBackground(bg(CARD,16));root.addView(now,cardParams(11));
        now.addView(label("RAIN NOWCAST · NEXT 0–2 HOURS",12,TEAL,true));
        nowcastHead=label("Checking radar availability…",17,INK,true);now.addView(nowcastHead,cardParams(7));
        nowcastDetail=label("Radar motion · model · nearby airport observations",12,MUTED,false);now.addView(nowcastDetail,cardParams(4));
        nowcastTrust=label("Rain is an estimate. Check timestamps before relying on alerts.",11,MUTED,false);now.addView(nowcastTrust,cardParams(4));
        now.addView(button("Live radar preview & nowcast  ↗",()->startActivity(new Intent(this,RadarPreviewActivity.class))),cardParams(8));
        root.addView(label("DAILY FORECAST",15,INK,true),cardParams(20));
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);
        forecastRow=row();horizontal.addView(forecastRow,new ViewGroup.LayoutParams(-2,-2));root.addView(horizontal,cardParams(9));
        root.addView(label("AIR QUALITY & CONDITIONS",15,INK,true),cardParams(20));
        aqiLabel=label("Tap to check current model-based AQI",12,MUTED,false);root.addView(aqiLabel,cardParams(7));
        root.addView(button("Check AQI",this::loadAqi),cardParams(6));
        root.addView(label("FORECAST SOURCE & TRUST",15,INK,true),cardParams(20));
        sourceLabel=label("Select a forecast source above",12,MUTED,false);root.addView(sourceLabel,cardParams(7));
        root.addView(button("Compare "+WeatherApi.COMPARE_IDS.length+" forecast sources (on demand)",this::compareModels),cardParams(8));
        comparisonLabel=label("Model comparison not requested yet",12,MUTED,false);root.addView(comparisonLabel,cardParams(7));
        root.addView(label("CHECK PROVIDER WEBSITES",15,INK,true),cardParams(16));
        root.addView(button("Google Weather ↗",()->openProviderWebsite("google")),cardParams(6));
        root.addView(button("MSN Weather ↗",()->openProviderWebsite("msn")),cardParams(6));
        root.addView(button("AccuWeather ↗",()->openProviderWebsite("accuweather")),cardParams(6));
        root.addView(button("Open-Meteo data source & licence ↗",()->{
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/en/terms")));
        }),cardParams(6));
        root.addView(button("MET Norway data terms ↗",()->{
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://api.met.no/doc/TermsOfService")));
        }),cardParams(6));
        root.addView(button("Official Pakistan forecasts & warnings ↗",()->{
            Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://weather.gov.pk/"));startActivity(i);
        }),cardParams(8));
    }
    private void openProviderWebsite(String provider){
        if(!prefs.hasLocation()){locationDialog();return;}
        String coordinates=String.format(Locale.US,"%.4f,%.4f",prefs.lat(),prefs.lon());
        Uri url;
        if("msn".equals(provider))url=Uri.parse("https://www.msn.com/en-us/weather/forecast")
            .buildUpon().appendQueryParameter("latlong",coordinates).appendQueryParameter("weadegreetype",prefs.fahrenheit()?"F":"C").build();
        else if("accuweather".equals(provider))url=Uri.parse("https://www.accuweather.com/en/search-locations")
            .buildUpon().appendQueryParameter("query",prefs.gps()?coordinates:prefs.city()).build();
        else url=Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q","weather "+coordinates).build();
        try{startActivity(new Intent(Intent.ACTION_VIEW,url));}
        catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Install or enable a browser to view this provider",Toast.LENGTH_LONG).show();}
    }
    private void render(Forecast f,String error){
        locationLabel.setText(prefs.city());
        if(f==null){
            status.setText(error.isEmpty()?"No saved weather data. Tap Refresh.":"Unable to refresh: "+error);
            forecastRow.removeAllViews();return;
        }
        String updated=(f.stale?"STALE / CACHED · ":f.cached?"CACHED · ":"UPDATED · ")+f.retrievedAt;
        status.setText(updated+(error.isEmpty()?"":" · "+error));
        temperatureLabel.setText(ForecastMath.temp(f.temperatureC,prefs.fahrenheit()));
        conditionLabel.setText(f.conditionText.isEmpty()?ForecastMath.condition(f.weatherCode,f.day):f.conditionText);
        scene.setCondition(f.weatherCode,f.day);
        metricsLabel.setText("Feels "+ForecastMath.temp(f.feelsLikeC,prefs.fahrenheit())
            +" · Humidity "+(Double.isFinite(f.humidityPct)?Math.round(f.humidityPct)+"%":"—")
            +"\nWind "+(Double.isFinite(f.windKmh)?Math.round(f.windKmh)+" km/h":"—"));
        RainNowcast n=f.nowcast;
        if(n!=null){
            long now=System.currentTimeMillis();
            nowcastHead.setText((n.current(now)?"":"Older result · ")+n.headline);
            nowcastDetail.setText(n.detail);
            nowcastTrust.setText(n.evidence+" · "+n.radarState);
        }else{
            nowcastHead.setText("Radar and station observations not checked yet");
            nowcastDetail.setText("Use Refresh to request current sources");
            nowcastTrust.setText("No local nowcast evidence available");
        }
        ForecastMath.RainWindow rain=ForecastMath.rainWindow(f,java.time.Instant.now());
        rainHead.setText(f.hours.isEmpty()?"Hourly rain outlook unavailable for this source":rain.headline());
        rainSub.setText(f.hours.isEmpty()?"Hourly timing unavailable · check daily rain chances"
            :rain.period() + (f.stale?" · Older forecast":f.rainAgreement.isEmpty()?"":" · "+f.rainAgreement));
        sourceLabel.setText("Using: "+f.source+"\n"+(f.status.isEmpty()?"Model-based forecast; check PMD for official warnings.":f.status));
        forecastRow.removeAllViews();
        for(Forecast.Day d:f.days){
            LinearLayout cell=column();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(dp(9),dp(9),dp(9),dp(11));cell.setBackground(bg(CARD,13));
            LinearLayout.LayoutParams para=new LinearLayout.LayoutParams(dp(90),-2);para.rightMargin=dp(7);forecastRow.addView(cell,para);
            String day;try {day=LocalDate.parse(d.date).format(DateTimeFormatter.ofPattern("EEE d",Locale.getDefault()));}
            catch(Exception e){day=d.date;}
            TextView title=label(day,13,INK,true);title.setGravity(Gravity.CENTER);cell.addView(title);
            ImageView ico=new ImageView(this);
            int style=new int[]{4,8,9,4,5,3,7,6,3,10}[prefs.appTheme()];
            ico.setImageBitmap(WeatherIconPainter.make(d.weatherCode,true,dp(44),style,TEAL,INK));
            ico.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(ico,new LinearLayout.LayoutParams(dp(47),dp(47)));
            TextView temp=label(ForecastMath.temp(d.highC,prefs.fahrenheit())+" / "+ForecastMath.temp(d.lowC,prefs.fahrenheit()),12,INK,true);
            temp.setGravity(Gravity.CENTER);cell.addView(temp);
            String precip=d.rainProbability>=0?("☂ "+d.rainProbability+"%"):("☂ chance —");
            TextView prob=label(precip,12,TEAL,false);prob.setGravity(Gravity.CENTER);cell.addView(prob);
            String mm=Double.isFinite(d.rainMm)?String.format(Locale.US,"%.1f mm",d.rainMm):"Rain —";
            TextView amount=label(mm,11,MUTED,false);amount.setGravity(Gravity.CENTER);cell.addView(amount);
        }
    }
    private void refresh(boolean force){
        if(!prefs.hasLocation()){locationDialog();return;}
        refreshButton.setEnabled(false);status.setText("Checking available sources…");
        WeatherCoordinator.refresh(this,force,(f,msg)->{
            refreshButton.setEnabled(true);render(f,msg);
            if(f==null)Toast.makeText(this,"Forecast unavailable. Check network or source.",Toast.LENGTH_LONG).show();
        });
    }
    private void sourceDialog(){
        int idx=0;for(int i=0;i<WeatherApi.MODEL_IDS.length;i++)if(WeatherApi.MODEL_IDS[i].equals(prefs.model()))idx=i;
        new AlertDialog.Builder(this).setTitle("Choose forecast source")
            .setSingleChoiceItems(WeatherApi.MODEL_LABELS,idx,(d,which)->{
                prefs.setModel(WeatherApi.MODEL_IDS[which]);d.dismiss();refresh(true);
            }).setNegativeButton("Cancel",null).show();
    }
    private void settingsDialog(){
        LinearLayout col=column();col.setPadding(dp(20),dp(15),dp(20),0);
        CheckBox f=new CheckBox(this);f.setText("Use Fahrenheit");f.setChecked(prefs.fahrenheit());col.addView(f);
        CheckBox compare=new CheckBox(this);compare.setText("Compare independent sources in Smart Auto");compare.setChecked(prefs.compare());col.addView(compare);
        CheckBox radarOpt=new CheckBox(this);radarOpt.setText("Use keyless live radar and station observations");radarOpt.setChecked(prefs.radarEnabled());col.addView(radarOpt);
        CheckBox alerts=new CheckBox(this);alerts.setText("Optional rain forecast notifications");alerts.setChecked(prefs.rainAlerts());col.addView(alerts);
        TextView themeLabel=label("App-wide visual theme",12,INK,false);col.addView(themeLabel);
        Spinner theme=new Spinner(this);
        theme.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,WidgetSettings.THEMES));
        theme.setSelection(prefs.appTheme());col.addView(theme);
        TextView interval=label("Preferred background refresh (Android may delay)",12,INK,false);col.addView(interval);
        Spinner schedule=new Spinner(this);String[] opts={"15 minutes (rain priority)","30 minutes","60 minutes (recommended)","180 minutes (battery saving)"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts);schedule.setAdapter(adapter);
        schedule.setSelection(prefs.refreshMinutes()==15?0:prefs.refreshMinutes()==30?1:prefs.refreshMinutes()==180?3:2);col.addView(schedule);
        new AlertDialog.Builder(this).setTitle("Forecast settings").setView(col)
            .setPositiveButton("Apply",(d,w)->{
                int oldTheme=prefs.appTheme();prefs.setAppTheme(theme.getSelectedItemPosition());
                prefs.setFahrenheit(f.isChecked());prefs.setCompare(compare.isChecked());prefs.setRainAlerts(alerts.isChecked());prefs.setRadarEnabled(radarOpt.isChecked());
                prefs.setRefreshMinutes(schedule.getSelectedItemPosition()==0?15:schedule.getSelectedItemPosition()==1?30:schedule.getSelectedItemPosition()==3?180:60);
                RefreshJob.schedule(this);
                if(alerts.isChecked() && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},82);
                if (oldTheme!=prefs.appTheme()) {WeatherWidget.updateAll(this);recreate();}
                else {render(new ForecastStore(this).load(),"");refresh(false);}
            }).setNegativeButton("Cancel",null).show();
    }
    private void locationDialog(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setHint("e.g. Lahore or Islamabad");
        new AlertDialog.Builder(this).setTitle("Search any city").setView(input)
            .setPositiveButton("Search",(d,w)->searchCity(input.getText().toString().trim()))
            .setNeutralButton("Use device GPS",(d,w)->getDeviceLocation())
            .setNegativeButton("Cancel",null).show();
    }
    private void searchCity(String query){
        if(query.length()<2){Toast.makeText(this,"Enter at least 2 characters",Toast.LENGTH_SHORT).show();return;}
        status.setText("Searching places…");background.execute(()->{
            try{
                List<WeatherApi.Place> places=new WeatherApi(this).searchCities(query);
                runOnUiThread(()->{
                    if(places.isEmpty()){Toast.makeText(this,"No cities found",Toast.LENGTH_LONG).show();return;}
                    String[] names=new String[places.size()];for(int i=0;i<places.size();i++)names[i]=places.get(i).display();
                    new AlertDialog.Builder(this).setTitle("Choose precise location").setItems(names,(d,index)->{
                        WeatherApi.Place place=places.get(index);prefs.setLocation(place.lat,place.lon,place.name);prefs.setTimezone(place.timezone);refresh(true);
                    }).show();
                });
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"City lookup unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show());}
        });
    }
    private void getDeviceLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},81);return;
        }
        try{
            LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location l=manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if(l==null)l=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(l==null){Toast.makeText(this,"No recent GPS fix. Search by city instead.",Toast.LENGTH_LONG).show();return;}
            prefs.setGps(true);prefs.setLocation(l.getLatitude(),l.getLongitude(),"Device location");refresh(true);
        }catch(Exception err){Toast.makeText(this,"Location not available: "+err.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(req,permissions,grants);
        if(req==81){boolean granted=false;for(int x:grants)if(x==PackageManager.PERMISSION_GRANTED)granted=true;
            if(granted)getDeviceLocation();else Toast.makeText(this,"Use city search without location permission",Toast.LENGTH_LONG).show();}
    }
    private void compareModels(){
        if(!prefs.hasLocation()){locationDialog();return;}
        comparisonLabel.setText("Comparing independent models…");
        background.execute(()->{
            String[] models=WeatherApi.COMPARE_IDS;
            java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(4);
            java.util.List<java.util.concurrent.Callable<String>> tasks=new java.util.ArrayList<>();
            for(String model:models) tasks.add(()->{
                try{
                    WeatherApi api=new WeatherApi(this);
                    Forecast f=api.fetchModel(prefs.lat(),prefs.lon(),model,false);
                    String rain=f.days.isEmpty()?"—":(f.days.get(0).rainProbability>=0?f.days.get(0).rainProbability+"%":"unavailable");
                    return WeatherApi.modelLabel(model)+" · "+ForecastMath.temp(f.temperatureC,prefs.fahrenheit())
                        +" · rain chance "+rain+(f.stale?" · STALE":"")+"\n";
                }catch(Exception e){return WeatherApi.modelLabel(model)+" · unavailable ("+e.getMessage()+")\n";}
            });
            StringBuilder b=new StringBuilder();
            try{
                java.util.List<java.util.concurrent.Future<String>> outcomes=pool.invokeAll(tasks,25,java.util.concurrent.TimeUnit.SECONDS);
                for(java.util.concurrent.Future<String> f:outcomes){
                    try{b.append(f.isCancelled()?"Source timed out\n":f.get());}
                    catch(Exception e){b.append("Source error\n");}
                }
            }catch(Exception e){b.append("Comparison interrupted: "+e.getMessage());}
            finally{pool.shutdownNow();}
            b.append("\nForecast agreement ≠ verified accuracy. Open-Meteo models share a delivery platform.");
            runOnUiThread(()->comparisonLabel.setText(b.toString()));
        });
    }
    private void loadAqi(){
        if(!prefs.hasLocation()){locationDialog();return;}
        aqiLabel.setText("Checking air quality…");background.execute(()->{
            try{
                String v=new WeatherApi(this).fetchAqi(prefs.lat(),prefs.lon());runOnUiThread(()->aqiLabel.setText(v));
            }catch(Exception e){runOnUiThread(()->aqiLabel.setText("Air quality unavailable · "+e.getMessage()));}
        });
    }
    @Override protected void onDestroy(){background.shutdownNow();super.onDestroy();}
}
