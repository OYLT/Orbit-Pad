package app.orbitpad.controller;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Native dialogs and widgets share runtime wallpaper and day/night color roles. */
final class ThemeDialogBuilder extends AlertDialog.Builder {
    private final MainActivity app;
    ThemeDialogBuilder(MainActivity app){super(app);this.app=app;}
    @Override public AlertDialog create(){AlertDialog dialog=super.create();dialog.setOnShowListener(d->style(app,dialog));return dialog;}
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    static void widgets(View view,ThemePalette theme){
        if(view instanceof CompoundButton){CompoundButton button=(CompoundButton)view;
            ColorStateList tint=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{theme.primary,theme.outline});
            button.setButtonTintList(tint);button.setTextColor(theme.onSurface);
            if(button instanceof Switch){((Switch)button).setThumbTintList(tint);((Switch)button).setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{theme.secondaryContainer,theme.outlineVariant}));}
        }
        if(view instanceof SeekBar){((SeekBar)view).setProgressTintList(ColorStateList.valueOf(theme.primary));((SeekBar)view).setThumbTintList(ColorStateList.valueOf(theme.primary));}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)widgets(group.getChildAt(i),theme);}
    }
    static void style(MainActivity app,AlertDialog dialog){
        if(dialog.getWindow()==null)return;
        dialog.getWindow().setBackgroundDrawable(app.rounded(app.theme.surfaceHigh,0,28));
        widgets(dialog.getWindow().getDecorView(),app.theme);
        TextView message=dialog.findViewById(android.R.id.message);if(message!=null)message.setTextColor(app.theme.onSurface);
        int titleId=app.getResources().getIdentifier("alertTitle","id","android");TextView title=dialog.findViewById(titleId);if(title!=null)title.setTextColor(app.theme.onSurface);
        for(int which:new int[]{-1,-2,-3})if(dialog.getButton(which)!=null)dialog.getButton(which).setTextColor(app.theme.primary);
    }
    private static int changedColor(int color,ThemePalette before,ThemePalette after){
        int[] old={before.background,before.surface,before.surfaceHigh,before.onSurface,before.muted,before.primary,before.onPrimary,before.primaryContainer,before.secondaryContainer,before.outline,before.outlineVariant};
        int[] next={after.background,after.surface,after.surfaceHigh,after.onSurface,after.muted,after.primary,after.onPrimary,after.primaryContainer,after.secondaryContainer,after.outline,after.outlineVariant};
        for(int i=0;i<old.length;i++)if((color&0xffffff)==(old[i]&0xffffff))return (color&0xff000000)|(next[i]&0xffffff);return color;
    }
    private static void recolorDrawable(android.graphics.drawable.Drawable background,ThemePalette before,ThemePalette after){
        if(background instanceof android.graphics.drawable.ColorDrawable){android.graphics.drawable.ColorDrawable fill=(android.graphics.drawable.ColorDrawable)background;fill.setColor(changedColor(fill.getColor(),before,after));}
        if(background instanceof android.graphics.drawable.GradientDrawable){android.graphics.drawable.GradientDrawable fill=(android.graphics.drawable.GradientDrawable)background;if(fill.getColor()!=null)fill.setColor(changedColor(fill.getColor().getDefaultColor(),before,after));}
        if(background instanceof android.graphics.drawable.LayerDrawable){android.graphics.drawable.LayerDrawable layers=(android.graphics.drawable.LayerDrawable)background;for(int i=0;i<layers.getNumberOfLayers();i++)recolorDrawable(layers.getDrawable(i),before,after);}
        if(background instanceof android.graphics.drawable.RippleDrawable)((android.graphics.drawable.RippleDrawable)background).setColor(ColorStateList.valueOf((after.primary&0xffffff)|0x33000000));
    }
    static void recolor(View view,ThemePalette before,ThemePalette after){
        if(view instanceof TextView){TextView text=(TextView)view;text.setTextColor(changedColor(text.getCurrentTextColor(),before,after));}
        recolorDrawable(view.getBackground(),before,after);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)recolor(group.getChildAt(i),before,after);}
        widgets(view,after);
    }
}
