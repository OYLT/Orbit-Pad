package app.orbitpad.controller;

/** Run the production gravity calculation on the JVM against known physical vectors. */
public final class GyroSteeringTest {
    private static int checks;
    private static void angle(float x,float z,float expected) {
        float actual=GyroSteering.gravityAngle(x,z);
        if(!Float.isFinite(actual)||Math.abs(actual-expected)>0.001f)
            throw new AssertionError("X="+x+" Z="+z+": "+actual+" != "+expected);
        checks++;
    }
    public static void main(String[] args) {
        angle(0,9.81f,0);
        for(float degrees:new float[]{-135,-90,-45,-23.5f,-2,2,23.5f,45,90,135}) {
            double radians=Math.toRadians(degrees);
            for(float magnitude:new float[]{5,9.81f,15})
                angle((float)(-magnitude*Math.sin(radians)),(float)(magnitude*Math.cos(radians)),degrees);
        }
        // A fresh/restarted listener must preserve the absolute angle, even if already tilted.
        angle(-4.905f,8.495709f,30);
        angle(0,9.81f,0);
        angle(-4.905f,8.495709f,30);
        angle(0,0,0);
        angle(0.1f,-0.1f,0);
        angle(Float.NaN,9.81f,0);
        angle(0,Float.POSITIVE_INFINITY,0);
        angle(Float.NEGATIVE_INFINITY,0,0);
        System.out.println("Gravity X/Z steering: "+checks+" checks passed");
    }
}
