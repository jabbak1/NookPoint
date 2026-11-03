package org.nookpoint.reader;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** Helpers for using NookPoint as the device's home screen (launcher). */
class LauncherHelper {
    private static Intent homeIntent() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        return i;
    }

    /** True when pressing the Home button currently opens NookPoint. */
    static boolean isDefaultHome(Context c) {
        try {
            ResolveInfo ri = c.getPackageManager().resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Fires the Home intent: with no default set, Android shows its chooser ("Always" makes NookPoint the home screen). */
    static void chooseHome(Context c) {
        Intent i = homeIntent();
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Removes NookPoint as the default home screen; the next Home press lets you pick another launcher. */
    static void stopBeingHome(Context c) {
        try {
            c.getPackageManager().clearPackagePreferredActivities(c.getPackageName());
        } catch (Throwable t) {
            Diag.e("clear default home", t);
        }
    }

    static int battery(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int lvl = i.getIntExtra("level", 0), scale = i.getIntExtra("scale", 100);
            return scale > 0 ? lvl * 100 / scale : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    static String clock(Context c) {
        try {
            return android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date());
        } catch (Throwable t) {
            return "";
        }
    }
}
