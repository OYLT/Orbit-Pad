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
import android.graphics.RadialGradient;
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

/** Native adaptation of Kyant LiquidBottomTabs: frosted panel + combined-layer lens. */
final class GlassDock extends LinearLayout {
    interface Navigation {void select(int index);}
    private static final int ACTIVE=0xff65b5ff,INACTIVE=0xffeeedf2;
    private final MainActivity app;
    private final GlassBackdrop backdrop;
    private final Navigation navigation;
    private final Tab[] tabs=new Tab[4];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF surface=new RectF(),slider=new RectF();
    private final Path clip=new Path(),lensPath=new Path();
    private final Matrix lensMatrix=new Matrix();
    private Shader tint,rim,spot;
    private final Matrix spotMatrix=new Matrix();
    private KyantGlass.Highlight panelHighlight,lensHighlight;
    private ContentLens contentLens;
    private boolean contentFailed;
    private int selected,scrollDistance,pointerId=-1,pressedIndex;
    private final int touchSlop;
    private boolean ticking,compact,touchActive,dragging,held,crossCancelled,positionReady,vertical;
    private long previousFrame,lastMoveTime;
    private float downMain,downCross,originMain,grabOffset,lastMain,fingerVelocity;
    private VelocityTracker tracker;
    private final Spring position=new Spring(400,34),activation=new Spring(210,26),
        deformation=new Spring(160,23),dockShift=new Spring(160,18),jelly=new Spring(140,20);
    private final int[] screenLocation=new int[2];
    private final Runnable longPress=()->{
        if(!touchActive||dragging||crossCancelled)return;
        held=true;activation.target=1;jelly.velocity=2.2f;
        longPressFeedback();animateMaterial();
    };
    private final Runnable previewRelease=()->{position.target=tabStart(tabs[selected]);activation.target=0;held=false;jelly.velocity=-1.25f;animateMaterial();};
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
        setGravity(Gravity.CENTER);setPadding(app.dp(4),app.dp(4),app.dp(4),app.dp(4));
        setWillNotDraw(false);setClickable(true);setClipChildren(false);setClipToPadding(false);setElevation(app.dp(10));
        setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline outline){outline.setRoundRect(0,0,v.getWidth(),v.getHeight(),Math.min(v.getWidth(),v.getHeight())/2f);}});
        String[] labels={"主页","连接","主题","设置"};
        for(int i=0;i<4;i++){
            final int index=i;Tab tab=new Tab(labels[i],i);tabs[i]=tab;
            tab.setOnClickListener(v->commitSelection(index));
            tab.setOnLongClickListener(v->{position.target=tabStart(tabs[index]);activation.target=1;jelly.velocity=2.2f;held=true;animateMaterial();removeCallbacks(previewRelease);postDelayed(previewRelease,450);return true;});
            addView(tab,new LinearLayout.LayoutParams(0,-1,1));
        }
        setSelectedIndex(selected);
    }
    private boolean material(){return app.theme.material&&!backdrop.preview;}
    void refreshTheme(){updateColors();invalidate();}
    int preferredHeight(){return app.dp(Math.max(72,48+16*getResources().getConfiguration().fontScale));}
    int currentHeight(){return compact?app.dp(60):preferredHeight();}
    void setVertical(boolean value){
        if(vertical==value)return;
        resetMotion();vertical=value;compact=false;scrollDistance=0;positionReady=false;
        setOrientation(value?VERTICAL:HORIZONTAL);
        for(Tab tab:tabs){tab.label.setVisibility(VISIBLE);tab.setLayoutParams(new LinearLayout.LayoutParams(value?-1:0,value?0:-1,1));}
        requestLayout();invalidate();
    }
    private float tabStart(View tab){return vertical?tab.getTop():tab.getLeft();}
    private float tabSize(View tab){return vertical?tab.getHeight():tab.getWidth();}
    private float main(float x,float y){return vertical?y:x;}
    private float cross(float x,float y){return vertical?x:y;}
    void setSelectedIndex(int index){
        selected=index;setCompact(false);scrollDistance=0;
        for(int i=0;i<4;i++){
            boolean active=i==index;tabs[i].setSelected(active);
            if(Build.VERSION.SDK_INT>=30)tabs[i].setStateDescription(active?"已选中":null);
        }
        if(getWidth()>0){position.target=tabStart(tabs[index]);if(!positionReady){position.snap();positionReady=true;}animateMaterial();}
        updateColors();
    }
    private void longPressFeedback(){if(app.settings!=null&&app.settings.optBoolean("haptics",true))performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}
    private void commitSelection(int index){
        if(touchActive)endGesture(false);
        setSelectedIndex(index);navigation.select(index);
    }
    void onScroll(int delta,int scrollY){
        if(material()){setCompact(false);return;}
        if(touchActive||vertical)return;
        if(scrollY<=app.dp(12)){scrollDistance=0;setCompact(false);return;}
        if((delta>0&&scrollDistance<0)||(delta<0&&scrollDistance>0))scrollDistance=0;
        scrollDistance+=delta;
        if(scrollDistance>app.dp(56))setCompact(true);else if(scrollDistance< -app.dp(24))setCompact(false);
    }
    private void setCompact(boolean value){
        if(vertical||compact==value||getResources().getConfiguration().fontScale>1.3f)return;
        compact=value;for(Tab tab:tabs)if(tab!=null)tab.label.setVisibility(value?GONE:VISIBLE);
        if(getLayoutParams()!=null){getLayoutParams().height=value?app.dp(60):preferredHeight();requestLayout();}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return event.getActionMasked()==MotionEvent.ACTION_DOWN||touchActive;}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(!isEnabled()||getWidth()==0)return false;
            removeCallbacks(previewRelease);removeCallbacks(longPress);recycleTracker();
            pointerId=event.getPointerId(0);touchActive=true;dragging=held=crossCancelled=false;
            getLocationOnScreen(screenLocation);originMain=main(screenLocation[0]-getTranslationX(),screenLocation[1]-getTranslationY());
            downMain=lastMain=main(event.getRawX(),event.getRawY());downCross=cross(event.getRawX(),event.getRawY());lastMoveTime=event.getEventTime();fingerVelocity=0;
            pressedIndex=nearest(downMain-originMain);grabOffset=downMain-originMain-tabStart(tabs[pressedIndex]);
            position.target=tabStart(tabs[pressedIndex]);activation.target=.28f;
            tracker=VelocityTracker.obtain();track(event);getParent().requestDisallowInterceptTouchEvent(true);
            postDelayed(longPress,ViewConfiguration.getLongPressTimeout());animateMaterial();return true;
        }
        if(!touchActive)return false;
        track(event);
        if(action==MotionEvent.ACTION_CANCEL){endGesture(false);return true;}
        if(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointerId){endGesture(false);return true;}
        int pointer=event.findPointerIndex(pointerId);if(pointer<0){endGesture(false);return true;}
        float rawX=event.getRawX()+event.getX(pointer)-event.getX(0),rawY=event.getRawY()+event.getY(pointer)-event.getY(0);
        float rawMain=main(rawX,rawY),delta=rawMain-downMain,crossDelta=cross(rawX,rawY)-downCross;
        if(action==MotionEvent.ACTION_MOVE){
            if(!dragging&&Math.abs(crossDelta)>touchSlop*1.5f&&Math.abs(crossDelta)>Math.abs(delta)*1.2f){crossCancelled=true;removeCallbacks(longPress);activation.target=0;position.target=tabStart(tabs[selected]);animateMaterial();return true;}
            if(crossCancelled)return true;
            if(!dragging&&Math.abs(delta)>touchSlop&&Math.abs(delta)>Math.abs(crossDelta)){
                dragging=true;held=true;removeCallbacks(longPress);activation.target=1;activation.velocity=Math.max(activation.velocity,1.5f);jelly.velocity=1.1f;
            }
            long now=event.getEventTime();float seconds=Math.max(.008f,(now-lastMoveTime)/1000f);
            fingerVelocity=fingerVelocity*.65f+(rawMain-lastMain)/seconds*.35f;lastMain=rawMain;lastMoveTime=now;
            if(dragging){
                float desired=rawMain-originMain-dockShift.value-grabOffset;
                position.target=rubber(desired,tabStart(tabs[0]),tabStart(tabs[3]),app.dp(10));
                dockShift.target=(float)Math.tanh(fingerVelocity/app.dp(900))*app.dp(7)+(float)Math.tanh(delta/app.dp(160))*app.dp(3);
            }
            animateMaterial();return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(crossCancelled){endGesture(false);return true;}
            int index=pressedIndex;
            if(dragging){
                tracker.computeCurrentVelocity(1000,app.dp(4500));float velocity=vertical?tracker.getYVelocity(pointerId):tracker.getXVelocity(pointerId);
                float center=rawMain-originMain-dockShift.value-grabOffset+tabSize(tabs[0])/2f;
                center+=clamp(velocity*.025f,-tabSize(tabs[0])*.18f,tabSize(tabs[0])*.18f);
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
    private int nearest(float center){int closest=0;float distance=Float.MAX_VALUE;for(int i=0;i<4;i++){float d=Math.abs(center-(tabStart(tabs[i])+tabSize(tabs[i])/2f));if(d<distance){distance=d;closest=i;}}return closest;}
    private void endGesture(boolean committed){
        boolean hadGesture=touchActive;touchActive=dragging=held=crossCancelled=false;pointerId=-1;
        removeCallbacks(longPress);removeCallbacks(previewRelease);recycleTracker();
        activation.target=0;dockShift.target=0;position.target=tabStart(tabs[selected]);
        if(hadGesture)jelly.velocity=committed?-1.25f:0;
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
    private void applyMotion(){if(material()){setTranslationX(0);setTranslationY(0);updateColors();invalidate();return;}setTranslationX(vertical?-Math.abs(dockShift.value)*.075f:dockShift.value);setTranslationY(vertical?dockShift.value:-Math.abs(dockShift.value)*.075f);updateColors();invalidate();}
    private void updateColors(){
        if(tabs[0]==null)return;
        float center=(!positionReady?tabStart(tabs[selected]):position.value)+tabSize(tabs[selected])/2f;
        for(Tab tab:tabs){
            float weight=tabSize(tab)==0?(tab==tabs[selected]?1:0):clamp(1-Math.abs(tabStart(tab)+tabSize(tab)/2f-center)/(tabSize(tab)*.8f),0,1);
            int color=blend(material()||!app.theme.dark?app.theme.muted:INACTIVE,material()||!app.theme.dark?app.theme.primary:ACTIVE,weight);
            if(tab.icon.color!=color){tab.icon.color=color;tab.icon.invalidate();tab.label.setTextColor(color);}
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        if(changed&&touchActive)endGesture(false);
        if(!touchActive)position.target=tabStart(tabs[selected]);
        if(!positionReady||changed){position.snap();positionReady=true;deformation.value=deformation.velocity=0;}invalidateOutline();updateColors();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);surface.set(.75f,.75f,w-.75f,h-.75f);clip.reset();clip.addRoundRect(surface,Math.min(w,h)/2f,Math.min(w,h)/2f,Path.Direction.CW);
        tint=new LinearGradient(0,0,0,h,new int[]{0x24ffffff,0x09ffffff,0x18ffffff},new float[]{0,.56f,1},Shader.TileMode.CLAMP);
        rim=new LinearGradient(0,0,w,h,new int[]{0x80ffffff,0x10ffffff,0x65ffffff},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
        spot=new RadialGradient(0,0,Math.min(w,h)*1.5f,new int[]{0x38ffffff,0x00ffffff},null,Shader.TileMode.CLAMP);
    }
    private void measureLens(){
        float a=material()?0:clamp(activation.value,0,1.18f),speed=material()?0:Math.abs(deformation.value),g=material()?0:clamp(jelly.value,-.18f,.18f);
        float baseMain=tabSize(tabs[selected])-app.dp(4),baseCross=vertical?getWidth()-getPaddingLeft()-getPaddingRight():getHeight()-getPaddingTop()-getPaddingBottom();
        float length=baseMain*(1+a*.20f+speed*.18f+g*.055f),thickness=baseCross*(1+a*.18f-speed*.05f-g*.06f);
        float center=position.value+tabSize(tabs[selected])/2f+deformation.value*app.dp(1);
        center=clamp(center,length/2-app.dp(8),(vertical?getHeight():getWidth())-length/2+app.dp(8));
        float width=vertical?thickness:length,height=vertical?length:thickness;
        float centerX=vertical?getWidth()/2f:center,centerY=vertical?center:getHeight()/2f;
        slider.set(centerX-width/2,centerY-height/2,centerX+width/2,centerY+height/2);
        lensPath.reset();lensPath.addRoundRect(slider,Math.min(width,height)/2,Math.min(width,height)/2,Path.Direction.CW);
        // Match the refraction shader's shear so its boundary and the clipping path coincide.
        lensMatrix.setSkew(deformation.value*-.045f,0,centerX,centerY);lensPath.transform(lensMatrix);
    }
    private void drawPanel(Canvas canvas){
        if(material()){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(app.theme.surfaceHigh);canvas.drawPath(clip,paint);return;}
        float a=clamp(activation.value,0,1);int save=canvas.save();canvas.clipPath(clip);
        boolean sampled=backdrop.drawGlass(canvas,this,a);
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(app.theme.dark?(sampled?0x66121212:0xf02b2b32):(sampled?0xaaf8f7ff:0xf0eeedf4));canvas.drawPath(clip,paint);
        paint.setShader(tint);paint.setAlpha(255);canvas.drawPath(clip,paint);
        if(a>0){
            spotMatrix.setTranslate(slider.centerX(),slider.centerY());spot.setLocalMatrix(spotMatrix);
            paint.setShader(spot);paint.setAlpha((int)(255*a));canvas.drawPath(clip,paint);
        }
        canvas.restoreToCount(save);paint.setShader(rim);paint.setAlpha(140);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(app.dp(.65f));
        if(Build.VERSION.SDK_INT>=33&&backdrop.quality()!=GlassQualityPolicy.SIMPLE){
            if(panelHighlight==null)panelHighlight=new KyantGlass.Highlight(app);
            paint.setShader(panelHighlight.shader(surface,0));paint.setAlpha(115);
        }
        canvas.drawPath(clip,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    }
    private void drawLensFinish(Canvas canvas){
        if(material()){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(app.theme.secondaryContainer);
            Tab tab=tabs[selected];float iconY=tab.getTop()+tab.icon.getTop()+tab.icon.getHeight()/2f;
            float halfWidth=Math.min(app.dp(32),slider.width()/2-app.dp(4)),halfHeight=app.dp(16);
            canvas.drawRoundRect(slider.centerX()-halfWidth,iconY-halfHeight,slider.centerX()+halfWidth,iconY+halfHeight,app.dp(16),app.dp(16),paint);return;}
        float a=clamp(activation.value,0,1);
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb((int)(26*(1-a)),255,255,255));canvas.drawPath(lensPath,paint);
        if(a>0){paint.setColor(Color.argb((int)(8*a),0,0,0));canvas.drawPath(lensPath,paint);}
        paint.setShader(rim);paint.setAlpha((int)(45+180*a));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(app.dp(.65f+a*.2f));
        if(Build.VERSION.SDK_INT>=33&&backdrop.quality()!=GlassQualityPolicy.SIMPLE){
            if(lensHighlight==null)lensHighlight=new KyantGlass.Highlight(app);
            paint.setShader(lensHighlight.shader(slider,deformation.value*-.045f));paint.setAlpha((int)(55+195*a));
        }
        canvas.drawPath(lensPath,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(getWidth()==0||!positionReady)return;measureLens();drawPanel(canvas);if(material())return;
        float a=clamp(activation.value,0,1);
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.argb((int)(12+a*22),0,0,0));paint.setStrokeWidth(app.dp(2+a*2));canvas.drawPath(lensPath,paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void dispatchDraw(Canvas canvas){
        if(!positionReady){super.dispatchDraw(canvas);return;}
        if(material()||Build.VERSION.SDK_INT<33||!canvas.isHardwareAccelerated()||backdrop.quality()!=GlassQualityPolicy.FULL||GlassQualityPolicy.refractionStrength(app)<=0||contentFailed){
            drawLensFinish(canvas);super.dispatchDraw(canvas);return;
        }
        try{
            if(contentLens==null)contentLens=new ContentLens(app);
            int padding=app.dp(48),width=getWidth()+padding*2,height=getHeight()+padding*2;
            Canvas recording=contentLens.begin(width,height);recording.translate(padding,padding);
            try{super.dispatchDraw(recording);}finally{contentLens.end();}
            // Same isolated frosted panel and tabs are refracted together, as in CombinedBackdrop.
            recording=contentLens.combined.beginRecording();
            try{
                recording.translate(padding,padding);backdrop.drawBackdropSource(recording,this);drawPanel(recording);
                recording.translate(-padding,-padding);recording.drawRenderNode(contentLens.source);
            }finally{contentLens.combined.endRecording();}
            contentLens.configure(slider,padding,clamp(activation.value,0,1),deformation.value,GlassQualityPolicy.refractionStrength(app),getResources().getDisplayMetrics().density);
            int save=canvas.save();
            try{canvas.clipOutPath(lensPath);canvas.translate(-padding,-padding);contentLens.draw(canvas,false);}finally{canvas.restoreToCount(save);}
            save=canvas.save();
            try{canvas.clipPath(lensPath);canvas.translate(-padding,-padding);contentLens.draw(canvas,true);}finally{canvas.restoreToCount(save);}
            drawLensFinish(canvas);
        }catch(RuntimeException error){
            contentFailed=true;android.util.Log.w("GlassDock","Combined lens unavailable",error);drawLensFinish(canvas);super.dispatchDraw(canvas);
        }
    }
    /** Isolated GPU equivalent of upstream rememberCombinedBackdrop(backdrop, tabsBackdrop). */
    private static final class ContentLens {
        final android.graphics.RenderNode source=new android.graphics.RenderNode("Kyant tabs backdrop"),
            combined=new android.graphics.RenderNode("Kyant combined backdrop"),warped=new android.graphics.RenderNode("Kyant liquid selection");
        final KyantGlass.Lens lens;
        final RectF bounds=new RectF();
        ContentLens(MainActivity app){lens=new KyantGlass.Lens(app,true);}
        Canvas begin(int width,int height){source.setPosition(0,0,width,height);combined.setPosition(0,0,width,height);warped.setPosition(0,0,width,height);return source.beginRecording();}
        void end(){source.endRecording();Canvas c=warped.beginRecording();try{c.drawRenderNode(combined);}finally{warped.endRecording();}}
        void configure(RectF shape,int padding,float pressure,float deformation,float strength,float dp){
            bounds.set(shape);bounds.offset(padding,padding);float progress=.22f+.78f*pressure;
            warped.setRenderEffect(lens.effect(null,bounds,dp*10*progress,dp*14*progress*strength,deformation*-.045f));
        }
        void draw(Canvas c,boolean refracted){c.drawRenderNode(refracted?warped:source);}
        void release(){source.discardDisplayList();combined.discardDisplayList();warped.discardDisplayList();}
    }
    @Override protected boolean drawChild(Canvas canvas,View child,long drawingTime){
        float a=material()?0:clamp(activation.value,0,1),center=position.value+tabSize(tabs[selected])/2f;
        float influence=tabSize(child)==0?0:clamp(1-Math.abs(tabStart(child)+tabSize(child)/2f-center)/(tabSize(child)*.8f),0,1);
        int save=canvas.save();
        canvas.scale(1+a*influence*(vertical?.065f:.05f),1+a*influence*(vertical?.05f:.065f),child.getLeft()+child.getWidth()/2f,child.getTop()+child.getHeight()/2f);
        float shift=material()?0:deformation.value*app.dp(1.5f)*influence;canvas.translate(vertical?0:shift,vertical?shift:0);
        try{return super.drawChild(canvas,child,drawingTime);}finally{canvas.restoreToCount(save);}
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus&&touchActive)endGesture(false);}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility!=VISIBLE&&tabs!=null&&tabs[0]!=null)resetMotion();}
    @Override protected void onDetachedFromWindow(){resetMotion();if(contentLens!=null){contentLens.release();contentLens=null;}super.onDetachedFromWindow();}
    private void resetMotion(){
        removeCallbacks(frame);removeCallbacks(longPress);removeCallbacks(previewRelease);recycleTracker();ticking=touchActive=dragging=held=crossCancelled=false;pointerId=-1;
        activation.target=deformation.target=dockShift.target=jelly.target=0;activation.snap();deformation.snap();dockShift.snap();jelly.snap();
        if(tabs[0]!=null){position.target=tabStart(tabs[selected]);position.snap();}setTranslationX(0);setTranslationY(0);
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
            super(app);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setClickable(true);setFocusable(true);setContentDescription((backdrop.preview?"预览":"导航")+name);setMinimumHeight(app.dp(48));
            icon=new Icon(index);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(icon,new LayoutParams(app.dp(24),app.dp(24)));
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
