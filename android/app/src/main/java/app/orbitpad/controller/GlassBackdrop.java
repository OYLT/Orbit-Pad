package app.orbitpad.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
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

/** Records only the page into a GPU display list. The Dock is never a source. */
final class GlassBackdrop extends FrameLayout {
    final FrameLayout page;
    private final MainActivity app;
    private Backend backend;
    private boolean failed;
    private GlassDock dock;
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
        if(child!=page||!canvas.isHardwareAccelerated()||quality()==GlassQualityPolicy.SIMPLE)return super.drawChild(canvas,child,drawingTime);
        try{
            if(backend==null)backend=new Api31();
            backend.record(page);
            int save=canvas.save();canvas.translate(page.getLeft(),page.getTop());
            try{backend.drawPage(canvas);}finally{canvas.restoreToCount(save);}return true;
        }catch(RuntimeException error){disable(error);return super.drawChild(canvas,child,drawingTime);}
    }
    boolean drawGlass(Canvas canvas,View glass,float pressure){
        if(backend==null||!canvas.isHardwareAccelerated()||quality()==GlassQualityPolicy.SIMPLE)return false;
        try{
            // Sibling positions are in the same parent space, including system insets.
            backend.drawSurface(canvas,glass.getX()-page.getX(),glass.getY()-page.getY(),glass.getWidth(),glass.getHeight(),app.dp(28),app.dp(7),quality(),pressure,GlassQualityPolicy.refractionStrength(app),false);
            return true;
        }catch(RuntimeException error){disable(error);return false;}
    }
    boolean drawLens(Canvas canvas,View glass,RectF bounds,float pressure){
        if(backend==null||!canvas.isHardwareAccelerated()||quality()==GlassQualityPolicy.SIMPLE)return false;
        try{
            int save=canvas.save();
            try{
                canvas.translate(bounds.left,bounds.top);
                backend.drawSurface(canvas,glass.getX()-page.getX()+bounds.left,glass.getY()-page.getY()+bounds.top,(int)Math.ceil(bounds.width()),(int)Math.ceil(bounds.height()),app.dp(28),app.dp(2.5f),quality(),pressure,GlassQualityPolicy.refractionStrength(app),true);
            }finally{canvas.restoreToCount(save);}return true;
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
        void drawSurface(Canvas canvas,float x,float y,int width,int height,int padding,int blur,int quality,float pressure,float refraction,boolean bubble);
        void release();
    }
    /** These GPU resources are only created on Android 12 and newer. */
    private static final class Api31 implements Backend {
        private final RenderNode source=new RenderNode("Dock page source");
        private final Surface outer=new Surface("Frosted dock"),bubble=new Surface("Raised liquid lens");
        public void record(View page){
            source.setPosition(0,0,page.getWidth(),page.getHeight());Canvas recording=source.beginRecording();
            try{page.draw(recording);}finally{source.endRecording();}
        }
        public void drawPage(Canvas canvas){canvas.drawRenderNode(source);}
        public void drawSurface(Canvas canvas,float x,float y,int width,int height,int padding,int blur,int quality,float pressure,float refraction,boolean isBubble){
            if(!source.hasDisplayList())return;
            Surface surface=isBubble?bubble:outer;int w=width+2*padding,h=height+2*padding;
            surface.configure(w,h,padding,blur,quality,pressure,refraction,isBubble);
            surface.node.setPosition(0,0,w,h);Canvas recording=surface.node.beginRecording();
            try{recording.translate(padding-x,padding-y);recording.drawRenderNode(source);}finally{surface.node.endRecording();}
            int save=canvas.save();try{canvas.translate(-padding,-padding);canvas.drawRenderNode(surface.node);}finally{canvas.restoreToCount(save);}
        }
        public void release(){source.discardDisplayList();outer.node.discardDisplayList();bubble.node.discardDisplayList();}
        private static final class Surface {
            final RenderNode node;RenderEffect blurEffect;Api33 lens;
            int lastBlur=-1;
            Surface(String name){node=new RenderNode(name);}
            void configure(int width,int height,int padding,int blur,int quality,float pressure,float refraction,boolean bubble){
                if(blurEffect==null||lastBlur!=blur){
                    ColorMatrix color=new ColorMatrix();color.setSaturation(1.10f);
                    blurEffect=RenderEffect.createBlurEffect(blur,blur,RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(color)),Shader.TileMode.CLAMP);lastBlur=blur;
                }
                if(quality==GlassQualityPolicy.FULL&&Build.VERSION.SDK_INT>=33){
                    if(lens==null)lens=new Api33();
                    float strength=refraction*(bubble?3.2f:1),zoom=1+(bubble?(.025f+.12f*pressure)*refraction:0),dispersion=bubble?blur*.18f*pressure*refraction:0;
                    node.setRenderEffect(lens.effect(blurEffect,width,height,padding,blur,pressure,strength,zoom,dispersion,bubble?.24f:.13f));
                }else node.setRenderEffect(blurEffect);
            }
        }
    }
    /** Capsule edge normals drive refraction; the raised lens can magnify and split the rim. */
    private static final class Api33 {
        private final RuntimeShader shader=new RuntimeShader(
            "uniform shader backdrop; uniform float2 size; uniform float inset; uniform float strength; uniform float rimWidth; uniform float zoom; uniform float dispersion;"+
            "half4 main(float2 p){"+
            "float2 halfSize=(size-float2(2.0*inset))*0.5; float radius=min(halfSize.x,halfSize.y);"+
            "float2 local=p-size*0.5; float2 q=abs(local)-(halfSize-float2(radius));"+
            "float distance=length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;"+
            "float2 normal=normalize(max(q,0.0)*sign(local)+float2(0.0001));"+
            "float rim=1.0-smoothstep(0.0,rimWidth,-distance);"+
            "float2 uv=(p-size*0.5)/zoom+size*0.5-normal*(strength*rim*rim);"+
            "uv=clamp(uv,float2(0.5),size-float2(0.5));"+
            "half4 middle=backdrop.eval(uv);"+
            "half red=backdrop.eval(clamp(uv+normal*dispersion*rim,float2(0.5),size-float2(0.5))).r;"+
            "half blue=backdrop.eval(clamp(uv-normal*dispersion*rim,float2(0.5),size-float2(0.5))).b;"+
            "return half4(red,middle.g,blue,middle.a);}");
        private float lastPressure=-1,lastRefraction=-1,lastZoom=-1,lastDispersion=-1,lastRimFraction=-1;
        private int lastWidth,lastHeight;private RenderEffect cached,input;
        RenderEffect effect(RenderEffect blur,int width,int height,int padding,int radius,float pressure,float refraction,float zoom,float dispersion,float rimFraction){
            if(cached==null||input!=blur||lastWidth!=width||lastHeight!=height||lastPressure!=pressure||lastRefraction!=refraction||lastZoom!=zoom||lastDispersion!=dispersion||lastRimFraction!=rimFraction){
                shader.setFloatUniform("size",width,height);shader.setFloatUniform("inset",padding);
                shader.setFloatUniform("strength",radius*refraction*(.85f+pressure*.3f));shader.setFloatUniform("rimWidth",Math.max(radius*2.4f,(height-2*padding)*rimFraction));
                shader.setFloatUniform("zoom",zoom);shader.setFloatUniform("dispersion",dispersion);
                cached=RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"backdrop"),blur);
                input=blur;lastWidth=width;lastHeight=height;lastPressure=pressure;lastRefraction=refraction;lastZoom=zoom;lastDispersion=dispersion;lastRimFraction=rimFraction;
            }return cached;
        }
    }
}
