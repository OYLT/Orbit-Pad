package app.orbitpad.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.TonalPalette;

/** Shared day/night color roles; Material palettes use HCT and live wallpaper colors. */
final class ThemePalette {
    static final String[] IDS={"lilac","ocean","jade","rose","sand","slate"};
    static final String[] NAMES={"鸢尾紫","海盐蓝","青玉绿","蔷薇粉","暖沙金","雾岩灰"};
    static final int[] SEEDS={0xff6750a4,0xff0061a4,0xff006c4c,0xff984061,0xff825500,0xff5c6070};
    final boolean material,dynamic,dark;
    final String appearance,dynamicSource;
    final String preset;
    int background=0xff141218,onSurface=0xffe6e0e9,muted=0xffcac4d0,
        surface=0xff211f26,surfaceHigh=0xff2b2930,primary=0xffd0bcff,
        onPrimary=0xff381e72,primaryContainer=0xff4f378b,secondaryContainer=0xff4a4458,
        tertiary=0xffefb8c8,outline=0xff938f99,outlineVariant=0xff49454f;

    ThemePalette(Context context,SharedPreferences preferences){
        material="material3".equals(preferences.getString("app_design","liquid"));
        String savedPreset=preferences.getString("monet_preset","lilac");boolean known=false;for(String id:IDS)if(id.equals(savedPreset))known=true;preset=known?savedPreset:"lilac";
        dynamic=preferences.getBoolean("monet_dynamic",false)&&Build.VERSION.SDK_INT>=31;
        appearance=preferences.getString("appearance_mode","system");
        dark="dark".equals(appearance)||(!"light".equals(appearance)&&(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        boolean wallpaper=dynamic&&preferences.contains("wallpaper_seed");
        dynamicSource=wallpaper?preferences.getString("wallpaper_seed_source","wallpaper"):"system";

        int seed=SEEDS[0];for(int i=0;i<IDS.length;i++)if(IDS[i].equals(preset))seed=SEEDS[i];
        if(wallpaper)seed=preferences.getInt("wallpaper_seed",seed);
        Hct source=Hct.fromInt(seed);double hue=source.getHue();
        TonalPalette p=TonalPalette.fromHueAndChroma(hue,36),s=TonalPalette.fromHueAndChroma(hue,16),
            t=TonalPalette.fromHueAndChroma(hue+60,24),n=TonalPalette.fromHueAndChroma(hue,6),nv=TonalPalette.fromHueAndChroma(hue,8);
        background=n.tone(dark?6:98);surface=n.tone(dark?12:94);surfaceHigh=n.tone(dark?17:92);onSurface=n.tone(dark?90:10);muted=nv.tone(dark?80:30);
        primary=p.tone(dark?80:40);onPrimary=p.tone(dark?20:100);primaryContainer=p.tone(dark?30:90);secondaryContainer=s.tone(dark?30:90);
        tertiary=t.tone(dark?80:40);outline=nv.tone(dark?60:50);outlineVariant=nv.tone(dark?30:80);
        if(dynamic&&!wallpaper&&material){
            // Read the actual system palettes: this respects the user's wallpaper and system style.
            primary=context.getColor((dark?android.R.color.system_accent1_200:android.R.color.system_accent1_600));
            onPrimary=context.getColor((dark?android.R.color.system_accent1_800:android.R.color.system_accent1_0));
            primaryContainer=context.getColor((dark?android.R.color.system_accent1_700:android.R.color.system_accent1_100));
            secondaryContainer=context.getColor((dark?android.R.color.system_accent2_700:android.R.color.system_accent2_100));
            tertiary=context.getColor((dark?android.R.color.system_accent3_200:android.R.color.system_accent3_600));
            onSurface=context.getColor((dark?android.R.color.system_neutral1_100:android.R.color.system_neutral1_900));
            muted=context.getColor((dark?android.R.color.system_neutral2_200:android.R.color.system_neutral2_700));
            outline=context.getColor((dark?android.R.color.system_neutral2_400:android.R.color.system_neutral2_500));
            outlineVariant=context.getColor((dark?android.R.color.system_neutral2_700:android.R.color.system_neutral2_200));
            // Neutral tones 6/12/17 are not exported before API 34; derive them from the system neutral hue/chroma.
            Hct neutral=Hct.fromInt(context.getColor(android.R.color.system_neutral1_500));
            n=TonalPalette.fromHueAndChroma(neutral.getHue(),neutral.getChroma());
            background=n.tone(dark?6:98);surface=n.tone(dark?12:94);surfaceHigh=n.tone(dark?17:92);
        }
        if(!material&&dark){background=0xff141218;onSurface=0xffe6e0e9;muted=0xffcac4d0;surface=0xff211f26;surfaceHigh=0xff2b2930;primary=0xffd0bcff;onPrimary=0xff381e72;primaryContainer=0xff4f378b;secondaryContainer=0xff4a4458;tertiary=0xffefb8c8;outline=0xff938f99;outlineVariant=0xff49454f;}
    }
    int color(int legacy){
        if(!material&&dark)return legacy;
        int role;
        switch(legacy&0xffffff){
            case 0x141218:role=background;break;
            case 0xe6e0e9:role=onSurface;break;
            case 0xcac4d0:role=muted;break;
            case 0xd0bcff:case 0x65b5ff:case 0xa8c7fa:role=primary;break;
            case 0x381e72:role=onPrimary;break;
            case 0x4f378b:case 0x4f3b67:role=primaryContainer;break;
            case 0x4a4458:role=secondaryContainer;break;
            case 0x211f26:case 0x25212c:role=surface;break;
            case 0x2b2930:case 0x37313e:role=surfaceHigh;break;
            case 0x938f99:role=outline;break;
            case 0x38333f:case 0x494250:case 0x51445f:case 0x665b72:role=outlineVariant;break;
            default:return legacy;
        }
        return (legacy&0xff000000)|(role&0xffffff);
    }
    void applyPlatform(Context context){
        context.getTheme().applyStyle(dark?R.style.AppTheme:R.style.AppThemeLight,true);
        String name=(dynamic&&"system".equals(dynamicSource)&&material?"ThemeMonetDynamic":"ThemeMonet_"+preset)+(dark?"":"Light");
        int id=context.getResources().getIdentifier(name,"style",context.getPackageName());
        if(id!=0)context.getTheme().applyStyle(id,true);
    }
    boolean sameColors(ThemePalette other){return material==other.material&&dark==other.dark&&appearance.equals(other.appearance)&&dynamicSource.equals(other.dynamicSource)&&dynamic==other.dynamic&&preset.equals(other.preset)&&primary==other.primary&&onPrimary==other.onPrimary&&primaryContainer==other.primaryContainer&&secondaryContainer==other.secondaryContainer&&tertiary==other.tertiary&&background==other.background&&surface==other.surface&&surfaceHigh==other.surfaceHigh&&onSurface==other.onSurface&&muted==other.muted&&outline==other.outline&&outlineVariant==other.outlineVariant;}
}
