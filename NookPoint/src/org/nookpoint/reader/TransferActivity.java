package org.nookpoint.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

public class TransferActivity extends Activity {
    private WebServer server;
    private WifiManager.WifiLock lock;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView tv = new TextView(this);
        tv.setBackgroundColor(Color.WHITE);
        tv.setTextColor(Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(24, 24, 24, 24);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
        setContentView(tv);

        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int ip = wm.getConnectionInfo() == null ? 0 : wm.getConnectionInfo().getIpAddress();
        if (ip == 0) {
            tv.setText(T.t("File transfer\n\nNo Wi-Fi connection.\nTurn on Wi-Fi and connect to your network first."));
            return;
        }
        String addr = (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        try {
            lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "nookpoint");
            lock.acquire();
            server = new WebServer(Prefs.booksDir(), 8080);
            server.start();
            tv.setText(T.f("File transfer\n\nOn a phone or computer in the same Wi-Fi network open:\n\n{0}\n\nBooks go to:\n{1}\n\nPress Back to stop.",
                    "http://" + addr + ":8080", Prefs.booksDir().getAbsolutePath()));
        } catch (Exception e) {
            tv.setText(T.f("Could not start server:\n{0}", e.getMessage()));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) server.shutdown();
        if (lock != null && lock.isHeld()) lock.release();
    }
}
