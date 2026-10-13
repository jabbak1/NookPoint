package org.nookpoint.reader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

/** Reading statistics stored in SharedPreferences. */
class Stats {
    static class BookStat { String path; long secs; }

    static String ymd(Calendar c) {
        return String.valueOf(c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100
                + c.get(Calendar.DAY_OF_MONTH));
    }
    static String today() { return ymd(Calendar.getInstance()); }
    static String daysAgo(int n) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -n);
        return ymd(c);
    }
    static String dayLabel(int n) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -n);
        return new SimpleDateFormat("EEE", T.locale()).format(c.getTime());
    }

    private static long num(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    /** {seconds, pages} for the given yyyymmdd. */
    static long[] day(String ymd) {
        long[] r = new long[2];
        String s = Prefs.get("d:" + ymd, null);
        if (s != null) {
            int i = s.indexOf(',');
            if (i > 0) { r[0] = num(s.substring(0, i)); r[1] = num(s.substring(i + 1)); }
        }
        return r;
    }

    static long bookSecs(String path) { return num(Prefs.get("t:" + path, "0")); }
    static long bookPages(String path) { return num(Prefs.get("p:" + path, "0")); }

    static void add(String path, long secs, int pages) {
        if (secs <= 0 && pages <= 0) return;
        String t = today();
        long[] d = day(t);
        Prefs.putMany(new String[]{
                "d:" + t, (d[0] + secs) + "," + (d[1] + pages),
                "t:" + path, String.valueOf(bookSecs(path) + secs),
                "p:" + path, String.valueOf(bookPages(path) + pages)});
    }

    /** Marks the book finished; returns true only the first time. */
    static boolean finished(String path) {
        if (Prefs.get("fin:" + path, null) == null) { Prefs.put("fin:" + path, today()); return true; }
        return false;
    }
    static boolean isFinished(String path) { return Prefs.get("fin:" + path, null) != null; }

    static int finishedCount() {
        int n = 0;
        for (String k : Prefs.all().keySet()) if (k.startsWith("fin:")) n++;
        return n;
    }

    // ------------------------------------------------------------------ goals
    static final String[] GOAL_MIN = {"0", "10", "15", "20", "30", "45", "60", "90", "120"};
    static final String[] GOAL_BOOKS = {"0", "6", "12", "18", "24", "36", "52"};

    static int goalMinutes() { return (int) num(Prefs.get("goal_min", "0")); }
    static int goalBooks() { return (int) num(Prefs.get("goal_books", "0")); }

    static void cycleGoal(String key, String[] vals) {
        String cur = Prefs.get(key, "0");
        int i = 0;
        for (int k = 0; k < vals.length; k++) if (vals[k].equals(cur)) i = k;
        Prefs.put(key, vals[(i + 1) % vals.length]);
    }

    /** Consecutive days on which the daily goal was reached (today may still be open). */
    static int goalStreak() {
        int g = goalMinutes();
        if (g <= 0) return 0;
        long need = g * 60L;
        int i = day(daysAgo(0))[0] >= need ? 0 : 1;
        int n = 0;
        while (n < 3650 && day(daysAgo(i))[0] >= need) { n++; i++; }
        return n;
    }

    static int goalDaysOfLast7() {
        int g = goalMinutes();
        if (g <= 0) return 0;
        int n = 0;
        for (int i = 0; i < 7; i++) if (day(daysAgo(i))[0] >= g * 60L) n++;
        return n;
    }

    static int booksThisYear() {
        String y = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        int n = 0;
        for (Map.Entry<String, ?> e : Prefs.all().entrySet()) {
            if (e.getKey().startsWith("fin:") && String.valueOf(e.getValue()).startsWith(y)) n++;
        }
        return n;
    }

    static String pace() {
        int g = goalBooks();
        if (g <= 0) return "";
        int done = booksThisYear();
        if (done >= g) return T.t("goal reached");
        Calendar c = Calendar.getInstance();
        double exp = g * (double) c.get(Calendar.DAY_OF_YEAR) / c.getActualMaximum(Calendar.DAY_OF_YEAR);
        if (done >= (int) Math.floor(exp)) return T.t("on track");
        return T.f("{0} behind", (int) Math.ceil(exp - done));
    }

    /** Consecutive days with at least a minute of reading (today may still be empty). */
    static int streak() {
        int i = day(daysAgo(0))[0] >= 60 ? 0 : 1;
        int n = 0;
        while (n < 3650 && day(daysAgo(i))[0] >= 60) { n++; i++; }
        return n;
    }

    /** {total seconds, total pages, active days}. */
    static long[] totals() {
        long s = 0, p = 0, a = 0;
        for (Map.Entry<String, ?> e : Prefs.all().entrySet()) {
            if (e.getKey().startsWith("d:")) {
                long[] d = day(e.getKey().substring(2));
                s += d[0]; p += d[1];
                if (d[0] > 0) a++;
            }
        }
        return new long[]{s, p, a};
    }

    static ArrayList<BookStat> top(int n) {
        ArrayList<BookStat> l = new ArrayList<BookStat>();
        for (Map.Entry<String, ?> e : Prefs.all().entrySet()) {
            if (e.getKey().startsWith("t:")) {
                BookStat b = new BookStat();
                b.path = e.getKey().substring(2);
                b.secs = num(String.valueOf(e.getValue()));
                if (b.secs > 0) l.add(b);
            }
        }
        Collections.sort(l, new Comparator<BookStat>() {
            public int compare(BookStat a, BookStat b) { return a.secs < b.secs ? 1 : (a.secs > b.secs ? -1 : 0); }
        });
        while (l.size() > n) l.remove(l.size() - 1);
        return l;
    }

    static String fmt(long secs) {
        if (secs < 60) return (secs > 0 ? "<1 " : "0 ") + T.t("min");
        long h = secs / 3600, m = (secs % 3600) / 60;
        if (h > 0) return h + T.t("h") + " " + (m < 10 ? "0" : "") + m + T.t("m");
        return m + " " + T.t("min");
    }

    static String fmtShort(long secs) {
        long m = secs / 60;
        if (m < 60) return m + T.t("m");
        return (m / 60) + T.t("h") + (m % 60 < 10 ? "0" : "") + (m % 60);
    }
}
