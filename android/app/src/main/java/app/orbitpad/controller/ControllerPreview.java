package app.orbitpad.controller;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;

/** Each card owns an immutable resolved layout; drawing never touches the active controller. */
final class ControllerPreview extends View {
    private final JSONArray layout;
    private final boolean gyro;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Drawable icon;
    private Bitmap fallbackBlur;
    private float softness;
    ControllerPreview(MainActivity app,JSONObject settings,String name){
        super(app);layout=app.layoutFor(settings);gyro=settings.optBoolean("gyro_enabled");icon=app.getDrawable(R.drawable.ic_launcher);
        setContentDescription(name+" · 已保存手柄样式");setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void softness(float value){softness=Math.max(0,Math.min(1,value));if(Build.VERSION.SDK_INT<31)invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        if(fallbackBlur!=null){fallbackBlur.recycle();fallbackBlur=null;}
        if(Build.VERSION.SDK_INT<31&&w>0&&h>0){
            int bw=Math.min(240,w),bh=Math.max(1,Math.round(h*bw/(float)w));Bitmap bitmap=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
            drawLayout(new Canvas(bitmap),bw,bh);int[] pixels=new int[bw*bh];bitmap.getPixels(pixels,0,bw,0,0,bw,bh);
            bitmap.setPixels(gaussian(pixels,bw,bh,2.2f),0,bw,0,0,bw,bh);fallbackBlur=bitmap;
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);drawLayout(canvas,getWidth(),getHeight());
        if(Build.VERSION.SDK_INT<31&&softness>0&&fallbackBlur!=null){paint.setAlpha(Math.round(255*softness));canvas.drawBitmap(fallbackBlur,null,new Rect(0,0,getWidth(),getHeight()),paint);paint.setAlpha(255);}
    }
    private void drawLayout(Canvas canvas,int width,int height){
        float scale=Math.min(width/1000f,height/450f),ox=(width-scale*1000)/2,oy=(height-scale*450)/2;
        canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scale);paint.setAlpha(255);
        for(int i=0;i<layout.length();i++){
            JSONObject b=layout.optJSONObject(i);if(b==null)continue;String id=b.optString("id");
            if(!b.optBoolean("visible",true)||id.equals("settings")||(id.equals("calibrate")&&!gyro))continue;
            float x=(float)b.optDouble("x"),y=(float)b.optDouble("y"),w=(float)b.optDouble("width"),h=(float)b.optDouble("height");
            boolean stick=id.equals("move")||id.equals("look");
            paint.setColor(0xff37313e);paint.setStyle(Paint.Style.FILL);float corner=stick?Math.min(w,h)/2:Math.min(Math.min(w,h)/2,id.length()==1?200:18);
            canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,corner,corner,paint);
            if(stick){paint.setColor(0xff938f99);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);canvas.drawCircle(x,y,Math.min(w,h)/2-1,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0xffd0bcff);canvas.drawCircle(x,y,Math.min(w,h)*.19f,paint);}
            if(id.equals("xbox")){int d=Math.round(Math.min(w,h)*.72f);icon.setBounds(Math.round(x-d/2f),Math.round(y-d/2f),Math.round(x+d/2f),Math.round(y+d/2f));icon.draw(canvas);}
            else{paint.setColor(stick?0xff381e72:0xffe6e0e9);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setTextSize(Math.min(22,Math.min(w,h)*.32f));canvas.drawText(b.optString("label"),x,y-(paint.ascent()+paint.descent())/2,paint);}
        }canvas.restore();
    }
    // A small, cached separable Gaussian keeps the transition available on Android 8–11.
    static int[] gaussian(int[] source,int width,int height,float sigma){
        int radius=(int)Math.ceil(sigma*3);float[] kernel=new float[radius*2+1];float total=0;
        for(int i=-radius;i<=radius;i++){float weight=(float)Math.exp(-i*i/(2*sigma*sigma));kernel[i+radius]=weight;total+=weight;}for(int i=0;i<kernel.length;i++)kernel[i]/=total;
        int[] temp=new int[source.length],output=new int[source.length];
        for(int pass=0;pass<2;pass++){
            int[] input=pass==0?source:temp,dest=pass==0?temp:output;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                float a=0,r=0,g=0,b=0;for(int n=-radius;n<=radius;n++){
                    int px=pass==0?Math.max(0,Math.min(width-1,x+n)):x,py=pass==0?y:Math.max(0,Math.min(height-1,y+n));
                    int color=input[py*width+px];float weight=kernel[n+radius];a+=(color>>>24)*weight;r+=((color>>>16)&255)*weight;g+=((color>>>8)&255)*weight;b+=(color&255)*weight;
                }dest[y*width+x]=(Math.round(a)<<24)|(Math.round(r)<<16)|(Math.round(g)<<8)|Math.round(b);
            }
        }return output;
    }
}
