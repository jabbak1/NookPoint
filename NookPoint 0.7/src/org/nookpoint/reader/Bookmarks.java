package org.nookpoint.reader;

import java.util.ArrayList;

/** Per-book bookmarks stored in preferences: chapter, text offset, percent, time, snippet. */
class Bookmarks {
    static class BM {
        int chapter, offset, pct;
        long time;
        String snippet = "";
    }

    static ArrayList<BM> list(String path) {
        ArrayList<BM> r = new ArrayList<BM>();
        String s = Prefs.get("bm:" + path, "");
        if (s.length() == 0) return r;
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\t", 5);
            if (f.length < 4) continue;
            try {
                BM b = new BM();
                b.chapter = Integer.parseInt(f[0]);
                b.offset = Integer.parseInt(f[1]);
                b.pct = Integer.parseInt(f[2]);
                b.time = Long.parseLong(f[3]);
                b.snippet = f.length > 4 ? f[4] : "";
                r.add(b);
            } catch (Exception e) { }
        }
        return r;
    }

    private static void save(String path, ArrayList<BM> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            BM b = l.get(i);
            if (i > 0) sb.append('\n');
            sb.append(b.chapter).append('\t').append(b.offset).append('\t').append(b.pct).append('\t')
                    .append(b.time).append('\t').append(b.snippet.replace('\t', ' ').replace('\n', ' '));
        }
        Prefs.put("bm:" + path, sb.toString());
    }

    static void add(String path, int chapter, int offset, int pct, String snippet) {
        ArrayList<BM> l = list(path);
        BM b = new BM();
        b.chapter = chapter; b.offset = offset; b.pct = pct; b.time = System.currentTimeMillis();
        b.snippet = snippet == null ? "" : snippet;
        int i = 0;
        while (i < l.size() && (l.get(i).chapter < chapter || (l.get(i).chapter == chapter && l.get(i).offset <= offset))) i++;
        l.add(i, b);
        save(path, l);
    }

    static void remove(String path, int index) {
        ArrayList<BM> l = list(path);
        if (index >= 0 && index < l.size()) { l.remove(index); save(path, l); }
    }

    static boolean has(String path, int chapter, int from, int to) {
        ArrayList<BM> l = list(path);
        for (int i = 0; i < l.size(); i++) {
            BM b = l.get(i);
            if (b.chapter == chapter && b.offset >= from && b.offset < to) return true;
        }
        return false;
    }

    static void removeIn(String path, int chapter, int from, int to) {
        ArrayList<BM> l = list(path);
        for (int i = l.size() - 1; i >= 0; i--) {
            BM b = l.get(i);
            if (b.chapter == chapter && b.offset >= from && b.offset < to) l.remove(i);
        }
        save(path, l);
    }
}
