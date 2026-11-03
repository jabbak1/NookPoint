package org.nookpoint.reader;

/** Remembers recent crashes so that a launcher that keeps crashing can step aside instead of locking the device. */
class CrashGuard {
    static final long WINDOW = 3 * 60 * 1000L;
    static final int LIMIT = 3;

    static synchronized void note() {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        String[] p = Prefs.get("crashes", "").split(",");
        for (int i = 0; i < p.length; i++) {
            try {
                long t = Long.parseLong(p[i].trim());
                if (now - t < WINDOW) sb.append(t).append(',');
            } catch (Exception e) { }
        }
        sb.append(now);
        Prefs.put("crashes", sb.toString());
    }

    static boolean isLoop() {
        long now = System.currentTimeMillis();
        int n = 0;
        String[] p = Prefs.get("crashes", "").split(",");
        for (int i = 0; i < p.length; i++) {
            try {
                if (now - Long.parseLong(p[i].trim()) < WINDOW) n++;
            } catch (Exception e) { }
        }
        return n >= LIMIT;
    }

    static void clear() { Prefs.put("crashes", ""); }
}
