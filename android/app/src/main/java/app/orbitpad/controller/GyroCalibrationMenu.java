package app.orbitpad.controller;

import android.view.Gravity;
import android.widget.*;

/** In-app secondary menu; showing it never starts the rotation sensor. */
final class GyroCalibrationMenu extends LinearLayout {
    private final TextView message;
    private final Button start,cancel;
    GyroCalibrationMenu(MainActivity app,Runnable begin,Runnable dismiss){
        super(app);setOrientation(VERTICAL);setPadding(app.dp(22),app.dp(16),app.dp(22),app.dp(16));
        setBackground(app.rounded(app.themeColor(0xfa2b2930),app.themeColor(0xff665b72),28));setElevation(app.dp(24));setClickable(true);
        setContentDescription("陀螺仪校准提示");
        TextView title=app.text("陀螺仪校准",20,app.themeColor(0xffe6e0e9));addView(title);
        message=app.text("握好手机并保持稳定。点击「开始校准」后，才会记录当前握持姿态。",13,app.themeColor(0xffcac4d0));app.add(this,message,10);
        LinearLayout actions=new LinearLayout(app);actions.setGravity(Gravity.CENTER_VERTICAL);
        cancel=app.button("暂不校准");cancel.setTextColor(app.themeColor(0xffd0bcff));cancel.setBackground(app.rounded(app.themeColor(0xff37313e),0,24));cancel.setOnClickListener(v->dismiss.run());
        start=app.button("开始校准");start.setOnClickListener(v->begin.run());
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=app.dp(8);actions.addView(cancel,left);actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));app.add(this,actions,16);
    }
    void calibrating(){message.setText("正在校准，请保持手机稳定约 0.6 秒；移动手机会重新计时。");start.setText("正在校准…");start.setEnabled(false);cancel.setText("取消校准");}
    void failed(){message.setText("无法启动传感器，请稍后重试。");start.setText("开始校准");start.setEnabled(true);cancel.setText("暂不校准");}
}
