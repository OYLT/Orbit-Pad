package app.orbitpad.controller;

import android.content.Context;
import android.hardware.*;

/** Relative screen-normal rotation, calibrated each time controller input starts. */
final class GyroSteering implements SensorEventListener {
    interface Listener { void angle(float degrees); }
    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;
    private final float[] current=new float[4];
    private final Rotation rotation=new Rotation();
    private boolean running;
    private long last;
    GyroSteering(Context context, Listener listener) {
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        Sensor game=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        sensor=game!=null?game:manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        this.listener=listener;
    }
    boolean available(){return sensor!=null;}
    void start(){if(!running&&available()){calibrate();last=0;running=manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME);}}
    void stop(){manager.unregisterListener(this);running=false;rotation.reset();}
    void calibrate(){rotation.reset();listener.angle(0);}
    boolean calibrating(){return running&&!rotation.calibrated;}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onSensorChanged(SensorEvent event){
        if(!running||event.sensor!=sensor)return;
        if(last!=0&&event.timestamp-last<16000000)return;last=event.timestamp;
        SensorManager.getQuaternionFromVector(current,event.values);
        listener.angle(Math.max(-450,Math.min(450,rotation.sample(current,event.timestamp))));
    }
    static float relativeAngle(float[] zero,float[] current){
        float w=zero[0]*current[0]+zero[1]*current[1]+zero[2]*current[2]+zero[3]*current[3];
        float z=zero[0]*current[3]-zero[1]*current[2]+zero[2]*current[1]-zero[3]*current[0];
        if(w*w+z*z<0.000001f)return Float.NaN;
        return ((float)-Math.toDegrees(2*Math.atan2(z,w))+540)%360-180;
    }
    /** Pure state machine shared by real sensor events and deterministic tests. */
    static final class Rotation {
        private final float[] zero=new float[4];
        private long stableSince;
        private boolean reference;
        boolean calibrated;
        private float previous,total;
        void reset(){reference=false;calibrated=false;stableSince=0;previous=total=0;}
        float sample(float[] q,long timestamp){
            float norm=0;for(float value:q){if(!Float.isFinite(value)){reset();return 0;}norm+=value*value;}
            if(q.length!=4||Math.abs(norm-1)>0.01f){reset();return 0;}
            if(!calibrated){
                float dot=0;for(int i=0;i<4;i++)dot+=zero[i]*q[i];
                // All rotation axes must stay within 1.5 degrees for 600 ms.
                if(!reference||Math.abs(dot)<Math.cos(Math.toRadians(1.5)/2)){
                    System.arraycopy(q,0,zero,0,4);stableSince=timestamp;reference=true;
                }else if(timestamp-stableSince>=600000000L){
                    System.arraycopy(q,0,zero,0,4);calibrated=true;
                }
                return 0;
            }
            float angle=relativeAngle(zero,q);
            if(!Float.isFinite(angle)){reset();return 0;}
            float delta=(angle-previous+540)%360-180;
            total+=delta;previous=angle;
            return total;
        }
    }
}
