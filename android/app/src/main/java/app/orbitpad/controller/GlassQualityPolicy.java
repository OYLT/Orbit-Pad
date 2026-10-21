package app.orbitpad.controller;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/** API support is a ceiling; battery saver and the user's choice can lower it. */
final class GlassQualityPolicy {
    static final int SIMPLE=0, STANDARD=1, FULL=2;
    static int resolve(MainActivity app) {
        PowerManager power=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
        ActivityManager memory=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
        String choice=app.getPreferences(0).getString("glass_quality","auto");
        if("simple".equals(choice)||(power!=null&&power.isPowerSaveMode())||(memory!=null&&memory.isLowRamDevice()))return SIMPLE;
        if(Build.VERSION.SDK_INT<31)return SIMPLE;
        return Build.VERSION.SDK_INT>=33&&!"standard".equals(choice)?FULL:STANDARD;
    }
    static int refractionLevel(MainActivity app){
        return Math.max(0,Math.min(3,app.getPreferences(0).getInt("glass_refraction",2)));
    }
    static float refractionStrength(MainActivity app){
        switch(refractionLevel(app)){case 0:return 0;case 1:return .5f;case 3:return 1.6f;default:return 1;}
    }
    private GlassQualityPolicy(){}
}
