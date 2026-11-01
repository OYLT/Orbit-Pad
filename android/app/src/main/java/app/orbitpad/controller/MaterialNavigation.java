package app.orbitpad.controller;

import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fixed Material navigation bar / rail. It reserves its own space in the layout. */
final class MaterialNavigation extends LinearLayout {
    private final MainActivity app;
    private final GlassDock.Navigation navigation;
    private int selected;
    private boolean vertical;
    MaterialNavigation(MainActivity app,int selected,GlassDock.Navigation navigation){
        super(app);this.app=app;this.selected=selected;this.navigation=navigation;setGravity(Gravity.CENTER);refreshTheme();
    }
    void setVertical(boolean value){if(vertical!=value){vertical=value;refreshTheme();}}
    void setSelectedIndex(int index){if(selected!=index){selected=index;refreshTheme();}}
    void refreshTheme(){
        setOrientation(vertical?VERTICAL:HORIZONTAL);setBackgroundColor(app.theme.surface);removeAllViews();
        String[] names={"主页","连接","主题","设置"};
        for(int i=0;i<names.length;i++){
            final int index=i;boolean active=selected==i;
            LinearLayout item=new LinearLayout(app);item.setOrientation(VERTICAL);item.setGravity(Gravity.CENTER);item.setMinimumHeight(app.dp(64));
            item.setContentDescription("导航"+names[i]);item.setSelected(active);item.setClickable(true);item.setFocusable(true);
            item.setOnClickListener(v->{setSelectedIndex(index);navigation.select(index);});
            View icon=new View(app){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas c){
                    p.setColor(active?app.theme.secondaryContainer:app.theme.surface);c.drawRoundRect(0,0,getWidth(),getHeight(),app.dp(16),app.dp(16),p);
                    int save=c.save();c.translate((getWidth()-app.dp(24))/2f,(getHeight()-app.dp(24))/2f);c.scale(app.dp(24)/24f,app.dp(24)/24f);
                    p.setColor(active?app.theme.onSurface:app.theme.muted);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);
                    if(index==0){c.drawRoundRect(3,5,21,20,3,3,p);c.drawLine(7,10,11,10,p);c.drawLine(9,8,9,12,p);c.drawCircle(16,10,1,p);c.drawLine(7,16,17,16,p);}
                    else if(index==1){for(int[] q:new int[][]{{3,3},{15,3},{3,15}})c.drawRoundRect(q[0],q[1],q[0]+6,q[1]+6,1,1,p);c.drawLine(15,15,21,21,p);c.drawLine(21,15,15,21,p);}
                    else if(index==2){c.drawCircle(12,12,9,p);c.drawCircle(8,8,1,p);c.drawCircle(15,7,1,p);c.drawCircle(17,13,1,p);c.drawLine(9,17,13,17,p);}
                    else{c.drawCircle(12,12,6.5f,p);c.drawCircle(12,12,2.5f,p);for(int a=0;a<360;a+=45){c.save();c.rotate(a,12,12);c.drawLine(12,2,12,5,p);c.restore();}}
                    p.setStyle(Paint.Style.FILL);c.restoreToCount(save);
                }
            };icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(icon,new LayoutParams(app.dp(64),app.dp(32)));
            TextView label=app.text(names[i],12,active?app.theme.onSurface:app.theme.muted);label.setGravity(Gravity.CENTER);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(label);
            item.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((app.theme.primary&0xffffff)|0x22000000),null,null));
            addView(item,new LayoutParams(vertical?-1:0,vertical?app.dp(80):-1,vertical?0:1));
        }
    }
}
