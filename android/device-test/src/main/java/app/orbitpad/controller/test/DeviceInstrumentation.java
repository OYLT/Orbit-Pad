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
        Rect rect = bounds(waitNode(name, 5000));
        float[][] point = {{rect.exactCenterX(), rect.exactCenterY()}};
        downTime = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, new int[]{0}, point);
        touch(MotionEvent.ACTION_UP, new int[]{0}, point);
    }
    private Rect bounds(AccessibilityNodeInfo node) { Rect rect = new Rect(); node.getBoundsInScreen(rect); return rect; }
    private void connect(String address, String code) {
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
            if (node != null && !bounds(node).isEmpty()) return node;
            SystemClock.sleep(150);
        }
        throw new AssertionError("UI node not found: " + text);
    }
    private void returnToNative() {
        key(KeyEvent.KEYCODE_BACK);
        waitNode("断开", 5000).performAction(AccessibilityNodeInfo.ACTION_CLICK);
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
