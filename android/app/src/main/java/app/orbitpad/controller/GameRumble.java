package app.orbitpad.controller;

import android.content.Context;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import org.json.JSONArray;

/** Two game motors are mixed onto the phone actuator, with bounded renewable pulses. */
final class GameRumble {
    private final Vibrator vibrator;
    private long lastPulse;
    private int amplitude;
    GameRumble(Context context){vibrator=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);}
    boolean available(){return vibrator!=null&&vibrator.hasVibrator();}
    void update(boolean enabled,JSONArray motors){
        int large=motors==null?0:motors.optInt(0),small=motors==null?0:motors.optInt(1);
        int next=Math.max(0,Math.min(255,Math.max(large,small)));
        if(!enabled||!available()||next==0){stop();return;}
        long now=SystemClock.elapsedRealtime();
        if(next!=amplitude||now-lastPulse>=160){
            try{vibrator.vibrate(VibrationEffect.createOneShot(240,vibrator.hasAmplitudeControl()?next:VibrationEffect.DEFAULT_AMPLITUDE));amplitude=next;lastPulse=now;}
            catch(RuntimeException error){stop();}
        }
    }
    void stop(){if(amplitude!=0&&vibrator!=null)vibrator.cancel();amplitude=0;lastPulse=0;}
}
