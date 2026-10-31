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
            if("layout".equals(arguments.getString("suite"))){layoutCustomization();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("connection".equals(arguments.getString("suite"))){persistentConnection();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("racing".equals(arguments.getString("suite"))){racingControls();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("native".equals(arguments.getString("suite"))){nativeGamepad();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
            if("polish".equals(arguments.getString("suite"))){glassPolish();result.putString("orbit_result","PASS");finish(Activity.RESULT_OK,result);return;}
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
    private void persistentConnection() throws Exception {
        org.json.JSONObject original=core("{\"op\":\"snapshot\"}").getJSONObject("settings");
        org.json.JSONObject config=new org.json.JSONObject(original.toString());config.put("gyro_enabled",false);
        core(new org.json.JSONObject().put("op","configure").put("settings",config).toString());
        try{
        waitContains("未连接",5000);phase("connection-home-disconnected","","false");
        connect(arguments.getString("address"),arguments.getString("code"));waitNode("A",15000);
        phase("connection-paired","","true");
        Rect a=bounds(waitNode("A",5000));float[][] point={{a.exactCenterX(),a.exactCenterY()}};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},point);
        phase("connection-held-before-home","a","true");
        key(KeyEvent.KEYCODE_BACK);waitContains("你的手柄",5000);waitContains("已连接",5000);
        touch(MotionEvent.ACTION_UP,new int[]{0},point);SystemClock.sleep(1600);
        phase("connection-home-keeps-session-neutral","","true");
        Rect badge=bounds(waitContains("已连接",5000)),title=bounds(waitContains("你的手柄",5000));
        if(badge.centerX()<=title.centerX()||badge.centerY()>=title.centerY())throw new AssertionError("Connection status must be at the top right");
        tapNode("进入手柄");waitNode("A",5000);hold("A","a","connection-reentered");
        tapNode("手柄设置");tapNode("返回主页");waitContains("已连接",5000);
        phase("connection-settings-home-keeps-session","","true");
        tapNode("自定义按键布局");waitNode("A",5000);key(KeyEvent.KEYCODE_BACK);waitContains("已连接",5000);
        phase("connection-editor-home-keeps-session","","true");
        tapNode("导航设置");tapNode("导航主页");waitContains("已连接",5000);
        phase("connection-tabs-keep-session","","true");
        tapNode("进入手柄");waitNode("A",5000);returnToNative();
        phase("connection-explicit-disconnect","","false");
        tapNode("导航主页");waitContains("未连接",5000);waitNode("扫码连接电脑",5000);
        phase("connection-home-status-updated","","false");
        }finally{core(new org.json.JSONObject().put("op","configure").put("settings",original).toString());}
    }
    private void applyLayoutConfig(org.json.JSONObject config,String name) throws Exception {
        java.lang.reflect.Method apply=activity.getClass().getDeclaredMethod("applyTemplate",org.json.JSONObject.class,String.class);apply.setAccessible(true);
        runOnMainSync(()->{try{apply.invoke(activity,config,name);}catch(Exception error){throw new RuntimeException(error);}});waitForIdleSync();SystemClock.sleep(180);
    }
    private float previewWidth(Object preview,String id) throws Exception {
        org.json.JSONArray layout=(org.json.JSONArray)member(preview,"layout");for(int i=0;i<layout.length();i++){org.json.JSONObject b=layout.getJSONObject(i);if(id.equals(b.optString("id")))return (float)b.optDouble("width");}return -1;
    }
    private void carouselSelect(Object carousel,int index,boolean animate) throws Exception {
        java.lang.reflect.Method method=carousel.getClass().getDeclaredMethod("select",int.class,boolean.class);method.setAccessible(true);
        runOnMainSync(()->{try{method.invoke(carousel,index,animate);}catch(Exception error){throw new RuntimeException(error);}});
    }
    private void editorDrag(String name,float dx,float dy){
        Rect rect=bounds(waitNode(name,5000));float[] point={rect.exactCenterX(),rect.exactCenterY()},end={point[0]+dx,point[1]+dy};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{point});touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{end});touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{end});SystemClock.sleep(200);
    }
    private void layoutCustomization() throws Exception {
        String[] keys={"rust_settings","template_name","templates_v1","active_template_id","preset_layouts_v1"};String[] old=new String[keys.length];for(int i=0;i<keys.length;i++)old[i]=activity.getPreferences(0).getString(keys[i],null);
        org.json.JSONObject original=core("{\"op\":\"snapshot\"}").getJSONObject("settings");
        org.json.JSONObject defaults=core("{\"op\":\"preset\",\"name\":\"general\"}");
        try{
            org.json.JSONObject wide=new org.json.JSONObject(defaults.toString());wide.put("layout",new org.json.JSONObject("{\"a\":{\"width\":4},\"b\":{\"hidden\":true}}"));
            org.json.JSONObject small=new org.json.JSONObject(defaults.toString());small.put("layout",new org.json.JSONObject("{\"a\":{\"scale\":0.6}}"));
            org.json.JSONArray templates=new org.json.JSONArray();templates.put(new org.json.JSONObject().put("id","layout-test-wide").put("name","长键布局").put("settings",wide));templates.put(new org.json.JSONObject().put("id","layout-test-small").put("name","紧凑布局").put("settings",small));
            activity.getPreferences(0).edit().putString("templates_v1",templates.toString()).remove("preset_layouts_v1").remove("active_template_id").commit();applyLayoutConfig(defaults,"通用游戏");invokeActivity("finishEditor");waitNode("横向手柄配置卡片",5000);
            Object shell=member(activity,"shell"),carousel=member(shell,"templateCarousel");
            java.util.ArrayList<?> previews=(java.util.ArrayList<?>)member(carousel,"previews");
            glassCheck("each-card-resolves-its-own-saved-layout",previewWidth(previews.get(3),"a")>260&&previewWidth(previews.get(4),"a")<40);
            glassShot("layout-home-cards").recycle();
            carouselSelect(carousel,1,true);SystemClock.sleep(110);
            java.util.ArrayList<?> cards=(java.util.ArrayList<?>)member(carousel,"cards");android.view.View outgoing=(android.view.View)cards.get(0);
            glassCheck("carousel-stacks-and-translates-during-switch",outgoing.getScaleX()<.999f&&Math.abs(outgoing.getTranslationX())>1);
            SystemClock.sleep(500);glassCheck("carousel-commits-selected-preset",core("{\"op\":\"snapshot\"}").getJSONObject("settings").optString("preset").equals("racing"));
            android.view.View deck=(android.view.View)carousel;int[] deckXY=new int[2];runOnMainSync(()->deck.getLocationOnScreen(deckXY));
            float[] swipeStart={deckXY[0]+deck.getWidth()*.2f,deckXY[1]+deck.getHeight()*.5f},swipeEnd={deckXY[0]+deck.getWidth()*.8f,deckXY[1]+deck.getHeight()*.5f};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{swipeStart});touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{swipeEnd});touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{swipeEnd});SystemClock.sleep(500);
            glassCheck("horizontal-touch-swipe-changes-card",((Integer)member(carousel,"active"))==0);
            Class<?> previewType=previews.get(0).getClass();java.lang.reflect.Method blur=previewType.getDeclaredMethod("gaussian",int[].class,int.class,int.class,float.class);blur.setAccessible(true);
            int[] impulse=new int[81];impulse[40]=0xffffffff;int[] filtered=(int[])blur.invoke(null,impulse,9,9,1f);
            glassCheck("legacy-gaussian-fallback-spreads-focus-symmetrically",(filtered[40]>>>24)>(filtered[39]>>>24)&&(filtered[39]>>>24)>0&&filtered[39]==filtered[41]&&filtered[31]==filtered[49]&&impulse[39]==0);
            carouselSelect(carousel,3,false);SystemClock.sleep(200);glassCheck("card-selection-restores-wide-and-hidden-controls",core("{\"op\":\"snapshot\"}").getJSONObject("settings").getJSONObject("layout").getJSONObject("b").optBoolean("hidden"));
            glassCheck("switching-cards-does-not-overwrite-saved-templates",templates.toString().equals(activity.getPreferences(0).getString("templates_v1","")));
            glassShot("layout-saved-wide-card").recycle();
            final android.os.Handler uiHandler=(android.os.Handler)member(activity,"handler");final Runnable pollTask=(Runnable)member(activity,"poll");runOnMainSync(()->uiHandler.removeCallbacks(pollTask));
            invokeActivity("openController");SystemClock.sleep(900);Rect playA=bounds(waitNode("A",5000)),playStick=bounds(waitNode("左摇杆，模拟量输出",5000));
            invokeActivity("openEditor");SystemClock.sleep(650);Rect editA=bounds(waitNode("A",5000)),editStick=bounds(waitNode("左摇杆，模拟量输出",5000));
            glassCheck("editor-and-play-control-bounds-are-identical",playA.equals(editA)&&playStick.equals(editStick));runOnMainSync(()->uiHandler.post(pollTask));
            applyLayoutConfig(defaults,"通用游戏");SystemClock.sleep(200);
            String[] face={"X","A","B","Y"};Rect[] before=new Rect[4];for(int i=0;i<4;i++)before[i]=bounds(waitNode(face[i],5000));
            editorDrag("A",-100,-32);boolean together=true;for(int i=0;i<4;i++){Rect after=bounds(waitNode(face[i],5000));together&=Math.abs(after.centerX()-before[i].centerX()+100)<=2&&Math.abs(after.centerY()-before[i].centerY()+32)<=2;}
            glassCheck("face-buttons-drag-as-one-group-by-default",together);
            Rect up=bounds(waitNode("方向上",5000)),left=bounds(waitNode("方向左",5000));editorDrag("方向上",-65,-20);
            Rect movedUp=bounds(waitNode("方向上",5000)),movedLeft=bounds(waitNode("方向左",5000));glassCheck("dpad-drags-as-one-group-by-default",Math.abs(movedUp.centerX()-up.centerX()+65)<=2&&Math.abs(movedLeft.centerX()-left.centerX()+65)<=2);
            tapNode("A");waitNode("绑定 XABY 组合",5000);Object pad=member(activity,"pad");glassCheck("floating-editor-menu-does-not-shrink-canvas",((android.view.View)pad).getHeight()==((android.view.View)member(member(activity,"layoutEditor"),"root")).getHeight());
            glassShot("layout-floating-size-menu").recycle();tapNode("绑定 XABY 组合");Rect b=bounds(waitNode("B",5000));progress("控件宽度",9000);SystemClock.sleep(250);
            Rect stretched=bounds(waitNode("A",5000));Rect unchangedB=bounds(waitNode("B",5000));
            android.view.View canvas=(android.view.View)pad;int[] xy=new int[2];runOnMainSync(()->canvas.getLocationOnScreen(xy));
            glassCheck("unlinked-face-key-stretches-without-old-cap",stretched.width()>canvas.getWidth()*.7f&&stretched.left>=xy[0]&&stretched.right<=xy[0]+canvas.getWidth());
            glassCheck("unlinked-width-change-preserves-other-face-keys",b.equals(unchangedB));
            tapNode("完成");glassShot("layout-elongated-a").recycle();
            invokeActivity("finishEditor");
            org.json.JSONObject presetSaved=new org.json.JSONObject(activity.getPreferences(0).getString("preset_layouts_v1","{}"));
            glassCheck("save-and-return-updates-the-current-configuration-card",presetSaved.getJSONObject("general").getJSONObject("layout").getJSONObject("a").optDouble("width")>10);
            tapNode("＋ 保存为新模板");List<AccessibilityNodeInfo> inputs=new ArrayList<>();inputs(automation.getRootInActiveWindow(),inputs);setText(inputs.get(0),"自定义长键");tapNode("保存");
            org.json.JSONArray stored=new org.json.JSONArray(activity.getPreferences(0).getString("templates_v1","[]"));org.json.JSONObject config=stored.getJSONObject(stored.length()-1).getJSONObject("settings");
            glassCheck("group-binding-and-long-width-survive-template-save",!config.optBoolean("group_face",true)&&config.getJSONObject("layout").getJSONObject("a").optDouble("width")>10);
            glassShot("layout-saved-custom-card").recycle();
        }finally{
            applyLayoutConfig(original,old[1]==null?"通用游戏":old[1]);
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();for(int i=0;i<keys.length;i++){if(old[i]==null)restore.remove(keys[i]);else restore.putString(keys[i],old[i]);}restore.commit();
        }
    }
    private void nativeShell() throws Exception {
        String[] keys={"rust_settings","template_name","templates_v1","active_template_id","preset_layouts_v1"};String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)previous[i]=activity.getPreferences(0).getString(keys[i],null);
        try{
            waitContains("你的手柄",5000);phase("shell-home","","false");
            tapNode("导航主题");waitNode("主题",5000);phase("shell-theme","","false");
            tapNode("导航设置");tapNode("关于 Orbit Pad");waitContains("版本 0.5.0",5000);phase("shell-about","","false");
            tapNode("导航连接");waitNode("打开相机扫码",5000);phase("shell-scan-default","","false");
            tapNode("USB 直连");waitNode("连接 USB 手柄",5000);phase("shell-usb-option","","false");
            tapNode("蓝牙直连");waitNode("选择已配对的电脑",5000);phase("shell-bluetooth-option","","false");
            tapNode("导航主页");layoutCustomization();invokeActivity("finishEditor");
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
            glassCheck("long-press-raises-lens",held.height()>rest.height()*1.10f&&held.height()<rest.height()*1.24f&&held.width()<rest.width()*1.28f&&held.top<0);
            glassCheck("hold-does-not-navigate",waitNode("导航主页",5000).isSelected());
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{homePoint});SystemClock.sleep(120);motionShot("motion-release-wobble").recycle();SystemClock.sleep(1100);
            glassCheck("release-restores-lens",Math.abs(lensBounds(dock).height()-rest.height())<density*1.2f);
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{homePoint});SystemClock.sleep(120);
            dragPath(homePoint,themePoint,14,18);
            android.graphics.RectF stretched=lensBounds(dock);motionShot("motion-drag-right").recycle();
            glassCheck("drag-stretches-lens",stretched.width()>rest.width()*1.12f&&stretched.width()<rest.width()*1.42f);
            trace("motion-widths:hold="+held.width()+",drag="+stretched.width());
            glassCheck("inertia-adds-width-beyond-hold",stretched.width()>held.width()*1.005f&&stretched.width()<held.width()*1.12f);
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
    private void glassPolish() throws Exception {
        String quality=activity.getPreferences(0).getString("glass_quality",null);
        boolean hadStrength=activity.getPreferences(0).contains("glass_refraction");int strength=activity.getPreferences(0).getInt("glass_refraction",2);
        Object shell=member(activity,"shell");android.view.View root=(android.view.View)member(shell,"view"),dock=(android.view.View)member(shell,"glassDock");
        android.widget.FrameLayout page=(android.widget.FrameLayout)member(root,"page");
        java.lang.reflect.Method show=shell.getClass().getDeclaredMethod("show",int.class);show.setAccessible(true);
        java.lang.reflect.Method top=root.getClass().getDeclaredMethod("setTopFade",int.class,float.class);top.setAccessible(true);
        java.lang.reflect.Field contentFailed=dock.getClass().getDeclaredField("contentFailed");contentFailed.setAccessible(true);
        float density=activity.getResources().getDisplayMetrics().density;
        android.view.View stripes=new android.view.View(activity){final android.graphics.Paint p=new android.graphics.Paint();
            @Override protected void onDraw(android.graphics.Canvas c){for(int x=0;x<getWidth();x+=(int)(density*4)){p.setColor((x/(int)(density*4)%2)==0?0xfff4d077:0xff286aa8);c.drawRect(x,0,x+density*4,getHeight(),p);}}};
        try{
            runOnMainSync(()->{activity.getPreferences(0).edit().putString("glass_quality","auto").putInt("glass_refraction",2).commit();activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);root.invalidate();dock.invalidate();});
            tapNode("导航主页");SystemClock.sleep(800);
            android.widget.ScrollView scroll=(android.widget.ScrollView)member(shell,"currentScroll");
            runOnMainSync(()->{scroll.scrollTo(0,0);page.addView(stripes,new android.widget.FrameLayout.LayoutParams(-1,-1));});
            android.graphics.Bitmap clear=glassShot("polish-top-clear");
            glassCheck("top-veil-hidden-at-page-start",((Float)member(root,"topFade"))==0);
            runOnMainSync(()->scroll.scrollTo(0,(int)(density*88)));SystemClock.sleep(400);
            android.graphics.Bitmap blurred=glassShot("polish-top-blur");int height=(Integer)member(root,"topHeight"),inset=(Integer)member(shell,"safeTop");
            glassCheck("scroll-enables-top-veil",((Float)member(root,"topFade"))>.99f);
            Rect upper=new Rect((int)(density*24),inset,clear.getWidth()-(int)(density*24),height-(int)(density*22));
            Rect lower=new Rect(upper.left,height+(int)(density*20),upper.right,height+(int)(density*80));
            glassCheck("top-blur-changes-scrolled-pixels",pixelDifference(clear,blurred,upper)>50000);
            glassCheck("top-veil-leaves-lower-content-clear",pixelDifference(clear,blurred,lower)<1000);
            long high=0,low=0;int samples=0;
            for(int y=inset+2;y<height-(int)(density*22);y+=3)for(int x=upper.left+4;x<upper.right-4;x+=3){
                high+=Math.abs(android.graphics.Color.red(clear.getPixel(x,y))-android.graphics.Color.red(clear.getPixel(x+2,y)));
                low+=Math.abs(android.graphics.Color.red(blurred.getPixel(x,y))-android.graphics.Color.red(blurred.getPixel(x+2,y)));samples++;
            }
            glassCheck("gaussian-blur-reduces-stripe-contrast",high>samples*10&&low<high*.6);clear.recycle();blurred.recycle();
            runOnMainSync(()->scroll.scrollTo(0,0));SystemClock.sleep(300);glassCheck("return-to-top-removes-veil",((Float)member(root,"topFade"))==0);
            runOnMainSync(()->page.removeView(stripes));
            trace("record-liquid-motion");SystemClock.sleep(400);
            Rect h=bounds(waitNode("导航主页",5000)),settings=bounds(waitNode("导航设置",5000));
            float[] home={h.exactCenterX(),h.exactCenterY()},end={settings.exactCenterX(),settings.exactCenterY()};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{home});SystemClock.sleep(1000);
            // The upstream lens preserves its center. Move its rim over the home icon/label.
            float[] probePoint={home[0]+h.width()*.60f,home[1]};dragPath(home,probePoint,12,16);SystemClock.sleep(650);
            android.graphics.Bitmap refracted=motionShot("polish-content-refracted");
            glassCheck("icon-text-lens-active",member(dock,"contentLens")!=null&&!((Boolean)member(dock,"contentFailed")));
            Object combinedLens=member(dock,"contentLens");
            glassCheck("kyant-original-lens-is-used",member(combinedLens,"lens").getClass().getName().endsWith("KyantGlass$Lens"));
            glassCheck("combined-backdrop-is-recorded",((android.graphics.RenderNode)member(combinedLens,"combined")).hasDisplayList());
            runOnMainSync(()->{try{contentFailed.setBoolean(dock,true);}catch(Exception error){throw new RuntimeException(error);}dock.invalidate();});SystemClock.sleep(120);
            android.graphics.Bitmap plain=motionShot("polish-content-plain");
            glassCheck("refraction-warps-icon-and-text-pixels",pixelDifference(refracted,plain,new Rect(h.left+10,h.top+8,h.right-10,h.bottom-8))>8000);
            Rect outside=new Rect(settings.left+10,settings.top+8,settings.right-10,settings.bottom-8);
            glassCheck("uncovered-icons-stay-unwarped",pixelDifference(refracted,plain,outside)<1500);refracted.recycle();plain.recycle();
            runOnMainSync(()->{try{contentFailed.setBoolean(dock,false);}catch(Exception error){throw new RuntimeException(error);}dock.invalidate();});
            dragPath(probePoint,end,20,16);touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{end});SystemClock.sleep(600);
            glassCheck("slider-release-navigates-settings",waitNode("导航设置",5000).isSelected());
            runOnMainSync(()->{try{show.invoke(shell,0);}catch(Exception error){throw new RuntimeException(error);}});
            android.widget.ScrollView incoming=(android.widget.ScrollView)member(shell,"currentScroll");
            glassCheck("leftward-page-transition-starts",member(shell,"pageTransition")!=null&&incoming.getTranslationX()<0&&page.getChildCount()==2);
            SystemClock.sleep(65);motionShot("polish-transition-left").recycle();SystemClock.sleep(500);
            glassCheck("transition-cleans-outgoing-page",page.getChildCount()==1&&member(shell,"pageTransition")==null&&incoming.getAlpha()==1&&incoming.getTranslationX()==0);
            runOnMainSync(()->{try{show.invoke(shell,3);}catch(Exception error){throw new RuntimeException(error);}});
            incoming=(android.widget.ScrollView)member(shell,"currentScroll");
            glassCheck("rightward-page-transition-starts",member(shell,"pageTransition")!=null&&incoming.getTranslationX()>0);SystemClock.sleep(65);motionShot("polish-transition-right").recycle();SystemClock.sleep(500);
            runOnMainSync(()->{try{show.invoke(shell,0);show.invoke(shell,1);show.invoke(shell,3);}catch(Exception error){throw new RuntimeException(error);}});
            glassCheck("rapid-switch-retains-only-two-pages",page.getChildCount()<=2);SystemClock.sleep(550);
            glassCheck("rapid-switch-finishes-on-last-page",waitNode("导航设置",5000).isSelected()&&page.getChildCount()==1&&member(shell,"outgoingScroll")==null);
            android.view.View same=(android.view.View)member(shell,"currentScroll");tapNode("导航设置");
            glassCheck("same-tab-does-not-restart-transition",same==member(shell,"currentScroll")&&member(shell,"pageTransition")==null);
            glassCheck("polish-renderers-stay-active",!((Boolean)member(root,"failed"))&&!((Boolean)member(dock,"contentFailed")));
            tapNode("关于 Orbit Pad");tapNode("AndroidLiquidGlass 开源许可");waitContains("Copyright 2025 Kyant",5000);
            glassCheck("upstream-license-readable-in-apk",find(automation.getRootInActiveWindow(),"Apache License",true)!=null);
            key(KeyEvent.KEYCODE_BACK);glassCheck("license-dismiss-keeps-settings",waitNode("导航设置",5000).isSelected());
            tapNode("导航主页");SystemClock.sleep(500);motionShot("polish-home").recycle();
        }finally{
            runOnMainSync(()->{page.removeView(stripes);try{contentFailed.setBoolean(dock,false);}catch(Exception ignored){}dock.invalidate();});
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();if(quality==null)restore.remove("glass_quality");else restore.putString("glass_quality",quality);
            if(hadStrength)restore.putInt("glass_refraction",strength);else restore.remove("glass_refraction");restore.commit();
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
    private org.json.JSONObject core(String command) throws Exception {
        return new org.json.JSONObject(coreRaw(command));
    }
    private String coreRaw(String command) throws Exception {
        Class<?> bridge=activity.getClassLoader().loadClass("app.orbitpad.controller.NativeBridge");
        java.lang.reflect.Method method=bridge.getDeclaredMethod("call",String.class);method.setAccessible(true);
        return (String)method.invoke(null,command);
    }
    private void invokeActivity(String method) throws Exception {
        java.lang.reflect.Method target=activity.getClass().getDeclaredMethod(method);target.setAccessible(true);
        runOnMainSync(()->{try{target.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
    }
    private void racingControls() throws Exception {
        String saved=activity.getPreferences(0).getString("rust_settings",null);
        String savedTemplate=activity.getPreferences(0).getString("template_name","通用游戏");
        if(savedTemplate.equals("racing-test"))savedTemplate="通用游戏";
        final String templateName=savedTemplate;
        org.json.JSONObject original=core("{\"op\":\"snapshot\"}").getJSONObject("settings");
        try{
            invokeActivity("openEditor");SystemClock.sleep(800);
            // Measure from a known size even when the saved user layout is already enlarged.
            tapNode("A");progress("控件大小",0);key(KeyEvent.KEYCODE_BACK);
            Rect before=bounds(waitNode("A",5000));tapNode("A");progress("控件大小",5000);key(KeyEvent.KEYCODE_BACK);
            Rect after=bounds(waitNode("A",5000));
            if(after.width()<before.width()*1.35f)throw new AssertionError("Live size preview did not resize A");
            tapNode("LB");progress("控件宽度",2615);progress("控件高度",2536);key(KeyEvent.KEYCODE_BACK);
            org.json.JSONObject layout=core("{\"op\":\"snapshot\"}").getJSONObject("settings").getJSONObject("layout");
            if(Math.abs(layout.getJSONObject("lb").getDouble("width")-2.1)>.01||Math.abs(layout.getJSONObject("lb").getDouble("height")-2.6)>.01)throw new AssertionError("Independent shoulder dimensions not saved");
            // Restore defaults, then place a vertical brake in free canvas space.
            key(KeyEvent.KEYCODE_BACK);
            org.json.JSONObject config=new org.json.JSONObject(original.toString());config.put("linear_triggers",true);config.put("gyro_enabled",false);
            org.json.JSONObject positions=new org.json.JSONObject();positions.put("lt",new org.json.JSONObject("{\"x\":500,\"y\":330,\"width\":0.6,\"height\":4}"));config.put("layout",positions);
            java.lang.reflect.Method apply=activity.getClass().getDeclaredMethod("applyTemplate",org.json.JSONObject.class,String.class);apply.setAccessible(true);
            runOnMainSync(()->{try{apply.invoke(activity,config,templateName);}catch(Exception e){throw new RuntimeException(e);}});
            connect(arguments.getString("address"),arguments.getString("code"));waitNode("A",15000);SystemClock.sleep(700);
            waitNode("Xbox · 呼出 Game Bar",5000);phase("racing-xbox-visible","","true");
            Rect lt=bounds(waitNode("LT 模拟扳机",5000)),rt=bounds(waitNode("RT 模拟扳机",5000));
            if(lt.height()<=lt.width())throw new AssertionError("LT did not become a vertical pedal");
            float[] brake={lt.exactCenterX(),lt.top+lt.height()*.25f},gas={rt.left+rt.width()*.75f,rt.exactCenterY()};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{brake});phase("racing-brake-quarter","lt","true");
            touch(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},new float[][]{brake,gas});phase("racing-two-pedals","lt,rt","true");
            touch(MotionEvent.ACTION_POINTER_UP|(0<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),new int[]{0,1},new float[][]{brake,gas});phase("racing-brake-released","rt","true");
            touch(MotionEvent.ACTION_CANCEL,new int[]{1},new float[][]{gas});phase("racing-cancel-neutral","","true");
            tapNode("手柄设置");tapNode("手感调节");waitNode("LT / RT 线性油门 / 刹车",5000);phase("racing-feel-settings","","true");key(KeyEvent.KEYCODE_BACK);
            Object gyro=member(activity,"gyro");
            Class<?> gyroClass=gyro.getClass();java.lang.reflect.Method available=gyroClass.getDeclaredMethod("available");available.setAccessible(true);
            if(!(Boolean)available.invoke(gyro))throw new AssertionError("Test device has no rotation sensor");
            java.lang.reflect.Method angle=gyroClass.getDeclaredMethod("relativeAngle",float[].class,float[].class);angle.setAccessible(true);
            double half=Math.toRadians(23.5)/2;float[] zero={1,0,0,0},right={(float)Math.cos(half),0,0,(float)-Math.sin(half)};
            if(Math.abs((Float)angle.invoke(null,zero,right)-23.5f)>.01)throw new AssertionError("Relative quaternion steering angle incorrect");
            steeringPresets();
            org.json.JSONObject settings=(org.json.JSONObject)member(activity,"settings");settings.put("gyro_enabled",true);invokeActivity("saveSettings");SystemClock.sleep(300);
            if(!(Boolean)member(gyro,"running"))throw new AssertionError("Gyro listener not registered while controlling");
            org.json.JSONArray controls=new org.json.JSONArray(coreRaw("{\"op\":\"layout\"}"));
            boolean recenter=false;for(int i=0;i<controls.length();i++)if(controls.getJSONObject(i).getString("id").equals("calibrate"))recenter=true;
            if(!recenter)throw new AssertionError("Manual recenter control missing");
            java.lang.reflect.Method calibrate=gyroClass.getDeclaredMethod("calibrate"),calibrating=gyroClass.getDeclaredMethod("calibrating");calibrate.setAccessible(true);calibrating.setAccessible(true);
            runOnMainSync(()->{try{calibrate.invoke(gyro);}catch(Exception e){throw new RuntimeException(e);}});
            if(!(Boolean)calibrating.invoke(gyro))throw new AssertionError("Manual calibration did not start");
            long deadline=SystemClock.uptimeMillis()+5000;while((Boolean)calibrating.invoke(gyro)&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50);
            if((Boolean)calibrating.invoke(gyro))throw new AssertionError("Stable phone did not finish calibration");
            phase("racing-gyro-calibrated","","true");
            // Already enabled: each new controller entry must restart calibration automatically.
            runOnMainSync(()->{try{java.lang.reflect.Method open=activity.getClass().getDeclaredMethod("openController");open.setAccessible(true);open.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(200);
            if(!(Boolean)calibrating.invoke(gyro))throw new AssertionError("Already enabled gyro skipped startup calibration");
            org.json.JSONObject during=core("{\"op\":\"snapshot\"}").getJSONObject("state");
            if(during.getJSONArray("axes").getDouble(0)!=0)throw new AssertionError("Startup calibration emitted steering");
            SystemClock.sleep(1000);
            if((Boolean)calibrating.invoke(gyro))throw new AssertionError("Startup calibration did not finish");
            phase("racing-gyro-startup-calibrated","","true");
            tapNode("手柄设置");SystemClock.sleep(100);
            if((Boolean)member(gyro,"running"))throw new AssertionError("Gyro listener remained registered in settings");
            phase("racing-gyro-settings-neutral","","true");key(KeyEvent.KEYCODE_BACK);
            returnToNative();SystemClock.sleep(200);
            if((Boolean)member(gyro,"running"))throw new AssertionError("Gyro listener remained registered after disconnect");
            phase("racing-disconnected","","false");
        }finally{
            core(new org.json.JSONObject().put("op","configure").put("settings",original).toString());
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();if(saved==null)restore.remove("rust_settings");else restore.putString("rust_settings",saved);restore.putString("template_name",templateName);restore.commit();
        }
    }
    private void steeringPresets() throws Exception {
        android.widget.Spinner[] ratio=new android.widget.Spinner[1];
        runOnMainSync(()->{try{
            android.widget.LinearLayout page=new android.widget.LinearLayout(activity);
            java.lang.reflect.Method feel=activity.getClass().getDeclaredMethod("feelSettings",android.widget.LinearLayout.class);feel.setAccessible(true);feel.invoke(activity,page);
            for(int i=0;i<page.getChildCount();i++)if("陀螺仪转向比".contentEquals(page.getChildAt(i).getContentDescription()==null?"":page.getChildAt(i).getContentDescription()))ratio[0]=(android.widget.Spinner)page.getChildAt(i);
            if(ratio[0]==null)throw new AssertionError("Steering preset selector missing");
            ratio[0].setSelection(0);
        }catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        if(ratio[0].getCount()!=2||!ratio[0].getItemAtPosition(1).equals("自定义"))throw new AssertionError("Only F1 and custom presets should be offered");
        // Invoke the selection listener without attaching a second settings page to the window.
        runOnMainSync(()->{
            ratio[0].setSelection(0);
            ratio[0].getOnItemSelectedListener().onItemSelected(ratio[0],null,0,0);
        });waitForIdleSync();
        if(core("{\"op\":\"snapshot\"}").getJSONObject("settings").getInt("gyro_range")!=180)throw new AssertionError("F1 steering preset not applied");
        org.json.JSONObject saved=new org.json.JSONObject(activity.getPreferences(0).getString("rust_settings","{}"));
        if(saved.getInt("gyro_range")!=180)throw new AssertionError("F1 steering preset not persisted");
    }
    private void nativeGamepad() throws Exception {
        connect(arguments.getString("address"),arguments.getString("code"));
        waitNode("A",15000);SystemClock.sleep(800);phase("native-connected","","true");
        hold("A","a","native-a");
        tapNode("手柄设置");tapNode("手感调节");if(!waitNode("LT / RT 线性油门 / 刹车",5000).isChecked())tapNode("LT / RT 线性油门 / 刹车");key(KeyEvent.KEYCODE_BACK);
        Rect lt=bounds(waitNode("LT 模拟扳机",5000));
        float[] lc={lt.right-1,lt.exactCenterY()};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{lc});
        phase("native-lt-full","lt","true");
        touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{lt.exactCenterX(),lc[1]}});
        phase("native-lt-half","lt","true");
        touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{lc});phase("native-lt-released","","true");
        Rect rt=bounds(waitNode("RT 模拟扳机",5000));float[] rc={rt.exactCenterX(),rt.exactCenterY()};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{rc});
        touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{rt.left+rt.width()*.25f,rc[1]}});
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
            if(scroll!=null)scroll.performAction(node!=null&&bounds(node).bottom<=bounds(scroll).top?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(150);
        }
        throw new AssertionError("UI node not found: " + text);
    }
    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isScrollable())return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=findScrollable(node.getChild(i));if(result!=null)return result;}return null;}
    private void returnToNative() {
        key(KeyEvent.KEYCODE_BACK);
        waitContains("你的手柄",5000);tapNode("导航连接");tapNode("断开当前连接");
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
