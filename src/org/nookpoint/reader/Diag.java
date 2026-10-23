package org.nookpoint.reader;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Error log on the card (NookPoint/log.txt): uncaught crashes and caught errors, readable in Settings. */
class Diag {
    private static boolean installed;
    private static String version = "?";

    static String version() { return version; }

    static File dir() {
        File d = new File(Prefs.root(), "NookPoint");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File file() { return new File(dir(), "log.txt"); }

    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Throwable t) { }
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                write("CRASH in thread " + t.getName(), e);
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    static void e(String where, Throwable t) { write(where, t); }

    static void i(String msg) { write(msg, null); }

    private static synchronized void write(String head, Throwable t) {
        try {
            File f = file();
            if (f.length() > 120000) {                               // keep the newest part
                String tail = tail(60000);
                FileOutputStream o = new FileOutputStream(f);
                o.write(tail.getBytes("UTF-8"));
                o.close();
            }
            StringBuilder sb = new StringBuilder();
            sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            sb.append("  v").append(version).append("  ").append(Build.MODEL).append(" (Android ")
                    .append(Build.VERSION.RELEASE).append(", API ").append(Build.VERSION.SDK_INT).append(")  free mem ")
                    .append((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()
                            + Runtime.getRuntime().freeMemory()) / 1024).append(" KB\n");
            sb.append(head).append('\n');
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                String s = sw.toString();
                sb.append(s.length() > 3500 ? s.substring(0, 3500) + "..." : s);
            }
            sb.append('\n');
            FileOutputStream o = new FileOutputStream(f, true);
            o.write(sb.toString().getBytes("UTF-8"));
            o.close();
        } catch (Throwable x) {
            // logging must never crash the app
        }
    }

    private static String tail(int maxChars) {
        try {
            File f = file();
            if (!f.exists()) return "";
            long len = f.length();
            FileInputStream in = new FileInputStream(f);
            try {
                long skip = Math.max(0, len - maxChars * 2L);          // up to 2 bytes per char
                while (skip > 0) {
                    long s = in.skip(skip);
                    if (s <= 0) break;
                    skip -= s;
                }
                byte[] b = new byte[(int) Math.min(len, maxChars * 2L)];
                int n = 0, r;
                while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
                String s = new String(b, 0, n, "UTF-8");
                return s.length() > maxChars ? s.substring(s.length() - maxChars) : s;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return "";
        }
    }

    static String read(int maxChars) { return tail(maxChars); }

    static void clear() { try { file().delete(); } catch (Throwable t) { } }
}
