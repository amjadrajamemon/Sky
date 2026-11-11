package com.skycast.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Three explicit rows: LIVE upper-left, TODAY hourly, and seven daily forecast cells. */
final class WidgetRenderer {
    private WidgetRenderer() {}

    static RemoteViews render(Context context, Forecast forecast, String city, WidgetSettings s,
                              boolean fahrenheit, boolean online, int preset, int width, int height) {
        RemoteViews out = new RemoteViews(context.getPackageName(), R.layout.widget_panel);
        int w=Math.max(150,width), h=Math.max(90,height);
        boolean compact=h<150;
        boolean tight=h<124;
        float scale=s.textSize()/100f * (tight ? .88f : compact ? .96f : 1f);
        int pad=s.density()==0?3:s.density()==1?5:7;
        out.setViewPadding(R.id.widget_content,dp(context,pad+2),dp(context,tight?2:pad),
                dp(context,pad+2),dp(context,tight?2:pad));
        out.setImageViewBitmap(R.id.widget_background, WidgetBackdropPainter.make(
                Math.max(110,Math.min(520,dp(context,w/1.4f))),
                Math.max(65,Math.min(340,dp(context,h/1.4f))),
                s.background(),s.accent(),s.foreground(),s.theme()));

        // Changing the theme changes the section construction as well as its color and artwork.
        int panel=s.theme()==1||s.theme()==9 ? 0x00ffffff
                : (s.theme()==8 ? 0x34270d54 : s.theme()==7 ? 0x3a000000 : 0x20000000);
        out.setInt(R.id.widget_today_section,"setBackgroundColor",panel);
        out.setInt(R.id.widget_week_section,"setBackgroundColor",panel);
        out.setViewPadding(R.id.widget_today_section,dp(context,s.theme()==9?1:3),0,dp(context,2),0);
        out.setViewPadding(R.id.widget_week_section,dp(context,s.theme()==9?1:3),0,dp(context,2),0);

        int mode=s.layout();
        boolean live=mode==WidgetSettings.DEFAULT||mode==WidgetSettings.CURRENT||mode==WidgetSettings.BOTH
                ||mode==WidgetSettings.TEMP_ONLY||mode==WidgetSettings.TEMP_RAIN
                ||mode==WidgetSettings.CURRENT_WEEK||mode==WidgetSettings.CURRENT_TODAY;
        boolean today=mode==WidgetSettings.DEFAULT||mode==WidgetSettings.BOTH||mode==WidgetSettings.TEMP_RAIN
                ||mode==WidgetSettings.TODAY_WEEK||mode==WidgetSettings.CURRENT_TODAY||mode==WidgetSettings.TODAY_ONLY;
        boolean week=mode==WidgetSettings.DEFAULT||mode==WidgetSettings.FORECAST||mode==WidgetSettings.BOTH
                ||mode==WidgetSettings.TODAY_WEEK||mode==WidgetSettings.CURRENT_WEEK;
        boolean has=forecast!=null;
        boolean icons=s.show("icons");
        boolean rain=s.show("rain");
        boolean chance=s.showOption("chance",true);
        boolean amount=s.showOption("amount",true);

        String title=s.text("title").trim();
        if(title.isEmpty()) {title=city==null?"":city;int comma=title.indexOf(',');if(comma>0)title=title.substring(0,comma).trim();}
        out.setTextViewText(R.id.widget_city,styled(title,s));
        visible(out,R.id.widget_city,s.show("location"));
        visible(out,R.id.widget_header,s.show("location")||s.showOption("settingsIcon",true));
        visible(out,R.id.widget_settings,s.showOption("settingsIcon",true));
        text(out,R.id.widget_city,s.foreground(),s.size("title",9),scale);
        text(out,R.id.widget_settings,s.accent(),13,1);

        visible(out,R.id.widget_message,!has);
        if(!has)out.setTextViewText(R.id.widget_message,styled(!online?"Offline · data unavailable":"Waiting for forecast",s));
        visible(out,R.id.widget_now,has&&live);
        visible(out,R.id.widget_live_label,s.showOption("liveLabel",true)&&mode!=WidgetSettings.TEMP_ONLY);
        visible(out,R.id.widget_temp,s.show("temperature"));
        visible(out,R.id.widget_condition,s.show("condition")&&mode!=WidgetSettings.TEMP_ONLY);
        visible(out,R.id.widget_icon,icons&&mode!=WidgetSettings.TEMP_ONLY);
        text(out,R.id.widget_live_label,s.accent(),s.size("liveLabel",8),scale);
        text(out,R.id.widget_temp,s.foreground(),s.size("temperature",tight?21:compact?25:30),scale);
        text(out,R.id.widget_condition,s.conditionColor(),s.size("condition",9),scale);
        if(has&&live){
            out.setTextViewText(R.id.widget_temp,styled(ForecastMath.temp(forecast.temperatureC,fahrenheit),s));
            out.setTextViewText(R.id.widget_condition,styled(forecast.conditionText.isEmpty()
                    ? ForecastMath.condition(forecast.weatherCode,forecast.day):forecast.conditionText,s));
            if(icons){
                int image=WidgetSizing.iconDimension(tight?21:compact?27:35,s.iconSize(),s.iconScale("live"),tight?29:41);
                applyIcon(out,R.id.widget_icon,context,forecast.weatherCode,forecast.day,image,41,s);
            }
        }

        // TODAY is a time series of hours remaining until local midnight, never a daily rain total.
        List<Forecast.Hour> hours=has ? TodayHours.remaining(forecast,Instant.now(),
                Math.max(3,Math.min(7,(w-44)/34))) : java.util.Collections.emptyList();
        visible(out,R.id.widget_today_section,has&&today);
        visible(out,R.id.widget_today_label,has&&today);
        out.setTextViewText(R.id.widget_today_label,styled("TODAY",s));
        text(out,R.id.widget_today_label,s.accent(),s.size("todayLabel",8),scale);
        out.removeAllViews(R.id.widget_hours);
        if(has&&today){
            if(hours.isEmpty()){
                RemoteViews empty=new RemoteViews(context.getPackageName(),R.layout.widget_hour_cell);
                empty.setTextViewText(R.id.hour_label,styled("No hours",s));
                visible(empty,R.id.hour_icon,false);
                visible(empty,R.id.hour_chance,false);
                visible(empty,R.id.hour_mm,false);
                out.addView(R.id.widget_hours,empty);
            }
            for(Forecast.Hour hour:hours){
                RemoteViews cell=new RemoteViews(context.getPackageName(),R.layout.widget_hour_cell);
                cell.setTextViewText(R.id.hour_label,styled(TodayHours.shortTime(hour.time),s));
                text(cell,R.id.hour_label,s.foreground(),s.size("hourName",8),scale);
                visible(cell,R.id.hour_icon,icons);
                if(icons){
                    int d=WidgetSizing.iconDimension(tight?12:compact?15:21,s.iconSize(),s.iconScale("hour"),tight?20:27);
                    applyIcon(cell,R.id.hour_icon,context,hour.weatherCode,hourTimeIsDay(hour.time),d,27,s);
                }
                visible(cell,R.id.hour_chance,rain&&chance);
                cell.setTextViewText(R.id.hour_chance,styled(RainBrief.percent(hour.rainProbability),s));
                text(cell,R.id.hour_chance,s.rainColor(),s.size("hourChance",8),scale);
                visible(cell,R.id.hour_mm,rain&&amount);
                cell.setTextViewText(R.id.hour_mm,styled(RainBrief.compactMm(hour.rainMm),s));
                text(cell,R.id.hour_mm,s.amountColor(),s.size("hourAmount",7),scale);
                out.addView(R.id.widget_hours,cell);
            }
        }

        int weekCount=has&&week?Math.min(s.days(),Math.min(7,forecast.days.size())):0;
        visible(out,R.id.widget_week_section,has&&weekCount>0);
        visible(out,R.id.widget_week_label,has&&weekCount>0);
        out.setTextViewText(R.id.widget_week_label,styled("7 DAYS",s));
        text(out,R.id.widget_week_label,s.dayColor(),s.size("weekLabel",8),scale);
        out.removeAllViews(R.id.widget_days);
        float adjusted=weekCount>=7&&w<285?.84f:1f;
        for(int n=0;n<weekCount;n++){
            int index=s.reverseWeek()?weekCount-n-1:n;
            Forecast.Day day=forecast.days.get(index);
            RemoteViews cell=new RemoteViews(context.getPackageName(),R.layout.weekly_day_cell);
            cell.setTextViewText(R.id.day_label,styled(shortDay(day.date),s));
            text(cell,R.id.day_label,s.foreground(),s.size("dayName",8),scale*adjusted);
            visible(cell,R.id.day_icon,icons);
            if(icons){
                int d=WidgetSizing.iconDimension(tight?12:compact?17:23,s.iconSize(),s.iconScale("week"),tight?20:29);
                applyIcon(cell,R.id.day_icon,context,day.weatherCode,true,d,29,s);
            }
            boolean temperatures=s.show("highsLows")&&(!tight||mode==WidgetSettings.FORECAST);
            visible(cell,R.id.day_temp,temperatures);
            cell.setTextViewText(R.id.day_temp,styled(ForecastMath.temp(day.highC,fahrenheit).replace("°","")
                    +"/"+ForecastMath.temp(day.lowC,fahrenheit),s));
            text(cell,R.id.day_temp,s.foreground(),s.size("dayTemp",8),scale*adjusted);
            visible(cell,R.id.day_rain,rain&&chance);
            cell.setTextViewText(R.id.day_rain,styled(RainBrief.percent(day.rainProbability),s));
            text(cell,R.id.day_rain,s.rainColor(),s.size("dayChance",8),scale*adjusted);
            visible(cell,R.id.day_mm,rain&&amount);
            cell.setTextViewText(R.id.day_mm,styled(RainBrief.compactMm(day.rainMm),s));
            text(cell,R.id.day_mm,s.amountColor(),s.size("dayAmount",8),scale*adjusted);
            out.addView(R.id.widget_days,cell);
        }

        // The fourth rain-alert strip is optional on a tall widget; never displace hourly/week rows on 4×2.
        boolean showAlert=has&&today&&rain&&h>=173&&s.showOption("rainHeadline",true);
        visible(out,R.id.widget_rain_panel,showAlert);
        visible(out,R.id.widget_alert_sub,showAlert&&s.showOption("timing",true));
        if(showAlert){
            out.setTextViewText(R.id.widget_alert_head,styled(RainBrief.today(forecast,chance,amount),s));
            out.setTextViewText(R.id.widget_alert_sub,styled(RainBrief.timing(forecast,Instant.now()),s));
            text(out,R.id.widget_alert_head,s.rainColor(),s.size("todayRain",13),scale);
            text(out,R.id.widget_alert_sub,s.amountColor(),s.size("rainTiming",9),scale);
        }
        String footer=footer(forecast,s,online);
        boolean footerVisible=has&&!footer.isEmpty()&&h>=187;
        visible(out,R.id.widget_footer,footerVisible);
        out.setTextViewText(R.id.widget_footer,styled(footer,s));
        text(out,R.id.widget_footer,s.conditionColor(),s.size("footer",8),scale);
        return out;
    }

