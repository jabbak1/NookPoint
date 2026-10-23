package org.nookpoint.reader;

import java.util.ArrayList;

/** Per-book highlights with optional notes, stored in preferences. */
class Highlights {
    static class H {
        int chapter, start, end, pct;
        long time;
        String text = "", note = "";
    }

    static ArrayList<H> list(String path) {
        ArrayList<H> r = new ArrayList<H>();
        String s = Prefs.get("hl:" + path, "");
        if (s.length() == 0) return r;
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\t", 7);
            if (f.length < 7) continue;
            try {
                H h = new H();
                h.chapter = Integer.parseInt(f[0]);
                h.start = Integer.parseInt(f[1]);
                h.end = Integer.parseInt(f[2]);
                h.pct = Integer.parseInt(f[3]);
                h.time = Long.parseLong(f[4]);
                h.text = Esc.unesc(f[5]);
                h.note = Esc.unesc(f[6]);
                r.add(h);
            } catch (Exception e) { }
        }
        return r;
    }

    static void save(String path, ArrayList<H> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            H h = l.get(i);
            if (i > 0) sb.append('\n');
            sb.append(h.chapter).append('\t').append(h.start).append('\t').append(h.end).append('\t').append(h.pct)
                    .append('\t').append(h.time).append('\t').append(Esc.esc(h.text)).append('\t').append(Esc.esc(h.note));
        }
        Prefs.put("hl:" + path, sb.toString());
    }

    /** Adds (or replaces a highlight that starts at the same place). */
    static H add(String path, H h) {
        ArrayList<H> l = list(path);
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).chapter == h.chapter && l.get(i).start == h.start) {
                if (h.note.length() == 0) h.note = l.get(i).note;
                l.remove(i);
            }
        }
        int i = 0;
        while (i < l.size() && (l.get(i).chapter < h.chapter || (l.get(i).chapter == h.chapter && l.get(i).start <= h.start))) i++;
        l.add(i, h);
        save(path, l);
        return h;
    }

    static void remove(String path, int index) {
        ArrayList<H> l = list(path);
        if (index >= 0 && index < l.size()) { l.remove(index); save(path, l); }
    }

    static void setNote(String path, int index, String note) {
        ArrayList<H> l = list(path);
        if (index >= 0 && index < l.size()) { l.get(index).note = note; save(path, l); }
    }

    /** {start, end, hasNote} of the highlights in one chapter. */
    static int[][] forChapter(String path, int chapter) {
        ArrayList<H> l = list(path);
        int n = 0;
        for (int i = 0; i < l.size(); i++) if (l.get(i).chapter == chapter) n++;
        int[][] r = new int[n][];
        int k = 0;
        for (int i = 0; i < l.size(); i++) {
            H h = l.get(i);
            if (h.chapter == chapter) r[k++] = new int[]{h.start, h.end, h.note.length() > 0 ? 1 : 0};
        }
        return r;
    }
}
