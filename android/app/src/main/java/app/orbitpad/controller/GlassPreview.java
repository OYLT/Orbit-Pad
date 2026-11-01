package app.orbitpad.controller;

import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** Real production shader and foreground lens, isolated from the page's sampling source. */
final class GlassPreview extends FrameLayout {
    private final GlassBackdrop backdrop;
    private final GlassDock dock;
    GlassPreview(MainActivity app){
        super(app);setBackground(app.rounded(0xff263854,0,28));setClipToOutline(true);
        setContentDescription("实时玻璃效果预览");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        backdrop=new GlassBackdrop(app,true);addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        backdrop.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        backdrop.page.addView(new View(app){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff30466b,0xff82aabd},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
                p.setColor(0xffb2dce0);c.drawCircle(w*.13f,h*.3f,h*.7f,p);p.setColor(0xff648ac1);c.drawCircle(w*.9f,h*.85f,h*.7f,p);
                p.setColor(0xaae9faff);p.setStrokeWidth(app.dp(4));for(int i=0;i<12;i++)c.drawLine(w*.25f+i*app.dp(16),h*.28f,w*.25f+i*app.dp(16),h,p);
                p.setColor(0xff172d46);p.setTextSize(app.dp(14));p.setTypeface(Typeface.create("sans-serif-medium",0));c.drawText("LIQUID / LIVE",app.dp(20),app.dp(32),p);
            }
        },new FrameLayout.LayoutParams(-1,-1));
        dock=new GlassDock(app,backdrop,2,index->{});backdrop.setDock(dock);
        FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-1,dock.preferredHeight(),Gravity.BOTTOM);size.setMargins(app.dp(16),0,app.dp(16),app.dp(28));backdrop.addView(dock,size);
    }
    void refresh(){backdrop.invalidate();dock.invalidate();invalidate();}
}
