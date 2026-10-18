package org.nookpoint.reader;

import java.util.HashMap;

/** Tiny CSS reader: .class, tag and tag.class selectors, a few properties that matter for e-ink reading. */
class Css {
    static class Props {
        int bold = -1, ital = -1, align = -1, noInd = -1, indent = -1, hide = -1;
        void merge(Props o) {
            if (o.bold != -1) bold = o.bold;
            if (o.ital != -1) ital = o.ital;
            if (o.align != -1) align = o.align;
            if (o.noInd != -1) noInd = o.noInd;
            if (o.indent != -1) indent = o.indent;
            if (o.hide != -1) hide = o.hide;
        }
    }

    private HashMap<String, Props> rules = new HashMap<String, Props>();

    Css copy() {
        Css c = new Css();
        c.rules = new HashMap<String, Props>(rules);
        return c;
    }

    void addSheet(String css) {
        if (css == null) return;
        css = stripComments(css);
        int i = 0, n = css.length();
        while (i < n) {
            int b = css.indexOf('{', i);
            if (b < 0) break;
            int e = matchBrace(css, b);
            String sel = css.substring(i, b).trim();
            String body = css.substring(b + 1, Math.min(e, n));
            i = e + 1;
            if (sel.startsWith("@")) continue;
            Props p = new Props();
            parseDecl(body, p);
            String[] ss = sel.split(",");
            for (int k = 0; k < ss.length; k++) {
                String s = ss[k].trim().toLowerCase();
                if (s.length() == 0 || !simple(s)) continue;
                Props old = rules.get(s);
                if (old == null) rules.put(s, p);
                else { Props m = new Props(); m.merge(old); m.merge(p); rules.put(s, m); }
            }
        }
    }

    Props lookup(String tag, String cls, String style) {
        Props r = new Props();
        Props t = rules.get(tag);
        if (t != null) r.merge(t);
        if (cls != null) {
            String[] cs = cls.trim().split("\\s+");
            for (int i = 0; i < cs.length; i++) {
                String c = cs[i].toLowerCase();
                Props a = rules.get("." + c);
                if (a != null) r.merge(a);
                Props b = rules.get(tag + "." + c);
                if (b != null) r.merge(b);
            }
        }
        if (style != null) {
            Props s = new Props();
            parseDecl(style, s);
            r.merge(s);
        }
        return r;
    }

    // ------------------------------------------------------------------
    static void parseDecl(String body, Props p) {
        String[] ds = body.split(";");
        for (int i = 0; i < ds.length; i++) {
            int c = ds[i].indexOf(':');
            if (c <= 0) continue;
            String name = ds[i].substring(0, c).trim().toLowerCase();
            String v = ds[i].substring(c + 1).trim().toLowerCase();
            int imp = v.indexOf('!');
            if (imp >= 0) v = v.substring(0, imp).trim();
            if (name.equals("font-style")) {
                if (v.indexOf("italic") >= 0 || v.indexOf("oblique") >= 0) p.ital = 1;
                else if (v.indexOf("normal") >= 0) p.ital = 0;
            } else if (name.equals("font-weight")) {
                if (v.indexOf("bold") >= 0) p.bold = 1;
                else if (v.equals("normal")) p.bold = 0;
                else {
                    int w = (int) numf(v);
                    if (w >= 600) p.bold = 1; else if (w > 0) p.bold = 0;
                }
            } else if (name.equals("text-align")) {
                if (v.startsWith("center")) p.align = 1;
                else if (v.startsWith("right") || v.startsWith("end")) p.align = 2;
            } else if (name.equals("text-indent")) {
                if (v.length() > 0 && Character.isDigit(v.charAt(0)) && numf(v) == 0f) p.noInd = 1;
            } else if (name.equals("margin-left") || name.equals("padding-left")) {
                if (bigIndent(v)) p.indent = 1;
            } else if (name.equals("display")) {
                if (v.startsWith("none")) p.hide = 1;
            }
        }
    }

    private static float numf(String v) {
        int i = 0;
        while (i < v.length() && (Character.isDigit(v.charAt(i)) || v.charAt(i) == '.')) i++;
        if (i == 0) return -1f;
        try { return Float.parseFloat(v.substring(0, i)); } catch (Exception e) { return -1f; }
    }

    private static boolean bigIndent(String v) {
        float f = numf(v);
        if (f < 0) return false;
        if (v.endsWith("%")) return f >= 8;
        if (v.endsWith("em")) return f >= 1.5f;
        if (v.endsWith("px")) return f >= 20;
        if (v.endsWith("pt")) return f >= 15;
        return false;
    }

    private static boolean simple(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-')) return false;
        }
        return true;
    }

    private static int matchBrace(String s, int open) {
        int d = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') d++;
            else if (c == '}') { d--; if (d == 0) return i; }
        }
        return s.length();
    }

    private static String stripComments(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int a = s.indexOf("/*", i);
            if (a < 0) { sb.append(s, i, s.length()); break; }
            sb.append(s, i, a);
            int b = s.indexOf("*/", a + 2);
            if (b < 0) break;
            i = b + 2;
        }
        return sb.toString();
    }
}
