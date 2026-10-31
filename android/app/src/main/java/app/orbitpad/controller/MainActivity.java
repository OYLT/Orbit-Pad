package app.orbitpad.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Native Android widgets and drawing. All controller behavior is delegated to Rust. */
public final class MainActivity extends Activity {
    private static final int BG=0xff141218, FG=0xffe6e0e9, PRIMARY=0xffd0bcff,
        MUTED=0xffcac4d0, SURFACE=0xff2b2930, OUTLINE=0xff938f99;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private PadView pad;
    JSONObject snapshot=new JSONObject(), settings=new JSONObject();
    private boolean controllerVisible, checking, resumed, editing;
    private AppShell shell;
    private BluetoothProxy bluetooth;
    private LayoutEditor layoutEditor;
    private AlertDialog settingsDialog;
    private GyroSteering gyro;
    private void syncGyro(){
        if(gyro==null)return;
        if(resumed&&controllerVisible&&snapshot.optBoolean("enabled")&&settings.optBoolean("gyro_enabled"))gyro.start();else gyro.stop();
    }
    private final Runnable poll=new Runnable() { public void run() {
        if (!resumed) return;
        snapshot=call("snapshot");
        if (checking) {
            if (snapshot.optBoolean("connected")) { checking=false; openController(); }
            else if (!snapshot.optBoolean("reconnectable")) { checking=false; if(shell!=null)shell.error(snapshot.optString("message")); }
        }
        if (controllerVisible && !snapshot.optBoolean("reconnectable")) {
            String error=snapshot.optString("message"); showConnection(error);
        }
        if (shell!=null && !controllerVisible && !editing) shell.update(snapshot);
        if (status!=null && controllerVisible) {
            String latency=snapshot.isNull("rtt") ? "" : " · "+snapshot.optLong("rtt")+" ms";
            status.setText(gyro!=null&&gyro.calibrating()?"陀螺仪校准中 · 保持手机稳定…":"Orbit Pad  ·  "+snapshot.optString("message")+latency);
        }
        syncGyro();
        if (pad!=null) pad.refresh();
        handler.postDelayed(this,50);
    }};

