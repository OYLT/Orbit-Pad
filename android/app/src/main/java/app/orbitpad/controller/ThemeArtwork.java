package app.orbitpad.controller;

import android.graphics.*;
import android.view.View;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.TonalPalette;

/** Small vector illustrations; follow the active palette without bitmap assets. */
final class ThemeArtwork extends View {
    private final MainActivity app;
    private final boolean liquid;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    ThemeArtwork(MainActivity app,boolean liquid){super(app);this.app=app;this.liquid=liquid;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    static int[] tones(int seed){Hct h=Hct.fromInt(seed);return new int[]{TonalPalette.fromHueAndChroma(h.getHue(),36).tone(80),TonalPalette.fromHueAndChroma(h.getHue(),16).tone(60),TonalPalette.fromHueAndChroma(h.getHue()+60,24).tone(80)};}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float w=getWidth(),h=getHeight();int save=canvas.save();
        Path clip=new Path();clip.addRoundRect(new RectF(0,0,w,h),app.dp(18),app.dp(18),Path.Direction.CW);canvas.clipPath(clip);
        if(liquid){
            paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xff283b5e,0xff7086ab,0xffb5cbd9},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            paint.setColor(0xffadcddd);canvas.drawCircle(w*.12f,h*.25f,w*.6f,paint);paint.setColor(0xff667eb0);canvas.drawCircle(w*.98f,h*.8f,w*.68f,paint);
            paint.setShader(new LinearGradient(0,h*.25f,w,h*.75f,new int[]{0x88ffffff,0x126594d4,0x70ffffff},null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(w*.12f,h*.32f,w*.88f,h*.68f,h*.18f,h*.18f,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(app.dp(1));paint.setColor(0x99ffffff);canvas.drawRoundRect(w*.12f,h*.32f,w*.88f,h*.68f,h*.18f,h*.18f,paint);paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99ffffff);canvas.drawCircle(w*.38f,h*.5f,h*.12f,paint);paint.setColor(0xff365b87);canvas.drawCircle(w*.63f,h*.5f,h*.045f,paint);
        }else{
            ThemePalette t=app.theme;paint.setColor(t.surfaceHigh);canvas.drawRect(0,0,w,h,paint);
            float u=Math.min(w,h);paint.setColor(t.primary);canvas.drawRoundRect(w*.08f,h*.12f,w*.56f,h*.55f,u*.2f,u*.2f,paint);
            paint.setColor(t.tertiary);canvas.drawCircle(w*.78f,h*.35f,u*.19f,paint);
            paint.setColor(t.secondaryContainer);canvas.drawRoundRect(w*.08f,h*.64f,w*.44f,h*.88f,u*.12f,u*.12f,paint);
            paint.setColor(t.primaryContainer);canvas.drawRoundRect(w*.5f,h*.64f,w*.92f,h*.88f,u*.07f,u*.07f,paint);
            paint.setColor(t.onPrimary);paint.setStrokeWidth(app.dp(3));canvas.drawLine(w*.23f,h*.34f,w*.4f,h*.34f,paint);canvas.drawLine(w*.315f,h*.23f,w*.315f,h*.45f,paint);
        }
        canvas.restoreToCount(save);
    }
}
