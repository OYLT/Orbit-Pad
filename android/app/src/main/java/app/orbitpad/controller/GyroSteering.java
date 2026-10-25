package app.orbitpad.controller;

import android.content.Context;
import android.hardware.*;

/** Absolute steering from the device X/Z gravity plane; screen-up level is neutral. */
final class GyroSteering implements SensorEventListener {
    interface Listener { void angle(float degrees); }
    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;
    private boolean running;
    private long last;
    GyroSteering(Context context, Listener listener) {
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        sensor=manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        this.listener=listener;
    }
    boolean available(){return sensor!=null;}
    void start(){if(!running&&available()){last=0;running=manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME);}}
    void stop(){manager.unregisterListener(this);running=false;}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onSensorChanged(SensorEvent event){
        if(!running||event.sensor!=sensor)return;
        if(last!=0&&event.timestamp-last<16000000)return;last=event.timestamp;
        // Android delivers three components; only X and Z enter steering or app state.
        listener.angle(gravityAngle(event.values[0],event.values[2]));
    }
    static float gravityAngle(float x,float z){
        // Near the Y axis the X/Z projection has no reliable direction: release steering.
        if(!Float.isFinite(x)||!Float.isFinite(z)||Math.hypot(x,z)<0.5)return 0;
        return (float)-Math.toDegrees(Math.atan2(x,z));
    }
}
