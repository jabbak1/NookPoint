package org.nookpoint.reader;

import android.graphics.Typeface;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

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
    boolean justify = true, indent = true, status = true, swap = false, touch = true, hyph = true, links = true, saveWords = true;
    int weight, left = 1, lpress;       // text weight 0/1/2, time-left mode 0/1/2, long press 0 dictionary / 1 menu
    float pgap = 0f;
    int battery = 1;               // 0 off, 1 icon, 2 percent
    FontSet fs;

    static final String[] BUNDLED = {"Lora", "IBM Plex Serif", "DejaVu Serif", "DejaVu Sans"};
    private static final String[] STEMS = {"Lora", "IBMPlexSerif", "DejaVuSerif", "DejaVuSans"};
    private static final String[] LANGS = {"Auto", "English", "\u0420\u0443\u0441\u0441\u043A\u0438\u0439", "Fran\u00E7ais", "Espa\u00F1ol"};

    static String[] fontList() {
        ArrayList<String> l = new ArrayList<String>();
        l.add("Serif"); l.add("Sans"); l.add("Mono");
        for (int i = 0; i < BUNDLED.length; i++) l.add(BUNDLED[i]);
        File[] fs = Prefs.fontsDir().listFiles();
        if (fs != null) {
            java.util.Arrays.sort(fs);
            for (int i = 0; i < fs.length; i++) {
                String n = fs[i].getName();
                String lo = n.toLowerCase();
                if (!(lo.endsWith(".ttf") || lo.endsWith(".otf"))) continue;
                String b = lo.substring(0, lo.length() - 4);
                if (b.endsWith("bold") || b.endsWith("italic") || b.endsWith("oblique")) continue;   // style variants
                l.add(n);
            }
        }
        return l.toArray(new String[l.size()]);
    }

    static Opt[] opts() {
        return new Opt[]{
            new Opt("lang", "Language", "Auto", LANGS),
            new Opt("size", "Text size", "24", SIZES),
            new Opt("font", "Font", "Lora", fontList()),
            new Opt("spacing", "Line spacing", "1.2", new String[]{"1.0", "1.1", "1.2", "1.3", "1.5"}),
            new Opt("margin", "Screen margin", "24", new String[]{"8", "16", "24", "32", "44"}),
            new Opt("justify", "Justify text", "On", ONOFF),
            new Opt("indent", "Paragraph indent", "On", ONOFF),
            new Opt("hyph", "Hyphenation", "On", ONOFF),
            new Opt("pgap", "Paragraph spacing", "None", new String[]{"None", "Small", "Medium"}),
            new Opt("links", "Underline links", "On", ONOFF),
            new Opt("status", "Status bar", "On", ONOFF),
            new Opt("bat", "Battery indicator", "Icon", new String[]{"Icon", "Percent", "Off"}),
            new Opt("left", "Time left in status bar", "Chapter", new String[]{"Off", "Chapter", "Book"}),
            new Opt("weight", "Text weight", "Normal", new String[]{"Normal", "Medium", "Bold"}),
            new Opt("lpress", "Long press on a word", "Dictionary", new String[]{"Dictionary", "Menu"}),
            new Opt("savewords", "Save looked-up words", "On", ONOFF),
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
        c.hyph = Prefs.get("hyph", "On").equals("On");
        c.links = Prefs.get("links", "On").equals("On");
        String pg = Prefs.get("pgap", "None");
        c.pgap = pg.equals("Small") ? 0.3f : (pg.equals("Medium") ? 0.6f : 0f);
        c.status = Prefs.get("status", "On").equals("On");
        c.swap = Prefs.get("swap", "Off").equals("On");
        c.touch = Prefs.get("touch", "On").equals("On");
        String wt = Prefs.get("weight", "Normal");
        c.weight = wt.equals("Bold") ? 2 : (wt.equals("Medium") ? 1 : 0);
        String lf = Prefs.get("left", "Chapter");
        c.left = lf.equals("Off") ? 0 : (lf.equals("Book") ? 2 : 1);
        c.lpress = Prefs.get("lpress", "Dictionary").equals("Menu") ? 1 : 0;
        c.saveWords = Prefs.get("savewords", "On").equals("On");
        String b = Prefs.get("bat", "Icon");
        c.battery = b.equals("Off") ? 0 : (b.equals("Percent") ? 2 : 1);
        c.fs = fontSet(Prefs.get("font", "Lora"));
        return c;
    }

    // ------------------------------------------------------------------ fonts
    private static final HashMap<String, FontSet> FONTS = new HashMap<String, FontSet>();
    private static Typeface uiReg, uiBold;

    /** Bundled DejaVu Sans for the interface, so Cyrillic and accents never depend on system fonts. */
    static Typeface uiFace(boolean bold) {
        try {
            if (uiReg == null) uiReg = Typeface.createFromAsset(Prefs.assets(), "fonts/DejaVuSans-Regular.ttf");
            if (uiBold == null) uiBold = Typeface.createFromAsset(Prefs.assets(), "fonts/DejaVuSans-Bold.ttf");
        } catch (Throwable t) {
            return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
        return bold ? uiBold : uiReg;
    }

    private static Typeface asset(String stem, String style) {
        try {
            return Typeface.createFromAsset(Prefs.assets(), "fonts/" + stem + "-" + style + ".ttf");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Typeface file(File dir, String stem, String style, String ext) {
        File f = new File(dir, stem + "-" + style + ext);
        if (!f.exists()) return null;
        try { return Typeface.createFromFile(f); } catch (Throwable t) { return null; }
    }

    private static void fill(FontSet fs) {
        Typeface reg = fs.f[0] != null ? fs.f[0] : Typeface.SERIF;
        fs.f[0] = reg;
        if (fs.f[1] == null) fs.f[1] = Typeface.create(reg, Typeface.BOLD);
        if (fs.f[2] == null) fs.f[2] = Typeface.create(reg, Typeface.ITALIC);
        if (fs.f[3] == null) fs.f[3] = Typeface.create(reg, Typeface.BOLD_ITALIC);
    }

    static synchronized FontSet fontSet(String name) {
        FontSet fs = FONTS.get(name);
        if (fs != null) return fs;
        fs = new FontSet();
        if (name.equals("Serif") || name.equals("Sans") || name.equals("Mono")) {
            Typeface base = name.equals("Serif") ? Typeface.SERIF : (name.equals("Sans") ? Typeface.SANS_SERIF : Typeface.MONOSPACE);
            fs.f[0] = base;
        } else {
            int bi = -1;
            for (int i = 0; i < BUNDLED.length; i++) if (BUNDLED[i].equals(name)) bi = i;
            if (bi >= 0) {
                fs.f[0] = asset(STEMS[bi], "Regular");
                fs.f[1] = asset(STEMS[bi], "Bold");
                fs.f[2] = asset(STEMS[bi], "Italic");
                fs.f[3] = asset(STEMS[bi], "BoldItalic");
            } else {
                File dir = Prefs.fontsDir();
                try { fs.f[0] = Typeface.createFromFile(new File(dir, name)); } catch (Throwable t) { fs.f[0] = null; }
                int dot = name.lastIndexOf('.');
                String ext = dot >= 0 ? name.substring(dot) : ".ttf";
                String stem = (dot >= 0 ? name.substring(0, dot) : name).replaceAll("(?i)[-_ ]?regular$", "");
                fs.f[1] = file(dir, stem, "Bold", ext);
                fs.f[2] = file(dir, stem, "Italic", ext);
                if (fs.f[2] == null) fs.f[2] = file(dir, stem, "Oblique", ext);
                fs.f[3] = file(dir, stem, "BoldItalic", ext);
                if (fs.f[3] == null) fs.f[3] = file(dir, stem, "BoldOblique", ext);
            }
        }
        fill(fs);
        FONTS.put(name, fs);
        return fs;
    }
}
