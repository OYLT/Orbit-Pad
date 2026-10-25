package app.orbitpad.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

/** Page display list shared by the Dock and the scroll-dependent status-bar veil. */
final class GlassBackdrop extends FrameLayout {
    final FrameLayout page;
    private final MainActivity app;
    private Backend backend;
    private boolean failed;
    private GlassDock dock;
    private float topFade;
    private int topHeight;
    private final Paint veilPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient veilMask,veilTint;
    private final PorterDuffXfermode veilBlend=new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    void setTopFade(int inset,float amount){
        int height=inset+app.dp(56);amount=Math.max(0,Math.min(1,amount));
        if(topHeight==height&&topFade==amount)return;
        if(topHeight!=height){
            topHeight=height;
            veilMask=new LinearGradient(0,0,0,height,new int[]{0xffffffff,0xf2ffffff,0x00ffffff},new float[]{0,Math.min(.72f,(inset+app.dp(8))/(float)height),1},Shader.TileMode.CLAMP);
            veilTint=new LinearGradient(0,0,0,height,new int[]{0xb3141218,0x60141218,0x00141218},new float[]{0,.48f,1},Shader.TileMode.CLAMP);
        }
        topFade=amount;invalidate();
    }
    private void drawTopVeil(Canvas canvas){
        if(topFade<=0||topHeight<=0)return;
        int save=canvas.save();canvas.clipRect(0,0,getWidth(),topHeight);
        if(backend!=null&&!failed&&canvas.isHardwareAccelerated()&&quality()!=GlassQualityPolicy.SIMPLE){
            int layer=canvas.saveLayerAlpha(0,0,getWidth(),topHeight,(int)(255*topFade));
            try{
                backend.drawTop(canvas,getWidth(),topHeight,app.dp(16));
                veilPaint.setShader(veilMask);veilPaint.setAlpha(255);veilPaint.setXfermode(veilBlend);
                canvas.drawRect(0,0,getWidth(),topHeight,veilPaint);
            }catch(RuntimeException error){disable(error);}
            finally{veilPaint.setXfermode(null);canvas.restoreToCount(layer);}
        }
        veilPaint.setShader(veilTint);veilPaint.setAlpha((int)(255*topFade));canvas.drawRect(0,0,getWidth(),topHeight,veilPaint);
        veilPaint.setShader(null);canvas.restoreToCount(save);
    }
    private final BroadcastReceiver powerChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){invalidate();if(dock!=null)dock.invalidate();}
    };
    GlassBackdrop(MainActivity app){
        super(app);this.app=app;setBackgroundColor(0xff141218);setClipChildren(false);setClipToPadding(false);
        page=new FrameLayout(app);page.setBackgroundColor(0xff141218);
        addView(page,new FrameLayout.LayoutParams(-1,-1));
    }
    void setDock(GlassDock dock){this.dock=dock;}
    int quality(){return failed?GlassQualityPolicy.SIMPLE:GlassQualityPolicy.resolve(app);}
    @Override protected boolean drawChild(Canvas canvas,View child,long drawingTime){
        if(child!=page)return super.drawChild(canvas,child,drawingTime);
        boolean drawn=false;
        if(canvas.isHardwareAccelerated()&&quality()!=GlassQualityPolicy.SIMPLE){
            try{
                if(backend==null)backend=new Api31(app);backend.record(page);
                int save=canvas.save();canvas.translate(page.getLeft(),page.getTop());
                try{backend.drawPage(canvas);}finally{canvas.restoreToCount(save);}drawn=true;
            }catch(RuntimeException error){disable(error);}
        }
        if(!drawn)super.drawChild(canvas,child,drawingTime);
        drawTopVeil(canvas);return true;
    }
    boolean drawBackdropSource(Canvas canvas,View glass){
        if(backend==null||!canvas.isHardwareAccelerated()||quality()==GlassQualityPolicy.SIMPLE)return false;
        int save=canvas.save();
        try{canvas.translate(page.getX()-glass.getX(),page.getY()-glass.getY());backend.drawPage(canvas);return true;}
        finally{canvas.restoreToCount(save);}
    }
    boolean drawGlass(Canvas canvas,View glass,float pressure){
        if(backend==null||!canvas.isHardwareAccelerated()||quality()==GlassQualityPolicy.SIMPLE)return false;
        try{
            // Sibling positions are in the same parent space, including system insets.
            backend.drawSurface(canvas,glass.getX()-page.getX(),glass.getY()-page.getY(),glass.getWidth(),glass.getHeight(),app.dp(28),app.dp(8),quality(),pressure,GlassQualityPolicy.refractionStrength(app));
            return true;
        }catch(RuntimeException error){disable(error);return false;}
    }
    private void disable(RuntimeException error){failed=true;Log.w("GlassDock","Falling back to a simple surface",error);invalidate();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();
        IntentFilter filter=new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)getContext().registerReceiver(powerChanges,filter,Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(powerChanges,filter);
    }
    @Override protected void onDetachedFromWindow(){getContext().unregisterReceiver(powerChanges);if(backend!=null){backend.release();backend=null;}super.onDetachedFromWindow();}
    private interface Backend {
        void record(View page);void drawPage(Canvas canvas);
        void drawTop(Canvas canvas,int width,int height,int blur);
        void drawSurface(Canvas canvas,float x,float y,int width,int height,int padding,int blur,int quality,float pressure,float refraction);
        void release();
    }
    /** These GPU resources are only created on Android 12 and newer. */
    private static final class Api31 implements Backend {
        private final RenderNode source=new RenderNode("Dock page source"),top=new RenderNode("Top gradient Gaussian blur");
        private int topBlur=-1;
        private final Surface outer;
        Api31(Context context){outer=new Surface(context,"Kyant frosted dock");}
        public void record(View page){
            source.setPosition(0,0,page.getWidth(),page.getHeight());Canvas recording=source.beginRecording();
            try{page.draw(recording);}finally{source.endRecording();}
        }
        public void drawPage(Canvas canvas){canvas.drawRenderNode(source);}
        public void drawTop(Canvas canvas,int width,int height,int blur){
            if(topBlur!=blur){top.setRenderEffect(RenderEffect.createBlurEffect(blur,blur,Shader.TileMode.CLAMP));topBlur=blur;}
            top.setPosition(0,0,width,height+blur*3);Canvas recording=top.beginRecording();
            try{recording.drawRenderNode(source);}finally{top.endRecording();}canvas.drawRenderNode(top);
        }
        public void drawSurface(Canvas canvas,float x,float y,int width,int height,int padding,int blur,int quality,float pressure,float refraction){
            if(!source.hasDisplayList())return;
            Surface surface=outer;int w=width+2*padding,h=height+2*padding;
            surface.configure(w,h,padding,blur,quality,refraction);
            surface.node.setPosition(0,0,w,h);Canvas recording=surface.node.beginRecording();
            try{recording.translate(padding-x,padding-y);recording.drawRenderNode(source);}finally{surface.node.endRecording();}
            int save=canvas.save();try{canvas.translate(-padding,-padding);canvas.drawRenderNode(surface.node);}finally{canvas.restoreToCount(save);}
        }
        public void release(){source.discardDisplayList();top.discardDisplayList();outer.node.discardDisplayList();}
        private static final class Surface {
            final RenderNode node;final Context context;final RectF shape=new RectF();RenderEffect blurEffect;KyantGlass.Lens lens;
            int lastBlur=-1;
            Surface(Context context,String name){this.context=context;node=new RenderNode(name);}
            void configure(int width,int height,int padding,int blur,int quality,float refraction){
                if(blurEffect==null||lastBlur!=blur){
                    ColorMatrix color=new ColorMatrix();color.setSaturation(1.5f);
                    blurEffect=RenderEffect.createBlurEffect(blur,blur,RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(color)),Shader.TileMode.CLAMP);lastBlur=blur;
                }
                if(quality==GlassQualityPolicy.FULL&&Build.VERSION.SDK_INT>=33){
                    if(lens==null)lens=new KyantGlass.Lens(context,false);
                    float density=context.getResources().getDisplayMetrics().density;
                    shape.set(padding,padding,width-padding,height-padding);
                    node.setRenderEffect(lens.effect(blurEffect,shape,density*24,density*24*refraction,0));
                }else node.setRenderEffect(blurEffect);
            }
        }
    }
}
