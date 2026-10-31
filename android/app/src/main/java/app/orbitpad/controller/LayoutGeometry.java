package app.orbitpad.controller;

/** Canvas-space transforms shared by linked groups and individual controls. */
final class LayoutGeometry {
    static final float LEFT=4, TOP=4, RIGHT=996, BOTTOM=446, MIN=24;
    static final class Box {
        final float x,y,w,h;
        Box(float x,float y,float w,float h){this.x=x;this.y=y;this.w=w;this.h=h;}
        float left(){return x-w/2;} float right(){return x+w/2;}
        float top(){return y-h/2;} float bottom(){return y+h/2;}
    }
    static Box bounds(Box[] boxes){
        float l=Float.MAX_VALUE,t=l,r=-l,b=-l;
        for(Box box:boxes){l=Math.min(l,box.left());t=Math.min(t,box.top());r=Math.max(r,box.right());b=Math.max(b,box.bottom());}
        return new Box((l+r)/2,(t+b)/2,r-l,b-t);
    }
    static Box[] move(Box[] boxes,float dx,float dy){
        Box all=bounds(boxes);dx=clamp(dx,LEFT-all.left(),RIGHT-all.right());dy=clamp(dy,TOP-all.top(),BOTTOM-all.bottom());
        Box[] result=new Box[boxes.length];for(int i=0;i<boxes.length;i++){Box b=boxes[i];result[i]=new Box(b.x+dx,b.y+dy,b.w,b.h);}return result;
    }
    static Box[] resize(Box[] boxes,float targetW,float targetH,boolean uniform){
        Box all=bounds(boxes);float minX=0,minY=0;
        for(Box b:boxes){minX=Math.max(minX,MIN/b.w);minY=Math.max(minY,MIN/b.h);}
        float maxX=(RIGHT-LEFT)/all.w,maxY=(BOTTOM-TOP)/all.h;
        float sx=clamp(targetW/all.w,minX,maxX),sy=clamp(targetH/all.h,minY,maxY);
        if(uniform)sx=sy=clamp(Math.min(sx,sy),Math.max(minX,minY),Math.min(maxX,maxY));
        Box[] result=new Box[boxes.length];for(int i=0;i<boxes.length;i++){
            Box b=boxes[i];result[i]=new Box(all.x+(b.x-all.x)*sx,all.y+(b.y-all.y)*sy,b.w*sx,b.h*sy);
        }
        return move(result,0,0);
    }
    static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
}