    JSONObject command(String op) { JSONObject value=new JSONObject(); put(value,"op",op); return value; }
    void put(JSONObject object,String key,Object value) { try {object.put(key,value);} catch(Exception error) {throw new IllegalStateException(error);} }
    JSONObject call(String op) { return call(command(op)); }
    JSONObject call(JSONObject value) {
        try {return new JSONObject(NativeBridge.call(value.toString()));}
        catch(Exception error) {JSONObject failure=new JSONObject();put(failure,"error","手柄核心返回异常");return failure;}
    }
    int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    GradientDrawable rounded(int color,int border,int radius) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));
        if(border!=0)shape.setStroke(dp(1),border);return shape;
    }
    TextView text(String value,int size,int color) {
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(4),1);return view;
    }
    void add(LinearLayout parent,View child,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);
    }
    Button button(String label) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(0xff381e72);b.setTextSize(14);
        b.setMinHeight(dp(50));b.setPadding(dp(20),dp(10),dp(20),dp(10));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33381e72),rounded(PRIMARY,0,28),null));return b;
    }
    EditText field(String hint) {
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(FG);e.setTextSize(16);e.setHintTextColor(OUTLINE);e.setHint(hint);
        e.setMinHeight(dp(56));e.setPadding(dp(16),dp(16),dp(16),dp(16));e.setBackground(rounded(BG,OUTLINE,12));
        e.setOnFocusChangeListener((v,focus)->e.setBackground(rounded(BG,focus?PRIMARY:OUTLINE,12)));return e;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);edgeToEdge();
        gyro=new GyroSteering(this,angle->{JSONObject c=command("gyro");put(c,"angle",angle);snapshot=call(c);if(pad!=null)pad.refresh();});
        try {
            String saved=getPreferences(0).getString("rust_settings",null);
            if(saved!=null){JSONObject c=command("configure");put(c,"settings",new JSONObject(saved));call(c);}
        } catch(Exception ignored) { /* Invalid saved settings leave Rust defaults intact. */ }
        settings=call("snapshot").optJSONObject("settings");showShell(0,null);
        if(state!=null&&shell!=null)shell.restoreState(state);
    }
    @Override protected void onSaveInstanceState(Bundle state){if(shell!=null)shell.saveState(state);super.onSaveInstanceState(state);}
    private void showConnection(String error) {
        disconnectTransport();showShell(1,error);
    }
    private void showShell(int page,String error) {
        if(gyro!=null)gyro.stop();
        controllerVisible=false;editing=false;layoutEditor=null;pad=null;status=null;
        if(settingsDialog!=null)settingsDialog.dismiss();
        snapshot=call("release");JSONObject c=command("settings");put(c,"open",true);snapshot=call(c);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        edgeToEdge();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        shell=new AppShell(this,page,error);setContentView(shell.view);
    }
    boolean isConnecting(){return checking;}
    void disconnectTransport(){checking=false;snapshot=call("disconnect");if(bluetooth!=null){bluetooth.close();bluetooth=null;}}
    void startConnection(String address,String code,String method) {
        if(checking)return;
        if(!method.equals("bluetooth")&&bluetooth!=null){bluetooth.close();bluetooth=null;}
        JSONObject c=command("connect");put(c,"address",address);put(c,"code",code);put(c,"method",method);snapshot=call(c);
        if(snapshot.has("error")){if(shell!=null)shell.error(snapshot.optString("error"));return;}
        if(method.equals("manual")||method.equals("scan"))getPreferences(0).edit().putString("receiver",address.trim()).apply();
        checking=true;if(shell!=null)shell.update(snapshot);
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);
    }
    void openScanner(){startActivityForResult(new Intent(this,ScanActivity.class),40);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==40&&result==RESULT_OK&&data!=null)connectQr(data.getStringExtra("qr"));
    }
    void connectQr(String raw){
        JSONObject c=command("parse_qr");put(c,"value",raw);JSONObject pair=call(c);
        if(pair.has("error")){if(shell!=null)shell.error(pair.optString("error"));return;}
        startConnection(pair.optString("address"),pair.optString("code"),"scan");
    }
    void chooseBluetooth(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},51);return;}
        try{
            BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){if(shell!=null)shell.error("请先在系统蓝牙设置中开启蓝牙并配对电脑");return;}
            ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
            if(devices.isEmpty()){if(shell!=null)shell.error("尚无已配对设备，请先打开系统蓝牙配对");return;}
            String[] names=new String[devices.size()];for(int i=0;i<names.length;i++){BluetoothDevice d=devices.get(i);names[i]=(d.getName()==null?"蓝牙设备":d.getName())+" · "+d.getAddress();}
            new AlertDialog.Builder(this).setTitle("选择运行 Orbit Pad 接收端的电脑").setItems(names,(dialog,index)->{
                disconnectTransport();
                try{bluetooth=new BluetoothProxy(devices.get(index));startConnection("127.0.0.1:"+bluetooth.port(),"","bluetooth");}
                catch(Exception e){if(shell!=null)shell.error("蓝牙连接启动失败："+e.getMessage());}
            }).setNegativeButton("取消",null).show();
        }catch(SecurityException error){if(shell!=null)shell.error("需要附近设备权限以连接已配对电脑");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==51&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)chooseBluetooth();
        else if(request==51&&shell!=null)shell.error("蓝牙权限未授予，可以使用扫码或 USB 连接");
    }
    void applyTemplate(JSONObject config,String name){
        if(config==null)return;JSONObject c=command("configure");put(c,"settings",config);JSONObject result=call(c);
        if(result.has("error")){Toast.makeText(this,result.optString("error"),Toast.LENGTH_LONG).show();return;}
        settings=result.optJSONObject("settings");snapshot=result;getPreferences(0).edit().putString("template_name",name).putString("rust_settings",settings.toString()).apply();if(pad!=null)pad.requestLayout();
    }
    /** Keep the system gesture handle visible over app content, including HyperOS 3. */
    private void edgeToEdge(){
        android.view.Window window=getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=28)window.setNavigationBarDividerColor(android.graphics.Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=29){window.setNavigationBarContrastEnforced(false);window.setStatusBarContrastEnforced(false);}
        if(Build.VERSION.SDK_INT>=30){
            window.getDecorView().setSystemUiVisibility(0);
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller=window.getInsetsController();
            if(controller!=null){controller.show(WindowInsets.Type.systemBars());controller.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
        }else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    void openController() {
        if(gyro!=null)gyro.stop();
        editing=false;layoutEditor=null;controllerVisible=true;shell=null;JSONObject c=command("settings");put(c,"open",false);snapshot=call(c);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);immersive();pad=new PadView();setContentView(pad);
    }
    void saveSettings() {
        JSONObject c=command("configure");put(c,"settings",settings);JSONObject result=call(c);
        if(result.has("error")){Toast.makeText(this,result.optString("error"),Toast.LENGTH_LONG).show();return;}
        getPreferences(0).edit().putString("rust_settings",settings.toString()).apply();snapshot=result;if(pad!=null)pad.requestLayout();
    }
    void openEditor(){
        if(gyro!=null)gyro.stop();controllerVisible=false;editing=true;shell=null;
        JSONObject c=command("settings");put(c,"open",true);snapshot=call(c);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);immersive();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pad=new PadView();layoutEditor=new LayoutEditor(this,pad);setContentView(layoutEditor.root);
    }
    void finishEditor(){if(editing)persistEditedTemplate();showShell(0,null);}
    private void persistEditedTemplate(){
        try{
            String name=getPreferences(0).getString("template_name","通用游戏"),active=getPreferences(0).getString("active_template_id","");
            JSONArray templates=new JSONArray(getPreferences(0).getString("templates_v1","[]"));JSONObject chosen=null;
            for(int i=0;i<templates.length();i++){JSONObject item=templates.optJSONObject(i);if(item!=null&&name.equals(item.optString("name"))&&(chosen==null||active.equals(item.optString("id"))))chosen=item;}
            if(chosen!=null){put(chosen,"settings",new JSONObject(settings.toString()));getPreferences(0).edit().putString("templates_v1",templates.toString()).putString("active_template_id",chosen.optString("id")).apply();return;}
            String[] names={"通用游戏","竞速驾驶","平台跳跃"},ids={"general","racing","platform"};
            for(int i=0;i<names.length;i++)if(name.equals(names[i])){JSONObject saved=new JSONObject(getPreferences(0).getString("preset_layouts_v1","{}"));put(saved,ids[i],new JSONObject(settings.toString()));getPreferences(0).edit().putString("preset_layouts_v1",saved.toString()).putString("active_template_id",ids[i]).apply();break;}
        }catch(Exception error){Toast.makeText(this,"布局已保存在当前手柄，可另存为新模板",Toast.LENGTH_SHORT).show();}
    }
    void refreshPad(){if(pad!=null)pad.refresh();}
    float padUnit(){return pad==null?1:Math.min((pad.getWidth()-pad.getPaddingLeft()-pad.getPaddingRight())/1000f,(pad.getHeight()-pad.getPaddingTop()-pad.getPaddingBottom())/450f);}
    JSONArray layoutFor(JSONObject config){
        JSONObject c=command("preview_layout");put(c,"settings",config);
        try{return new JSONArray(NativeBridge.call(c.toString()));}catch(Exception error){throw new IllegalStateException("模板预览无效",error);}
    }
    JSONObject placement(String id){
        JSONObject layout=settings.optJSONObject("layout");if(layout==null){layout=new JSONObject();put(settings,"layout",layout);}
        JSONObject p=layout.optJSONObject(id);if(p==null){p=new JSONObject();put(layout,id,p);}return p;
    }
    void visibleControls(){
        try{
            JSONArray all=new JSONArray(NativeBridge.call(command("layout").toString()));ArrayList<String> ids=new ArrayList<>(),labels=new ArrayList<>();
            for(int i=0;i<all.length();i++){JSONObject p=all.getJSONObject(i);if(!p.optString("id").equals("settings")){ids.add(p.optString("id"));labels.add(p.optString("description"));}}
            boolean[] visible=new boolean[ids.size()];for(int i=0;i<visible.length;i++)visible[i]=!placement(ids.get(i)).optBoolean("hidden");
            new AlertDialog.Builder(this).setTitle("显示控件").setMultiChoiceItems(labels.toArray(new String[0]),visible,(d,index,on)->{put(placement(ids.get(index)),"hidden",!on);saveSettings();})
                .setPositiveButton("完成",null).show();
        }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void editControl(String id){if(layoutEditor!=null)layoutEditor.edit(id);}
    void showSettings() {
        if(settingsDialog!=null)return;if(gyro!=null)gyro.stop();JSONObject c=command("settings");put(c,"open",true);snapshot=call(c);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(12));body.setBackgroundColor(SURFACE);
        LinearLayout tabs=new LinearLayout(this);Button mapping=button("按键映射"),feel=button("手感调节");
        tabs.addView(mapping,new LinearLayout.LayoutParams(0,-2,1));tabs.addView(feel,new LinearLayout.LayoutParams(0,-2,1));add(body,tabs,0);
        ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);scroll.addView(page);
        int available=getResources().getDisplayMetrics().heightPixels-dp(190);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(230),Math.max(dp(100),available))));
        mapping.setOnClickListener(v->mappingSettings(page));feel.setOnClickListener(v->feelSettings(page));mappingSettings(page);
        settingsDialog=new AlertDialog.Builder(this).setTitle("手柄设置").setView(body).setPositiveButton("完成",null).setNeutralButton("返回主页",(d,w)->showShell(0,null)).create();
        settingsDialog.setOnDismissListener(d->{JSONObject close=command("settings");put(close,"open",!controllerVisible||editing);snapshot=call(close);settingsDialog=null;if(controllerVisible)immersive();});
        settingsDialog.show();settingsDialog.getWindow().setBackgroundDrawable(rounded(SURFACE,0,28));settingsDialog.getButton(-1).setTextColor(PRIMARY);
    }
    private void mappingSettings(LinearLayout page) {
        page.removeAllViews();LinearLayout presets=new LinearLayout(this);
        String[] names={"通用","竞速","跳跃"},ids={"general","racing","platform"};
        for(int i=0;i<names.length;i++){final String id=ids[i];Button b=button(names[i]);presets.addView(b,new LinearLayout.LayoutParams(0,-2,1));
            b.setOnClickListener(v->{JSONObject c=command("preset");put(c,"name",id);JSONObject preset=call(c);put(settings,"mapping",preset.optJSONObject("mapping"));put(settings,"preset",id);saveSettings();mappingSettings(page);});}
        add(page,presets,12);
        String[] controls={"a","b","x","y","lb","rb","lt","rt","select","start","xbox","up","down","left","right","moveUp","moveDown","moveLeft","moveRight","lookUp","lookDown","lookLeft","lookRight"};
        String[] labels={"A","B","X","Y","LB · 左肩键","RB · 右肩键","LT · 左扳机","RT · 右扳机","View","Menu","Xbox · Game Bar","方向键 ↑","方向键 ↓","方向键 ←","方向键 →","左摇杆 ↑","左摇杆 ↓","左摇杆 ←","左摇杆 →","右摇杆 ↑","右摇杆 ↓","右摇杆 ←","右摇杆 →"};
        add(page,text("Xbox 键使用本 App 图标呼出 Game Bar。线性扳机可在手感调节中开启，沿长边从 0% 滑到 100%。",13,MUTED),12);
        ArrayList<String> keys=new ArrayList<>(java.util.Arrays.asList(controls));
        java.util.Collections.addAll(keys,"l3","r3","none");
        ArrayList<String> outputLabels=new ArrayList<>(java.util.Arrays.asList(labels));
        java.util.Collections.addAll(outputLabels,"L3 · 左摇杆按下","R3 · 右摇杆按下","禁用");
        for(int i=0;i<controls.length;i++){
            final String control=controls[i];LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text(labels[i],14,FG),new LinearLayout.LayoutParams(0,dp(48),1));Spinner spinner=new Spinner(this);
            spinner.setContentDescription(labels[i]+"映射");ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,outputLabels);spinner.setAdapter(adapter);
            spinner.setSelection(keys.indexOf(settings.optJSONObject("mapping").optString(control)));row.addView(spinner,new LinearLayout.LayoutParams(dp(140),dp(48)));
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                String key=keys.get(position);if(!key.equals(settings.optJSONObject("mapping").optString(control))){put(settings.optJSONObject("mapping"),control,key);put(settings,"preset","custom");saveSettings();}}});add(page,row,4);
        }
    }
    private void feelSettings(LinearLayout page) {
        page.removeAllViews();slider(page,"摇杆死区",10,50,(int)Math.round(settings.optDouble("deadzone",.24)*100),"deadzone");
        slider(page,"显示比例",80,110,settings.optInt("size",100),"size");Switch haptics=new Switch(this);haptics.setText("振动反馈");haptics.setTextColor(FG);haptics.setChecked(settings.optBoolean("haptics",true));
        Switch linear=new Switch(this);linear.setText("LT / RT 线性油门 / 刹车");linear.setTextColor(FG);linear.setChecked(settings.optBoolean("linear_triggers"));
        linear.setOnCheckedChangeListener((b,on)->{put(settings,"linear_triggers",on);saveSettings();});add(page,linear,20);
        add(page,text("开启：沿扳机长边滑动，左/上端 0%，右/下端 100%；关闭：按住全按，松手归零。",12,MUTED),8);
        Switch motion=new Switch(this);motion.setText(gyro.available()?"陀螺仪赛车转向":"设备不支持陀螺仪转向");motion.setTextColor(FG);motion.setEnabled(gyro.available());motion.setChecked(settings.optBoolean("gyro_enabled")&&gyro.available());
        motion.setOnCheckedChangeListener((b,on)->{put(settings,"gyro_enabled",on);saveSettings();syncGyro();});add(page,motion,20);
        add(page,text("每次进入手柄或恢复控制时自动校准；握好手机并保持稳定约 0.6 秒，将当前姿态设为中心。",14,MUTED),8);
        add(page,text("转向比 / 方向盘总行程",14,FG),16);
        Spinner ratio=new Spinner(this);ratio.setContentDescription("陀螺仪转向比");
        ratio.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"F1 标准 · 360°（左右各 180°）","自定义"}));
        int range=(int)settings.optDouble("gyro_range",45);ratio.setSelection(range==180?0:1);add(page,ratio,8);
        SeekBar rangeSlider=slider(page,"单侧满舵角度（度，越小越灵敏）",15,450,range,"gyro_range");
        ratio.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
            if(position!=0)return;int degrees=180;if(settings.optDouble("gyro_range",45)==degrees)return;
            put(settings,"gyro_range",degrees);rangeSlider.setProgress(degrees-15);saveSettings();
        }});
        rangeSlider.setTag(ratio);
        slider(page,"转向死区（度）",0,10,(int)settings.optDouble("gyro_deadzone",2),"gyro_deadzone");
        Switch reverse=new Switch(this);reverse.setText("转向反向");reverse.setTextColor(FG);reverse.setChecked(settings.optBoolean("gyro_invert"));reverse.setOnCheckedChangeListener((b,on)->{put(settings,"gyro_invert",on);saveSettings();});add(page,reverse,12);
        add(page,text("像方向盘一样转动手机控制左摇杆横轴，支持跨圈累计角度；触摸左摇杆优先。游戏中可点击「回中」重新校准。F1 档采用常见模拟方向盘行程预设。",12,MUTED),12);
        haptics.setOnCheckedChangeListener((b,on)->{put(settings,"haptics",on);saveSettings();});add(page,haptics,20);
    }
    private SeekBar slider(LinearLayout page,String label,int min,int max,int initial,String key) {
        String unit=key.startsWith("gyro_")?"°":"%";TextView title=text(label+" · "+initial+unit,14,FG);add(page,title,20);SeekBar slider=new SeekBar(this);slider.setMax(max-min);slider.setProgress(initial-min);slider.setContentDescription(label);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean user){
            int percent=value+min;title.setText(label+" · "+percent+unit);if(!user)return;if(s.getTag() instanceof Spinner)((Spinner)s.getTag()).setSelection(1);if(key.equals("deadzone"))put(settings,key,percent/100.0);else put(settings,key,percent);saveSettings();}});add(page,slider,8);return slider;
    }

    private final class PadView extends FrameLayout {
        private final ArrayList<ControlView> controls=new ArrayList<>();
        PadView(){super(MainActivity.this);setBackgroundColor(BG);setMotionEventSplittingEnabled(true);
            status=text(editing?"自定义 · 点击编辑 / 拖动调整":"Orbit Pad · 正在连接…",13,MUTED);addView(status);
            try{JSONArray layout=new JSONArray(NativeBridge.call(command("layout").toString()));for(int i=0;i<layout.length();i++){ControlView v=new ControlView(layout.getJSONObject(i));controls.add(v);addView(v);}}
            catch(Exception e){throw new IllegalStateException(e);}
            setOnApplyWindowInsetsListener((v,i)->{setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            float width=getWidth()-getPaddingLeft()-getPaddingRight(),height=getHeight()-getPaddingTop()-getPaddingBottom();
            float scale=Math.min(width/1000f,height/450f),ox=getPaddingLeft()+(width-1000*scale)/2,oy=getPaddingTop()+(height-450*scale)/2;
            status.layout((int)(ox+24*scale),(int)(oy+8*scale),(int)(ox+850*scale),(int)(oy+49*scale));
            try{JSONArray layout=new JSONArray(NativeBridge.call(command("layout").toString()));for(int i=0;i<controls.size();i++){
                JSONObject p=layout.getJSONObject(i);float cx=(float)p.getDouble("x")*scale+ox,cy=(float)p.getDouble("y")*scale+oy;
                float w=(float)p.getDouble("width")*scale,h=(float)p.getDouble("height")*scale;
                ControlView control=controls.get(i);control.setVisibility(p.optBoolean("visible",true)?View.VISIBLE:View.GONE);
                if(editing&&control.id.equals("settings"))control.setVisibility(View.GONE);
                if(control.id.equals("calibrate")&&!editing&&!settings.optBoolean("gyro_enabled"))control.setVisibility(View.GONE);
                control.layout(Math.round(cx-w/2),Math.round(cy-h/2),Math.round(cx+w/2),Math.round(cy+h/2));}}
            catch(Exception e){throw new IllegalStateException(e);}
        }
        void refresh(){for(ControlView v:controls)v.invalidate();}
    }
    private final class ControlView extends View {
        final String id,label;final boolean stick;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float dragX,dragY,offsetX,offsetY;boolean resizing;
        ControlView(JSONObject p){super(MainActivity.this);id=p.optString("id");label=p.optString("label");stick=id.equals("move")||id.equals("look");
            setContentDescription(p.optString("description"));setClickable(true);setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),radius=Math.min(w,h)/2;JSONObject state=snapshot.optJSONObject("state");boolean pressed=false;
            if(state!=null){JSONArray buttons=state.optJSONArray("buttons");for(int i=0;buttons!=null&&i<buttons.length();i++)if(id.equals(buttons.optString(i)))pressed=true;}
            paint.setStyle(Paint.Style.FILL);paint.setColor(pressed?PRIMARY:SURFACE);
            if(stick){c.drawCircle(w/2,h/2,radius-2,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(OUTLINE);c.drawCircle(w/2,h/2,radius-2,paint);paint.setStyle(Paint.Style.FILL);
                JSONArray axes=state==null?null:state.optJSONArray("axes");int axis=id.equals("move")?0:2;float x=axes==null?0:(float)axes.optDouble(axis),y=axes==null?0:(float)axes.optDouble(axis+1);
                paint.setColor(PRIMARY);c.drawCircle(w/2+x*radius*.55f,h/2+y*radius*.55f,radius*.38f,paint);
                paint.setColor(0xff381e72);paint.setTextSize(radius*.3f);paint.setTextAlign(Paint.Align.CENTER);c.drawText(label,w/2+x*radius*.55f,h/2+y*radius*.55f+radius*.1f,paint);
            }else{
                float corner=id.length()==1?radius:Math.min(radius,dp(18));c.drawRoundRect(1,1,w-1,h-1,corner,corner,paint);
                paint.setColor(pressed?0xff381e72:FG);paint.setTextSize(Math.min(h*.35f,dp(22)));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-medium",0));
                if(id.equals("xbox")){
                    android.graphics.drawable.Drawable icon=getDrawable(app.orbitpad.controller.R.drawable.ic_launcher);
                    int d=(int)(Math.min(w,h)*.72f);icon.setBounds((int)(w-d)/2,(int)(h-d)/2,(int)(w+d)/2,(int)(h+d)/2);icon.draw(c);
                }else {
                    boolean pedal=(id.equals("lt")||id.equals("rt"))&&settings.optBoolean("linear_triggers");
                    if(pedal)paint.setTextSize(Math.min(h*.28f,dp(22)));
                    c.drawText(label,w/2,h*(pedal?.38f:.5f)-(paint.ascent()+paint.descent())/2,paint);
                }
                if((id.equals("lt")||id.equals("rt"))&&settings.optBoolean("linear_triggers")){
                    JSONArray triggers=state==null?null:state.optJSONArray("triggers");float value=triggers==null?0:(float)triggers.optDouble(id.equals("lt")?0:1);
                    paint.setColor(0xffa8c7fa);paint.setStrokeWidth(dp(4));
                    if(w>=h)c.drawLine(dp(10),h-dp(6),dp(10)+(w-dp(20))*value,h-dp(6),paint);
                    else c.drawLine(w-dp(6),dp(10),w-dp(6),dp(10)+(h-dp(20))*value,paint);
                    paint.setTextSize(Math.min(h*.22f,dp(12)));c.drawText(Math.round(value*100)+"%",w/2,h*.8f,paint);
                }
            }
            if(editing&&layoutEditor!=null&&layoutEditor.selected(id)){paint.setColor(PRIMARY);paint.setStrokeWidth(dp(2));c.drawLine(w-dp(18),h-dp(5),w-dp(5),h-dp(18),paint);c.drawLine(w-dp(10),h-dp(5),w-dp(5),h-dp(10),paint);}
            setAlpha(editing||id.equals("settings")||snapshot.optBoolean("enabled")?1f:.45f);
            if(editing&&layoutEditor!=null&&layoutEditor.selected(id)){paint.setColor(PRIMARY);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));float corner=stick||id.length()==1?Math.min(w,h)/2:Math.min(Math.min(w,h)/2,dp(18));c.drawRoundRect(dp(1),dp(1),w-dp(1),h-dp(1),corner,corner,paint);paint.setStyle(Paint.Style.FILL);}
        }
        private void touch(MotionEvent e,int index,String phase){
            JSONObject cmd=command("touch");put(cmd,"id",e.getPointerId(index));put(cmd,"phase",phase);put(cmd,"control",id);
            put(cmd,"x",(e.getX(index)-getWidth()/2f)/(getWidth()/2f));put(cmd,"y",(e.getY(index)-getHeight()/2f)/(getHeight()/2f));snapshot=call(cmd);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(editing){
                if(layoutEditor==null)return true;
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){dragX=e.getRawX();dragY=e.getRawY();offsetX=offsetY=0;
                    float grab=Math.min(dp(22),Math.min(getWidth(),getHeight())*.28f);resizing=e.getX()>getWidth()-grab&&e.getY()>getHeight()-grab;layoutEditor.begin(id);}
                else if(action==MotionEvent.ACTION_MOVE){offsetX=e.getRawX()-dragX;offsetY=e.getRawY()-dragY;layoutEditor.drag(offsetX,offsetY,resizing);}
                else if(action==MotionEvent.ACTION_UP){layoutEditor.end();if(!resizing&&Math.abs(offsetX)+Math.abs(offsetY)<=dp(6))editControl(id);}
                else if(action==MotionEvent.ACTION_CANCEL)layoutEditor.end();return true;
            }
            if(id.equals("calibrate")||id.equals("settings")){if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
            int action=e.getActionMasked(),index=e.getActionIndex();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){touch(e,index,"down");
                if(snapshot.optBoolean("enabled")&&settings.optBoolean("haptics",true))performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);}
            else if(action==MotionEvent.ACTION_MOVE){for(int i=0;i<e.getPointerCount();i++)touch(e,i,"move");}
            else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)touch(e,index,"up");
            else if(action==MotionEvent.ACTION_CANCEL)snapshot=call("release");
            if(pad!=null)pad.refresh();return true;
        }
        @Override public boolean performClick(){
            super.performClick();
            if(editing){editControl(id);}
            else if(id.equals("calibrate")){gyro.calibrate();Toast.makeText(MainActivity.this,"陀螺仪校准中，请保持手机稳定",Toast.LENGTH_SHORT).show();}
            else if(id.equals("settings"))showSettings();
            else if(!stick){
                JSONObject c=command("touch");put(c,"id",-1);put(c,"control",id);put(c,"x",0);put(c,"y",0);put(c,"phase","down");snapshot=call(c);
                put(c,"phase","up");snapshot=call(c);if(pad!=null)pad.refresh();
            }
            return true;
        }
    }

    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus&&gyro!=null)gyro.stop();JSONObject c=command("focus");put(c,"value",focus);snapshot=call(c);if(focus){if(controllerVisible||editing)immersive();else edgeToEdge();}}
    @Override public void onBackPressed(){
        if(editing){if(layoutEditor!=null&&layoutEditor.back())return;finishEditor();return;}
        if(!controllerVisible){if(checking){disconnectTransport();if(shell!=null)shell.update(snapshot);}else if(shell==null||!shell.back())super.onBackPressed();return;}
        showShell(0,null);
    }
    @Override protected void onPause(){if(gyro!=null)gyro.stop();resumed=false;handler.removeCallbacks(poll);snapshot=call("pause");super.onPause();}
    @Override protected void onResume(){super.onResume();snapshot=call("resume");resumed=true;handler.removeCallbacks(poll);handler.post(poll);if(controllerVisible||editing)immersive();else edgeToEdge();}
    @Override protected void onDestroy(){handler.removeCallbacks(poll);disconnectTransport();super.onDestroy();}
}
