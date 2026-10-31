package app.orbitpad.controller;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Floating tools never reserve space in, or resize, the controller canvas. */
final class LayoutEditor {
    private static final String[] FACE={"x","a","b","y"},DPAD={"up","down","left","right"};
    private final MainActivity app;
    private final View canvas;
    final FrameLayout root;
    private final LinearLayout toolbar;
    private LinearLayout menu;
    private ScrollView menuScroll;
    private String selected;
    private int tab=1;
    private Session gesture;
    private static final int FG=0xffe6e0e9,MUTED=0xffcac4d0,PRIMARY=0xffd0bcff;

    LayoutEditor(MainActivity app,View canvas){
        this.app=app;this.canvas=canvas;root=new FrameLayout(app);root.setBackgroundColor(0xff141218);
        root.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        toolbar=new LinearLayout(app);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(app.dp(6),app.dp(4),app.dp(6),app.dp(4));
        toolbar.setBackground(app.rounded(0xf52b2930,0xff494250,28));toolbar.setElevation(app.dp(12));
        action(toolbar,"显示控件",()->app.visibleControls());
        action(toolbar,"重置布局",()->new AlertDialog.Builder(app).setTitle("重置布局？").setMessage("恢复位置、尺寸与默认分组。")
            .setPositiveButton("重置",(d,w)->{app.put(app.settings,"layout",new JSONObject());app.put(app.settings,"group_face",true);app.put(app.settings,"group_dpad",true);app.saveSettings();closeMenu();}).setNegativeButton("取消",null).show());
        action(toolbar,"保存并返回",()->app.finishEditor());
        FrameLayout.LayoutParams tools=new FrameLayout.LayoutParams(-2,app.dp(48),Gravity.TOP|Gravity.RIGHT);tools.setMargins(app.dp(12),app.dp(10),app.dp(16),0);root.addView(toolbar,tools);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)toolbar.getLayoutParams();p.rightMargin=app.dp(12)+insets.getSystemWindowInsetRight();p.topMargin=app.dp(8)+insets.getSystemWindowInsetTop();toolbar.setLayoutParams(p);
            if(menu!=null)placeMenu();return insets;
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(menu!=null&&(r-l!=or-ol||b-t!=ob-ot))placeMenu();});
    }
    private void action(LinearLayout row,String text,Runnable run){
        TextView button=app.text(text,13,PRIMARY);button.setGravity(Gravity.CENTER);button.setPadding(app.dp(14),0,app.dp(14),0);button.setMinHeight(app.dp(42));button.setClickable(true);button.setFocusable(true);button.setBackground(app.rounded(0x0037313e,0,22));button.setOnClickListener(v->run.run());row.addView(button,new LinearLayout.LayoutParams(-2,-1));
    }
    static boolean stretchable(String id){return contains(FACE,id)||contains(new String[]{"lb","lt","rb","rt"},id);}
    private static boolean contains(String[] list,String id){for(String value:list)if(value.equals(id))return true;return false;}
    private String groupKey(String id){return contains(FACE,id)?"group_face":contains(DPAD,id)?"group_dpad":null;}
    String[] members(String id){String key=groupKey(id);return key!=null&&app.settings.optBoolean(key,true)?(contains(FACE,id)?FACE:DPAD):new String[]{id};}
    boolean selected(String id){return selected!=null&&contains(members(selected),id);}
    void select(String id){selected=id;app.refreshPad();}
    void edit(String id){selected=id;showMenu();}
    boolean back(){if(menu==null)return false;closeMenu();return true;}
    void closeMenu(){if(menu!=null)root.removeView(menu);menu=null;menuScroll=null;}
    void begin(String id){selected=id;closeMenu();gesture=capture(id);app.refreshPad();}
    void drag(float dx,float dy,boolean resize){
        if(gesture==null)return;float unit=app.padUnit();if(unit<=0)return;dx/=unit;dy/=unit;
        LayoutGeometry.Box all=LayoutGeometry.bounds(gesture.boxes);
        LayoutGeometry.Box[] result=resize?resize(gesture,all.w+dx,all.h+dy,!stretchable(selected)):LayoutGeometry.move(gesture.boxes,dx,dy);
        apply(gesture,result);
    }
    void end(){gesture=null;app.saveSettings();}
    private Session capture(String id){
        try{
            String[] ids=members(id);JSONArray layout=app.layoutFor(app.settings);Session session=new Session(ids);
            for(int i=0;i<ids.length;i++)for(int n=0;n<layout.length();n++){
                JSONObject b=layout.getJSONObject(n);if(!ids[i].equals(b.optString("id")))continue;
                session.boxes[i]=new LayoutGeometry.Box((float)b.optDouble("x"),(float)b.optDouble("y"),(float)b.optDouble("width"),(float)b.optDouble("height"));
                session.placements[i]=new JSONObject(app.placement(ids[i]).toString());
            }return session;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private LayoutGeometry.Box[] resize(Session session,float w,float h,boolean uniform){
        LayoutGeometry.Box all=LayoutGeometry.bounds(session.boxes);
        if(!stretchable(session.ids[0])){
            float min=.01f,max=Float.MAX_VALUE;
            for(JSONObject p:session.placements){float scale=(float)p.optDouble("scale",1);min=Math.max(min,.6f/scale);max=Math.min(max,1.6f/scale);}
            float ratio=LayoutGeometry.clamp(Math.min(w/all.w,h/all.h),min,max);w=all.w*ratio;h=all.h*ratio;uniform=true;
        }
        return LayoutGeometry.resize(session.boxes,w,h,uniform);
    }
    private void apply(Session session,LayoutGeometry.Box[] result){
        for(int i=0;i<session.ids.length;i++){
            JSONObject p=app.placement(session.ids[i]),old=session.placements[i];LayoutGeometry.Box before=session.boxes[i],after=result[i];
            app.put(p,"x",after.x);app.put(p,"y",after.y);
            if(stretchable(session.ids[i])){
                app.put(p,"width",old.optDouble("width",1)*after.w/before.w);app.put(p,"height",old.optDouble("height",1)*after.h/before.h);
            }else app.put(p,"scale",LayoutGeometry.clamp((float)old.optDouble("scale",1)*after.w/before.w,.6f,1.6f));
        }
        app.saveSettings();
    }
    private void showMenu(){
        closeMenu();menu=new LinearLayout(app);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(app.dp(16),app.dp(10),app.dp(16),app.dp(10));
        menu.setBackground(app.rounded(0xfa2b2930,0xff665b72,26));menu.setElevation(app.dp(20));
        LinearLayout header=new LinearLayout(app);header.setGravity(Gravity.CENTER_VERTICAL);
        String title=members(selected).length>1?(contains(FACE,selected)?"XABY · 组合":"方向键 · 组合"):selected.toUpperCase();
        TextView heading=app.text(title,16,FG);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));action(header,"完成",()->closeMenu());menu.addView(header,new LinearLayout.LayoutParams(-1,app.dp(42)));
        LinearLayout tabs=new LinearLayout(app);String[] names={"位置","尺寸","显示"};
        for(int i=0;i<3;i++){final int index=i;TextView button=app.text(names[i],13,i==tab?PRIMARY:MUTED);button.setGravity(Gravity.CENTER);button.setBackground(app.rounded(i==tab?0xff4f3b67:0xff37313e,0,18));button.setOnClickListener(v->{tab=index;showMenu();});tabs.addView(button,new LinearLayout.LayoutParams(0,app.dp(36),1));}menu.addView(tabs);
        menuScroll=new ScrollView(app);menuScroll.setFillViewport(false);LinearLayout body=new LinearLayout(app);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,app.dp(6),0,app.dp(8));menuScroll.addView(body);menu.addView(menuScroll,new LinearLayout.LayoutParams(-1,0,1));
        String key=groupKey(selected);if(key!=null){
            Switch group=new Switch(app);group.setText(contains(FACE,selected)?"绑定 XABY 组合":"绑定方向键组合");group.setTextSize(13);group.setTextColor(FG);group.setChecked(app.settings.optBoolean(key,true));group.setMinHeight(app.dp(44));
            group.setOnCheckedChangeListener((b,on)->{app.put(app.settings,key,on);app.saveSettings();showMenu();app.refreshPad();});body.addView(group);
        }
        Session session=capture(selected);LayoutGeometry.Box bounds=LayoutGeometry.bounds(session.boxes);
        if(tab==0){
            app.add(body,app.text("拖动画布上的选中组合调整位置",12,MUTED),8);
            range(body,"水平位置","组合水平位置",bounds.x, bounds.w/2+4,996-bounds.w/2,value->{Session now=capture(selected);apply(now,LayoutGeometry.move(now.boxes,value-LayoutGeometry.bounds(now.boxes).x,0));},false);
            range(body,"垂直位置","组合垂直位置",bounds.y, bounds.h/2+4,446-bounds.h/2,value->{Session now=capture(selected);apply(now,LayoutGeometry.move(now.boxes,0,value-LayoutGeometry.bounds(now.boxes).y));},false);
        }else if(tab==1){
            range(body,"大小","控件大小",100,60,stretchable(selected)?Math.min(992/bounds.w,442/bounds.h)*100:160,value->apply(session,resize(session,bounds.w*value/100,bounds.h*value/100,true)),true);
            if(stretchable(selected)){
                range(body,"宽度","控件宽度",bounds.w,24,992,value->{Session now=capture(selected);apply(now,resize(now,value,LayoutGeometry.bounds(now.boxes).h,false));},false);
                range(body,"高度","控件高度",bounds.h,24,442,value->{Session now=capture(selected);apply(now,resize(now,LayoutGeometry.bounds(now.boxes).w,value,false));},false);
                app.add(body,app.text("连续调节，尺寸自动限制在屏幕内",12,MUTED),4);
            }
        }else{
            Switch visible=new Switch(app);visible.setText("显示选中控件");visible.setTextSize(13);visible.setTextColor(FG);visible.setChecked(!app.placement(selected).optBoolean("hidden"));visible.setMinHeight(app.dp(48));
            visible.setOnCheckedChangeListener((b,on)->{for(String id:members(selected))app.put(app.placement(id),"hidden",!on);app.saveSettings();});body.addView(visible);
            Button reset=app.button("恢复选中控件默认布局");reset.setOnClickListener(v->{for(String id:members(selected))app.settings.optJSONObject("layout").remove(id);app.saveSettings();showMenu();});app.add(body,reset,10);
        }
        root.addView(menu);placeMenu();menu.setAlpha(0);menu.setTranslationY(app.dp(12));menu.animate().alpha(1).translationY(0).setDuration(180).start();
    }
    private interface Change{void set(float value);}
    private void range(LinearLayout body,String name,String description,float initial,float min,float max,Change change,boolean percent){
        max=Math.max(min+.01f,max);final float low=min,high=max;
        TextView title=app.text(name+" · "+Math.round(initial)+(percent?"%":""),12,FG);app.add(body,title,8);
        SeekBar slider=new SeekBar(app);slider.setMax(10000);slider.setProgress(Math.round((initial-min)/(max-min)*10000));slider.setContentDescription(description);slider.setMinimumHeight(app.dp(38));body.addView(slider,new LinearLayout.LayoutParams(-1,app.dp(38)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){root.post(()->{if(menu!=null)showMenu();});}public void onProgressChanged(SeekBar v,int n,boolean user){if(user){float value=low+(high-low)*n/10000;change.set(value);title.setText(name+" · "+Math.round(value)+(percent?"%":""));}}});
    }
    private void placeMenu(){
        if(menu==null)return;int width=Math.min(app.dp(300),Math.max(app.dp(180),root.getWidth()-app.dp(24))),height=Math.min(app.dp(310),Math.max(app.dp(180),root.getHeight()-app.dp(78)));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height,Gravity.BOTTOM|Gravity.RIGHT);p.bottomMargin=app.dp(12)+canvas.getPaddingBottom();p.rightMargin=app.dp(12)+canvas.getPaddingRight();
        // Prefer the side opposite the selection, preserving a full-size visible target.
        try{if(capture(selected).boxes[0].x>500){p.gravity=Gravity.BOTTOM|Gravity.LEFT;p.leftMargin=app.dp(12)+canvas.getPaddingLeft();p.rightMargin=0;}}catch(Exception ignored){}
        menu.setLayoutParams(p);
    }
    private static final class Session{
        final String[] ids;final LayoutGeometry.Box[] boxes;final JSONObject[] placements;
        Session(String[] ids){this.ids=ids;boxes=new LayoutGeometry.Box[ids.length];placements=new JSONObject[ids.length];}
    }
}
