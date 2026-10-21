package app.orbitpad.controller;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Frosted capsule + clear liquid lens. Gesture coordinates stay in screen space. */
final class GlassDock extends LinearLayout {
    interface Navigation {void select(int index);}
    private static final int ACTIVE=0xffa5f0cf,INACTIVE=0xffeeedf2;
    private final MainActivity app;
    private final GlassBackdrop backdrop;
    private final Navigation navigation;
    private final Tab[] tabs=new Tab[4];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF surface=new RectF(),slider=new RectF();
    private final Path clip=new Path(),lensPath=new Path();
    private final Matrix lensMatrix=new Matrix();
    private Shader tint,rim,lensTint,lensRim;
    private int selected,scrollDistance,pointerId=-1,pressedIndex;
    private final int touchSlop;
    private boolean ticking,compact,touchActive,dragging,held,verticalCancelled,positionReady;
    private long previousFrame,lastMoveTime;
    private float downRawX,downRawY,originX,grabOffset,lastRawX,fingerVelocity;
    private VelocityTracker tracker;
    private final Spring position=new Spring(400,29),activation=new Spring(210,18),
        deformation=new Spring(160,16),dockShift=new Spring(160,18),jelly=new Spring(140,10);
    private final int[] screenLocation=new int[2];
    private final Runnable longPress=()->{
        if(!touchActive||dragging||verticalCancelled)return;
        held=true;activation.target=1;jelly.velocity=5.5f;
        longPressFeedback();animateMaterial();
    };
    private final Runnable previewRelease=()->{position.target=tabs[selected].getLeft();activation.target=0;held=false;jelly.velocity=-3.5f;animateMaterial();};
    private final Runnable frame=new Runnable(){public void run(){
        long now=SystemClock.uptimeMillis();float dt=Math.min(.048f,Math.max(.001f,(now-previousFrame)/1000f));previousFrame=now;
        int steps=Math.max(1,(int)Math.ceil(dt/.008));float h=dt/steps;
        for(int i=0;i<steps;i++){
            position.step(h);activation.step(h);
            deformation.target=clamp((dragging?position.velocity*.45f+fingerVelocity*.55f:position.velocity)/app.dp(1000),-1,1);deformation.step(h);
            dockShift.step(h);jelly.step(h);
        }
        boolean moving=!(position.settled(.12f,.8f)&&activation.settled(.001f,.008f)&&deformation.settled(.001f,.008f)&&dockShift.settled(.08f,.4f)&&jelly.settled(.001f,.008f));
        if(!moving){position.snap();activation.snap();deformation.snap();dockShift.snap();jelly.snap();}
        applyMotion();ticking=moving;if(moving)postOnAnimation(this);
    }};
    GlassDock(MainActivity app,GlassBackdrop backdrop,int selected,Navigation navigation){
        super(app);this.app=app;this.backdrop=backdrop;this.navigation=navigation;this.selected=selected;
        touchSlop=ViewConfiguration.get(app).getScaledTouchSlop();
        setGravity(Gravity.CENTER);setPadding(app.dp(6),app.dp(6),app.dp(6),app.dp(6));
        setWillNotDraw(false);setClickable(true);setClipChildren(false);setClipToPadding(false);setElevation(app.dp(10));
        setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline outline){outline.setRoundRect(0,0,v.getWidth(),v.getHeight(),v.getHeight()/2f);}});
        String[] labels={"主页","连接","主题","设置"};
        for(int i=0;i<4;i++){
            final int index=i;Tab tab=new Tab(labels[i],i);tabs[i]=tab;
            tab.setOnClickListener(v->commitSelection(index));
            tab.setOnLongClickListener(v->{position.target=tabs[index].getLeft();activation.target=1;jelly.velocity=5.5f;held=true;animateMaterial();removeCallbacks(previewRelease);postDelayed(previewRelease,450);return true;});
            addView(tab,new LinearLayout.LayoutParams(0,-1,1));
        }
        setSelectedIndex(selected);
    }
    int preferredHeight(){return app.dp(Math.max(72,48+16*getResources().getConfiguration().fontScale));}
    void setSelectedIndex(int index){
        selected=index;setCompact(false);scrollDistance=0;
        for(int i=0;i<4;i++){
            boolean active=i==index;tabs[i].setSelected(active);
            if(Build.VERSION.SDK_INT>=30)tabs[i].setStateDescription(active?"已选中":null);
        }
        if(getWidth()>0){position.target=tabs[index].getLeft();if(!positionReady){position.snap();positionReady=true;}animateMaterial();}
        updateColors();
    }
    private void longPressFeedback(){if(app.settings!=null&&app.settings.optBoolean("haptics",true))performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}
    private void commitSelection(int index){
        if(touchActive)endGesture(false);
        setSelectedIndex(index);navigation.select(index);
    }
    void onScroll(int delta,int scrollY){
        if(touchActive)return;
        if(scrollY<=app.dp(12)){scrollDistance=0;setCompact(false);return;}
        if((delta>0&&scrollDistance<0)||(delta<0&&scrollDistance>0))scrollDistance=0;
        scrollDistance+=delta;
        if(scrollDistance>app.dp(56))setCompact(true);else if(scrollDistance< -app.dp(24))setCompact(false);
    }
    private void setCompact(boolean value){
        if(compact==value||getResources().getConfiguration().fontScale>1.3f)return;
        compact=value;for(Tab tab:tabs)if(tab!=null)tab.label.setVisibility(value?GONE:VISIBLE);
        if(getLayoutParams()!=null){getLayoutParams().height=value?app.dp(60):preferredHeight();requestLayout();}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return event.getActionMasked()==MotionEvent.ACTION_DOWN||touchActive;}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(!isEnabled()||getWidth()==0)return false;
            removeCallbacks(previewRelease);removeCallbacks(longPress);recycleTracker();
            pointerId=event.getPointerId(0);touchActive=true;dragging=held=verticalCancelled=false;
            getLocationOnScreen(screenLocation);originX=screenLocation[0]-getTranslationX();
            downRawX=lastRawX=event.getRawX();downRawY=event.getRawY();lastMoveTime=event.getEventTime();fingerVelocity=0;
            pressedIndex=nearest(event.getRawX()-originX);grabOffset=event.getRawX()-originX-tabs[pressedIndex].getLeft();
            position.target=tabs[pressedIndex].getLeft();activation.target=.28f;
            tracker=VelocityTracker.obtain();track(event);getParent().requestDisallowInterceptTouchEvent(true);
            postDelayed(longPress,ViewConfiguration.getLongPressTimeout());animateMaterial();return true;
        }
        if(!touchActive)return false;
        track(event);
        if(action==MotionEvent.ACTION_CANCEL){endGesture(false);return true;}
        if(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointerId){endGesture(false);return true;}
        int pointer=event.findPointerIndex(pointerId);if(pointer<0){endGesture(false);return true;}
        float rawX=event.getRawX()+event.getX(pointer)-event.getX(0),rawY=event.getRawY()+event.getY(pointer)-event.getY(0);
        float dx=rawX-downRawX,dy=rawY-downRawY;
        if(action==MotionEvent.ACTION_MOVE){
            if(!dragging&&Math.abs(dy)>touchSlop*1.5f&&Math.abs(dy)>Math.abs(dx)*1.2f){verticalCancelled=true;removeCallbacks(longPress);activation.target=0;position.target=tabs[selected].getLeft();animateMaterial();return true;}
            if(verticalCancelled)return true;
            if(!dragging&&Math.abs(dx)>touchSlop&&Math.abs(dx)>Math.abs(dy)){
                dragging=true;held=true;removeCallbacks(longPress);activation.target=1;activation.velocity=Math.max(activation.velocity,3.5f);jelly.velocity=2.5f;
            }
            long now=event.getEventTime();float seconds=Math.max(.008f,(now-lastMoveTime)/1000f);
            fingerVelocity=fingerVelocity*.65f+(rawX-lastRawX)/seconds*.35f;lastRawX=rawX;lastMoveTime=now;
            if(dragging){
                float desired=rawX-originX-dockShift.value-grabOffset;
                position.target=rubber(desired,tabs[0].getLeft(),tabs[3].getLeft(),app.dp(10));
                dockShift.target=(float)Math.tanh(fingerVelocity/app.dp(900))*app.dp(7)+(float)Math.tanh(dx/app.dp(160))*app.dp(3);
            }
            animateMaterial();return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(verticalCancelled){endGesture(false);return true;}
            int index=pressedIndex;
            if(dragging){
                tracker.computeCurrentVelocity(1000,app.dp(4500));float vx=tracker.getXVelocity(pointerId);
                float center=rawX-originX-dockShift.value-grabOffset+tabs[0].getWidth()/2f;
                center+=clamp(vx*.025f,-tabs[0].getWidth()*.18f,tabs[0].getWidth()*.18f);
                index=nearest(center);
            }
            endGesture(true);tabs[index].performClick();return true;
        }
        return true;
    }
    private void track(MotionEvent event){
        if(tracker==null)return;
        // Local event coordinates change while the whole Dock is translated. Track raw positions.
        MotionEvent screen=MotionEvent.obtain(event);screen.offsetLocation(event.getRawX()-event.getX(),event.getRawY()-event.getY());tracker.addMovement(screen);screen.recycle();
    }
    private int nearest(float center){int closest=0;float distance=Float.MAX_VALUE;for(int i=0;i<4;i++){float d=Math.abs(center-(tabs[i].getLeft()+tabs[i].getWidth()/2f));if(d<distance){distance=d;closest=i;}}return closest;}
    private void endGesture(boolean committed){
        boolean hadGesture=touchActive;touchActive=dragging=held=verticalCancelled=false;pointerId=-1;
        removeCallbacks(longPress);removeCallbacks(previewRelease);recycleTracker();
        activation.target=0;dockShift.target=0;position.target=tabs[selected].getLeft();
        if(hadGesture)jelly.velocity=committed?-3.5f:0;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);animateMaterial();
    }
    private void recycleTracker(){if(tracker!=null){tracker.recycle();tracker=null;}}
    private void animateMaterial(){
        if(!ValueAnimator.areAnimatorsEnabled()){
            position.snap();activation.snap();deformation.target=0;deformation.snap();dockShift.target=0;dockShift.snap();jelly.target=0;jelly.snap();applyMotion();return;
        }
        if(!ticking&&isAttachedToWindow()&&getVisibility()==VISIBLE){ticking=true;previousFrame=SystemClock.uptimeMillis();postOnAnimation(frame);}
        invalidate();
    }
    private void applyMotion(){setTranslationX(dockShift.value);setTranslationY(-Math.abs(dockShift.value)*.075f);updateColors();invalidate();}
    private void updateColors(){
        if(tabs[0]==null)return;
        float center=!positionReady?tabs[selected].getLeft()+tabs[selected].getWidth()/2f:position.value+tabs[selected].getWidth()/2f;
        for(Tab tab:tabs){
            float weight=tab.getWidth()==0?(tab==tabs[selected]?1:0):clamp(1-Math.abs(tab.getLeft()+tab.getWidth()/2f-center)/(tab.getWidth()*.8f),0,1);
            int color=blend(INACTIVE,ACTIVE,weight);
            if(tab.icon.color!=color){tab.icon.color=color;tab.icon.invalidate();tab.label.setTextColor(color);}
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        if(changed&&touchActive)endGesture(false);
        if(!touchActive)position.target=tabs[selected].getLeft();
        if(!positionReady||changed){position.snap();positionReady=true;deformation.value=deformation.velocity=0;}invalidateOutline();updateColors();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);surface.set(.75f,.75f,w-.75f,h-.75f);clip.reset();clip.addRoundRect(surface,h/2f,h/2f,Path.Direction.CW);
        tint=new LinearGradient(0,0,w,h,new int[]{0x55ffffff,0x20313b48,0x42e7f0ef},new float[]{0,.52f,1},Shader.TileMode.CLAMP);
        rim=new LinearGradient(0,0,w,h,new int[]{0xcaffffff,0x32edf7ff,0x9affffff},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
        lensTint=new LinearGradient(0,0,0,h,new int[]{0x38ffffff,0x10d9e8ee,0x36ffffff},new float[]{0,.55f,1},Shader.TileMode.CLAMP);
        lensRim=new SweepGradient(0,0,new int[]{0xffffffff,0xffb0ddff,0xffffdebd,0xffffffff,0xffb0f8e5,0xffd9c3ff,0xffffffff},null);
    }
    private void measureLens(){
        float a=clamp(activation.value,0,1.18f),speed=Math.abs(deformation.value),g=clamp(jelly.value,-.3f,.3f);
        float baseWidth=tabs[selected].getWidth()-app.dp(4),baseHeight=getHeight()-getPaddingTop()-getPaddingBottom();
        float width=baseWidth*(1+a*.50f+speed*.38f+g*.11f),height=baseHeight*(1+a*.36f-speed*.10f-g*.12f);
        float centerX=position.value+tabs[selected].getWidth()/2f+deformation.value*app.dp(2),centerY=getHeight()/2f;
        centerX=clamp(centerX,width/2-app.dp(8),getWidth()-width/2+app.dp(8));
        slider.set(centerX-width/2,centerY-height/2,centerX+width/2,centerY+height/2);
        lensPath.reset();lensPath.addRoundRect(slider,Math.min(width,height)/2,Math.min(width,height)/2,Path.Direction.CW);
        lensMatrix.setSkew(deformation.value*-.10f,0,centerX,centerY);lensPath.transform(lensMatrix);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(getWidth()==0||!positionReady)return;
        float a=clamp(activation.value,0,1);
        int save=canvas.save();canvas.clipPath(clip);
        boolean sampled=backdrop.drawGlass(canvas,this,a);
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(sampled?0:0xee353b42);if(!sampled)canvas.drawPath(clip,paint);
        paint.setShader(tint);paint.setAlpha(255);canvas.drawPath(clip,paint);canvas.restoreToCount(save);
        paint.setShader(rim);paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(app.dp(.8f));canvas.drawPath(clip,paint);
        measureLens();
        // The raised lens is allowed outside the capsule, but remains above the system handle.
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.argb((int)(20+a*18),0,0,0));paint.setStrokeWidth(app.dp(3));canvas.drawPath(lensPath,paint);
        save=canvas.save();canvas.clipPath(lensPath);
        boolean lensSampled=backdrop.drawLens(canvas,this,slider,a);
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(lensSampled?0x10ffffff:0x40535d65);canvas.drawPath(lensPath,paint);
        paint.setShader(lensTint);paint.setAlpha(255);canvas.drawPath(lensPath,paint);canvas.restoreToCount(save);
        paint.setShader(rim);paint.setAlpha((int)(145+a*90));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(app.dp(1.15f));canvas.drawPath(lensPath,paint);
        if(backdrop.quality()==GlassQualityPolicy.FULL&&GlassQualityPolicy.refractionLevel(app)>0){
            lensMatrix.setScale(slider.width()/2,slider.height()/2);lensMatrix.postTranslate(slider.centerX(),slider.centerY());lensRim.setLocalMatrix(lensMatrix);
            paint.setShader(lensRim);paint.setAlpha((int)(18+a*88+Math.abs(deformation.value)*22));paint.setStrokeWidth(app.dp(1.6f));canvas.drawPath(lensPath,paint);
        }
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    }
    @Override protected boolean drawChild(Canvas canvas,View child,long drawingTime){
        float a=clamp(activation.value,0,1),center=position.value+tabs[selected].getWidth()/2f;
        float influence=clamp(1-Math.abs(child.getLeft()+child.getWidth()/2f-center)/(child.getWidth()*.8f),0,1);
        int save=canvas.save();
        canvas.scale(1+a*influence*.10f,1+a*influence*.13f,child.getLeft()+child.getWidth()/2f,getHeight()/2f);
        canvas.translate(deformation.value*app.dp(1.5f)*influence,0);
        try{return super.drawChild(canvas,child,drawingTime);}finally{canvas.restoreToCount(save);}
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus&&touchActive)endGesture(false);}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility!=VISIBLE&&tabs!=null&&tabs[0]!=null)resetMotion();}
    @Override protected void onDetachedFromWindow(){resetMotion();super.onDetachedFromWindow();}
    private void resetMotion(){
        removeCallbacks(frame);removeCallbacks(longPress);removeCallbacks(previewRelease);recycleTracker();ticking=touchActive=dragging=held=verticalCancelled=false;pointerId=-1;
        activation.target=deformation.target=dockShift.target=jelly.target=0;activation.snap();deformation.snap();dockShift.snap();jelly.snap();
        if(tabs[0]!=null){position.target=tabs[selected].getLeft();position.snap();}setTranslationX(0);setTranslationY(0);
    }
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private static float rubber(float value,float min,float max,float limit){if(value<min)return min-limit*(1-(float)Math.exp((value-min)/limit));if(value>max)return max+limit*(1-(float)Math.exp((max-value)/limit));return value;}
    private static int blend(int from,int to,float amount){return Color.rgb((int)(Color.red(from)+(Color.red(to)-Color.red(from))*amount),(int)(Color.green(from)+(Color.green(to)-Color.green(from))*amount),(int)(Color.blue(from)+(Color.blue(to)-Color.blue(from))*amount));}
    private static final class Spring {
        float value,target,velocity;final float stiffness,damping;
        Spring(float stiffness,float damping){this.stiffness=stiffness;this.damping=damping;}
        void step(float dt){velocity+=((target-value)*stiffness-velocity*damping)*dt;value+=velocity*dt;}
        void snap(){value=target;velocity=0;}
        boolean settled(float distance,float speed){return Math.abs(value-target)<distance&&Math.abs(velocity)<speed;}
    }
    private final class Tab extends LinearLayout {
        final Icon icon;final TextView label;
        Tab(String name,int index){
            super(app);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setClickable(true);setFocusable(true);setContentDescription("导航"+name);setMinimumHeight(app.dp(48));
            icon=new Icon(index);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(icon,new LayoutParams(app.dp(23),app.dp(23)));
            label=app.text(name,11,INACTIVE);label.setGravity(Gravity.CENTER);label.setIncludeFontPadding(false);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(label);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo node){super.onInitializeAccessibilityNodeInfo(node);node.setClassName("android.widget.Button");node.setSelected(isSelected());}
    }
    private final class Icon extends View {
        final int type;int color=0xffc8c1d1;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(int type){super(app);this.type=type;}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);
            if(type==0){c.drawRoundRect(3,5,21,20,3,3,p);c.drawLine(7,10,11,10,p);c.drawLine(9,8,9,12,p);c.drawCircle(16,10,1,p);c.drawLine(7,16,17,16,p);}
            else if(type==1){for(int[] q:new int[][]{{3,3},{15,3},{3,15}})c.drawRoundRect(q[0],q[1],q[0]+6,q[1]+6,1,1,p);c.drawLine(15,15,21,21,p);c.drawLine(21,15,15,21,p);}
            else if(type==2){c.drawCircle(12,12,9,p);c.drawCircle(8,8,1,p);c.drawCircle(15,7,1,p);c.drawCircle(17,13,1,p);c.drawLine(9,17,13,17,p);}
            else{c.drawCircle(12,12,6.5f,p);c.drawCircle(12,12,2.5f,p);for(int angle=0;angle<360;angle+=45){c.save();c.rotate(angle,12,12);c.drawLine(12,2,12,5,p);c.restore();}}c.restore();
        }
    }
}
