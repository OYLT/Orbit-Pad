package app.orbitpad.controller.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** A separately signed test APK; never included in the production APK. */
public final class DeviceInstrumentation extends Instrumentation {
    private Bundle arguments;
    private UiAutomation automation;
    private Activity activity;
    private long downTime;

    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            trace("get-ui-automation");
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            trace("launch-activity");
            Intent intent = new Intent(Intent.ACTION_MAIN).setClassName("app.orbitpad.controller", "app.orbitpad.controller.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(intent);
            trace("wait-idle");
            waitForIdleSync();
            if("native".equals(arguments.getString("suite"))){nativeGamepad();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("edge".equals(arguments.getString("suite"))){dockEdge();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("motion".equals(arguments.getString("suite"))){dockMotion();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("glass".equals(arguments.getString("suite"))){glassDock();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("bluetooth".equals(arguments.getString("suite"))){bluetoothDirect();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("camera".equals(arguments.getString("suite"))){cameraPreview();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if ("shell".equals(arguments.getString("suite")) || "usb".equals(arguments.getString("suite"))) {
                runOnMainSync(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                if("shell".equals(arguments.getString("suite")))nativeShell();else usbDirect();
                result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;
            }
            if ("settings".equals(arguments.getString("suite"))) {
                nativeSettings();
                result.putString("orbit_result", "PASS");
                finish(Activity.RESULT_OK, result);
                return;
            }
            trace("connect-invalid-code");
            connect(arguments.getString("address"), "000000");
            waitContains("配对码不正确", 15000);
            phase("wrong-code-rejected", "", "false");
            connect(arguments.getString("address"), arguments.getString("code"));
            waitNode("A", 15000);
            SystemClock.sleep(1400);
            if (activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) throw new AssertionError("Not in landscape");
            if ((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) throw new AssertionError("Screen awake flag missing");
            phase("landscape-awake-connected", "", "true");

            String[][] buttons = {
                {"A", "a"}, {"B", "b"}, {"X", "x"}, {"Y", "y"},
                {"LB", "lb"}, {"RB", "rb"}, {"LT 模拟扳机", "lt"}, {"RT 模拟扳机", "rt"},
                {"View 背包", "select"}, {"Menu 暂停", "start"},
                {"方向上", "up"}, {"方向下", "down"}, {"方向左", "left"}, {"方向右", "right"}
            };
            for (int i = 0; i < buttons.length; i++) {
                Rect rect = bounds(waitNode(buttons[i][0], 5000));
                float[] point = {rect.exactCenterX(), rect.exactCenterY()};
                // Android's input CLI supplies the OEM-compatible single-finger stream.
                // UiAutomation remains responsible for the simultaneous multi-finger steps.
                trace("single-touch:" + Math.round(point[0]) + ":" + Math.round(point[1]));
                SystemClock.sleep(250);
                phase("button-" + i + "-down", buttons[i][1], "true");
                phase("button-" + i + "-released", "", "true");
            }

            Rect left = bounds(waitNode("左摇杆，模拟量输出", 5000));
            Rect right = bounds(waitNode("右摇杆，模拟量输出", 5000));
            Rect a = bounds(waitNode("A", 5000));
            float[] l0 = {left.exactCenterX(), left.exactCenterY()};
            float[] l1 = {l0[0] + left.width() * .32f, l0[1] - left.height() * .32f};
            float[] r0 = {right.exactCenterX(), right.exactCenterY()};
            float[] r1 = {r0[0] - right.width() * .32f, r0[1] - right.height() * .32f};
            float[] a0 = {a.exactCenterX(), a.exactCenterY()};

            downTime = SystemClock.uptimeMillis();
            touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{l0});
            touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{{l0[0] + 5, l0[1] + 5}});
            phase("left-stick-deadzone", "", "true");
            touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{l0});
            phase("deadzone-released", "", "true");

            downTime = SystemClock.uptimeMillis();
            touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{l0});
            touch(MotionEvent.ACTION_MOVE, new int[]{0}, new float[][]{l1});
            phase("left-stick-diagonal", "moveRight,moveUp", "true");
            touch(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1}, new float[][]{l1,r0});
            touch(MotionEvent.ACTION_MOVE, new int[]{0,1}, new float[][]{l1,r1});
            phase("dual-stick", "moveRight,moveUp,lookLeft,lookUp", "true");
            touch(MotionEvent.ACTION_POINTER_DOWN | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1,2}, new float[][]{l1,r1,a0});
            phase("three-finger-dual-stick-and-a", "moveRight,moveUp,lookLeft,lookUp,a", "true");
            touch(MotionEvent.ACTION_POINTER_UP | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1,2}, new float[][]{l1,r1,a0});
            phase("a-released-sticks-held", "moveRight,moveUp,lookLeft,lookUp", "true");
            touch(MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1}, new float[][]{l1,r1});
            phase("right-released-left-held", "moveRight,moveUp", "true");
            touch(MotionEvent.ACTION_UP, new int[]{0}, new float[][]{l1});
            phase("all-fingers-released", "", "true");

            tapNode("手柄设置");
            waitNode("按键映射", 5000);
            phase("material-settings-mapping", "", "true");
            tapNode("手感调节");
            waitContains("摇杆死区", 5000);
            phase("material-settings-feel", "", "true");
            key(KeyEvent.KEYCODE_BACK);
            waitNode("A", 5000);
            phase("settings-back-preserves-connection", "", "true");

            downTime = SystemClock.uptimeMillis();
            touch(MotionEvent.ACTION_DOWN, new int[]{0}, new float[][]{a0});
            phase("background-key-held", "a", "true");
            key(KeyEvent.KEYCODE_HOME);
            SystemClock.sleep(2100);
            phase("background-released", "", "false");
            Intent resume = new Intent(Intent.ACTION_MAIN).setClassName("app.orbitpad.controller", "app.orbitpad.controller.MainActivity");
            resume.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            trace("resume-activity");
            getTargetContext().startActivity(resume);
            waitNode("A", 15000); SystemClock.sleep(2000);
            phase("resumed-reconnected", "", "true");
            returnToNative();
            SystemClock.sleep(400);
            phase("back-disconnect-released", "", "false");
            result.putString("orbit_result", "PASS");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            trace("failed-screenshot");
            if (automation != null) { StringBuilder tree = new StringBuilder(); describe(automation.getRootInActiveWindow(), tree); result.putString("orbit_ui", tree.toString()); }
            SystemClock.sleep(1200);
            result.putString("orbit_result", "FAIL"); result.putString("orbit_error", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void trace(String value) { Bundle b = new Bundle(); b.putString("orbit_trace", value); sendStatus(1, b); }
    private void cameraPreview() throws Exception {
        automation.grantRuntimePermission("app.orbitpad.controller","android.permission.CAMERA");
        ActivityMonitor monitor=addMonitor("app.orbitpad.controller.ScanActivity",null,false);
        tapNode("导航连接");tapNode("打开相机扫码");Activity scanner=monitor.waitForActivityWithTimeout(8000);
        if(scanner==null)throw new AssertionError("Scanner did not launch");
        java.lang.reflect.Field field=scanner.getClass().getDeclaredField("camera");field.setAccessible(true);
        long until=SystemClock.uptimeMillis()+8000;java.util.concurrent.atomic.AtomicBoolean open=new java.util.concurrent.atomic.AtomicBoolean();
        while(!open.get()&&SystemClock.uptimeMillis()<until){runOnMainSync(()->{try{open.set(field.get(scanner)!=null);}catch(Exception e){throw new RuntimeException(e);}});SystemClock.sleep(150);}
        if(!open.get())throw new AssertionError("Camera preview did not open");SystemClock.sleep(500);phase("camera-preview-open","","false");
        runOnMainSync(()->scanner.finish());SystemClock.sleep(600);if(field.get(scanner)!=null)throw new AssertionError("Camera was not released");phase("camera-release-on-exit","","false");removeMonitor(monitor);
    }
    private void nativeShell() throws Exception {
        String[] keys={"rust_settings","template_name","templates_v1"};String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)previous[i]=activity.getPreferences(0).getString(keys[i],null);
        try{
            waitContains("你的手柄",5000);phase("shell-home","","false");
            tapNode("导航主题");waitNode("主题",5000);phase("shell-theme","","false");
            tapNode("导航设置");tapNode("关于 Orbit Pad");waitContains("版本 0.5.0",5000);phase("shell-about","","false");
            tapNode("导航连接");waitNode("打开相机扫码",5000);phase("shell-scan-default","","false");
            tapNode("USB 直连");waitNode("连接 USB 手柄",5000);phase("shell-usb-option","","false");
            tapNode("蓝牙直连");waitNode("选择已配对的电脑",5000);phase("shell-bluetooth-option","","false");
            tapNode("导航主页");tapNode("自定义按键布局");waitNode("A",5000);SystemClock.sleep(700);
            Rect a=bounds(waitNode("A",5000));float[][] first={{a.exactCenterX(),a.exactCenterY()}},moved={{a.exactCenterX()-140,a.exactCenterY()-60}};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},first);touch(MotionEvent.ACTION_MOVE,new int[]{0},moved);touch(MotionEvent.ACTION_UP,new int[]{0},moved);SystemClock.sleep(250);
            Rect changed=bounds(waitNode("A",5000));if(Math.abs(changed.exactCenterX()-moved[0][0])>8||Math.abs(changed.exactCenterY()-moved[0][1])>8)throw new AssertionError("Drag layout was not saved");
            phase("shell-editor-drag","","false");tapNode("A");progress("控件大小",20);tapNode("完成");SystemClock.sleep(200);
            Rect scaled=bounds(waitNode("A",5000));if(Math.abs(scaled.width()/(float)a.width()-.8f)>.04)throw new AssertionError("Per-control size did not change");
            phase("shell-control-scale","","false");tapNode("保存并返回");tapNode("＋ 保存为新模板");
            List<AccessibilityNodeInfo> inputs=new ArrayList<>();inputs(automation.getRootInActiveWindow(),inputs);if(inputs.size()!=1)throw new AssertionError("Template name field missing");setText(inputs.get(0),"测试模板");tapNode("保存");
            phase("shell-template-saved","","false");tapNode("通用游戏  ↗");tapNode("测试模板");tapNode("自定义按键布局");SystemClock.sleep(500);
            Rect loaded=bounds(waitNode("A",5000));if(Math.abs(loaded.exactCenterX()-scaled.exactCenterX())>8||Math.abs(loaded.width()-scaled.width())>4)throw new AssertionError("Template did not restore custom layout");
            phase("shell-template-loaded","","false");tapNode("保存并返回");tapNode("通用游戏  ↗");
            byte[] png=android.util.Base64.decode(arguments.getString("qrBase64"),android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(png,0,png.length);
            String qr=(String)Class.forName("app.orbitpad.controller.QrDecoder").getMethod("decodeBitmap",android.graphics.Bitmap.class).invoke(null,bitmap);bitmap.recycle();
            if(qr==null||!qr.endsWith("#pair="+arguments.getString("code")))throw new AssertionError("Actual receiver QR PNG did not decode");
            phase("shell-qr-image-decoded","","false");
            final java.lang.reflect.Method connect=activity.getClass().getDeclaredMethod("connectQr",String.class);connect.setAccessible(true);
            final String value=qr;runOnMainSync(()->{try{connect.invoke(activity,value);}catch(Exception e){throw new RuntimeException(e);}});
            waitNode("A",15000);SystemClock.sleep(600);phase("shell-qr-paired","","true");hold("A","a","shell-qr-a");returnToNative();phase("shell-qr-disconnect","","false");
        }finally{
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();for(int i=0;i<keys.length;i++){if(previous[i]==null)restore.remove(keys[i]);else restore.putString(keys[i],previous[i]);}restore.commit();
        }
    }
    private Object member(Object object,String name) throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private void glassCheck(String name,boolean passed){
        if(!passed)throw new AssertionError(name);Bundle status=new Bundle();status.putString("orbit_glass",name);sendStatus(1,status);
    }
    private android.graphics.Bitmap glassShot(String name) throws Exception {
        waitForIdleSync();SystemClock.sleep(350);android.graphics.Bitmap bitmap=automation.takeScreenshot();
        java.io.File dir=new java.io.File(getTargetContext().getExternalFilesDir(null),"glass-test");dir.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        return bitmap;
    }
    private long pixelDifference(android.graphics.Bitmap a,android.graphics.Bitmap b,Rect rect){
        long difference=0;for(int y=rect.top+4;y<rect.bottom-4;y+=3)for(int x=rect.left+4;x<rect.right-4;x+=3){
            int first=a.getPixel(x,y),second=b.getPixel(x,y);
            difference+=Math.abs(((first>>16)&255)-((second>>16)&255))+Math.abs(((first>>8)&255)-((second>>8)&255))+Math.abs((first&255)-(second&255));
        }return difference;
    }
    private void glassDock() throws Exception {
        String previous=activity.getPreferences(0).getString("glass_quality",null);
        boolean hadStrength=activity.getPreferences(0).contains("glass_refraction");int previousStrength=activity.getPreferences(0).getInt("glass_refraction",2);
        android.widget.FrameLayout page=null;android.view.View probe=null;
        try{
            Object shell=member(activity,"shell");android.view.View dock=(android.view.View)member(shell,"glassDock");
            android.view.View root=(android.view.View)member(shell,"view");page=(android.widget.FrameLayout)member(shell,"page");
            runOnMainSync(()->{activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.getPreferences(0).edit().putString("glass_quality","auto").putInt("glass_refraction",2).commit();root.invalidate();dock.invalidate();});
            glassCheck("dock-minimum-touch-height",dock.getHeight()/activity.getResources().getDisplayMetrics().density>=60);
            glassCheck("home-selected",waitNode("导航主页",5000).isSelected());
            android.widget.ScrollView scroll=(android.widget.ScrollView)member(shell,"currentScroll");
            runOnMainSync(()->scroll.scrollTo(0,220));SystemClock.sleep(300);int savedScroll=scroll.getScrollY();
            glassShot("home-scrolled").recycle();tapNode("导航设置");
            glassCheck("settings-selected",waitNode("导航设置",5000).isSelected());
            String[] strengths={"关闭","轻柔","标准","强烈"};
            for(int level=0;level<4;level++){
                tapNode("折射强度"+strengths[level]);
                glassCheck("strength-choice-"+level,waitNode("折射强度"+strengths[level],5000).isChecked()&&activity.getPreferences(0).getInt("glass_refraction",-1)==level);
            }
            glassShot("settings").recycle();tapNode("关于 Orbit Pad");waitContains("版本 0.5.0",5000);
            glassCheck("about-embedded-in-settings",waitNode("导航设置",5000).isSelected());glassShot("about-expanded").recycle();
            key(KeyEvent.KEYCODE_BACK);glassCheck("back-collapses-about",waitNode("导航设置",5000).isSelected()&&find(automation.getRootInActiveWindow(),"组件与隐私",false)==null);
            tapNode("导航主页");tapNode("导航设置");glassCheck("strength-survives-navigation",waitNode("折射强度强烈",5000).isChecked());
            tapNode("导航主页");
            android.widget.ScrollView restored=(android.widget.ScrollView)member(shell,"currentScroll");
            glassCheck("tab-scroll-restored",Math.abs(restored.getScrollY()-savedScroll)<3);
            Object sameScroll=member(shell,"currentScroll");tapNode("导航主页");
            glassCheck("repeat-tab-keeps-page",sameScroll==member(shell,"currentScroll"));
            tapNode("导航主题");key(KeyEvent.KEYCODE_BACK);
            glassCheck("back-updates-selection",waitNode("导航主页",5000).isSelected());
            tapNode("导航连接");tapNode("手动配对");
            android.widget.EditText address=(android.widget.EditText)member(shell,"address");
            runOnMainSync(()->address.setText("192.168.1.77:3210"));tapNode("导航主题");tapNode("导航连接");
            android.widget.EditText restoredAddress=(android.widget.EditText)member(shell,"address");
            glassCheck("connection-draft-restored","192.168.1.77:3210".equals(restoredAddress.getText().toString()));
            runOnMainSync(()->{restoredAddress.requestFocus();((android.view.inputmethod.InputMethodManager)activity.getSystemService("input_method")).showSoftInput(restoredAddress,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
            SystemClock.sleep(1200);glassCheck("keyboard-hides-dock",dock.getVisibility()==android.view.View.GONE);
            key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(700);glassCheck("keyboard-dismiss-restores-dock",dock.getVisibility()==android.view.View.VISIBLE);
            tapNode("导航主页");
            android.widget.ScrollView home=(android.widget.ScrollView)member(shell,"currentScroll");runOnMainSync(()->home.scrollTo(0,0));SystemClock.sleep(300);
            glassShot("home").recycle();
            glassCheck("transparent-navigation-bar",activity.getWindow().getNavigationBarColor()==android.graphics.Color.TRANSPARENT&&!activity.getWindow().isNavigationBarContrastEnforced());
            android.view.WindowInsets windowInsets=root.getRootWindowInsets();int navigationBottom=windowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            int[] rootLocation=new int[2];root.getLocationOnScreen(rootLocation);int[] dockLocation=new int[2];dock.getLocationOnScreen(dockLocation);
            glassCheck("dock-avoids-system-gestures",dockLocation[1]+dock.getHeight()<=rootLocation[1]+root.getHeight()-navigationBottom);
            glassCheck("page-extends-under-gesture-area",page.getHeight()==root.getHeight()&&root.getPaddingBottom()==0);
            int orientation=activity.getRequestedOrientation();
            try{
                runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));SystemClock.sleep(900);
                glassCheck("landscape-tabs-accessible",!bounds(waitNode("导航设置",5000)).isEmpty());glassShot("landscape").recycle();
            }finally{runOnMainSync(()->activity.setRequestedOrientation(orientation));SystemClock.sleep(900);}
            glassCheck("rotation-keeps-selection",waitNode("导航主页",5000).isSelected());
            // A colorful moving source makes real blur/refraction distinguishable from tint.
            probe=new android.view.View(activity){
                final android.graphics.Paint p=new android.graphics.Paint();
                @Override protected void onDraw(android.graphics.Canvas c){
                    int[] colors={0xffe3a257,0xff679fa9,0xffb67db9,0xff718f68};
                    float stripe=getResources().getDisplayMetrics().density*22;
                    for(int i=0;i<getWidth()/stripe+1;i++){p.setColor(colors[i%4]);c.drawRect(i*stripe,0,(i+1)*stripe,getHeight(),p);}
                    p.setColor(0xfff7efdf);p.setStrokeWidth(stripe*.15f);
                    for(float y=0;y<getHeight();y+=stripe*2)c.drawLine(0,y,getWidth(),y+stripe,p);
                }
            };
            android.view.View finalProbe=probe;android.widget.FrameLayout finalPage=page;
            runOnMainSync(()->{finalPage.addView(finalProbe,new android.widget.FrameLayout.LayoutParams(-1,-1));activity.getPreferences(0).edit().putString("glass_quality","auto").putInt("glass_refraction",2).commit();root.invalidate();dock.invalidate();});
            int[] location=new int[2];dock.getLocationOnScreen(location);Rect region=new Rect(location[0],location[1],location[0]+dock.getWidth(),location[1]+dock.getHeight());
            android.graphics.Bitmap full=glassShot("probe-full");
            long bottomBrightness=0;int bottomSamples=0;
            for(int x=8;x<full.getWidth();x+=37){int pixel=full.getPixel(x,full.getHeight()-8);bottomBrightness+=((pixel>>16)&255)+((pixel>>8)&255)+(pixel&255);bottomSamples++;}
            glassCheck("gesture-area-shows-page-background",bottomBrightness/(bottomSamples*3)>70);
            glassCheck("renderer-has-not-fallen-back",!((Boolean)member(root,"failed")));
            Object backend=member(root,"backend");glassCheck("gpu-backdrop-active",backend!=null);
            if(android.os.Build.VERSION.SDK_INT>=33)glassCheck("lens-shader-active",member(member(backend,"outer"),"lens")!=null);
            runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality","standard").commit();root.invalidate();dock.invalidate();});
            android.graphics.Bitmap standard=glassShot("probe-standard");
            if(android.os.Build.VERSION.SDK_INT>=33)glassCheck("refraction-changes-edge-pixels",pixelDifference(full,standard,region)>10000);
            runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality","auto").putInt("glass_refraction",0).commit();root.invalidate();dock.invalidate();});
            android.graphics.Bitmap off=glassShot("probe-strength-0");glassCheck("zero-strength-keeps-blur",pixelDifference(off,standard,region)<10000);
            android.graphics.Bitmap last=off;
            for(int level=1;level<=3;level++){
                final int value=level;runOnMainSync(()->{activity.getPreferences(0).edit().putInt("glass_refraction",value).commit();root.invalidate();dock.invalidate();});
                android.graphics.Bitmap current=glassShot("probe-strength-"+level);glassCheck("strength-visibly-changes-"+level,pixelDifference(last,current,region)>10000);
                if(last!=off)last.recycle();last=current;
            }
            last.recycle();off.recycle();
            runOnMainSync(()->{activity.getPreferences(0).edit().putInt("glass_refraction",2).commit();root.invalidate();dock.invalidate();});
            runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality","simple").commit();root.invalidate();dock.invalidate();});
            android.graphics.Bitmap simple=glassShot("probe-simple");
            glassCheck("blur-samples-real-background",pixelDifference(standard,simple,region)>100000);
            runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality","auto").commit();finalProbe.setTranslationX(37);root.invalidate();dock.invalidate();});
            android.graphics.Bitmap moved=glassShot("probe-moved");
            glassCheck("source-movement-updates-glass",pixelDifference(full,moved,region)>100000);
            full.recycle();standard.recycle();simple.recycle();moved.recycle();
            runOnMainSync(()->finalPage.removeView(finalProbe));probe=null;
            Rect target=bounds(waitNode("导航设置",5000));float[][] point={{target.exactCenterX(),target.exactCenterY()}};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},point);SystemClock.sleep(200);touch(MotionEvent.ACTION_CANCEL,new int[]{0},point);SystemClock.sleep(400);
            glassCheck("cancelled-press-keeps-selection",waitNode("导航主页",5000).isSelected());
            tapNode("导航设置");glassShot("settings").recycle();
            tapNode("导航主页");
            glassPerformance(root,dock,(android.widget.ScrollView)member(shell,"currentScroll"));
            tapNode("导航设置");tapNode("折射强度强烈");tapNode("关于 Orbit Pad");runOnMainSync(()->activity.recreate());SystemClock.sleep(900);
            glassCheck("activity-recreation-keeps-page",find(automation.getRootInActiveWindow(),"关于 Orbit Pad",false)!=null);
            glassCheck("activity-recreation-keeps-selection",waitNode("导航设置",5000).isSelected());
            glassCheck("activity-recreation-keeps-about-expanded","已展开".contentEquals(waitNode("关于 Orbit Pad",5000).getStateDescription()));
            key(KeyEvent.KEYCODE_BACK);glassCheck("strength-survives-recreation",waitNode("折射强度强烈",5000).isChecked());glassShot("settings").recycle();
            tapNode("导航主页");
        }finally{
            if(probe!=null){android.widget.FrameLayout cleanup=page;android.view.View child=probe;runOnMainSync(()->cleanup.removeView(child));}
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();if(previous==null)restore.remove("glass_quality");else restore.putString("glass_quality",previous);if(hadStrength)restore.putInt("glass_refraction",previousStrength);else restore.remove("glass_refraction");restore.commit();
        }
    }
    private android.graphics.RectF lensBounds(android.view.View dock) throws Exception {
        java.util.concurrent.atomic.AtomicReference<android.graphics.RectF> bounds=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->{try{bounds.set(new android.graphics.RectF((android.graphics.RectF)member(dock,"slider")));}catch(Exception error){throw new RuntimeException(error);}});return bounds.get();
    }
    private android.graphics.Bitmap motionShot(String name) throws Exception {
        android.graphics.Bitmap bitmap=automation.takeScreenshot();
        java.io.File dir=new java.io.File(getTargetContext().getExternalFilesDir(null),"glass-test");dir.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}return bitmap;
    }
    private void dragPath(float[] from,float[] to,int steps,int pause){
        for(int i=1;i<=steps;i++){float t=i/(float)steps;touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{from[0]+(to[0]-from[0])*t,from[1]+(to[1]-from[1])*t}});SystemClock.sleep(pause);}
    }
    private void dockMotion() throws Exception {
        String quality=activity.getPreferences(0).getString("glass_quality",null);
        boolean hadStrength=activity.getPreferences(0).contains("glass_refraction");int strength=activity.getPreferences(0).getInt("glass_refraction",2);
        try{
            Object shell=member(activity,"shell");android.view.View dock=(android.view.View)member(shell,"glassDock");android.view.View root=(android.view.View)member(shell,"view");
            float density=activity.getResources().getDisplayMetrics().density;
            runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality","auto").putInt("glass_refraction",2).commit();activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);root.invalidate();dock.invalidate();});
            tapNode("导航主页");SystemClock.sleep(1100);
            Rect h=bounds(waitNode("导航主页",5000)),theme=bounds(waitNode("导航主题",5000)),settings=bounds(waitNode("导航设置",5000)),connection=bounds(waitNode("导航连接",5000));
            float[] homePoint={h.exactCenterX(),h.exactCenterY()},themePoint={theme.exactCenterX(),theme.exactCenterY()},settingsPoint={settings.exactCenterX(),settings.exactCenterY()},connectionPoint={connection.exactCenterX(),connection.exactCenterY()};
            android.graphics.RectF rest=lensBounds(dock);motionShot("motion-rest").recycle();
            trace("record-liquid-motion");SystemClock.sleep(500);
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{homePoint});
            SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout()+450);
            android.graphics.RectF held=lensBounds(dock);motionShot("motion-long-press").recycle();
            glassCheck("long-press-raises-lens",held.height()>rest.height()*1.25f&&held.top<0);
            glassCheck("hold-does-not-navigate",waitNode("导航主页",5000).isSelected());
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{homePoint});SystemClock.sleep(120);motionShot("motion-release-wobble").recycle();SystemClock.sleep(1100);
            glassCheck("release-restores-lens",Math.abs(lensBounds(dock).height()-rest.height())<density*1.2f);
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{homePoint});SystemClock.sleep(120);
            dragPath(homePoint,themePoint,14,18);
            android.graphics.RectF stretched=lensBounds(dock);motionShot("motion-drag-right").recycle();
            glassCheck("drag-stretches-lens",stretched.width()>rest.width()*1.15f);
            trace("motion-widths:hold="+held.width()+",drag="+stretched.width());
            glassCheck("inertia-adds-width-beyond-hold",stretched.width()>held.width()*1.02f);
            glassCheck("dock-follows-right-with-damping",dock.getTranslationX()>density*.8f&&dock.getTranslationX()<density*12);
            glassCheck("drag-preview-does-not-navigate",waitNode("导航主页",5000).isSelected());
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{themePoint});SystemClock.sleep(1200);
            glassCheck("release-selects-target-once",waitNode("导航主题",5000).isSelected());
            glassCheck("dock-returns-to-center",Math.abs(dock.getTranslationX())<density*.3f);
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{themePoint});
            dragPath(themePoint,settingsPoint,10,16);float rightShift=dock.getTranslationX();
            dragPath(settingsPoint,homePoint,18,16);motionShot("motion-drag-left").recycle();
            glassCheck("direction-reversal-moves-dock-left",rightShift>0&&dock.getTranslationX()< -density*.5f);
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{homePoint});SystemClock.sleep(1200);
            glassCheck("reverse-release-selects-home",waitNode("导航主页",5000).isSelected());
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{homePoint});dragPath(homePoint,settingsPoint,12,16);
            touch(MotionEvent.ACTION_CANCEL,new int[]{0},new float[][]{settingsPoint});SystemClock.sleep(1200);
            glassCheck("drag-cancel-keeps-page",waitNode("导航主页",5000).isSelected());
            glassCheck("cancel-restores-geometry",Math.abs(dock.getTranslationX())<density*.3f&&Math.abs(lensBounds(dock).height()-rest.height())<density*1.2f);
            float[] edge={5,homePoint[1]};downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{homePoint});dragPath(homePoint,edge,10,18);
            android.graphics.RectF overscroll=lensBounds(dock);glassCheck("edge-drag-is-bounded",Math.abs(dock.getTranslationX())<=density*12&&overscroll.left> -density*45&&overscroll.width()<rest.width()*1.7f);
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{edge});SystemClock.sleep(1200);glassCheck("edge-release-keeps-valid-selection",waitNode("导航主页",5000).isSelected());
            // Losing the owning pointer must cancel, even if another finger remains down.
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{homePoint});
            touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{0,1},new float[][]{homePoint,themePoint});
            touch(MotionEvent.ACTION_POINTER_UP,new int[]{0,1},new float[][]{homePoint,themePoint});
            touch(MotionEvent.ACTION_UP,new int[]{1},new float[][]{themePoint});SystemClock.sleep(1000);
            glassCheck("pointer-owner-loss-cancels",waitNode("导航主页",5000).isSelected()&&Math.abs(dock.getTranslationX())<density*.3f);
            boolean accessibleHold=waitNode("导航主题",5000).performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);SystemClock.sleep(1500);
            glassCheck("accessible-long-press-returns-to-selection",accessibleHold&&waitNode("导航主页",5000).isSelected()&&Math.abs(lensBounds(dock).centerX()-rest.centerX())<density*1.2f);
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{homePoint});SystemClock.sleep(650);
            runOnMainSync(()->dock.setVisibility(android.view.View.GONE));touch(MotionEvent.ACTION_CANCEL,new int[]{0},new float[][]{homePoint});runOnMainSync(()->dock.setVisibility(android.view.View.VISIBLE));SystemClock.sleep(400);
            glassCheck("hidden-dock-clears-gesture",Math.abs(dock.getTranslationX())<density*.3f&&Math.abs(lensBounds(dock).height()-rest.height())<density*1.2f);
            glassCheck("motion-renderer-stays-active",!((Boolean)member(root,"failed")));motionShot("motion-settled").recycle();
        }finally{
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();if(quality==null)restore.remove("glass_quality");else restore.putString("glass_quality",quality);
            if(hadStrength)restore.putInt("glass_refraction",strength);else restore.remove("glass_refraction");restore.commit();
        }
    }
    private float sliderPosition(android.view.View dock) throws Exception {
        java.util.concurrent.atomic.AtomicReference<Float> value=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(()->{try{value.set((Float)member(member(dock,"position"),"value"));}catch(Exception error){throw new RuntimeException(error);}});return value.get();
    }
    private void dockEdge() throws Exception {
        String quality=activity.getPreferences(0).getString("glass_quality",null);
        Object shell=member(activity,"shell");android.view.View dock=(android.view.View)member(shell,"glassDock"),root=(android.view.View)member(shell,"view");
        android.widget.FrameLayout page=(android.widget.FrameLayout)member(shell,"page");
        android.view.View stripes=new android.view.View(activity){final android.graphics.Paint p=new android.graphics.Paint();
            @Override protected void onDraw(android.graphics.Canvas c){float stripe=getResources().getDisplayMetrics().density*6;for(int i=0;i<getWidth()/stripe+1;i++){p.setColor(i%2==0?0xff1a6aaa:0xfff3b65e);c.drawRect(i*stripe,0,(i+1)*stripe,getHeight(),p);}}
        };
        android.graphics.Bitmap bare=null;boolean gestureRunning=false;
        try{
            runOnMainSync(()->{activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.getPreferences(0).edit().putString("glass_quality","auto").commit();page.addView(stripes,new android.widget.FrameLayout.LayoutParams(-1,-1));dock.setVisibility(android.view.View.GONE);root.invalidate();});
            bare=glassShot("edge-bare-background");runOnMainSync(()->{dock.setVisibility(android.view.View.VISIBLE);root.invalidate();dock.invalidate();});SystemClock.sleep(400);
            float density=activity.getResources().getDisplayMetrics().density;int[] location=new int[2];dock.getLocationOnScreen(location);
            Rect sample=new Rect(location[0]+(int)(dock.getWidth()*.45f),location[1]+(int)(density*3),location[0]+(int)(dock.getWidth()*.85f),location[1]+(int)(density*13));
            android.graphics.Bitmap rest=motionShot("edge-rest");long reference=pixelDifference(rest,bare,sample);trace("edge-reference:"+reference);glassCheck("glass-visible-before-edge-drag",reference>45000);rest.recycle();
            Rect home=bounds(waitNode("导航主页",5000));float[] from={home.exactCenterX(),home.exactCenterY()},left={5,home.exactCenterY()},right={bare.getWidth()-5,home.exactCenterY()};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{from});gestureRunning=true;dragPath(from,left,10,18);
            long deadline=SystemClock.uptimeMillis()+1800;while(sliderPosition(dock)>=0&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(60);
            glassCheck("left-rubber-band-reaches-negative-position",sliderPosition(dock)<0);
            for(String mode:new String[]{"auto","standard","simple"}){
                runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality",mode).commit();root.invalidate();dock.invalidate();});SystemClock.sleep(150);
                android.graphics.Bitmap image=motionShot("edge-negative-"+mode);long difference=pixelDifference(image,bare,sample);image.recycle();trace("edge-pixel-difference:"+mode+":"+difference);
                glassCheck("negative-position-keeps-glass-"+mode,difference>Math.max(45000,reference*.6));
            }
            runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality","auto").commit();root.invalidate();dock.invalidate();});
            dragPath(left,right,24,16);SystemClock.sleep(350);android.graphics.Bitmap atRight=motionShot("edge-right");
            glassCheck("right-edge-keeps-glass",pixelDifference(atRight,bare,sample)>Math.max(45000,reference*.6));atRight.recycle();
            dragPath(right,left,24,16);SystemClock.sleep(400);android.graphics.Bitmap again=motionShot("edge-left-again");
            glassCheck("repeated-edge-crossing-keeps-glass",sliderPosition(dock)<0&&pixelDifference(again,bare,sample)>Math.max(45000,reference*.6));again.recycle();
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{left});gestureRunning=false;SystemClock.sleep(1200);android.graphics.Bitmap released=motionShot("edge-released");
            glassCheck("edge-release-restores-glass-and-home",waitNode("导航主页",5000).isSelected()&&pixelDifference(released,bare,sample)>Math.max(45000,reference*.6));released.recycle();
            glassCheck("edge-renderer-stays-active",!((Boolean)member(root,"failed")));
        }finally{
            if(gestureRunning)touch(MotionEvent.ACTION_CANCEL,new int[]{0},new float[][]{{10,dock.getY()+dock.getHeight()/2f}});
            runOnMainSync(()->{page.removeView(stripes);dock.setVisibility(android.view.View.VISIBLE);});if(bare!=null)bare.recycle();
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();if(quality==null)restore.remove("glass_quality");else restore.putString("glass_quality",quality);restore.commit();
        }
    }
    private void glassPerformance(android.view.View root,android.view.View dock,android.widget.ScrollView scroll) throws Exception {
        android.os.HandlerThread worker=new android.os.HandlerThread("glass-frame-metrics");worker.start();
        java.util.List<Long> times=java.util.Collections.synchronizedList(new java.util.ArrayList<Long>());
        android.view.Window.OnFrameMetricsAvailableListener listener=(window,metrics,dropped)->{
            if(metrics.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME)==0)times.add(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION));
        };
        activity.getWindow().addOnFrameMetricsAvailableListener(listener,new android.os.Handler(worker.getLooper()));
        try{
            for(String quality:new String[]{"simple","standard","auto"}){
                runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality",quality).commit();root.invalidate();dock.invalidate();scroll.scrollTo(0,0);});
                SystemClock.sleep(400);times.clear();
                for(int i=0;i<8;i++){final int y=i%2==0?scroll.getChildAt(0).getHeight()-scroll.getHeight():0;
                    runOnMainSync(()->scroll.smoothScrollTo(0,y));SystemClock.sleep(420);
                }
                java.util.List<Long> sample; synchronized(times){sample=new java.util.ArrayList<>(times);}java.util.Collections.sort(sample);
                if(sample.isEmpty())throw new AssertionError("No hardware frame metrics");
                int count=sample.size();long slow=0;double refresh=activity.getWindowManager().getDefaultDisplay().getRefreshRate();
                for(long duration:sample)if(duration>1000000000.0/refresh)slow++;
                org.json.JSONObject report=new org.json.JSONObject();report.put("quality",quality);report.put("frames",count);report.put("refreshHz",refresh);
                report.put("p50Ms",sample.get(count/2)/1000000.0);report.put("p95Ms",sample.get(Math.min(count-1,(int)(count*.95)))/1000000.0);
                report.put("p99Ms",sample.get(Math.min(count-1,(int)(count*.99)))/1000000.0);report.put("overRefreshBudget",slow);
                Bundle status=new Bundle();status.putString("orbit_glass_perf",report.toString());sendStatus(1,status);
            }
        }finally{activity.getWindow().removeOnFrameMetricsAvailableListener(listener);worker.quitSafely();runOnMainSync(()->scroll.scrollTo(0,0));}
    }
    private void usbDirect() throws Exception {
        tapNode("导航连接");tapNode("USB 直连");tapNode("连接 USB 手柄");directControls("usb");
    }
    private void bluetoothDirect() throws Exception {
        automation.grantRuntimePermission("app.orbitpad.controller","android.permission.BLUETOOTH_CONNECT");
        tapNode("导航连接");tapNode("蓝牙直连");tapNode("选择已配对的电脑");
        AccessibilityNodeInfo computer=waitContains(arguments.getString("bluetoothName"),8000);
        if(!computer.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
            Rect rect=bounds(computer);float[][] p={{rect.exactCenterX(),rect.exactCenterY()}};downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},p);touch(MotionEvent.ACTION_UP,new int[]{0},p);
        }
        directControls("bluetooth");
    }
    private void directControls(String prefix) throws Exception {
        waitNode("A",35000);SystemClock.sleep(900);
        phase(prefix+"-auto-paired","","true");hold("A","a",prefix+"-a");
        Rect left=bounds(waitNode("左摇杆，模拟量输出",5000)),right=bounds(waitNode("右摇杆，模拟量输出",5000)),a=bounds(waitNode("A",5000));
        float[] l={left.exactCenterX()+left.width()*.3f,left.exactCenterY()-left.height()*.3f};float[] r={right.exactCenterX()-right.width()*.3f,right.exactCenterY()-right.height()*.3f};float[] button={a.exactCenterX(),a.exactCenterY()};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{l});
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{0,1},new float[][]{l,r});touch(MotionEvent.ACTION_POINTER_DOWN|(2<<8),new int[]{0,1,2},new float[][]{l,r,button});
        phase(prefix+"-three-finger","moveRight,moveUp,lookLeft,lookUp,a","true");touch(MotionEvent.ACTION_CANCEL,new int[]{0,1,2},new float[][]{l,r,button});phase(prefix+"-cancel-releases","","true");
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{button});phase(prefix+"-held-before-home","a","true");key(KeyEvent.KEYCODE_HOME);SystemClock.sleep(800);phase(prefix+"-background-release","","false");
        trace("resume-activity");SystemClock.sleep(prefix.equals("bluetooth")?5000:1500);waitNode("A",15000);phase(prefix+"-foreground-reconnected","","true");returnToNative();phase(prefix+"-disconnected","","false");
    }
    private void nativeGamepad() throws Exception {
        connect(arguments.getString("address"),arguments.getString("code"));
        waitNode("A",15000);SystemClock.sleep(800);phase("native-connected","","true");
        hold("A","a","native-a");
        Rect lt=bounds(waitNode("LT 模拟扳机",5000));
        float[] lc={lt.exactCenterX(),lt.exactCenterY()};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{lc});
        phase("native-lt-full","lt","true");
        touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{lc[0],lc[1]+lt.height()*.5f}});
        phase("native-lt-half","lt","true");
        touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{lc});phase("native-lt-released","","true");
        Rect rt=bounds(waitNode("RT 模拟扳机",5000));float[] rc={rt.exactCenterX(),rt.exactCenterY()};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{rc});
        touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{rc[0],rc[1]+rt.height()*.75f}});
        phase("native-rt-quarter","rt","true");
        touch(MotionEvent.ACTION_CANCEL,new int[]{0},new float[][]{rc});phase("native-rt-cancel","","true");
        Rect left=bounds(waitNode("左摇杆，模拟量输出",5000)),right=bounds(waitNode("右摇杆，模拟量输出",5000)),a=bounds(waitNode("A",5000));
        float[] l={left.exactCenterX()+left.width()*.32f,left.exactCenterY()-left.height()*.32f};
        float[] r={right.exactCenterX()-right.width()*.32f,right.exactCenterY()-right.height()*.32f};
        float[] button={a.exactCenterX(),a.exactCenterY()};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{l});
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},new float[][]{l,r});
        touch(MotionEvent.ACTION_POINTER_DOWN|(2<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1,2},new float[][]{l,r,button});
        phase("native-three-fingers","moveRight,moveUp,lookLeft,lookUp,a","true");
        touch(MotionEvent.ACTION_CANCEL,new int[]{0,1,2},new float[][]{l,r,button});phase("native-cancel-neutral","","true");
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{button});
        phase("native-held-before-background","a","true");key(KeyEvent.KEYCODE_HOME);SystemClock.sleep(900);
        phase("native-background-neutral","","false");trace("resume-activity");SystemClock.sleep(1800);waitNode("A",15000);
        phase("native-reconnected-neutral","","true");returnToNative();phase("native-disconnected-neutral","","false");
    }
    private void nativeSettings() throws Exception {
        String saved=activity.getPreferences(0).getString("rust_settings", null);
        try {
            connect(arguments.getString("address"), arguments.getString("code"));
            waitNode("A",15000);SystemClock.sleep(1000);
            Rect originalLeft=bounds(waitNode("左摇杆，模拟量输出",5000));
            tapNode("手柄设置");tapNode("竞速");key(KeyEvent.KEYCODE_BACK);
            hold("B","b","native-racing-b");hold("RT 模拟扳机","rt","native-racing-rt");
            tapNode("手柄设置");tapNode("跳跃");key(KeyEvent.KEYCODE_BACK);
            Rect left=bounds(waitNode("左摇杆，模拟量输出",5000));
            float[] center={left.exactCenterX(),left.exactCenterY()};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{center});
            touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{center[0]+left.width()*.4f,center[1]}});
            phase("native-platform-stick","moveRight","true");
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{center});phase("native-platform-release","","true");
            tapNode("手柄设置");tapNode("通用");tapNode("A映射");tapNode("R3 · 右摇杆按下");
            key(KeyEvent.KEYCODE_BACK);hold("A","r3","native-custom-a");
            tapNode("手柄设置");tapNode("手感调节");
            progress("摇杆死区",40);waitContains("摇杆死区 · 50%",5000);
            progress("显示比例",0);waitContains("显示比例 · 80%",5000);
            AccessibilityNodeInfo haptics=waitNode("振动反馈",5000);
            if(haptics.isChecked())tapNode("振动反馈");
            if(waitNode("振动反馈",5000).isChecked())throw new AssertionError("Haptics switch did not turn off");
            phase("native-settings-saved","","true");key(KeyEvent.KEYCODE_BACK);
            left=bounds(waitNode("左摇杆，模拟量输出",5000));
            if(Math.abs(left.width()/(float)originalLeft.width()-.8f)>.03f)throw new AssertionError("Control scale did not change to 80%");
            phase("native-control-scale-80","","true");
            center=new float[]{left.exactCenterX(),left.exactCenterY()};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{center});
            touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{center[0]+left.width()*.2f,center[1]}});
            phase("native-deadzone-50-small","","true");
            touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{center[0]+left.width()*.4f,center[1]}});
            phase("native-deadzone-50-large","moveRight","true");
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{center});phase("native-deadzone-release","","true");
            runOnMainSync(()->activity.recreate());
            waitContains("你的手柄",10000);SystemClock.sleep(500);
            connect(arguments.getString("address"),arguments.getString("code"));waitNode("A",15000);SystemClock.sleep(600);
            hold("A","r3","native-recreated-custom-a");
            tapNode("手柄设置");tapNode("手感调节");
            waitContains("摇杆死区 · 50%",5000);waitContains("显示比例 · 80%",5000);
            if(waitNode("振动反馈",5000).isChecked())throw new AssertionError("Haptics setting did not survive recreation");
            phase("native-settings-restored","","true");key(KeyEvent.KEYCODE_BACK);
            returnToNative();phase("native-settings-disconnect","","false");
        } finally {
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();
            if(saved==null)restore.remove("rust_settings");else restore.putString("rust_settings",saved);
            if(!restore.commit())throw new AssertionError("Could not restore original preferences");
        }
    }
    private void progress(String name,float value) {
        Bundle bundle=new Bundle();bundle.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);
        if(!waitNode(name,5000).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),bundle))throw new AssertionError("Slider update failed: "+name);
        SystemClock.sleep(200);
    }
    private void hold(String name,String expected,String phaseName) {
        Rect rect=bounds(waitNode(name,5000));float[][] point={{rect.exactCenterX(),rect.exactCenterY()}};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},point);phase(phaseName+"-down",expected,"true");
        touch(MotionEvent.ACTION_UP,new int[]{0},point);phase(phaseName+"-up","","true");
    }
    private void describe(AccessibilityNodeInfo node, StringBuilder text) {
        if (node == null || text.length() > 18000) return;
        if (node.getText() != null || node.getContentDescription() != null) text.append(node.getClassName()).append(" ").append(node.getText()).append(" / ").append(node.getContentDescription()).append(" ").append(bounds(node)).append("\n");
        for (int i=0;i<node.getChildCount();i++) describe(node.getChild(i), text);
    }

    private void phase(String name, String expected, String connected) {
        SystemClock.sleep(180);
        Bundle result = new Bundle(); result.putString("orbit_phase", name); result.putString("orbit_expected", expected); result.putString("orbit_connected", connected);
        result.putString("orbit_emitted_ms", Long.toString(System.currentTimeMillis()));
        sendStatus(1, result);
        // Allow the host to record screenshots before the next touch phase.
        SystemClock.sleep(name.equals("landscape-awake-connected") || name.startsWith("material-settings") || name.equals("settings-back-preserves-connection") ? 2400 : 800);
    }
    private void tapNode(String name) {
        AccessibilityNodeInfo target=waitNode(name,5000);
        if(target.isClickable()&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){SystemClock.sleep(250);return;}
        Rect rect = bounds(target);
        float[][] point = {{rect.exactCenterX(), rect.exactCenterY()}};
        downTime = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, point);
        touch(MotionEvent.ACTION_UP, new int[]{0}, point);
        SystemClock.sleep(200);
    }
    private Rect bounds(AccessibilityNodeInfo node) { Rect rect = new Rect(); node.getBoundsInScreen(rect); return rect; }
    private void connect(String address, String code) {
        if(find(automation.getRootInActiveWindow(),"导航连接",false)!=null){
            tapNode("导航连接");tapNode("手动配对");
        }
        List<AccessibilityNodeInfo> inputs = new ArrayList<>();
        long until = SystemClock.uptimeMillis() + 8000;
        while (inputs.size() != 2 && SystemClock.uptimeMillis() < until) { inputs.clear(); inputs(automation.getRootInActiveWindow(), inputs); SystemClock.sleep(120); }
        if (inputs.size() != 2) throw new AssertionError("Native connection inputs not found");
        setText(inputs.get(0), address); setText(inputs.get(1), code);
        waitContains("连接并开始", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    private void inputs(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> result) {
        if (node == null) return;
        if ("android.widget.EditText".contentEquals(node.getClassName())) result.add(node);
        for (int i=0;i<node.getChildCount();i++) inputs(node.getChild(i), result);
    }
    private void setText(AccessibilityNodeInfo node, String value) {
        Bundle bundle = new Bundle(); bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)) throw new AssertionError("Input failed");
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String query, boolean contains) {
        if (node == null) return null;
        String text = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        if (contains ? text.contains(query) || description.contains(query) : text.equals(query) || description.equals(query)) return node;
        for (int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo match = find(node.getChild(i), query, contains); if (match != null) return match; }
        return null;
    }
    private AccessibilityNodeInfo waitNode(String text, int timeout) { return waitNode(text, timeout, false); }
    private AccessibilityNodeInfo waitContains(String text, int timeout) { return waitNode(text, timeout, true); }
    private AccessibilityNodeInfo waitNode(String text, int timeout, boolean contains) {
        long until = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < until) {
            AccessibilityNodeInfo node = find(automation.getRootInActiveWindow(), text, contains);
            AccessibilityNodeInfo dock=find(automation.getRootInActiveWindow(),"导航主页",false);
            boolean covered=node!=null&&dock!=null&&!text.startsWith("导航")&&bounds(node).bottom>bounds(dock).top;
            if (node != null && !bounds(node).isEmpty()&&!covered) return node;
            AccessibilityNodeInfo scroll=findScrollable(automation.getRootInActiveWindow());
            if(scroll!=null)scroll.performAction(node!=null&&bounds(node).bottom<=bounds(node).top?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(150);
        }
        throw new AssertionError("UI node not found: " + text);
    }
    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isScrollable())return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=findScrollable(node.getChild(i));if(result!=null)return result;}return null;}
    private void returnToNative() {
        key(KeyEvent.KEYCODE_BACK);
        waitNode("断开", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        waitNode("手动配对",5000);tapNode("手动配对");
        waitContains("连接并开始", 5000); SystemClock.sleep(350);
    }
    private void key(int code) {
        long time = SystemClock.uptimeMillis();
        automation.injectInputEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), true);
        SystemClock.sleep(50);
        automation.injectInputEvent(new KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0, -1, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), true);
    }
    private void touch(int action, int[] ids, float[][] points) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[ids.length];
        for (int i=0;i<ids.length;i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i]; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords(); coordinates[i].x = points[i][0]; coordinates[i].y = points[i][1];
            int masked = action & MotionEvent.ACTION_MASK;
            boolean released = masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL
                || (masked == MotionEvent.ACTION_POINTER_UP && i == (action >> MotionEvent.ACTION_POINTER_INDEX_SHIFT));
            coordinates[i].pressure = released ? 0 : 1;
            // A real fingertip occupies a small fraction of the screen, not size=1 (a palm).
            coordinates[i].size = .04f; coordinates[i].touchMajor = 8; coordinates[i].touchMinor = 8;
        }
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, ids.length, properties, coordinates,
            0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        if (!automation.injectInputEvent(event, true)) throw new AssertionError("Touch injection failed");
        event.recycle(); SystemClock.sleep(60);
    }
}
