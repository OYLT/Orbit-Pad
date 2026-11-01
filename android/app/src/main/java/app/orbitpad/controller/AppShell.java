package app.orbitpad.controller;

import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.PathInterpolator;
import android.content.Intent;
import android.content.res.Configuration;
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
    private int BG=0xff141218, FG=0xffe6e0e9, MUTED=0xffcac4d0,
        SURFACE=0xff211f26, PRIMARY=0xffd0bcff;
    private final MainActivity app;
    final GlassBackdrop view;
    private GlassDock glassDock;
    private MaterialNavigation materialNavigation;
    private boolean themeExpansion;
    private final View[] themeCards=new View[2];
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
    private TemplateCarousel templateCarousel;
    private EditText address, code;
    private LinearLayout connectionBody;
    private String stickyError;
    private int safeLeft,safeTop,safeRight,safeBottom,keyboardBottom;
    private boolean keyboardVisible,aboutExpanded,landscape;
    private LinearLayout aboutBody;
    private Button aboutToggle,qualityButton;
    private TextView refractionHint;
    private String themeMenu="";
    private int themeRootScroll;
    private GlassPreview glassPreview;

    AppShell(MainActivity app,int initial,String error) {
        this.app=app;readTheme();selected=initial;stickyError=error;
        landscape=app.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
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
                if(Build.VERSION.SDK_INT>=28&&i.getDisplayCutout()!=null){
                    safeLeft=Math.max(safeLeft,i.getDisplayCutout().getSafeInsetLeft());safeRight=Math.max(safeRight,i.getDisplayCutout().getSafeInsetRight());
                    safeTop=Math.max(safeTop,i.getDisplayCutout().getSafeInsetTop());safeBottom=Math.max(safeBottom,i.getDisplayCutout().getSafeInsetBottom());
                }
            }
            applyInsets();return i;
        });
        if(Build.VERSION.SDK_INT<30){
            final int[] windowSize={0,0};
            final android.graphics.Rect visibleFrame=new android.graphics.Rect();final int[] location=new int[2];
            view.getViewTreeObserver().addOnGlobalLayoutListener(()->{
                int width=view.getWidth(),height=view.getHeight();
                if(windowSize[0]!=width){windowSize[0]=width;windowSize[1]=height;}
                windowSize[1]=Math.max(windowSize[1],height);
                view.getWindowVisibleDisplayFrame(visibleFrame);view.getLocationOnScreen(location);
                int obscured=Math.max(0,location[1]+height-visibleFrame.bottom);
                keyboardVisible=Math.max(windowSize[1]-height,obscured)>app.dp(100);
                keyboardBottom=obscured>app.dp(100)?obscured:0;applyInsets();
            });
        }
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){finishPageTransition();}
        });
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)applyInsets();});
        dock();show(initial);view.requestApplyInsets();
    }
    void onConfigurationChanged(){
        finishPageTransition();
        landscape=app.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        keyboardBottom=0;keyboardVisible=false;
        if(selected==2)refreshTheme();applyInsets();view.requestApplyInsets();
    }
    private void applyInsets(){
        view.setTopFade(landscape?0:safeTop,currentScroll==null?0:Math.min(1,currentScroll.getScrollY()/(float)app.dp(36)));
        if(landscape){safeLeft=0;safeTop=0;safeRight=0;safeBottom=0;}
        materialNavigation.setVertical(landscape);
        materialNavigation.setVisibility(app.theme.material&&!keyboardVisible?View.VISIBLE:View.GONE);
        glassDock.setVisibility(!app.theme.material&&!keyboardVisible?View.VISIBLE:View.GONE);
        if(app.theme.material){
            int rail=app.dp(96),bar=app.dp(Math.max(80,56+20*app.getResources().getConfiguration().fontScale));
            FrameLayout.LayoutParams pageParams=(FrameLayout.LayoutParams)page.getLayoutParams();
            int left=landscape?rail:0,bottom=landscape||keyboardVisible?0:bar+safeBottom;
            if(pageParams.leftMargin!=left||pageParams.bottomMargin!=bottom){pageParams.leftMargin=left;pageParams.bottomMargin=bottom;page.setLayoutParams(pageParams);}
            if(currentScroll!=null)currentScroll.setPadding(landscape?0:safeLeft,landscape?0:safeTop,safeRight,keyboardBottom);
            if(content!=null)content.setPadding(app.dp(24),app.dp(landscape?12:28),app.dp(24),app.dp(24));
            FrameLayout.LayoutParams nav=(FrameLayout.LayoutParams)materialNavigation.getLayoutParams();int width=landscape?rail:-1,height=landscape?-1:bar+safeBottom,gravity=landscape?Gravity.LEFT:Gravity.BOTTOM;
            materialNavigation.setPadding(0,0,0,landscape?0:safeBottom);if(nav.width!=width||nav.height!=height||nav.gravity!=gravity){nav.width=width;nav.height=height;nav.gravity=gravity;materialNavigation.setLayoutParams(nav);}return;
        }
        // Reserve a left rail in landscape, while keeping portrait's floating bottom Dock.
        int railWidth=glassDock.preferredHeight();
        FrameLayout.LayoutParams pageParams=(FrameLayout.LayoutParams)page.getLayoutParams();
        int pageLeft=landscape?app.dp(24)+railWidth:0;
        if(pageParams.bottomMargin!=0){pageParams.bottomMargin=0;page.setLayoutParams(pageParams);}
        if(pageParams.leftMargin!=pageLeft){pageParams.leftMargin=pageLeft;page.setLayoutParams(pageParams);}
        if(currentScroll!=null)currentScroll.setPadding(landscape?0:safeLeft,safeTop,safeRight,landscape?Math.max(safeBottom,keyboardBottom):keyboardBottom);
        if(content!=null)content.setPadding(app.dp(landscape?20:24),app.dp(landscape?12:28),app.dp(landscape?20:24),landscape||keyboardVisible?app.dp(24):glassDock.preferredHeight()+app.dp(52)+safeBottom);
        if(glassDock!=null){
            glassDock.setVertical(landscape);
            glassDock.setVisibility(keyboardVisible?View.GONE:View.VISIBLE);
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)glassDock.getLayoutParams();
            int left=app.dp(landscape?12:20)+safeLeft,right=landscape?0:app.dp(20)+safeRight;
            int top=landscape?safeTop+app.dp(12):0,bottom=app.dp(landscape?12:20)+safeBottom;
            int availableHeight=(view.getHeight()>0?view.getHeight():app.getResources().getDisplayMetrics().heightPixels)-top-bottom;
            int width=landscape?railWidth:-1,height=landscape?Math.min(railWidth*4+app.dp(8),Math.max(0,availableHeight)):glassDock.currentHeight();
            int gravity=landscape?Gravity.LEFT|Gravity.CENTER_VERTICAL:Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
            if(p.width!=width||p.height!=height||p.gravity!=gravity||p.leftMargin!=left||p.rightMargin!=right||p.topMargin!=top||p.bottomMargin!=bottom){
                p.width=width;p.height=height;p.gravity=gravity;p.leftMargin=left;p.rightMargin=right;p.topMargin=top;p.bottomMargin=bottom;glassDock.setLayoutParams(p);
            }
        }
    }
    private void show(int index) {
        boolean switching=selected!=index;
        if(switching&&index!=2)themeMenu="";
        finishPageTransition();
        int direction=index>selected?1:-1;
        ScrollView previous=currentScroll;
        boolean animate=switching&&previous!=null&&view.isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();
        if(currentScroll!=null)scrollPositions[selected]=currentScroll.getScrollY();
        if(address!=null){draftAddress=address.getText().toString();draftCode=code.getText().toString();}
        address=null;code=null;
        selected=index;page.removeAllViews();connectionStatus=null;homeConnectionStatus=null;homePlay=null;templateCarousel=null;formMessage=null;connect=null;aboutBody=null;aboutToggle=null;refractionHint=null;qualityButton=null;glassPreview=null;
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
        if(index==2&&!themeMenu.isEmpty()){TextView back=app.text("‹  主题",14,PRIMARY);back.setMinHeight(app.dp(48));back.setGravity(Gravity.CENTER_VERTICAL);back.setClickable(true);back.setFocusable(true);back.setContentDescription("返回主题列表");back.setOnClickListener(v->closeThemeMenu());add(back,8);}
        add(app.text(index==2&&!themeMenu.isEmpty()?("liquid".equals(themeMenu)?"液态玻璃":"Material 3"):titles[index],28,FG),12);
        if(index==0)home();else if(index==1)connection();else if(index==2)themesPage();else if(index==3)settingsPage();
        glassDock.setSelectedIndex(index);materialNavigation.setSelectedIndex(index);applyInsets();update(app.snapshot);
        if(switching)scroll.post(()->{if(currentScroll==scroll)scroll.scrollTo(0,scrollPositions[index]);});
        if(animate)startPageTransition(previous,scroll,direction);
    }
    private void finishPageTransition(){
        if(pageTransition!=null){ValueAnimator running=pageTransition;pageTransition=null;running.cancel();}
        if(outgoingScroll!=null){page.removeView(outgoingScroll);outgoingScroll=null;}
        themeExpansion=false;
        if(currentScroll!=null){currentScroll.setClipBounds(null);currentScroll.setClipToOutline(false);currentScroll.setScaleX(1);currentScroll.setScaleY(1);currentScroll.setTranslationX(0);currentScroll.setAlpha(1);currentScroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
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
        state.putString("shell_theme_menu",themeMenu);state.putInt("shell_theme_root_scroll",themeRootScroll);
        state.putInt("shell_page",selected);state.putString("shell_mode",mode);state.putBoolean("shell_about_expanded",aboutExpanded);
        if(currentScroll!=null)scrollPositions[selected]=currentScroll.getScrollY();
        state.putIntArray("shell_scroll",scrollPositions);
        state.putString("shell_address",address==null?draftAddress:address.getText().toString());
        state.putString("shell_code",code==null?draftCode:code.getText().toString());
    }
    void restoreState(Bundle state){
        themeMenu=state.getString("shell_theme_menu","");themeRootScroll=state.getInt("shell_theme_root_scroll",0);
        aboutExpanded=state.getBoolean("shell_about_expanded",false);mode=state.getString("shell_mode","scan");draftAddress=state.getString("shell_address");draftCode=state.getString("shell_code");
        int[] positions=state.getIntArray("shell_scroll");if(positions!=null&&positions.length==4)System.arraycopy(positions,0,scrollPositions,0,4);
        int index=Math.max(0,Math.min(3,state.getInt("shell_page",0)));currentScroll=null;show(index);
        currentScroll.post(()->currentScroll.scrollTo(0,scrollPositions[index]));
    }
    private void dock() {
        glassDock=new GlassDock(app,view,selected,index->{if(selected!=index)show(index);});
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,glassDock.preferredHeight(),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        p.leftMargin=p.rightMargin=app.dp(20);p.bottomMargin=app.dp(20);view.setDock(glassDock);view.addView(glassDock,p);
        materialNavigation=new MaterialNavigation(app,selected,index->{if(selected!=index)show(index);});
        view.addView(materialNavigation,new FrameLayout.LayoutParams(-1,app.dp(80),Gravity.BOTTOM));
    }
    private void add(View v,int top){app.add(content,v,top);}
    private LinearLayout card() {
        LinearLayout c=new LinearLayout(app);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(app.dp(20),app.dp(20),app.dp(20),app.dp(20));
        c.setBackground(app.rounded(SURFACE,0xff38333f,24));return c;
    }
    private void home() {
        add(app.text("滑动选择手柄，每张卡片都保留自己的布局。",14,MUTED),10);
        ArrayList<TemplateCarousel.Entry> choices=new ArrayList<>();
        String[] names={"通用游戏","竞速驾驶","平台跳跃","极限竞速：地平线"},ids={"general","racing","platform","forza"};
        JSONObject presetLayouts;try{presetLayouts=new JSONObject(app.getPreferences(0).getString("preset_layouts_v1","{}"));}catch(Exception error){presetLayouts=new JSONObject();}
        for(int i=0;i<ids.length;i++){
            JSONObject c=app.command("preset");app.put(c,"name",ids[i]);JSONObject config=presetLayouts.optJSONObject(ids[i]);
            if(config!=null){try{app.layoutFor(config);}catch(Exception error){config=null;}}
            choices.add(new TemplateCarousel.Entry(ids[i],names[i],config==null?app.call(c):config,false));
        }
        JSONArray saved=templates();for(int i=0;i<saved.length();i++){
            JSONObject item=saved.optJSONObject(i);if(item==null||item.optJSONObject("settings")==null)continue;
            try{app.layoutFor(item.optJSONObject("settings"));choices.add(new TemplateCarousel.Entry(item.optString("id"),item.optString("name"),item.optJSONObject("settings"),true));}catch(Exception ignored){}
        }
        String currentName=app.getPreferences(0).getString("template_name","通用游戏");int initial=-1;
        for(int i=0;i<choices.size();i++)if(choices.get(i).name.equals(currentName)&&choices.get(i).settings.toString().equals(app.settings.toString())){initial=i;break;}
        if(initial<0){choices.add(0,new TemplateCarousel.Entry("working",currentName,app.settings,false));initial=0;}
        LinearLayout navigation=new LinearLayout(app);navigation.setGravity(Gravity.CENTER_VERTICAL);
        TextView counter=app.text("",13,PRIMARY);counter.setGravity(Gravity.CENTER);navigation.addView(counter,new LinearLayout.LayoutParams(0,app.dp(44),1));
        Button previous=secondary("‹"),next=secondary("›");previous.setContentDescription("上一个手柄配置");next.setContentDescription("下一个手柄配置");
        navigation.addView(previous,new LinearLayout.LayoutParams(app.dp(48),app.dp(44)));navigation.addView(next,new LinearLayout.LayoutParams(app.dp(48),app.dp(44)));add(navigation,16);
        Button remove=secondary("删除此模板");remove.setContentDescription("删除当前手柄模板");
        final TemplateCarousel[] holder=new TemplateCarousel[1];
        TemplateCarousel carousel=new TemplateCarousel(app,choices,initial,entry->{
            app.applyTemplate(entry.settings,entry.name);if(!entry.id.equals("working"))app.getPreferences(0).edit().putString("active_template_id",entry.id).apply();
            int index=holder[0].activeIndex();counter.setText((index+1)+" / "+choices.size());previous.setEnabled(index>0);next.setEnabled(index<choices.size()-1);remove.setVisibility(entry.saved?View.VISIBLE:View.GONE);
        });holder[0]=carousel;templateCarousel=carousel;
        counter.setText((initial+1)+" / "+choices.size());previous.setEnabled(initial>0);next.setEnabled(initial<choices.size()-1);
        previous.setOnClickListener(v->carousel.select(carousel.activeIndex()-1,true));next.setOnClickListener(v->carousel.select(carousel.activeIndex()+1,true));
        int previewHeight=Math.max(190,Math.min(270,(int)((app.getResources().getDisplayMetrics().widthPixels/app.getResources().getDisplayMetrics().density-126)*.45f)+96));
        addCarousel(carousel,previewHeight);
        Button edit=app.button("自定义按键布局");edit.setOnClickListener(v->app.openEditor());add(edit,16);
        LinearLayout actions=new LinearLayout(app);Button save=secondary("＋ 保存为新模板");save.setOnClickListener(v->saveTemplate());actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        remove.setVisibility(choices.get(initial).saved?View.VISIBLE:View.GONE);remove.setOnClickListener(v->{
            TemplateCarousel.Entry entry=carousel.activeEntry();if(!entry.saved)return;
            new ThemeDialogBuilder(app).setTitle("删除模板？").setMessage(entry.name).setPositiveButton("删除",(d,w)->{
                JSONArray all=templates(),result=new JSONArray();for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&!item.optString("id").equals(entry.id))result.put(item);}storeTemplates(result);show(0);
            }).setNegativeButton("取消",null).show();
        });actions.addView(remove,new LinearLayout.LayoutParams(0,-2,1));add(actions,12);
        Button mapping=secondary("按键映射与手感设置");mapping.setOnClickListener(v->app.showSettings());add(mapping,16);
        homePlay=app.button("");homePlay.setOnClickListener(v->{if(app.snapshot.optBoolean("connected"))app.openController();else show(1);});add(homePlay,16);
    }
    private void addCarousel(TemplateCarousel carousel,int height){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,app.dp(height));p.topMargin=app.dp(8);content.addView(carousel,p);
    }
    private JSONArray templates(){try{return new JSONArray(app.getPreferences(0).getString("templates_v1","[]"));}catch(Exception e){return new JSONArray();}}
    private void storeTemplates(JSONArray data){app.getPreferences(0).edit().putString("templates_v1",data.toString()).apply();}
    private void saveTemplate() {
        EditText name=app.field("模板名称");name.setText("我的手柄");
        AlertDialog dialog=new ThemeDialogBuilder(app).setTitle("保存当前模板").setView(name).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->{ThemeDialogBuilder.style(app,dialog);dialog.getButton(-1).setOnClickListener(v->{
            String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>30){name.setError("请输入 1–30 字的名称");return;}
            JSONArray list=templates();if(list.length()>=24){name.setError("最多保存 24 个模板");return;}
            JSONObject entry=new JSONObject();app.put(entry,"id",UUID.randomUUID().toString());app.put(entry,"name",value);app.put(entry,"settings",app.settings);
            list.put(entry);storeTemplates(list);app.getPreferences(0).edit().putString("template_name",value).putString("active_template_id",entry.optString("id")).apply();dialog.dismiss();show(0);
        });});dialog.show();
    }
    private Button secondary(String label){Button b=app.button(label);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0xffffff)|0x33000000),app.rounded(app.theme.surfaceHigh,0,20),null));b.setTextColor(FG);return b;}
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
        if(selected==2&&!themeMenu.isEmpty()){closeThemeMenu();return true;}
        if(selected==3&&aboutExpanded){setAboutExpanded(false);return true;}
        if(selected!=0){show(0);return true;}return false;
    }
    void update(JSONObject snapshot) {
        boolean connected=snapshot.optBoolean("connected"),reconnecting=snapshot.optBoolean("reconnectable");
        if(homeConnectionStatus!=null){
            String label=connected?"● 已连接":reconnecting?(app.isConnecting()?"◌ 连接中":"◌ 重连中"):"○ 未连接";
            if(connected&&!snapshot.isNull("rtt"))label+=" · "+snapshot.optLong("rtt")+" ms";
            homeConnectionStatus.setText(label);homeConnectionStatus.setTextColor(connected?(app.theme.dark?0xffa8dab5:0xff256644):reconnecting?(app.theme.dark?0xffffd180:0xff785800):MUTED);
            homeConnectionStatus.setContentDescription("手柄连接状态："+label+"，点击管理连接");
        }
        if(homePlay!=null)homePlay.setText(connected?"进入手柄":"扫码连接电脑");
        if(connectionStatus!=null)connectionStatus.setText(snapshot.optBoolean("connected")?"● 已连接 · "+(snapshot.isNull("rtt")?"测量延迟中":snapshot.optLong("rtt")+" ms"):"○ "+snapshot.optString("message","等待连接"));
        if(connect!=null)connect.setEnabled(!app.isConnecting());
        if(formMessage!=null&&stickyError==null&&app.isConnecting())formMessage.setText(snapshot.optString("message"));
    }
    private void settingsPage(){
        add(app.text("管理手柄设置与应用信息。",14,MUTED),10);
        Button appearance=secondary("主题与外观  ›");appearance.setOnClickListener(v->show(2));add(appearance,24);
        Button controls=secondary("按键映射与手感设置");controls.setOnClickListener(v->app.showSettings());add(controls,12);
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
            new ThemeDialogBuilder(app).setTitle("AndroidLiquidGlass · Apache 2.0").setView(body).setPositiveButton("关闭",null).show();
        });app.add(aboutBody,glassLicense,12);
        Button colorsLicense=secondary("Material Color Utilities 开源许可");colorsLicense.setOnClickListener(v->{TextView license=app.text(KyantGlass.asset(app,"licenses/MaterialColorUtilities-NOTICE.txt")+"\n"+KyantGlass.asset(app,"licenses/MaterialColorUtilities-LICENSE.txt"),12,MUTED);ScrollView body=new ScrollView(app);body.addView(license);new ThemeDialogBuilder(app).setTitle("Material Color Utilities · Apache 2.0").setView(body).setPositiveButton("关闭",null).show();});app.add(aboutBody,colorsLicense,12);
        app.add(about,aboutBody,0);add(about,20);setAboutExpanded(aboutExpanded);
    }
    private String qualityLabel(){
        String quality=app.getPreferences(0).getString("glass_quality","auto");
        return "simple".equals(quality)?"简洁 · 降低渲染开销":"standard".equals(quality)?"柔和 · 模糊玻璃":"自动 · 随设备调整";
    }
    private void chooseGlassQuality(){
        String[] values={"auto","standard","simple"};String choice=app.getPreferences(0).getString("glass_quality","auto");
        int checked="simple".equals(choice)?2:"standard".equals(choice)?1:0;
        new ThemeDialogBuilder(app).setTitle("Dock 玻璃效果")
            .setSingleChoiceItems(new String[]{"自动 · 随设备调整","柔和 · 模糊玻璃","简洁 · 降低渲染开销"},checked,(dialog,which)->{
                app.getPreferences(0).edit().putString("glass_quality",values[which]).apply();refreshGlass();dialog.dismiss();
            }).setNegativeButton("取消",null).show();
    }
    private void refreshGlass(){
        view.invalidate();glassDock.invalidate();if(glassPreview!=null)glassPreview.refresh();
        if(qualityButton!=null)qualityButton.setText(qualityLabel());
        if(refractionHint!=null){
            float strength=GlassQualityPolicy.refractionStrength(app);
            String hint=strength==0?"保留模糊与高光，关闭透镜折射。":"拖动滑杆，实时查看折射变化；松手自动保存。";
            if(GlassQualityPolicy.resolveGlass(app)!=GlassQualityPolicy.FULL&&strength>0)hint="当前设备或质量模式使用简化效果。强度已保存，支持完整效果时生效。";
            refractionHint.setText(hint);
        }
    }
    private void readTheme(){BG=app.theme.background;FG=app.theme.onSurface;MUTED=app.theme.muted;SURFACE=app.theme.surface;PRIMARY=app.theme.primary;}
    void refreshTheme(){
        int y=currentScroll==null?0:currentScroll.getScrollY();readTheme();view.refreshTheme();glassDock.refreshTheme();materialNavigation.refreshTheme();show(selected);
        ScrollView target=currentScroll;target.post(()->{if(target==currentScroll)target.scrollTo(0,y);});
    }
    private void openThemeMenu(String menu){
        ScrollView previous=currentScroll;int index="liquid".equals(menu)?0:1;
        android.graphics.Rect bounds=cardBounds(themeCards[index]);
        if(themeMenu.isEmpty())themeRootScroll=currentScroll.getScrollY();themeMenu=menu;show(2);
        ScrollView next=currentScroll;next.post(()->{if(next==currentScroll){next.scrollTo(0,0);expandTheme(previous,next,bounds,false);}});
    }
    private android.graphics.Rect cardBounds(View card){
        android.graphics.Rect bounds=new android.graphics.Rect(0,0,page.getWidth(),page.getHeight());
        if(card!=null&&card.isAttachedToWindow()){card.getDrawingRect(bounds);page.offsetDescendantRectToMyCoords(card,bounds);}
        return bounds;
    }
    private void closeThemeMenu(){
        ScrollView previous=currentScroll;int index="liquid".equals(themeMenu)?0:1;themeMenu="";show(2);
        ScrollView next=currentScroll;next.post(()->{if(next==currentScroll){next.scrollTo(0,themeRootScroll);expandTheme(previous,next,cardBounds(themeCards[index]),true);}});
    }
    private void expandTheme(ScrollView previous,ScrollView next,android.graphics.Rect card,boolean closing){
        if(!ValueAnimator.areAnimatorsEnabled()||!view.isAttachedToWindow())return;
        finishPageTransition();outgoingScroll=previous;themeExpansion=true;
        previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(closing)page.addView(previous);else page.addView(previous,0);
        ScrollView expanding=closing?previous:next;expanding.setBackgroundColor(BG);
        android.graphics.Rect full=new android.graphics.Rect(0,0,page.getWidth(),page.getHeight());
        ValueAnimator animator=ValueAnimator.ofFloat(0,1);pageTransition=animator;animator.setDuration(400);animator.setInterpolator(new PathInterpolator(.2f,0,.15f,1));
        animator.addUpdateListener(a->{
            float t=(float)a.getAnimatedValue(),f=closing?1-t:t;
            expanding.setClipBounds(new android.graphics.Rect(Math.round(card.left*(1-f)),Math.round(card.top*(1-f)),Math.round(card.right+(full.right-card.right)*f),Math.round(card.bottom+(full.bottom-card.bottom)*f)));
            expanding.setAlpha(closing?1-t*.3f:.65f+.35f*t);if(!closing)previous.setAlpha(1-t*.45f);view.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){
            expanding.setClipBounds(null);expanding.setAlpha(1);page.removeView(previous);next.setAlpha(1);
            if(pageTransition==animation){pageTransition=null;outgoingScroll=null;themeExpansion=false;}
        }});animator.start();
    }
    private void useDesign(String design){app.getPreferences(0).edit().putString("app_design",design).apply();app.applyAppTheme();}
    private void themesPage(){
        if("liquid".equals(themeMenu)){liquidSettings();return;}
        if("material3".equals(themeMenu)){materialSettings();return;}
        add(app.text("为你的手柄，选一种气质。",16,MUTED),8);
        LinearLayout appearance=card();app.add(appearance,app.text("颜色模式",16,FG),0);
        String[] modeNames={"跟随系统深色模式","浅色","深色"},modeIds={"system","light","dark"};
        int checked="light".equals(app.theme.appearance)?1:"dark".equals(app.theme.appearance)?2:0;
        Button colorMode=secondary(modeNames[checked]);colorMode.setContentDescription("选择颜色模式");
        colorMode.setOnClickListener(v->new ThemeDialogBuilder(app).setTitle("颜色模式").setSingleChoiceItems(modeNames,checked,(dialog,which)->{dialog.dismiss();app.getPreferences(0).edit().putString("appearance_mode",modeIds[which]).apply();app.applyAppTheme();}).setNegativeButton("取消",null).show());app.add(appearance,colorMode,8);add(appearance,20);
        add(app.text("外观设计",12,PRIMARY),24);
        boolean narrow=app.getResources().getConfiguration().screenWidthDp<360||app.getResources().getConfiguration().fontScale>1.3f;
        LinearLayout masonry=new LinearLayout(app);masonry.setOrientation(narrow?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        String[] ids={"liquid","material3"};
        for(int i=0;i<2;i++){
            final String id=ids[i];boolean active=app.theme.material==id.equals("material3");
            LinearLayout tile=card();themeCards[i]=tile;tile.setPadding(app.dp(16),app.dp(16),app.dp(16),app.dp(20));
            tile.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0xffffff)|0x22000000),app.rounded(SURFACE,active?PRIMARY:app.theme.outlineVariant,28),null));
            tile.setClickable(true);tile.setFocusable(true);tile.setContentDescription((i==0?"液态玻璃":"Material 3")+"主题设置");
            tile.setOnClickListener(v->openThemeMenu(id));
            tile.addView(new ThemeArtwork(app,i==0),new LinearLayout.LayoutParams(-1,app.dp(i==0?172:128)));
            app.add(tile,app.text(i==0?"液态玻璃":"Material 3",20,FG),18);
            app.add(tile,app.text(i==0?"Liquid Glass":"Material You · Monet",11,PRIMARY),4);
            app.add(tile,app.text(i==0?"通透的光影，\n随指尖流动。":"色彩随你，\n处处协调。",14,MUTED),12);
            if(i==1){LinearLayout swatches=new LinearLayout(app);for(int color:ThemePalette.SEEDS){View dot=new View(app);dot.setBackground(app.rounded(color,0,20));LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,app.dp(12),1);d.setMargins(0,0,app.dp(3),0);swatches.addView(dot,d);}app.add(tile,swatches,16);}
            TextView status=app.text(active?"✓ 正在使用":"探索设置  ↗",12,active?PRIMARY:MUTED);app.add(tile,status,20);
            if(Build.VERSION.SDK_INT>=30)tile.setStateDescription(active?"正在使用":"未启用");
            tile.setSelected(active);
            LinearLayout column=new LinearLayout(app);column.setOrientation(LinearLayout.VERTICAL);column.addView(tile,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(narrow?-1:0,-2,narrow?0:1);size.topMargin=app.dp(narrow?(i==0?0:16):(i==0?0:30));if(!narrow&&i==0)size.rightMargin=app.dp(12);masonry.addView(column,size);
        }
        add(masonry,16);
    }
    private void liquidSettings(){
        add(app.text("通透材质与柔和光影，随指尖流动。",14,MUTED),8);
        glassPreview=new GlassPreview(app);LinearLayout.LayoutParams previewSize=new LinearLayout.LayoutParams(-1,app.dp(208));previewSize.topMargin=app.dp(20);content.addView(glassPreview,previewSize);
        Button apply=app.button(app.theme.material?"使用液态玻璃":"✓ 正在使用液态玻璃");apply.setEnabled(app.theme.material);apply.setOnClickListener(v->useDesign("liquid"));add(apply,16);
        LinearLayout glass=card();app.add(glass,app.text("Liquid Glass",20,FG),0);
        app.add(glass,app.text("Dock 玻璃效果",13,MUTED),16);
        qualityButton=secondary(qualityLabel());qualityButton.setContentDescription("Dock 玻璃效果");qualityButton.setOnClickListener(v->chooseGlassQuality());app.add(glass,qualityButton,8);
        TextView amount=app.text("",16,FG);app.add(glass,amount,20);
        SeekBar slider=new SeekBar(app);slider.setMax(1600);slider.setProgress(Math.round(GlassQualityPolicy.refractionStrength(app)*1000));
        slider.setMinHeight(app.dp(48));slider.setContentDescription("Liquid Glass 折射强度");if(Build.VERSION.SDK_INT>=30)slider.setStateDescription(String.format(java.util.Locale.ROOT,"%.1f%%",slider.getProgress()/10f));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(PRIMARY));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
        amount.setText("折射强度 · "+String.format(java.util.Locale.ROOT,"%.1f%%",slider.getProgress()/10f));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                app.getPreferences(0).edit().putFloat("glass_refraction_strength",progress/1000f).apply();
                amount.setText("折射强度 · "+String.format(java.util.Locale.ROOT,"%.1f%%",progress/10f));if(Build.VERSION.SDK_INT>=30)bar.setStateDescription(String.format(java.util.Locale.ROOT,"%.1f%%",progress/10f));refreshGlass();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });app.add(glass,slider,8);
        refractionHint=app.text("",12,MUTED);app.add(glass,refractionHint,10);add(glass,20);refreshGlass();
    }
    private void materialSettings(){
        add(app.text("让色彩贯穿每一页，也贯穿你的手柄。",14,MUTED),8);
        LinearLayout preview=card();preview.addView(new ThemeArtwork(app,false),new LinearLayout.LayoutParams(-1,app.dp(130)));
        app.add(preview,app.text("Material You",22,PRIMARY),12);app.add(preview,app.text("柔和色调 · 圆角卡片 · 清晰层次",13,MUTED),8);add(preview,20);
        Button apply=app.button(app.theme.material?"✓ 正在使用 Material 3":"使用 Material 3");apply.setEnabled(!app.theme.material);apply.setOnClickListener(v->useDesign("material3"));add(apply,16);
        LinearLayout dynamic=card();Switch follow=new Switch(app);follow.setText("系统动态取色");follow.setTextSize(16);follow.setTextColor(FG);follow.setMinHeight(app.dp(48));
        follow.setContentDescription("系统动态取色");follow.setChecked(app.getPreferences(0).getBoolean("monet_dynamic",false));follow.setEnabled(Build.VERSION.SDK_INT>=31);
        follow.setOnCheckedChangeListener((button,checked)->{app.getPreferences(0).edit().putBoolean("monet_dynamic",checked).putString("app_design","material3").apply();app.applyAppTheme();if(checked)app.refreshWallpaperPalette();});
        dynamic.addView(follow);app.add(dynamic,app.text(Build.VERSION.SDK_INT>=31?("wallpaper".equals(app.theme.dynamicSource)?"已从当前系统壁纸取色，壁纸变化时自动更新。":"image".equals(app.theme.dynamicSource)?"已从你选择的壁纸图片取色。":"正在使用系统调色板；若系统未提供壁纸颜色，可从壁纸图片取色。"):"系统动态取色需要 Android 12 及以上；可使用下方 Monet 预设。",12,MUTED),8);if(Build.VERSION.SDK_INT>=31){Button refresh=secondary("刷新壁纸取色");refresh.setOnClickListener(v->app.refreshWallpaperPalette());app.add(dynamic,refresh,12);Button image=secondary("从壁纸图片取色");image.setOnClickListener(v->app.chooseWallpaperImage());app.add(dynamic,image,8);}add(dynamic,20);
        add(app.text("Monet 调色板",20,FG),24);add(app.text("选择预设会关闭动态取色，并立即应用。",12,MUTED),6);
        boolean narrow=app.getResources().getConfiguration().screenWidthDp<360||app.getResources().getConfiguration().fontScale>1.3f;
        LinearLayout row=null;
        for(int i=0;i<ThemePalette.IDS.length;i++){
            final int index=i;int columns=narrow?1:2;
            if(i%columns==0){row=new LinearLayout(app);add(row,12);}
            LinearLayout option=card();option.setPadding(app.dp(14),app.dp(14),app.dp(14),app.dp(14));
            android.content.SharedPreferences prefs=app.getPreferences(0);
            // Preview each preset without mutating saved preferences.
            int[] colors=ThemeArtwork.tones(ThemePalette.SEEDS[i]);LinearLayout chips=new LinearLayout(app);
            for(int color:colors){View chip=new View(app);chip.setBackground(app.rounded(color,0,14));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,app.dp(36),1);cp.setMargins(0,0,app.dp(3),0);chips.addView(chip,cp);}option.addView(chips);
            boolean active=!app.theme.dynamic&&ThemePalette.IDS[i].equals(app.theme.preset);
            app.add(option,app.text(ThemePalette.NAMES[i]+(active?"  ✓":""),14,FG),12);
            option.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0xffffff)|0x33000000),app.rounded(SURFACE,active?PRIMARY:app.theme.outlineVariant,24),null));
            option.setContentDescription("Monet 配色"+ThemePalette.NAMES[i]);option.setSelected(active);option.setClickable(true);option.setFocusable(true);
            option.setOnClickListener(v->{prefs.edit().putString("monet_preset",ThemePalette.IDS[index]).putBoolean("monet_dynamic",false).putString("app_design","material3").apply();app.applyAppTheme();});
            LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,-2,1);if(i%columns==0&&columns>1)op.rightMargin=app.dp(10);row.addView(option,op);
        }
    }
    private void setAboutExpanded(boolean expanded){
        aboutExpanded=expanded;if(aboutBody==null||aboutToggle==null)return;
        aboutBody.setVisibility(expanded?View.VISIBLE:View.GONE);aboutToggle.setText("关于 Orbit Pad    "+(expanded?"⌃":"⌄"));
        if(Build.VERSION.SDK_INT>=30)aboutToggle.setStateDescription(expanded?"已展开":"已收起");
    }
    private final class QrMark extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        QrMark(MainActivity app){super(app);setContentDescription("扫码连接图标");}
        @Override protected void onDraw(Canvas c){float u=getWidth()/21f;p.setColor(PRIMARY);for(int[] q:new int[][]{{1,1},{13,1},{1,13}}){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u);c.drawRoundRect((q[0]+.5f)*u,(q[1]+.5f)*u,(q[0]+6.5f)*u,(q[1]+6.5f)*u,u,u,p);p.setStyle(Paint.Style.FILL);c.drawRect((q[0]+2)*u,(q[1]+2)*u,(q[0]+5)*u,(q[1]+5)*u,p);}for(int[] b:new int[][]{{13,13},{15,15},{18,13},{13,18},{18,18},{10,9},{9,12},{9,17}})c.drawRect(b[0]*u,b[1]*u,(b[0]+2)*u,(b[1]+2)*u,p);}
    }
}
