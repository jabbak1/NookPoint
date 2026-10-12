package org.nookpoint.reader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

public class KeyTestActivity extends Activity {
    private TextView tv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tv = new TextView(this);
        tv.setBackgroundColor(Color.WHITE);
        tv.setTextColor(Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32);
        tv.setText("Press any hardware button.\nBack button exits.\nTouch screen to exit.");
        setContentView(tv);
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK) return super.onKeyDown(k, e);
        tv.setText("keyCode = " + k + "\n\n" + e.toString());
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }
}
