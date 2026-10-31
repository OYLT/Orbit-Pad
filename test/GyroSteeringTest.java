package app.orbitpad.controller;

/** Production calibration and multi-turn rotation math, exercised without Android hardware. */
public final class GyroSteeringTest {
    private static int checks;
    private static void near(float actual,float expected) {
        if(!Float.isFinite(actual)||Math.abs(actual-expected)>0.002f)
            throw new AssertionError(actual+" != "+expected);
        checks++;
    }
    private static void check(boolean value) {if(!value)throw new AssertionError();checks++;}
    private static float[] turn(float degrees) {
        double half=Math.toRadians(degrees)/2;
        return new float[]{(float)Math.cos(half),0,0,(float)-Math.sin(half)};
    }
    private static float[] multiply(float[] a,float[] b) {
        return new float[]{a[0]*b[0]-a[1]*b[1]-a[2]*b[2]-a[3]*b[3],
            a[0]*b[1]+a[1]*b[0]+a[2]*b[3]-a[3]*b[2],
            a[0]*b[2]-a[1]*b[3]+a[2]*b[0]+a[3]*b[1],
            a[0]*b[3]+a[1]*b[2]-a[2]*b[1]+a[3]*b[0]};
    }
    public static void main(String[] args) {
        float[] zero=turn(0);
        for(float degrees:new float[]{-179,-90,-45,-23.5f,0,23.5f,45,90,179}) {
            near(GyroSteering.relativeAngle(zero,turn(degrees)),degrees);
            float[] q=turn(degrees);for(int i=0;i<4;i++)q[i]=-q[i];
            near(GyroSteering.relativeAngle(zero,q),degrees);
        }
        // Movement restarts calibration; any stable held orientation can become neutral.
        GyroSteering.Rotation rotation=new GyroSteering.Rotation();
        near(rotation.sample(turn(35),0),0);check(!rotation.calibrated);
        near(rotation.sample(turn(35),500000000L),0);check(!rotation.calibrated);
        near(rotation.sample(turn(45),550000000L),0);
        near(rotation.sample(turn(45),1000000000L),0);check(!rotation.calibrated);
        near(rotation.sample(turn(45),1150000000L),0);check(rotation.calibrated);
        near(rotation.sample(turn(68.5f),1200000000L),23.5f);
        // Restart/manual recenter discards both the previous zero and accumulated turns.
        rotation.reset();check(!rotation.calibrated);
        near(rotation.sample(turn(-70),1300000000L),0);
        near(rotation.sample(turn(-70),1900000000L),0);check(rotation.calibrated);
        near(rotation.sample(turn(-70),1950000000L),0);
        near(rotation.sample(turn(-25),2000000000L),45);
        for(float[] base:new float[][]{turn(90),turn(-90),new float[]{0.70710677f,0.70710677f,0,0}}) {
            rotation.reset();rotation.sample(base,0);rotation.sample(base,600000000L);
            near(rotation.sample(multiply(base,turn(45)),620000000L),45);
        }
        // Cross +/-180 repeatedly, then unwind to center without lost turns or sign flips.
        rotation.reset();rotation.sample(zero,0);rotation.sample(zero,600000000L);
        long time=620000000L;
        for(int degrees=0;degrees<=720;degrees+=10)near(rotation.sample(turn(degrees),time+=20000000L),degrees);
        for(int degrees=720;degrees>=-720;degrees-=10)near(rotation.sample(turn(degrees),time+=20000000L),degrees);
        for(int degrees=-720;degrees<=0;degrees+=10)near(rotation.sample(turn(degrees),time+=20000000L),degrees);
        near(rotation.sample(new float[]{Float.NaN,0,0,0},time+=20000000L),0);check(!rotation.calibrated);
        near(rotation.sample(new float[]{0,0,0,0},time+=20000000L),0);check(!rotation.calibrated);
        System.out.println("Gyro calibration / multi-turn steering: "+checks+" checks passed");
    }
}
