package org.nookpoint.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;

/** Saves and restores all settings, reading positions, bookmarks, highlights, statistics and shelves. */
class Backup {
    static final String EXT = ".npb";

    static File dir() {
        File d = new File(Diag.dir(), "backup");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File[] list() {
        File[] fs = dir().listFiles();
        if (fs == null) return new File[0];
        ArrayList<File> r = new ArrayList<File>();
        for (int i = 0; i < fs.length; i++) if (fs[i].getName().toLowerCase().endsWith(EXT)) r.add(fs[i]);
        Collections.sort(r, new Comparator<File>() {
            public int compare(File a, File b) { return a.lastModified() < b.lastModified() ? 1 : (a.lastModified() > b.lastModified() ? -1 : b.getName().compareTo(a.getName())); }
        });
        return r.toArray(new File[r.size()]);
    }

    static File export() throws IOException {
        File f = new File(dir(), "backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + EXT);
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write("NOOKPOINT-BACKUP 1\n");
            for (Map.Entry<String, ?> e : Prefs.all().entrySet()) {
                Object v = e.getValue();
                String type = v instanceof Integer ? "i" : (v instanceof Long ? "l" : (v instanceof Boolean ? "b"
                        : (v instanceof Float ? "f" : "s")));
                w.write(type + "\t" + Esc.esc(e.getKey()) + "\t" + Esc.esc(String.valueOf(v)) + "\n");
            }
        } finally {
            w.close();
        }
        Prefs.put("lastbackup", String.valueOf(System.currentTimeMillis()));
        prune(8);
        return f;
    }

    /** Replaces all settings/data with the content of a backup file. Returns the number of entries. */
    static int restore(File f) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        ArrayList<String[]> items = new ArrayList<String[]>();
        try {
            String head = r.readLine();
            if (head == null || !head.startsWith("NOOKPOINT-BACKUP")) throw new IOException("Not a NookPoint backup");
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", 3);
                if (p.length == 3) items.add(new String[]{p[0], Esc.unesc(p[1]), Esc.unesc(p[2])});
            }
        } finally {
            r.close();
        }
        Prefs.restoreAll(items);
        T.reload();
        return items.size();
    }

    private static void prune(int keep) {
        File[] fs = list();
        for (int i = keep; i < fs.length; i++) fs[i].delete();
    }

    /** Weekly automatic backup, done in the background. */
    static void auto() {
        long last = 0;
        try { last = Long.parseLong(Prefs.get("lastbackup", "0")); } catch (Exception e) { }
        if (System.currentTimeMillis() - last < 7L * 24 * 3600 * 1000) return;
        if (Prefs.all().size() < 5) return;                         // nothing worth saving yet
        new Thread(new Runnable() {
            public void run() {
                try { export(); } catch (Throwable t) { Diag.e("auto backup", t); }
            }
        }).start();
    }
}
