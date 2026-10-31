package app.orbitpad.controller;

import android.animation.*;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import org.json.JSONObject;
import java.util.ArrayList;

/** Horizontal touch carousel with layered depth and Gaussian focus transitions. */
final class TemplateCarousel extends FrameLayout {
    static final class Entry {
        final String id,name;final JSONObject settings;final boolean saved;
        Entry(String id,String name,JSONObject settings,boolean saved){
            this.id=id;this.name=name;this.saved=saved;
            try{this.settings=new JSONObject(settings.toString());}catch(Exception error){throw new IllegalArgumentException(error);}
        }
    }
    interface Selection {void select(Entry entry);}
    private final MainActivity app;
    private final ArrayList<Entry> entries;
    private final ArrayList<LinearLayout> cards=new ArrayList<>();
    private final ArrayList<ControllerPreview> previews=new ArrayList<>();
    private final Selection listener;
    private ValueAnimator transition;
    private int active,cardWidth;
    private float position,startX,startY,startPosition;
    private boolean dragging;
    private long started;
    TemplateCarousel(MainActivity app,ArrayList<Entry> entries,int initial,Selection listener){
        super(app);this.app=app;this.entries=entries;this.listener=listener;active=initial;position=initial;
        setClipChildren(false);setClipToPadding(false);setContentDescription("横向手柄配置卡片");setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        for(int i=0;i<entries.size();i++){
            final int index=i;Entry entry=entries.get(i);LinearLayout card=new LinearLayout(app);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(app.dp(18),app.dp(18),app.dp(18),app.dp(14));
            card.setBackground(app.rounded(0xff25212c,0xff51445f,28));card.setClipToOutline(true);card.setClickable(true);card.setFocusable(true);card.setContentDescription("选择配置 · "+entry.name);card.setOnClickListener(v->select(index,true));
            TextView title=app.text(entry.name,19,0xffe6e0e9);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title);
            TextView subtitle=app.text(entry.id.equals("working")?"当前手柄":entry.saved||(entry.settings.optJSONObject("layout")!=null&&entry.settings.optJSONObject("layout").length()>0)?"已保存布局":"系统预设",11,0xffcac4d0);card.addView(subtitle);
            ControllerPreview preview=new ControllerPreview(app,entry.settings,entry.name);LinearLayout.LayoutParams area=new LinearLayout.LayoutParams(-1,0,1);area.topMargin=app.dp(6);card.addView(preview,area);
            TextView hint=app.text("左右滑动切换",11,0xffd0bcff);hint.setGravity(Gravity.RIGHT);card.addView(hint);
            previews.add(preview);cards.add(card);addView(card);
        }
    }
    int activeIndex(){return active;}
    Entry activeEntry(){return entries.get(active);}
    void select(int index,boolean animate){
        index=Math.max(0,Math.min(entries.size()-1,index));cancelTransition();final int target=index;
        if(!animate||!ValueAnimator.areAnimatorsEnabled()||!isAttachedToWindow()){position=target;commit(target);render();return;}
        ValueAnimator animator=ValueAnimator.ofFloat(position,target);transition=animator;animator.setDuration(380);animator.setInterpolator(new PathInterpolator(.22f,.8f,.25f,1));
        animator.addUpdateListener(a->{position=(float)a.getAnimatedValue();render();});
        animator.addListener(new AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(Animator a){cancelled=true;}@Override public void onAnimationEnd(Animator a){if(!cancelled){transition=null;position=target;commit(target);render();}}});animator.start();
    }
    private void commit(int index){boolean changed=active!=index;active=index;render();if(changed)listener.select(entries.get(index));announceForAccessibility(entries.get(index).name);}
    private void cancelTransition(){if(transition!=null){ValueAnimator old=transition;transition=null;old.cancel();}}
    @Override protected void onMeasure(int width,int height){
        int w=MeasureSpec.getSize(width),h=MeasureSpec.getSize(height);setMeasuredDimension(w,h);cardWidth=Math.max(1,w-app.dp(42));
        for(View card:cards)card.measure(MeasureSpec.makeMeasureSpec(cardWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,h-app.dp(16)),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(View card:cards){int left=(getWidth()-cardWidth)/2;card.layout(left,app.dp(8),left+cardWidth,getHeight()-app.dp(8));}render();}
    private void render(){
        float fraction=Math.abs(position-Math.round(position)),pulse=(float)Math.sin(fraction*Math.PI);
        for(int i=0;i<cards.size();i++){
            LinearLayout card=cards.get(i);float offset=i-position,distance=Math.abs(offset);boolean visible=distance<2.1f;
            card.setVisibility(visible?View.VISIBLE:View.INVISIBLE);card.setTranslationX(offset*cardWidth*.88f);card.setTranslationY(app.dp(10)*Math.min(2,distance));
            card.setScaleX(1-.08f*Math.min(2,distance));card.setScaleY(1-.08f*Math.min(2,distance));card.setAlpha(Math.max(0,1-.36f*distance));card.setElevation(app.dp(Math.max(0,12-distance*4)));
            card.setSelected(i==active);card.setImportantForAccessibility(i==active?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            float blur=Math.min(10,distance*4+pulse*3);if(Build.VERSION.SDK_INT>=31)Blur.apply(card,visible?app.dp(blur):0);else previews.get(i).softness(Math.min(1,blur/6));
        }invalidate();
    }
    private void begin(MotionEvent e){startX=e.getX();startY=e.getY();startPosition=position;started=e.getEventTime();dragging=false;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:begin(event);break;
            case MotionEvent.ACTION_MOVE:if(Math.abs(event.getX()-startX)>app.dp(8)&&Math.abs(event.getX()-startX)>Math.abs(event.getY()-startY)){
                if(!dragging){cancelTransition();startPosition=position;}dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
            }break;
        }return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){begin(event);return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){if(!dragging){cancelTransition();startPosition=position;}dragging=true;position=Math.max(0,Math.min(entries.size()-1,startPosition-(event.getX()-startX)/Math.max(1,cardWidth*.88f)));render();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            float distance=event.getX()-startX;int target=Math.round(position);
            if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)target=active;
            else if(dragging&&event.getEventTime()-started<350&&Math.abs(distance)>app.dp(32))target=active+(distance<0?1:-1);
            select(target,true);getParent().requestDisallowInterceptTouchEvent(false);dragging=false;return true;
        }return true;
    }
    @Override public boolean onKeyDown(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){select(active+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1),true);return true;}return super.onKeyDown(code,event);}
    @Override protected void onDetachedFromWindow(){cancelTransition();position=active;for(View card:cards)if(Build.VERSION.SDK_INT>=31)Blur.apply(card,0);super.onDetachedFromWindow();}
    @android.annotation.TargetApi(31) private static final class Blur {
        static void apply(View view,float radius){view.setRenderEffect(radius<.5f?null:RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP));}
    }
}
