package org.nookpoint.reader;

import java.util.ArrayList;

/** Words looked up in the dictionary (newest first), for review and export. */
class WordList {
    static final int MAX = 500;

    static class W {
        String word = "", head = "", def = "", ctx = "", book = "";
        long time;
    }

    static ArrayList<W> list() {
        ArrayList<W> r = new ArrayList<W>();
        String s = Prefs.get("wordlist", "");
        if (s.length() == 0) return r;
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\t", 6);
            if (f.length < 6) continue;
            try {
                W w = new W();
                w.word = Esc.unesc(f[0]);
                w.head = Esc.unesc(f[1]);
                w.def = Esc.unesc(f[2]);
                w.ctx = Esc.unesc(f[3]);
                w.book = Esc.unesc(f[4]);
                w.time = Long.parseLong(f[5]);
                r.add(w);
            } catch (Exception e) { }
        }
        return r;
    }

    static void save(ArrayList<W> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < MAX; i++) {
            W w = l.get(i);
            if (i > 0) sb.append('\n');
            sb.append(Esc.esc(w.word)).append('\t').append(Esc.esc(w.head)).append('\t').append(Esc.esc(w.def)).append('\t')
                    .append(Esc.esc(w.ctx)).append('\t').append(Esc.esc(w.book)).append('\t').append(w.time);
        }
        Prefs.put("wordlist", sb.toString());
    }

    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) + "..." : s; }

    static void add(String word, String head, String def, String ctx, String book) {
        ArrayList<W> l = list();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).word.equalsIgnoreCase(word)) l.remove(i);
        W w = new W();
        w.word = word;
        w.head = head == null ? "" : head;
        w.def = cut(def == null ? "" : def, 600);
        w.ctx = cut(ctx == null ? "" : ctx, 300);
        w.book = book == null ? "" : book;
        w.time = System.currentTimeMillis();
        l.add(0, w);
        save(l);
    }

    static void remove(int index) {
        ArrayList<W> l = list();
        if (index >= 0 && index < l.size()) { l.remove(index); save(l); }
    }

    static void clear() { Prefs.put("wordlist", ""); }
}
