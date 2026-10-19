package app.orbitpad.controller;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera is opened only in this foreground activity and is released on pause. */
public final class ScanActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView preview;
    private TextView hint;
    private Camera camera;
    private boolean foreground,surface;
    private int generation;
    private long lastFrame;
    private final AtomicBoolean decoding=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(0xff141218);getWindow().setNavigationBarColor(0xff141218);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff141218);
        preview=new SurfaceView(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));preview.getHolder().addCallback(this);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(24,32,24,20);header.setBackgroundColor(0xcc141218);
        TextView title=new TextView(this);title.setText("扫描电脑二维码");title.setTextSize(23);title.setTextColor(0xffe6e0e9);header.addView(title);
        hint=new TextView(this);hint.setText("对准 Orbit Pad 接收端二维码，将自动配对");hint.setTextSize(14);hint.setTextColor(0xffd0bcff);header.addView(hint);
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(header,top);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(20,16,20,28);actions.setBackgroundColor(0xcc141218);
        Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());actions.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        Button photo=new Button(this);photo.setText("从图片识别");photo.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,9);});actions.addView(photo,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);
        if(checkSelfPermission("android.permission.CAMERA")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.CAMERA"},8);
    }
    @Override protected void onResume(){super.onResume();foreground=true;openCamera();}
    @Override protected void onPause(){foreground=false;generation++;releaseCamera();super.onPause();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==8&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)openCamera();else hint.setText("相机权限未授予，可以返回手动配对或从图片识别");}
    @Override public void surfaceCreated(SurfaceHolder holder){surface=true;openCamera();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){surface=false;releaseCamera();}
    private void releaseCamera(){if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();}catch(Exception ignored){}camera.release();camera=null;}}
    private void openCamera(){
        if(!foreground||!surface||camera!=null||checkSelfPermission("android.permission.CAMERA")!=PackageManager.PERMISSION_GRANTED)return;
        try{
            int chosen=-1;Camera.CameraInfo info=new Camera.CameraInfo();for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){chosen=i;break;}}
            if(chosen<0)throw new Exception("未找到后置相机");Camera.getCameraInfo(chosen,info);camera=Camera.open(chosen);
            Camera.Parameters params=camera.getParameters();Camera.Size best=null;for(Camera.Size s:params.getSupportedPreviewSizes())if(s.width<=1280&&s.width>=640&&(best==null||s.width*s.height>best.width*best.height))best=s;
            if(best!=null)params.setPreviewSize(best.width,best.height);
            if(params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(params);camera.setDisplayOrientation((info.orientation-getWindowManager().getDefaultDisplay().getRotation()*90+360)%360);camera.setPreviewDisplay(preview.getHolder());
            final Camera.Size size=camera.getParameters().getPreviewSize();
            camera.setPreviewCallback((bytes,source)->{
                if(!foreground||System.currentTimeMillis()-lastFrame<160||!decoding.compareAndSet(false,true))return;
                lastFrame=System.currentTimeMillis();int request=generation;byte[] frame=bytes.clone();
                worker.execute(()->{try{String value=QrDecoder.decodeYuv(frame,size.width,size.height);if(value!=null)runOnUiThread(()->{if(foreground&&generation==request)accept(value);});}finally{decoding.set(false);}});
            });camera.startPreview();
        }catch(Exception error){releaseCamera();hint.setText("无法打开相机："+error.getMessage()+"。可从图片识别或返回手动配对。");}
    }
    private void accept(String value){
        try{JSONObject c=new JSONObject();c.put("op","parse_qr");c.put("value",value);JSONObject parsed=new JSONObject(NativeBridge.call(c.toString()));
            if(parsed.has("error")){hint.setText(parsed.optString("error"));return;}
            setResult(RESULT_OK,new Intent().putExtra("qr",value));finish();
        }catch(Exception error){hint.setText("二维码解析失败，请重试");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=9||result!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();worker.execute(()->{
            try{
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1200)options.inSampleSize*=2;
                Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
                if(bitmap==null)throw new Exception("图片格式不支持");String value=QrDecoder.decodeBitmap(bitmap);bitmap.recycle();
                runOnUiThread(()->{if(isFinishing())return;if(value==null)hint.setText("图片中未找到二维码");else accept(value);});
            }catch(Exception error){runOnUiThread(()->hint.setText("无法识别图片，请重试"));}
        });
    }
}
