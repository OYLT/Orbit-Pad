package app.orbitpad.controller;

import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.PathInterpolator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Insets;
import android.view.WindowInsets;
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
    final GlassBackdrop view;
    private GlassDock glassDock;
    private ScrollView currentScroll;
    private ScrollView outgoingScroll;
    private ValueAnimator pageTransition;
    private final int[] scrollPositions=new int[4];
    private String draftAddress,draftCode;
    private final FrameLayout page;
    private LinearLayout content;
    private int selected;
    private String mode="scan";
    private TextView connectionStatus, homeConnectionStatus, formMessage;
    private Button connect, homePlay;
    private EditText address, code;
    private LinearLayout connectionBody;
    private String stickyError;
    private int safeLeft,safeTop,safeRight,safeBottom,keyboardBottom;
    private boolean keyboardVisible,aboutExpanded;
    private LinearLayout aboutBody;
    private Button aboutToggle,qualityButton;
    private TextView refractionHint;
    private final String[] refractionLabels={"关闭","轻柔","标准","强烈"};

    AppShell(MainActivity app,int initial,String error) {
        this.app=app;selected=initial;stickyError=error;
        view=new GlassBackdrop(app);page=view.page;
        view.setOnApplyWindowInsetsListener((v,i)->{
            if(Build.VERSION.SDK_INT>=30){
                Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                safeLeft=bars.left;safeTop=bars.top;safeRight=bars.right;safeBottom=bars.bottom;
                keyboardVisible=i.isVisible(WindowInsets.Type.ime());keyboardBottom=keyboardVisible?i.getInsets(WindowInsets.Type.ime()).bottom:0;
            }else{
                int bottom=i.getSystemWindowInsetBottom();keyboardVisible=bottom>app.dp(100);keyboardBottom=0;
                safeLeft=i.getSystemWindowInsetLeft();safeTop=i.getSystemWindowInsetTop();safeRight=i.getSystemWindowInsetRight();
                if(!keyboardVisible)safeBottom=bottom;
            }
            applyInsets();return i;
        });
        if(Build.VERSION.SDK_INT<30){
            final int[] windowSize={0,0};
            view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
                int width=r-l,height=b-t;
                if(windowSize[0]!=width){windowSize[0]=width;windowSize[1]=height;}
                windowSize[1]=Math.max(windowSize[1],height);
                keyboardVisible=windowSize[1]-height>app.dp(100);applyInsets();
            });
        }
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){finishPageTransition();}
        });
        dock();show(initial);view.requestApplyInsets();
    }
    private void applyInsets(){
        view.setTopFade(safeTop,currentScroll==null?0:Math.min(1,currentScroll.getScrollY()/(float)app.dp(36)));
        // The page fills the whole window; only controls avoid system gesture/cutout areas.
        if(currentScroll!=null)currentScroll.setPadding(safeLeft,safeTop,safeRight,keyboardBottom);
        if(content!=null)content.setPadding(app.dp(24),app.dp(28),app.dp(24),keyboardVisible?app.dp(24):glassDock.preferredHeight()+app.dp(52)+safeBottom);
        if(glassDock!=null){
            glassDock.setVisibility(keyboardVisible?View.GONE:View.VISIBLE);
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)glassDock.getLayoutParams();
            int left=app.dp(20)+safeLeft,right=app.dp(20)+safeRight,bottom=app.dp(20)+safeBottom;
            if(p.leftMargin!=left||p.rightMargin!=right||p.bottomMargin!=bottom){p.leftMargin=left;p.rightMargin=right;p.bottomMargin=bottom;glassDock.setLayoutParams(p);}
        }
    }
    private void show(int index) {
        boolean switching=selected!=index;
        finishPageTransition();
        int direction=index>selected?1:-1;
        ScrollView previous=currentScroll;
        boolean animate=switching&&previous!=null&&view.isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();
        if(currentScroll!=null)scrollPositions[selected]=currentScroll.getScrollY();
        if(address!=null){draftAddress=address.getText().toString();draftCode=code.getText().toString();}
        address=null;code=null;
        selected=index;page.removeAllViews();connectionStatus=null;homeConnectionStatus=null;homePlay=null;formMessage=null;connect=null;aboutBody=null;aboutToggle=null;refractionHint=null;qualityButton=null;
        ScrollView scroll=new ScrollView(app);currentScroll=scroll;scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(currentScroll!=v)return;
            glassDock.onScroll(y-oldY,y);view.setTopFade(safeTop,Math.min(1,y/(float)app.dp(36)));
        });
        content=new LinearLayout(app);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(app.dp(24),app.dp(28),app.dp(24),glassDock.preferredHeight()+app.dp(52));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));page.addView(scroll);
        String[] titles={"你的手柄，你来定义。","连接你的电脑","主题","设置"};
        if(index==0){
            LinearLayout header=new LinearLayout(app);header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(app.text("ORBIT PAD",12,PRIMARY),new LinearLayout.LayoutParams(0,-2,1));
            homeConnectionStatus=app.text("",13,MUTED);homeConnectionStatus.setGravity(Gravity.CENTER);
            homeConnectionStatus.setMinHeight(app.dp(48));homeConnectionStatus.setPadding(app.dp(12),0,app.dp(12),0);
            homeConnectionStatus.setBackground(app.rounded(SURFACE,0xff38333f,24));
            homeConnectionStatus.setOnClickListener(v->show(1));header.addView(homeConnectionStatus);add(header,0);
        }else add(app.text("ORBIT PAD",12,PRIMARY),0);
        add(app.text(titles[index],28,FG),12);
        if(index==0)home();else if(index==1)connection();else if(index==3)settingsPage();
        glassDock.setSelectedIndex(index);applyInsets();update(app.snapshot);
        if(switching)scroll.post(()->{if(currentScroll==scroll)scroll.scrollTo(0,scrollPositions[index]);});
        if(animate)startPageTransition(previous,scroll,direction);
    }
    private void finishPageTransition(){
        if(pageTransition!=null){ValueAnimator running=pageTransition;pageTransition=null;running.cancel();}
        if(outgoingScroll!=null){page.removeView(outgoingScroll);outgoingScroll=null;}
        if(currentScroll!=null){currentScroll.setTranslationX(0);currentScroll.setAlpha(1);currentScroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
    }
    private void startPageTransition(ScrollView previous,ScrollView next,int direction){
        outgoingScroll=previous;previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        page.addView(previous,0);float distance=app.dp(42)*direction;
        next.setTranslationX(distance);next.setAlpha(0);
        ValueAnimator animator=ValueAnimator.ofFloat(0,1);pageTransition=animator;
        animator.setDuration(320);animator.setInterpolator(new PathInterpolator(.22f,.8f,.25f,1));
        animator.addUpdateListener(a->{
            float progress=(float)a.getAnimatedValue();next.setTranslationX(distance*(1-progress));next.setAlpha(progress);
            previous.setTranslationX(-distance*.65f*progress);previous.setAlpha(1-progress);
            float scrollY=previous.getScrollY()*(1-progress)+next.getScrollY()*progress;
            view.setTopFade(safeTop,Math.min(1,scrollY/app.dp(36)));view.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){
            page.removeView(previous);next.setTranslationX(0);next.setAlpha(1);
            if(pageTransition==animation){pageTransition=null;outgoingScroll=null;view.setTopFade(safeTop,Math.min(1,next.getScrollY()/(float)app.dp(36)));}
        }});animator.start();
    }
    void saveState(Bundle state){
        state.putInt("shell_page",selected);state.putString("shell_mode",mode);state.putBoolean("shell_about_expanded",aboutExpanded);
        if(currentScroll!=null)scrollPositions[selected]=currentScroll.getScrollY();
        state.putIntArray("shell_scroll",scrollPositions);
        state.putString("shell_address",address==null?draftAddress:address.getText().toString());
        state.putString("shell_code",code==null?draftCode:code.getText().toString());
    }
    void restoreState(Bundle state){
        aboutExpanded=state.getBoolean("shell_about_expanded",false);mode=state.getString("shell_mode","scan");draftAddress=state.getString("shell_address");draftCode=state.getString("shell_code");
        int[] positions=state.getIntArray("shell_scroll");if(positions!=null&&positions.length==4)System.arraycopy(positions,0,scrollPositions,0,4);
        int index=Math.max(0,Math.min(3,state.getInt("shell_page",0)));currentScroll=null;show(index);
        currentScroll.post(()->currentScroll.scrollTo(0,scrollPositions[index]));
    }
    private void dock() {
        glassDock=new GlassDock(app,view,selected,index->{if(selected!=index)show(index);});
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,glassDock.preferredHeight(),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        p.leftMargin=p.rightMargin=app.dp(20);p.bottomMargin=app.dp(20);view.setDock(glassDock);view.addView(glassDock,p);
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
        homePlay=app.button("");
        homePlay.setOnClickListener(v->{if(app.snapshot.optBoolean("connected"))app.openController();else show(1);});add(homePlay,16);
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
            address.setText(draftAddress==null?app.getPreferences(0).getString("receiver",""):draftAddress);app.add(connectionBody,address,8);
            app.add(connectionBody,app.text("六位配对码",12,PRIMARY),18);code=app.field("000000");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});if(draftCode!=null)code.setText(draftCode);app.add(connectionBody,code,8);
            connect=app.button("连接并开始  →");connect.setOnClickListener(v->app.startConnection(address.getText().toString(),code.getText().toString().trim(),"manual"));app.add(connectionBody,connect,22);
        }
        formMessage=app.text(stickyError==null?"":stickyError,13,MUTED);app.add(connectionBody,formMessage,14);
        Button enter=secondary("进入手柄");enter.setOnClickListener(v->{if(app.snapshot.optBoolean("connected"))app.openController();else error("请先连接电脑");});add(enter,16);
        Button disconnect=secondary("断开当前连接");disconnect.setOnClickListener(v->{app.disconnectTransport();stickyError=null;show(1);});add(disconnect,10);
    }
    void error(String value){stickyError=value;if(formMessage!=null)formMessage.setText(value);else Toast.makeText(app,value,Toast.LENGTH_LONG).show();}
    boolean back(){
        if(selected==3&&aboutExpanded){setAboutExpanded(false);return true;}
        if(selected!=0){show(0);return true;}return false;
    }
    void update(JSONObject snapshot) {
        boolean connected=snapshot.optBoolean("connected"),reconnecting=snapshot.optBoolean("reconnectable");
        if(homeConnectionStatus!=null){
            String label=connected?"● 已连接":reconnecting?(app.isConnecting()?"◌ 连接中":"◌ 重连中"):"○ 未连接";
            if(connected&&!snapshot.isNull("rtt"))label+=" · "+snapshot.optLong("rtt")+" ms";
            homeConnectionStatus.setText(label);homeConnectionStatus.setTextColor(connected?0xffa8dab5:reconnecting?0xffffd180:MUTED);
            homeConnectionStatus.setContentDescription("手柄连接状态："+label+"，点击管理连接");
        }
        if(homePlay!=null)homePlay.setText(connected?"进入手柄":"扫码连接电脑");
        if(connectionStatus!=null)connectionStatus.setText(snapshot.optBoolean("connected")?"● 已连接 · "+(snapshot.isNull("rtt")?"测量延迟中":snapshot.optLong("rtt")+" ms"):"○ "+snapshot.optString("message","等待连接"));
        if(connect!=null)connect.setEnabled(!app.isConnecting());
        if(formMessage!=null&&stickyError==null&&app.isConnecting())formMessage.setText(snapshot.optString("message"));
    }
    private void settingsPage(){
        add(app.text("调整外观，让手柄更合你的习惯。",14,MUTED),10);
        LinearLayout glass=card();app.add(glass,app.text("Liquid Glass",20,FG),0);
        app.add(glass,app.text("Dock 玻璃效果",13,MUTED),12);
        qualityButton=secondary(qualityLabel());qualityButton.setContentDescription("Dock 玻璃效果");
        qualityButton.setOnClickListener(v->chooseGlassQuality());app.add(glass,qualityButton,8);
        app.add(glass,app.text("折射强度",16,FG),20);
        boolean stacked=app.getResources().getConfiguration().fontScale>1.3f||app.getResources().getConfiguration().screenWidthDp<360;
        RadioGroup strength=new RadioGroup(app);strength.setOrientation(stacked?RadioGroup.VERTICAL:RadioGroup.HORIZONTAL);
        strength.setContentDescription("Liquid Glass 折射强度");
        int selectedLevel=GlassQualityPolicy.refractionLevel(app);
        for(int level=0;level<4;level++){
            RadioButton choice=new RadioButton(app);choice.setId(View.generateViewId());choice.setText(refractionLabels[level]);
            choice.setTextSize(13);choice.setTextColor(FG);choice.setPadding(0,0,0,0);choice.setMinimumWidth(0);choice.setMinHeight(app.dp(48));
            choice.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY));choice.setContentDescription("折射强度"+refractionLabels[level]);
            choice.setTag(level);strength.addView(choice,new RadioGroup.LayoutParams(stacked?-1:0,-2,stacked?0:1));
            if(level==selectedLevel)strength.check(choice.getId());
        }
        strength.setOnCheckedChangeListener((group,id)->{
            View choice=group.findViewById(id);if(choice==null)return;
            app.getPreferences(0).edit().putInt("glass_refraction",(Integer)choice.getTag()).apply();refreshGlass();
        });app.add(glass,strength,8);
        refractionHint=app.text("",12,MUTED);app.add(glass,refractionHint,10);add(glass,24);refreshGlass();
        LinearLayout about=card();aboutToggle=secondary("关于 Orbit Pad");aboutToggle.setContentDescription("关于 Orbit Pad");
        aboutToggle.setOnClickListener(v->setAboutExpanded(!aboutExpanded));app.add(about,aboutToggle,0);
        aboutBody=new LinearLayout(app);aboutBody.setOrientation(LinearLayout.VERTICAL);
        app.add(aboutBody,app.text("手机，变身手柄。",20,PRIMARY),18);
        app.add(aboutBody,app.text("版本 "+app.snapshot.optString("version","0.5.0"),16,FG),10);
        app.add(aboutBody,app.text("Android 原生界面 · Rust 控制核心",14,MUTED),10);
        app.add(aboutBody,app.text("将手机变成 Windows 电脑的无线或有线手柄。支持模板布局与按键映射自定义。",14,MUTED),14);
        app.add(aboutBody,app.text("应用信息",18,FG),24);
        app.add(aboutBody,app.text("连接方式\n二维码 / 局域网 / USB 直连 / 蓝牙 RFCOMM\n\n系统支持\nAndroid 8.0 及以上 · ARM64 / x86_64\n\n电脑输出\nXbox 360 / XInput 原生手柄\n\n应用包名\napp.orbitpad.controller",14,MUTED),12);
        Button project=secondary("项目主页 · OYLT / Orbit-Pad");project.setOnClickListener(v->app.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/OYLT/Orbit-Pad"))));app.add(aboutBody,project,18);
        app.add(aboutBody,app.text("组件与隐私",18,FG),24);
        app.add(aboutBody,app.text("玻璃渲染：AndroidLiquidGlass / Backdrop（Kyant，Apache 2.0，原生适配）。\n二维码识别：ZXing 3.5.3（Apache 2.0）。\n蓝牙连接：Android Bluetooth API。\n相机仅在扫码时打开，二维码在手机本地识别。",14,MUTED),12);
        Button glassLicense=secondary("AndroidLiquidGlass 开源许可");
        glassLicense.setOnClickListener(v->{
            TextView license=app.text(KyantGlass.asset(app,"licenses/AndroidLiquidGlass-NOTICE.txt")+"\n"+KyantGlass.asset(app,"licenses/AndroidLiquidGlass-LICENSE.txt"),12,MUTED);
            license.setPadding(app.dp(20),app.dp(12),app.dp(20),app.dp(20));ScrollView body=new ScrollView(app);body.addView(license);
            new AlertDialog.Builder(app).setTitle("AndroidLiquidGlass · Apache 2.0").setView(body).setPositiveButton("关闭",null).show();
        });app.add(aboutBody,glassLicense,12);
        app.add(about,aboutBody,0);add(about,20);setAboutExpanded(aboutExpanded);
    }
    private String qualityLabel(){
        String quality=app.getPreferences(0).getString("glass_quality","auto");
        return "simple".equals(quality)?"简洁 · 降低渲染开销":"standard".equals(quality)?"柔和 · 模糊玻璃":"自动 · 随设备调整";
    }
    private void chooseGlassQuality(){
        String[] values={"auto","standard","simple"};String choice=app.getPreferences(0).getString("glass_quality","auto");
        int checked="simple".equals(choice)?2:"standard".equals(choice)?1:0;
        new AlertDialog.Builder(app).setTitle("Dock 玻璃效果")
            .setSingleChoiceItems(new String[]{"自动 · 随设备调整","柔和 · 模糊玻璃","简洁 · 降低渲染开销"},checked,(dialog,which)->{
                app.getPreferences(0).edit().putString("glass_quality",values[which]).apply();refreshGlass();dialog.dismiss();
            }).setNegativeButton("取消",null).show();
    }
    private void refreshGlass(){
        view.invalidate();glassDock.invalidate();
        if(qualityButton!=null)qualityButton.setText(qualityLabel());
        if(refractionHint!=null){
            int level=GlassQualityPolicy.refractionLevel(app);
            String hint=level==0?"保留模糊与高光，关闭透镜折射。":"当前："+refractionLabels[level]+"。调整后立即生效，并自动保存。";
            if(view.quality()!=GlassQualityPolicy.FULL&&level!=0)hint="强度已保存；自动模式且设备支持时启用折射，省电时暂停。";
            refractionHint.setText(hint);
        }
    }
    private void setAboutExpanded(boolean expanded){
        aboutExpanded=expanded;if(aboutBody==null||aboutToggle==null)return;
        aboutBody.setVisibility(expanded?View.VISIBLE:View.GONE);aboutToggle.setText("关于 Orbit Pad    "+(expanded?"⌃":"⌄"));
        if(Build.VERSION.SDK_INT>=30)aboutToggle.setStateDescription(expanded?"已展开":"已收起");
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
}
