package com.skycast.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** RainViewer past-only images, validated coverage, cache, native preview and optical flow input.
 *  No actual future radar frames are provided by its free API.
 */
public final class RadarNowcastClient {
    static final int GRID=41;
    private final Context context;
    private final AppPrefs prefs;
    public RadarNowcastClient(Context context,AppPrefs prefs){this.context=context.getApplicationContext();this.prefs=prefs;}
    public static final class Result {
        public boolean coverage,available;
        public String reason="Radar feed unavailable";
        public long latest;
        public RadarMotion.Motion motion;
    }
    public static File previewFile(Context context,int frame){return new File(context.getCacheDir(),frame==0?"skycast_radar_latest.png":"skycast_radar_previous.png");}
    static byte[] download(String address,int maxBytes)throws Exception {
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol()))throw new java.io.IOException("HTTPS required");
        String host=url.getHost();
        if(!"api.rainviewer.com".equals(host)&&!"tilecache.rainviewer.com".equals(host)&&!host.endsWith(".rainviewer.com"))
            throw new java.io.IOException("Unexpected radar host");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(4200);c.setReadTimeout(5200);
        c.setRequestProperty("User-Agent","SkyCastProNext/2.0 (https://github.com/amjadrajamemon/Sky)");
        try{
            if(c.getResponseCode()!=200)throw new java.io.IOException("Radar HTTP "+c.getResponseCode());
            ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            try(InputStream in=c.getInputStream()){
                while((n=in.read(buffer))!=-1){b.write(buffer,0,n);if(b.size()>maxBytes)throw new java.io.IOException("Radar payload limit");}
            }
            return b.toByteArray();
        }finally{c.disconnect();}
    }
    public Result fetch(double lat,double lon,long now){
        Result result=new Result();
        try {
            JSONObject manifest=new JSONObject(new String(download("https://api.rainviewer.com/public/weather-maps.json",400_000),StandardCharsets.UTF_8));
            String host=manifest.optString("host","");
            if(!host.startsWith("https://")||!host.matches("https://([A-Za-z0-9-]+\\.)*rainviewer\\.com/?"))
                throw new java.io.IOException("Untrusted radar tiles host");
            if(host.endsWith("/"))host=host.substring(0,host.length()-1);
            JSONArray frames=manifest.optJSONObject("radar")==null?null:manifest.optJSONObject("radar").optJSONArray("past");
            if(frames==null||frames.length()<2){result.reason="Not enough past radar frames";return result;}
            JSONObject last=frames.getJSONObject(frames.length()-1),before=frames.getJSONObject(frames.length()-2);
            long newest=last.optLong("time")*1000L,old=before.optLong("time")*1000L;
            long gap=Math.round((newest-old)/60000.0);
            result.latest=newest;
            if(newest>now+5*60_000L||now-newest>30*60_000L||gap<3||gap>30){result.reason="Radar frames are outdated or irregular";return result;}
            String center=String.format(Locale.US,"/512/7/%.4f/%.4f/",lat,lon);
            String covUrl=host+"/v2/coverage/0"+center+"0/0_0.png";
            Bitmap cov=decode(download(covUrl,1_200_000));
            if(cov==null){result.reason="Radar coverage map unavailable";return result;}
            result.coverage=covered(cov);
            cov.recycle();
            if(!result.coverage){result.reason="Radar does not verify coverage at this location";removeOldPreviews();return result;}
            String path1=path(last),path0=path(before);
            Bitmap latest=decode(download(host+path1+center+"2/0_0.png",1_800_000));
            Bitmap prev=decode(download(host+path0+center+"2/0_0.png",1_800_000));
            if(latest==null||prev==null||latest.getWidth()<256||prev.getWidth()<256){
                result.reason="Radar images unavailable";return result;
            }
            int[][] a=levels(prev),b=levels(latest);
            result.motion=RadarMotion.forecast(a,b,(int)gap);
            writeBitmap(previewFile(context,0),latest);
            writeBitmap(previewFile(context,1),prev);
            prefs.putLong("radar_preview_latest",newest);
            prefs.putLong("radar_preview_previous",old);
            prefs.put("radar_preview_location",locationKey(lat,lon));
            latest.recycle();prev.recycle();
            result.available=true;
            result.reason=result.motion.coherent?"Recent covered radar frames · motion extracted"
                :"Recent covered radar frames · motion uncertain";
        }catch(Exception e){result.reason="Radar temporarily unavailable: "+e.getMessage();}
        return result;
    }
    static String locationKey(double lat,double lon){return Math.round(lat*100)+":"+Math.round(lon*100);}
    private void removeOldPreviews(){previewFile(context,0).delete();previewFile(context,1).delete();prefs.put("radar_preview_location","");}
    private static String path(JSONObject frame)throws Exception {
        String p=frame.optString("path","");
        if(!p.matches("/v2/radar/[0-9A-Fa-f]{9,64}"))throw new java.io.IOException("Invalid radar frame path");
        return p;
    }
    private static Bitmap decode(byte[] b){return BitmapFactory.decodeByteArray(b,0,b.length);}
    private static void writeBitmap(File f,Bitmap img)throws Exception{
        try(FileOutputStream out=new FileOutputStream(f,false)){
            if(!img.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("Unable to save radar preview");
        }
    }
    /** Coverage mask: provider documents clear pixels as covered, black pixels as outside. */
    private static boolean covered(Bitmap image){
        int cx=image.getWidth()/2,cy=image.getHeight()/2,valid=0;
        for(int y=-10;y<=10;y+=5)for(int x=-10;x<=10;x+=5){
            int p=image.getPixel(Math.min(image.getWidth()-1,Math.max(0,cx+x)),Math.min(image.getHeight()-1,Math.max(0,cy+y)));
            if(Color.alpha(p)<30)valid++;
        }
        return valid>=18;
    }
    private static int[][] levels(Bitmap image){
        int[][] values=new int[GRID][GRID];
        int w=image.getWidth(),h=image.getHeight();
        int stride=Math.min(w,h)/GRID;
        for(int y=0;y<GRID;y++)for(int x=0;x<GRID;x++){
            int cx=w/2+(x-GRID/2)*stride,cy=h/2+(y-GRID/2)*stride;
            int pixels=0,sum=0,max=0;
            for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){
                int px=Math.min(w-1,Math.max(0,cx+ox*2));int py=Math.min(h-1,Math.max(0,cy+oy*2));
                int c=image.getPixel(px,py);int alpha=Color.alpha(c);
                if(alpha>50 && (Color.red(c)+Color.green(c)+Color.blue(c)>85)){
                    pixels++;sum+=Math.max(Color.red(c),Math.max(Color.green(c),Color.blue(c)));max=Math.max(max,alpha);
                }
            }
            if(pixels>=2){int strength=sum/pixels;values[y][x]=strength>235&&max>220?4:strength>175?3:strength>105?2:1;}
        }
        return values;
    }
}
