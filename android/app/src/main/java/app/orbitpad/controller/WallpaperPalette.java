package app.orbitpad.controller;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import com.google.android.material.color.utilities.Hct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wallpaper IPC stays off the UI thread; OEMs may not publish Monet resource overlays. */
final class WallpaperPalette {
    private final MainActivity app;
    private final Handler main;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private WallpaperManager manager;
    private WallpaperManager.OnColorsChangedListener listener;
    private boolean closed;
    WallpaperPalette(MainActivity app,Handler main){
        this.app=app;this.main=main;
        if(Build.VERSION.SDK_INT>=27){
            manager=WallpaperManager.getInstance(app);
            listener=(colors,which)->{if((which&WallpaperManager.FLAG_SYSTEM)!=0)refresh();};
            try{manager.addOnColorsChangedListener(listener,main);}catch(RuntimeException ignored){}
        }
    }
    static int seed(WallpaperColors colors){
        Color[] candidates={colors.getPrimaryColor(),colors.getSecondaryColor(),colors.getTertiaryColor()};
        int selected=colors.getPrimaryColor().toArgb();double best=-1;
        for(Color color:candidates)if(color!=null){Hct h=Hct.fromInt(color.toArgb());double score=h.getChroma()-(h.getTone()<10||h.getTone()>95?30:0);if(score>best){best=score;selected=color.toArgb();}}
        return selected;
    }
    void refresh(){
        if(closed||manager==null)return;
        worker.execute(()->{
            try{
                int id=manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
                WallpaperColors colors=manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                main.post(()->{
                    if(closed)return;SharedPreferences prefs=app.getPreferences(0);
                    SharedPreferences.Editor edit=prefs.edit();
                    if(id!=prefs.getInt("wallpaper_id",-1))edit.remove("wallpaper_seed").remove("wallpaper_seed_source");
                    edit.putInt("wallpaper_id",id);
                    if(colors!=null&&!(id==prefs.getInt("wallpaper_id",-1)&&"image".equals(prefs.getString("wallpaper_seed_source",""))))edit.putInt("wallpaper_seed",seed(colors)).putString("wallpaper_seed_source","wallpaper");
                    edit.apply();ThemePalette next=new ThemePalette(app,prefs);if(!next.sameColors(app.theme))app.applyAppTheme();
                });
            }catch(RuntimeException ignored){/* Keep a valid system or user-selected palette. */}
        });
    }
    void close(){closed=true;if(manager!=null&&listener!=null)try{manager.removeOnColorsChangedListener(listener);}catch(RuntimeException ignored){}worker.shutdownNow();}
}
