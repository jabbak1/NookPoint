package org.nookpoint.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;

class Prefs {
    private static SharedPreferences sp;

    static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("nookpoint", 0);
    }
    static String get(String k, String d) { return sp.getString(k, d); }
    static void put(String k, String v) { sp.edit().putString(k, v).commit(); }
    static int getInt(String k, int d) { return sp.getInt(k, d); }

    static File root() { return Environment.getExternalStorageDirectory(); }
    static File booksDir() {
        File d = new File(root(), "Books");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    static File fontsDir() { return new File(root(), "fonts"); }

    static int[] getPos(String path) {
        String s = get("pos:" + path, null);
        if (s != null) {
            try {
                int i = s.indexOf(':');
                return new int[]{Integer.parseInt(s.substring(0, i)), Integer.parseInt(s.substring(i + 1))};
            } catch (Exception e) { }
        }
        return new int[]{0, 0};
    }

    static void savePos(String path, int ch, int off, int pct) {
        sp.edit().putString("pos:" + path, ch + ":" + off).putInt("pct:" + path, pct)
                .putString("last", path).commit();
    }

    static ArrayList<String> recents() {
        ArrayList<String> r = new ArrayList<String>();
        String s = get("recent", "");
        if (s.length() > 0) {
            String[] parts = s.split("\n");
            for (int i = 0; i < parts.length; i++) if (parts[i].length() > 0) r.add(parts[i]);
        }
        return r;
    }

    static void addRecent(String path, String title) {
        ArrayList<String> r = recents();
        r.remove(path);
        r.add(0, path);
        while (r.size() > 8) r.remove(r.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(r.get(i));
        }
        sp.edit().putString("recent", sb.toString()).putString("title:" + path, title)
                .putString("last", path).commit();
    }

    static String title(String path) {
        String t = get("title:" + path, null);
        return t != null ? t : new File(path).getName();
    }
    static int pct(String path) { return getInt("pct:" + path, 0); }
}