    private static void applyIcon(RemoteViews views,int id,Context c,int code,boolean day,int actual,int container,WidgetSettings s){
        // Android 12+ can resize the actual RemoteViews control. Older launchers get a
        // fixed-size, transparent canvas with the correctly scaled art inside it.
        int box=android.os.Build.VERSION.SDK_INT>=31?actual:container;
        Bitmap art=WeatherIconPainter.make(code,day,Math.max(10,dp(c,actual)),s.iconStyle(),s.accent(),s.foreground());
        Bitmap fitted=Bitmap.createBitmap(dp(c,box),dp(c,box),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(fitted);
        float corner=(dp(c,box)-art.getWidth())/2f;
        canvas.drawBitmap(art,corner,corner,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        fitted.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        views.setImageViewBitmap(id,fitted);
        if(Build.VERSION.SDK_INT>=31){
            views.setViewLayoutHeight(id,actual,TypedValue.COMPLEX_UNIT_DIP);
            if(id==R.id.widget_icon)views.setViewLayoutWidth(id,actual,TypedValue.COMPLEX_UNIT_DIP);
        }
    }
    private static boolean hourTimeIsDay(String timestamp){
        java.time.LocalDateTime time=ForecastQuality.localTime(timestamp);
        return time==null||time.getHour()>=6&&time.getHour()<18;
    }
    private static String shortDay(String date){
        try{return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE",Locale.getDefault()));}
        catch(java.time.DateTimeException e){return "Day";}
    }
    private static int dp(Context c,float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    private static void visible(RemoteViews v,int id,boolean show){v.setViewVisibility(id,show?View.VISIBLE:View.GONE);}
    private static void text(RemoteViews v,int id,int color,float sp,float scale){
        v.setTextColor(id,color);v.setTextViewTextSize(id,TypedValue.COMPLEX_UNIT_SP,Math.max(6.4f,sp*scale));
    }
    private static CharSequence styled(String value,WidgetSettings s){
        String[] families={"sans-serif","sans-serif-rounded","sans-serif-condensed","serif","monospace"};
        SpannableString span=new SpannableString(value==null?"":value);
        if(span.length()>0)span.setSpan(new TypefaceSpan(families[s.font()]),0,span.length(),0);
        return span;
    }
    private static String footer(Forecast f,WidgetSettings s,boolean online){
        if(f==null)return "";
        StringBuilder b=new StringBuilder();
        if(s.showOption("source",false))b.append(WidgetSizing.source(f.source));
        if(s.showOption("updated",false)){if(b.length()>0)b.append(" · ");b.append(f.retrievedAt);}
        if(f.stale||!online){if(b.length()>0)b.append(" · ");b.append(!online?"Offline":"Cached");}
        return b.toString();
    }
}
