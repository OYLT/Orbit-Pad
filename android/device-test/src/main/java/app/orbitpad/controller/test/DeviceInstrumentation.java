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
                {"A", "Space"}, {"B", "E"}, {"X", "R"}, {"Y", "F"},
                {"LB", "Q"}, {"RB", "C"}, {"LT CTRL", "Control"}, {"RT SHIFT", "Shift"},
                {"View 背包", "Tab"}, {"Menu 暂停", "Escape"},
                {"方向上", "ArrowUp"}, {"方向下", "ArrowDown"}, {"方向左", "ArrowLeft"}, {"方向右", "ArrowRight"}
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

            Rect left = bounds(waitNode("左摇杆，映射 WASD", 5000));
            Rect right = bounds(waitNode("右摇杆，映射方向键", 5000));
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
            phase("left-stick-diagonal", "D,W", "true");
            touch(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1}, new float[][]{l1,r0});
            touch(MotionEvent.ACTION_MOVE, new int[]{0,1}, new float[][]{l1,r1});
            phase("dual-stick", "D,W,ArrowLeft,ArrowUp", "true");
            touch(MotionEvent.ACTION_POINTER_DOWN | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1,2}, new float[][]{l1,r1,a0});
            phase("three-finger-dual-stick-and-a", "D,W,ArrowLeft,ArrowUp,Space", "true");
            touch(MotionEvent.ACTION_POINTER_UP | (2 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1,2}, new float[][]{l1,r1,a0});
            phase("a-released-sticks-held", "D,W,ArrowLeft,ArrowUp", "true");
            touch(MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1}, new float[][]{l1,r1});
            phase("right-released-left-held", "D,W", "true");
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
            phase("background-key-held", "Space", "true");
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
            tapNode("导航关于");waitContains("版本 0.5.0",5000);phase("shell-about","","false");
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
            waitNode("A",15000);SystemClock.sleep(600);phase("shell-qr-paired","","true");hold("A","Space","shell-qr-a");returnToNative();phase("shell-qr-disconnect","","false");
        }finally{
            android.content.SharedPreferences.Editor restore=activity.getPreferences(0).edit();for(int i=0;i<keys.length;i++){if(previous[i]==null)restore.remove(keys[i]);else restore.putString(keys[i],previous[i]);}restore.commit();
        }
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
        phase(prefix+"-auto-paired","","true");hold("A","Space",prefix+"-a");
        Rect left=bounds(waitNode("左摇杆，映射 WASD",5000)),right=bounds(waitNode("右摇杆，映射方向键",5000)),a=bounds(waitNode("A",5000));
        float[] l={left.exactCenterX()+left.width()*.3f,left.exactCenterY()-left.height()*.3f};float[] r={right.exactCenterX()-right.width()*.3f,right.exactCenterY()-right.height()*.3f};float[] button={a.exactCenterX(),a.exactCenterY()};
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{l});
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{0,1},new float[][]{l,r});touch(MotionEvent.ACTION_POINTER_DOWN|(2<<8),new int[]{0,1,2},new float[][]{l,r,button});
        phase(prefix+"-three-finger","D,W,ArrowLeft,ArrowUp,Space","true");touch(MotionEvent.ACTION_CANCEL,new int[]{0,1,2},new float[][]{l,r,button});phase(prefix+"-cancel-releases","","true");
        downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{button});phase(prefix+"-held-before-home","Space","true");key(KeyEvent.KEYCODE_HOME);SystemClock.sleep(800);phase(prefix+"-background-release","","false");
        trace("resume-activity");SystemClock.sleep(prefix.equals("bluetooth")?5000:1500);waitNode("A",15000);phase(prefix+"-foreground-reconnected","","true");returnToNative();phase(prefix+"-disconnected","","false");
    }
    private void nativeSettings() throws Exception {
        String saved=activity.getPreferences(0).getString("rust_settings", null);
        try {
            connect(arguments.getString("address"), arguments.getString("code"));
            waitNode("A",15000);SystemClock.sleep(1000);
            Rect originalLeft=bounds(waitNode("左摇杆，映射 WASD",5000));
            tapNode("手柄设置");tapNode("竞速");key(KeyEvent.KEYCODE_BACK);
            hold("B","R","native-racing-b");hold("RT SHIFT","W","native-racing-rt");
            tapNode("手柄设置");tapNode("跳跃");key(KeyEvent.KEYCODE_BACK);
            Rect left=bounds(waitNode("左摇杆，映射 WASD",5000));
            float[] center={left.exactCenterX(),left.exactCenterY()};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{center});
            touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{center[0]+left.width()*.4f,center[1]}});
            phase("native-platform-stick","ArrowRight","true");
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{center});phase("native-platform-release","","true");
            tapNode("手柄设置");tapNode("通用");tapNode("A · 跳跃映射");tapNode("Enter");
            key(KeyEvent.KEYCODE_BACK);hold("A","Enter","native-custom-a");
            tapNode("手柄设置");tapNode("手感调节");
            progress("摇杆死区",40);waitContains("摇杆死区 · 50%",5000);
            progress("显示比例",0);waitContains("显示比例 · 80%",5000);
            AccessibilityNodeInfo haptics=waitNode("振动反馈",5000);
            if(haptics.isChecked())tapNode("振动反馈");
            if(waitNode("振动反馈",5000).isChecked())throw new AssertionError("Haptics switch did not turn off");
            phase("native-settings-saved","","true");key(KeyEvent.KEYCODE_BACK);
            left=bounds(waitNode("左摇杆，映射 WASD",5000));
            if(Math.abs(left.width()/(float)originalLeft.width()-.8f)>.03f)throw new AssertionError("Control scale did not change to 80%");
            phase("native-control-scale-80","","true");
            center=new float[]{left.exactCenterX(),left.exactCenterY()};
            downTime=SystemClock.uptimeMillis();touch(MotionEvent.ACTION_DOWN,new int[]{0},new float[][]{center});
            touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{center[0]+left.width()*.2f,center[1]}});
            phase("native-deadzone-50-small","","true");
            touch(MotionEvent.ACTION_MOVE,new int[]{0},new float[][]{{center[0]+left.width()*.4f,center[1]}});
            phase("native-deadzone-50-large","D","true");
            touch(MotionEvent.ACTION_UP,new int[]{0},new float[][]{center});phase("native-deadzone-release","","true");
            runOnMainSync(()->activity.recreate());
            waitContains("你的手柄",10000);SystemClock.sleep(500);
            connect(arguments.getString("address"),arguments.getString("code"));waitNode("A",15000);SystemClock.sleep(600);
            hold("A","Enter","native-recreated-custom-a");
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
