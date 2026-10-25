package app.orbitpad.controller;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Native adapter for Kyant0/AndroidLiquidGlass, Apache-2.0. See third_party/AndroidLiquidGlass. */
final class KyantGlass {
    static final String REVISION="65ab177e90e5c1d8c62e70cf7755841982da65f6";
    static String asset(Context context,String path){
        try(InputStream input=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1)out.write(bytes,0,count);
            return out.toString("UTF-8");
        }catch(java.io.IOException error){throw new IllegalStateException("Missing glass asset: "+path,error);}
    }
    /** Instantiated only on API 33+. Original circle-map SDF lens and seven-sample dispersion. */
    static final class Lens {
        final RuntimeShader shader;
        private final boolean dispersion;
        private RenderEffect cached,input;
        private float lastX=Float.NaN,lastY,lastW,lastH,lastHeight,lastAmount,lastShear;
        Lens(Context context,boolean dispersion){
            this.dispersion=dispersion;
            shader=new RuntimeShader(asset(context,"liquidglass/"+(dispersion?"refraction_dispersion.agsl":"refraction.agsl")));
        }
        RenderEffect effect(RenderEffect input,RectF shape,float height,float amount,float shear){
            if(height<=0||amount<=0)return input;
            if(cached!=null&&this.input==input&&lastX==shape.left&&lastY==shape.top&&lastW==shape.width()&&lastH==shape.height()&&lastHeight==height&&lastAmount==amount&&lastShear==shear)return cached;
            float radius=Math.min(shape.width(),shape.height())*.5f;
            shader.setFloatUniform("size",shape.width(),shape.height());shader.setFloatUniform("offset",-shape.left,-shape.top);
            shader.setFloatUniform("cornerRadii",radius,radius,radius,radius);
            shader.setFloatUniform("refractionHeight",Math.min(height,radius));shader.setFloatUniform("refractionAmount",-amount);
            shader.setFloatUniform("depthEffect",0);shader.setFloatUniform("shear",shear);
            if(dispersion)shader.setFloatUniform("chromaticAberration",1);
            RenderEffect lens=RenderEffect.createRuntimeShaderEffect(shader,"content");
            cached=input==null?lens:RenderEffect.createChainEffect(lens,input);this.input=input;
            lastX=shape.left;lastY=shape.top;lastW=shape.width();lastH=shape.height();lastHeight=height;lastAmount=amount;lastShear=shear;
            return cached;
        }
    }
    /** Upstream normal-directed 45-degree highlight, limited to the capsule outline by the caller. */
    static final class Highlight {
        private final RuntimeShader shader;
        private final Matrix matrix=new Matrix();
        Highlight(Context context){shader=new RuntimeShader(asset(context,"liquidglass/highlight.agsl"));}
        Shader shader(RectF bounds,float shear){
            float radius=Math.min(bounds.width(),bounds.height())*.5f;
            shader.setFloatUniform("size",bounds.width(),bounds.height());shader.setFloatUniform("cornerRadii",radius,radius,radius,radius);
            shader.setColorUniform("color",0xffffffff);shader.setFloatUniform("angle",(float)Math.PI/4);shader.setFloatUniform("falloff",1);
            matrix.setTranslate(bounds.left,bounds.top);matrix.postSkew(shear,0,bounds.centerX(),bounds.centerY());shader.setLocalMatrix(matrix);return shader;
        }
    }
    private KyantGlass(){}
}
