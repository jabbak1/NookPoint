package org.nookpoint.reader;

import android.graphics.Typeface;
import java.io.File;
import java.util.ArrayList;

class Cfg {
    static class Opt {
        final String key, label, def; final String[] vals;
        Opt(String key, String label, String def, String[] vals) {
            this.key = key; this.label = label; this.def = def; this.vals = vals;
        }
        String get() { return Prefs.get(key, def); }
        void cycle() {
            String cur = get();
            int i = 0;
            for (int k = 0; k < vals.length; k++) if (vals[k].equals(cur)) i = k;
            Prefs.put(key, vals[(i + 1) % vals.length]);
        }
    }

    static final String[] SIZES = {"18", "20", "22", "24", "26", "28", "32", "36"};
    private static final String[] ONOFF = {"On", "Off"};

    int size = 24, margin = 24, refresh = 10;
    float spacing = 1.2f;
    boolean justify = true, indent = true, status = true, swap = false, touch = true;
    Typeface tf = Typeface.SERIF;

    static String[] fontList() {
        ArrayList<String> l = new ArrayList<String>();
        l.add("Serif"); l.add("Sans"); l.add("Mono");
        File[] fs = Prefs.fontsDir().listFiles();
        if (fs != null) {
            for (int i = 0; i < fs.length; i++) {
                String n = fs[i].getName();
                String lo = n.toLowerCase();
                if (lo.endsWith(".ttf") || lo.endsWith(".otf")) l.add(n);
            }
        }
        return l.toArray(new String[l.size()]);
    }

    static Opt[] opts() {
        return new Opt[]{
            new Opt("size", "Text size", "24", SIZES),
            new Opt("font", "Font (extra: put .ttf in /fonts)", "Serif", fontList()),
            new Opt("spacing", "Line spacing", "1.2", new String[]{"1.0", "1.1", "1.2", "1.3", "1.5"}),
            new Opt("margin", "Screen margin", "24", new String[]{"8", "16", "24", "32", "44"}),
            new Opt("justify", "Justify text", "On", ONOFF),
            new Opt("indent", "Paragraph indent", "On", ONOFF),
            new Opt("status", "Status bar", "On", ONOFF),
            new Opt("refresh", "Full refresh every N pages", "10", new String[]{"Off", "5", "10", "15", "30"}),
            new Opt("swap", "Swap page buttons", "Off", new String[]{"Off", "On"}),
            new Opt("touch", "Touch page turn (edges)", "On", ONOFF)
        };
    }

    static String sig() {
        StringBuilder sb = new StringBuilder();
        Opt[] o = opts();
        for (int i = 0; i < o.length; i++) sb.append(o[i].get()).append('|');
        return sb.toString();
    }

    static void stepSize(int d) {
        String cur = Prefs.get("size", "24");
        int i = 3;
        for (int k = 0; k < SIZES.length; k++) if (SIZES[k].equals(cur)) i = k;
        i = Math.max(0, Math.min(SIZES.length - 1, i + d));
        Prefs.put("size", SIZES[i]);
    }

    static Cfg load() {
        Cfg c = new Cfg();
        try {
            c.size = Integer.parseInt(Prefs.get("size", "24"));
            c.margin = Integer.parseInt(Prefs.get("margin", "24"));
            c.spacing = Float.parseFloat(Prefs.get("spacing", "1.2"));
            String r = Prefs.get("refresh", "10");
            c.refresh = r.equals("Off") ? 0 : Integer.parseInt(r);
        } catch (Exception e) { }
        c.justify = Prefs.get("justify", "On").equals("On");
        c.indent = Prefs.get("indent", "On").equals("On");
        c.status = Prefs.get("status", "On").equals("On");
        c.swap = Prefs.get("swap", "Off").equals("On");
        c.touch = Prefs.get("touch", "On").equals("On");
        c.tf = typeface(Prefs.get("font", "Serif"));
        return c;
    }

    static Typeface typeface(String name) {
        if (name.equals("Serif")) return Typeface.SERIF;
        if (name.equals("Sans")) return Typeface.SANS_SERIF;
        if (name.equals("Mono")) return Typeface.MONOSPACE;
        try {
            return Typeface.createFromFile(new File(Prefs.fontsDir(), name));
        } catch (Exception e) {
            return Typeface.SERIF;
        }
    }
}
