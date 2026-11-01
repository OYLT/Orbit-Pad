package app.orbitpad.controller;

public final class LayoutGeometryTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void near(float a,float b){check(Math.abs(a-b)<.001f,"Expected "+a+" ≈ "+b);}
    private static void inside(LayoutGeometry.Box[] boxes){for(LayoutGeometry.Box b:boxes){check(b.left()>=-.001f&&b.right()<=1000.001f&&b.top()>=-.001f&&b.bottom()<=450.001f,"Control escaped screen");check(b.w>=23.999f&&b.h>=23.999f,"Control lost usable touch area");}}
    public static void main(String[] args){
        LayoutGeometry.Box[] face={new LayoutGeometry.Box(790,257,66,66),new LayoutGeometry.Box(858,325,66,66),new LayoutGeometry.Box(926,257,66,66),new LayoutGeometry.Box(858,189,66,66)};
        LayoutGeometry.Box[] moved=LayoutGeometry.move(face,-100,20);inside(moved);
        for(int i=0;i<4;i++){near(moved[i].x-face[i].x,-100);near(moved[i].y-face[i].y,20);}
        LayoutGeometry.Box[] edge=LayoutGeometry.move(face,10000,-10000);inside(edge);
        near(LayoutGeometry.bounds(edge).right(),1000);near(LayoutGeometry.bounds(edge).top(),0);
        LayoutGeometry.Box opposite=LayoutGeometry.bounds(LayoutGeometry.move(face,-10000,10000));near(opposite.left(),0);near(opposite.bottom(),450);
        for(int i=1;i<4;i++){near(edge[i].x-edge[0].x,face[i].x-face[0].x);near(edge[i].y-edge[0].y,face[i].y-face[0].y);}
        LayoutGeometry.Box[] stretched=LayoutGeometry.resize(face,2000,300,false);inside(stretched);near(LayoutGeometry.bounds(stretched).w,1000);
        check(stretched[0].w>face[0].w*4,"Face controls still have the retired width cap");
        LayoutGeometry.Box[] giant=LayoutGeometry.resize(face,9000,9000,true);inside(giant);near(giant[0].w,giant[0].h);
        LayoutGeometry.Box[] tiny=LayoutGeometry.resize(face,1,1,false);inside(tiny);near(tiny[0].w,24);
        LayoutGeometry.Box[] single={new LayoutGeometry.Box(990,440,30,30)};
        LayoutGeometry.Box full=LayoutGeometry.resize(single,1000,450,false)[0];inside(new LayoutGeometry.Box[]{full});near(full.left(),0);near(full.top(),0);near(full.right(),1000);near(full.bottom(),450);
        for(int i=1;i<100;i++)inside(LayoutGeometry.resize(face,i*17,i*6,false));
        System.out.println("Layout geometry: grouped movement, screen bounds, continuous dimensions and aspect lock passed.");
    }
}
