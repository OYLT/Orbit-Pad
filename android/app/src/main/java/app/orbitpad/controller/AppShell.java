package app.orbitpad.controller;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;

/** Four local pages, with a floating in-app dock. No overlay permission is needed. */
final class AppShell {
    private static final int BG=0xff141218, FG=0xffe6e0e9, MUTED=0xffcac4d0,
        SURFACE=0xff211f26, PRIMARY=0xffd0bcff;
    private final MainActivity app;
    final FrameLayout view;
    private final FrameLayout page;
    private LinearLayout content;
    private int selected;
    private String mode="scan";
    private TextView connectionStatus, formMessage;
    private Button connect;
    private EditText address, code;
    private LinearLayout connectionBody;
    private String stickyError;

    AppShell(MainActivity app,int initial,String error) {
        this.app=app;selected=initial;stickyError=error;
        view=new FrameLayout(app);view.setBackgroundColor(BG);
        page=new FrameLayout(app);view.addView(page,new FrameLayout.LayoutParams(-1,-1));
        view.setOnApplyWindowInsetsListener((v,i)->{
            v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;
        });
        show(initial);
    }
    private void show(int index) {
        selected=index;page.removeAllViews();connectionStatus=null;formMessage=null;connect=null;
        ScrollView scroll=new ScrollView(app);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=new LinearLayout(app);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(app.dp(24),app.dp(28),app.dp(24),app.dp(124));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));page.addView(scroll);
        String[] titles={"你的手柄，你来定义。","连接你的电脑","主题","关于 Orbit Pad"};
        add(app.text("ORBIT PAD",12,PRIMARY),0);add(app.text(titles[index],28,FG),12);
        if(index==0)home();else if(index==1)connection();else if(index==3)about();
        dock();update(app.snapshot);
    }
    private void dock() {
        if(view.getChildCount()>1)view.removeViewAt(1);
        LinearLayout dock=new LinearLayout(app);dock.setGravity(Gravity.CENTER);
        dock.setPadding(app.dp(6),app.dp(6),app.dp(6),app.dp(6));
        dock.setBackground(app.rounded(0xff302d38,0xff494452,32));dock.setElevation(app.dp(12));
        String[] labels={"主页","连接","主题","关于"};
        for(int i=0;i<labels.length;i++){
            final int index=i;LinearLayout tab=new LinearLayout(app);tab.setOrientation(LinearLayout.VERTICAL);tab.setGravity(Gravity.CENTER);
            tab.setContentDescription("导航"+labels[i]);tab.setClickable(true);tab.setFocusable(true);
            if(selected==i)tab.setBackground(app.rounded(0xff4f378b,0,26));
            View icon=new DockIcon(app,i,selected==i?PRIMARY:MUTED);tab.addView(icon,new LinearLayout.LayoutParams(app.dp(23),app.dp(23)));
            TextView name=app.text(labels[i],11,selected==i?PRIMARY:MUTED);name.setGravity(Gravity.CENTER);tab.addView(name);
            tab.setOnClickListener(v->show(index));dock.addView(tab,new LinearLayout.LayoutParams(0,-1,1));
        }
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,app.dp(72),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        p.leftMargin=p.rightMargin=app.dp(20);p.bottomMargin=app.dp(20);view.addView(dock,p);
    }
    private void add(View v,int top){app.add(content,v,top);}
    private LinearLayout card() {
        LinearLayout c=new LinearLayout(app);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(app.dp(20),app.dp(20),app.dp(20),app.dp(20));
        c.setBackground(app.rounded(SURFACE,0xff38333f,24));return c;
    }
    private void home() {
        add(app.text("选择模板，调整布局与映射，做一只顺手的手柄。",14,MUTED),10);
        LinearLayout current=card();
        app.add(current,app.text(app.getPreferences(0).getString("template_name","通用游戏"),20,PRIMARY),0);
        TemplatePreview preview=new TemplatePreview();current.addView(preview,new LinearLayout.LayoutParams(-1,app.dp(165)));
        Button edit=app.button("自定义按键布局");edit.setOnClickListener(v->app.openEditor());app.add(current,edit,12);
        add(current,24);
        LinearLayout templates=card();app.add(templates,app.text("按键模板",18,FG),0);
        String[] names={"通用游戏","竞速驾驶","平台跳跃"}, ids={"general","racing","platform"};
        for(int i=0;i<names.length;i++){
            final String id=ids[i],name=names[i];Button b=secondary(name+"  ↗");
            b.setOnClickListener(v->{JSONObject cmd=app.command("preset");app.put(cmd,"name",id);app.applyTemplate(app.call(cmd),name);show(0);});app.add(templates,b,10);
        }
        JSONArray saved=templates();
        for(int i=0;i<saved.length();i++){
            final JSONObject template=saved.optJSONObject(i);if(template==null)continue;
            LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);
            Button choose=secondary(template.optString("name"));choose.setOnClickListener(v->{app.applyTemplate(template.optJSONObject("settings"),template.optString("name"));show(0);});
            row.addView(choose,new LinearLayout.LayoutParams(0,-2,1));Button remove=secondary("删除");remove.setContentDescription("删除模板"+template.optString("name"));
            remove.setOnClickListener(v->new AlertDialog.Builder(app).setTitle("删除模板？").setMessage(template.optString("name"))
                .setPositiveButton("删除",(d,w)->{JSONArray next=new JSONArray();JSONArray all=templates();for(int n=0;n<all.length();n++){JSONObject item=all.optJSONObject(n);if(item!=null&&!item.optString("id").equals(template.optString("id")))next.put(item);}storeTemplates(next);show(0);}).setNegativeButton("取消",null).show());
            row.addView(remove,new LinearLayout.LayoutParams(app.dp(76),-2));app.add(templates,row,8);
        }
        Button save=app.button("＋ 保存为新模板");save.setOnClickListener(v->saveTemplate());app.add(templates,save,16);add(templates,20);
        Button mapping=secondary("按键映射与手感设置");mapping.setOnClickListener(v->app.showSettings());add(mapping,16);
        Button play=app.button(app.snapshot.optBoolean("connected")?"进入手柄":"扫码连接电脑");
        play.setOnClickListener(v->{if(app.snapshot.optBoolean("connected"))app.openController();else show(1);});add(play,16);
    }
    private JSONArray templates(){try{return new JSONArray(app.getPreferences(0).getString("templates_v1","[]"));}catch(Exception e){return new JSONArray();}}
    private void storeTemplates(JSONArray data){app.getPreferences(0).edit().putString("templates_v1",data.toString()).apply();}
    private void saveTemplate() {
        EditText name=app.field("模板名称");name.setText("我的手柄");
        AlertDialog dialog=new AlertDialog.Builder(app).setTitle("保存当前模板").setView(name).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>30){name.setError("请输入 1–30 字的名称");return;}
            JSONArray list=templates();if(list.length()>=24){name.setError("最多保存 24 个模板");return;}
            JSONObject entry=new JSONObject();app.put(entry,"id",UUID.randomUUID().toString());app.put(entry,"name",value);app.put(entry,"settings",app.settings);
            list.put(entry);storeTemplates(list);app.getPreferences(0).edit().putString("template_name",value).apply();dialog.dismiss();show(0);
        }));dialog.show();
    }
    private Button secondary(String label){Button b=app.button(label);b.setBackground(app.rounded(0xff37313e,0,20));b.setTextColor(FG);return b;}
    private void connection() {
        add(app.text("扫描电脑接收端二维码，连接后即可使用当前模板。",14,MUTED),10);
        connectionStatus=app.text("",13,PRIMARY);add(connectionStatus,16);
        LinearLayout methods=new LinearLayout(app);methods.setOrientation(LinearLayout.VERTICAL);
        String[][] names={{"scan","扫码连接 · 默认"},{"usb","USB 直连"},{"bluetooth","蓝牙直连"},{"manual","手动配对"}};
        for(int row=0;row<2;row++){
            LinearLayout line=new LinearLayout(app);
            for(int col=0;col<2;col++){
                String[] item=names[row*2+col];Button b=secondary(item[1]);b.setContentDescription("连接方式"+item[1]);
                if(item[0].equals(mode)){b.setBackground(app.rounded(0xff4f378b,0,22));b.setTextColor(PRIMARY);}
                b.setOnClickListener(v->{mode=item[0];stickyError=null;show(1);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,app.dp(62),1);p.setMargins(app.dp(3),app.dp(3),app.dp(3),app.dp(3));line.addView(b,p);
            }
            methods.addView(line);
        }
        add(methods,18);connectionBody=card();add(connectionBody,20);
        if(mode.equals("scan")){
            QrMark mark=new QrMark(app);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(app.dp(128),app.dp(128));size.gravity=Gravity.CENTER_HORIZONTAL;connectionBody.addView(mark,size);
            app.add(connectionBody,app.text("对准电脑上的二维码",20,FG),18);
            app.add(connectionBody,app.text("手机和电脑连接同一局域网。在电脑接收端显示二维码，扫描后自动完成配对。",14,MUTED),8);
            Button scan=app.button("打开相机扫码");scan.setOnClickListener(v->app.openScanner());app.add(connectionBody,scan,24);
        }else if(mode.equals("usb")){
            app.add(connectionBody,app.text("用数据线直接连接",20,FG),0);
            app.add(connectionBody,app.text("1. 用数据线连接手机和电脑。\n2. 在电脑接收端点击「USB 连接」。\n3. 手机开启 USB 调试并确认本机电脑授权。\n4. 点击下方按钮，自动完成配对。",14,MUTED),12);
            connect=app.button("连接 USB 手柄");connect.setOnClickListener(v->app.startConnection("127.0.0.1:3210","","usb"));app.add(connectionBody,connect,22);
        }else if(mode.equals("bluetooth")){
            app.add(connectionBody,app.text("通过蓝牙直接连接",20,FG),0);
            app.add(connectionBody,app.text("在电脑接收端开启「蓝牙连接」，先在系统中将手机与电脑配对，再选择电脑。无需连接 Wi-Fi。",14,MUTED),12);
            Button system=secondary("打开系统蓝牙配对");system.setOnClickListener(v->app.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)));app.add(connectionBody,system,18);
            connect=app.button("选择已配对的电脑");connect.setOnClickListener(v->app.chooseBluetooth());app.add(connectionBody,connect,12);
        }else{
            app.add(connectionBody,app.text("手动输入配对信息",20,FG),0);
            app.add(connectionBody,app.text("电脑接收地址",12,PRIMARY),18);address=app.field("192.168.1.100:3210");address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
            address.setText(app.getPreferences(0).getString("receiver",""));app.add(connectionBody,address,8);
            app.add(connectionBody,app.text("六位配对码",12,PRIMARY),18);code=app.field("000000");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});app.add(connectionBody,code,8);
            connect=app.button("连接并开始  →");connect.setOnClickListener(v->app.startConnection(address.getText().toString(),code.getText().toString().trim(),"manual"));app.add(connectionBody,connect,22);
        }
        formMessage=app.text(stickyError==null?"":stickyError,13,MUTED);app.add(connectionBody,formMessage,14);
        Button enter=secondary("进入手柄");enter.setOnClickListener(v->{if(app.snapshot.optBoolean("connected"))app.openController();else error("请先连接电脑");});add(enter,16);
        Button disconnect=secondary("断开当前连接");disconnect.setOnClickListener(v->{app.disconnectTransport();stickyError=null;show(1);});add(disconnect,10);
    }
    void error(String value){stickyError=value;if(formMessage!=null)formMessage.setText(value);else Toast.makeText(app,value,Toast.LENGTH_LONG).show();}
    boolean back(){if(selected!=0){show(0);return true;}return false;}
    void update(JSONObject snapshot) {
        if(connectionStatus!=null)connectionStatus.setText(snapshot.optBoolean("connected")?"● 已连接 · "+(snapshot.isNull("rtt")?"测量延迟中":snapshot.optLong("rtt")+" ms"):"○ "+snapshot.optString("message","等待连接"));
        if(connect!=null)connect.setEnabled(!app.isConnecting());
        if(formMessage!=null&&stickyError==null&&app.isConnecting())formMessage.setText(snapshot.optString("message"));
    }
    private void about() {
        add(app.text("手机，变身手柄。",16,MUTED),12);
        LinearLayout info=card();
        app.add(info,app.text("Orbit Pad",26,PRIMARY),0);
        app.add(info,app.text("版本 "+app.snapshot.optString("version","0.5.0"),16,FG),8);
        app.add(info,app.text("Android 原生界面 · Rust 控制核心",14,MUTED),12);
        app.add(info,app.text("将手机变成 Windows 电脑的无线或有线手柄。支持模板布局与按键映射自定义。",14,MUTED),16);add(info,24);
        LinearLayout details=card();
        app.add(details,app.text("应用信息",18,FG),0);
        app.add(details,app.text("连接方式\n二维码 / 局域网 / USB 直连 / 蓝牙 RFCOMM\n\n系统支持\nAndroid 8.0 及以上 · ARM64 / x86_64\n\n电脑输出\nWindows 键盘映射\n\n应用包名\napp.orbitpad.controller",14,MUTED),14);add(details,20);
        Button project=secondary("项目主页 · OYLT / Orbit-Pad");project.setOnClickListener(v->app.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/OYLT/Orbit-Pad"))));add(project,20);
        LinearLayout license=card();app.add(license,app.text("组件与隐私",18,FG),0);
        app.add(license,app.text("二维码识别：ZXing 3.5.3（Apache 2.0）。\n蓝牙连接：Android Bluetooth API。\n相机仅在扫码时打开，二维码在手机本地识别。",14,MUTED),12);add(license,20);
    }
    private final class TemplatePreview extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        TemplatePreview(){super(app);setContentDescription("当前手柄模板预览");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=Math.min(getWidth()/1000f,getHeight()/450f),ox=(getWidth()-scale*1000)/2,oy=(getHeight()-scale*450)/2;
            try{JSONArray layout=new JSONArray(NativeBridge.call(app.command("layout").toString()));for(int i=0;i<layout.length();i++){
                JSONObject b=layout.getJSONObject(i);String id=b.optString("id");if(!b.optBoolean("visible",true)||id.equals("settings"))continue;
                float x=(float)b.optDouble("x")*scale+ox,y=(float)b.optDouble("y")*scale+oy,w=(float)b.optDouble("width")*scale,h=(float)b.optDouble("height")*scale;
                p.setColor(0xff45404c);c.drawRoundRect(new RectF(x-w/2,y-h/2,x+w/2,y+h/2),h/2,h/2,p);
                if(id.equals("move")||id.equals("look")){p.setColor(PRIMARY);c.drawCircle(x,y,h*.22f,p);}else{p.setColor(FG);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(h*.38f);c.drawText(b.optString("label"),x,y-(p.ascent()+p.descent())/2,p);}
            }}catch(Exception ignored){}
        }
    }
    private static final class QrMark extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        QrMark(MainActivity app){super(app);setContentDescription("扫码连接图标");}
        @Override protected void onDraw(Canvas c){float u=getWidth()/21f;p.setColor(PRIMARY);for(int[] q:new int[][]{{1,1},{13,1},{1,13}}){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u);c.drawRoundRect((q[0]+.5f)*u,(q[1]+.5f)*u,(q[0]+6.5f)*u,(q[1]+6.5f)*u,u,u,p);p.setStyle(Paint.Style.FILL);c.drawRect((q[0]+2)*u,(q[1]+2)*u,(q[0]+5)*u,(q[1]+5)*u,p);}for(int[] b:new int[][]{{13,13},{15,15},{18,13},{13,18},{18,18},{10,9},{9,12},{9,17}})c.drawRect(b[0]*u,b[1]*u,(b[0]+2)*u,(b[1]+2)*u,p);}
    }
    private static final class DockIcon extends View {
        final int type,color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        DockIcon(MainActivity app,int type,int color){super(app);this.type=type;this.color=color;}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);
            if(type==0){c.drawRoundRect(3,5,21,20,3,3,p);c.drawLine(7,10,11,10,p);c.drawLine(9,8,9,12,p);c.drawCircle(16,10,1,p);c.drawLine(7,16,17,16,p);}
            else if(type==1){for(int[] q:new int[][]{{3,3},{15,3},{3,15}})c.drawRoundRect(q[0],q[1],q[0]+6,q[1]+6,1,1,p);c.drawLine(15,15,21,21,p);c.drawLine(21,15,15,21,p);}
            else if(type==2){c.drawCircle(12,12,9,p);c.drawCircle(8,8,1,p);c.drawCircle(15,7,1,p);c.drawCircle(17,13,1,p);c.drawLine(9,17,13,17,p);}
            else{c.drawCircle(12,12,9,p);c.drawPoint(12,7,p);c.drawLine(12,11,12,17,p);}c.restore();
        }
    }
}
