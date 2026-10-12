package org.nookpoint.reader;

import android.graphics.Paint;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Typesetting: styled words, greedy line breaking with hyphenation, justification, alignment,
 * hanging list labels, indented quotes, no stranded prepositions / dashes, widow/orphan control.
 */
class Layouter {
    static class Params {
        float width = 500, height = 700, indent = 36, spacing = 1.2f, pgap;
        boolean justify = true, hyph = true;
    }

    static class Result {
        ArrayList<Line> lines;
        int[] pageStart;
    }

    private static final float QI = 26f, LABEL_W = 36f;
    private static final String[] GLUE2 = {"\u043D\u0430", "\u043F\u043E", "\u0437\u0430", "\u0438\u0437",
            "\u043E\u0442", "\u0434\u043E", "\u043D\u0435", "\u043D\u043E", "\u043E\u0431", "\u0432\u043E",
            "\u043A\u043E", "\u0441\u043E", "\u043D\u0438", "\u043D\u0443"};

    private static class W {
        final int s, e;
        final float w;
        boolean glue;
        W(int s, int e, float w) { this.s = s; this.e = e; this.w = w; }
    }

    static Result layout(Chapter ch, Fonts f, Params prm) {
        ArrayList<Line> out = new ArrayList<Line>();
        float bodyLH = f.paint(0, 0).getFontSpacing() * prm.spacing;
        boolean prevHead = true;
        for (int i = 0; i < ch.paras.size(); i++) {
            Para p = ch.paras.get(i);
            wrapPara(out, p, f, prm, bodyLH, prevHead);
            prevHead = p.head > 0;
        }
        if (out.size() == 0) {
            Paint b = f.paint(0, 0);
            String msg = "(no text in this section)";
            Seg[] s = {new Seg(msg, 0, null, false, b.measureText(msg))};
            Line l = new Line(s, 0, 0, 0, 0, b.measureText(" "), bodyLH, bodyLH * 0.75f, 0, null, 0);
            l.pl = 1;
            out.add(l);
        }
        Result r = new Result();
        r.lines = out;
        r.pageStart = paginate(out, prm.height);
        return r;
    }

    // ------------------------------------------------------------------ pagination
    static int[] paginate(ArrayList<Line> lines, float height) {
        ArrayList<Integer> st = new ArrayList<Integer>();
        st.add(Integer.valueOf(0));
        int first = 0, i = 0, n = lines.size();
        float y = 0;
        while (i < n) {
            Line l = lines.get(i);
            float need = (i == first ? 0 : l.before) + l.h;
            if (y + need > height && i > first) {
                int j = i;
                if (j - 1 > first && l.lip == 1 && lines.get(j - 1).lip == 0) j--;                 // orphan
                else if (j - 1 > first && l.lip >= 2 && l.lip == l.pl - 1) j--;                    // widow
                while (j - 1 > first && lines.get(j - 1).level > 0) j--;                            // keep heading with text
                first = j;
                st.add(Integer.valueOf(j));
                y = 0;
                i = j;
                continue;
            }
            y += need;
            i++;
        }
        int[] r = new int[st.size()];
        for (int k = 0; k < r.length; k++) r[k] = st.get(k).intValue();
        return r;
    }

    // ------------------------------------------------------------------ paragraph
    private static float width(Fonts f, Para p, int[] spanOf, int lvl, int s, int e) {
        float sum = 0;
        int a = s;
        while (a < e) {
            int sp = spanOf == null ? -1 : spanOf[a];
            int b = a + 1;
            while (b < e && (spanOf == null ? -1 : spanOf[b]) == sp) b++;
            int st = sp < 0 ? 0 : p.spans.get(sp).style;
            sum += f.paint(st, lvl).measureText(p.text, a, b);
            a = b;
        }
        return sum;
    }

    private static boolean isDash(String s) {
        return s.equals("\u2014") || s.equals("\u2013") || s.equals("-");
    }

    private static boolean isGlueWord(String s) {
        if (s.length() == 1) return Character.isLetter(s.charAt(0));
        if (s.length() == 2) {
            String l = s.toLowerCase();
            for (int i = 0; i < GLUE2.length; i++) if (GLUE2[i].equals(l)) return true;
        }
        return false;
    }

    private static void wrapPara(ArrayList<Line> out, Para p, Fonts f, Params prm, float bodyLH, boolean prevHead) {
        int lvl = p.head;
        Paint base = f.paint(0, lvl);
        float lh = base.getFontSpacing() * prm.spacing;
        Paint.FontMetrics fm = base.getFontMetrics();
        float baseline = (lh - (fm.descent - fm.ascent)) / 2 - fm.ascent;
        String t = p.text;
        int n = t.length();

        float left = p.quote * QI;
        float gutter = p.label != null ? LABEL_W : 0;
        float avail = prm.width - left - gutter - (p.quote > 0 ? 14f : 0);
        int align = p.align >= 0 ? p.align : ((lvl == 1 || lvl == 2) ? 1 : 0);
        boolean justify = prm.justify && align == 0 && lvl == 0;
        boolean indentFirst = lvl == 0 && align == 0 && prm.indent > 0 && !p.noInd && !p.cont && p.label == null
                && !prevHead && p.quote == 0;
        float before;
        if (out.isEmpty()) before = 0;
        else if (lvl > 0) before = lh * 0.9f;
        else if (prevHead) before = bodyLH * 0.4f;
        else if (p.cont || p.label != null) before = 0;
        else if (p.noInd) before = Math.max(prm.pgap, 0.35f) * bodyLH;
        else before = prm.pgap * bodyLH;

        int[] spanOf = null;
        if (p.spans != null) {
            spanOf = new int[n];
            Arrays.fill(spanOf, -1);
            for (int k = 0; k < p.spans.size(); k++) {
                Span sp = p.spans.get(k);
                for (int i = sp.s; i < sp.e && i < n; i++) spanOf[i] = k;
            }
        }

        ArrayList<W> words = new ArrayList<W>();
        int i = 0;
        while (i < n) {
            while (i < n && t.charAt(i) == ' ') i++;
            if (i >= n) break;
            int s = i;
            while (i < n && t.charAt(i) != ' ') i++;
            words.add(new W(s, i, width(f, p, spanOf, lvl, s, i)));
        }
        for (int k = 0; k < words.size(); k++) {
            W w = words.get(k);
            String s = t.substring(w.s, w.e);
            if (k < words.size() - 1 && isGlueWord(s)) w.glue = true;
            if (isDash(s)) {
                if (k > 0) words.get(k - 1).glue = true;
                else if (words.size() > 1) w.glue = true;
            }
        }

        float sp = base.measureText(" ");
        float hw = base.measureText("-");
        int wi = 0;
        boolean first = true;
        int startIdx = out.size();
        while (wi < words.size()) {
            float ind = (first && indentFirst) ? prm.indent : 0;
            float lineAvail = avail - ind;
            ArrayList<W> lw = new ArrayList<W>();
            float used = 0;
            boolean hyphen = false;
            while (wi < words.size()) {
                W w = words.get(wi);
                float add = (lw.size() > 0 ? sp : 0) + w.w;
                if (used + add <= lineAvail + 0.5f) { lw.add(w); used += add; wi++; continue; }
                float room = lineAvail - used - (lw.size() > 0 ? sp : 0);
                int cut = 0;
                if (prm.hyph && room > hw * 3) cut = findCut(f, p, spanOf, lvl, w, room, hw);
                if (cut != 0) {
                    int k = Math.abs(cut);
                    W a = new W(w.s, k, width(f, p, spanOf, lvl, w.s, k));
                    used += (lw.size() > 0 ? sp : 0) + a.w;
                    lw.add(a);
                    hyphen = cut > 0;
                    if (hyphen) used += hw;
                    W r = new W(k, w.e, width(f, p, spanOf, lvl, k, w.e));
                    r.glue = w.glue;
                    words.set(wi, r);
                    break;
                }
                if (lw.size() == 0) {                       // a word wider than the line: split by characters
                    int k = w.e - 1;
                    while (k > w.s + 1 && width(f, p, spanOf, lvl, w.s, k) + hw > lineAvail) k--;
                    if (k <= w.s) k = w.s + 1;
                    if (k >= w.e) { lw.add(w); used = w.w; wi++; break; }
                    W a = new W(w.s, k, width(f, p, spanOf, lvl, w.s, k));
                    lw.add(a);
                    used = a.w + hw;
                    hyphen = true;
                    W r = new W(k, w.e, width(f, p, spanOf, lvl, k, w.e));
                    r.glue = w.glue;
                    words.set(wi, r);
                    break;
                }
                while (lw.size() > 1 && lw.get(lw.size() - 1).glue) {   // do not strand prepositions / dashes
                    W last = lw.remove(lw.size() - 1);
                    used -= sp + last.w;
                    wi--;
                }
                break;
            }

            boolean lastLine = wi >= words.size();
            ArrayList<Seg> segs = new ArrayList<Seg>();
            for (int j = 0; j < lw.size(); j++) {
                W w = lw.get(j);
                int a = w.s;
                boolean firstPiece = true;
                while (a < w.e) {
                    int s0 = spanOf == null ? -1 : spanOf[a];
                    int b = a + 1;
                    while (b < w.e && (spanOf == null ? -1 : spanOf[b]) == s0) b++;
                    Span span = s0 < 0 ? null : p.spans.get(s0);
                    int st = span == null ? 0 : span.style;
                    String txt = t.substring(a, b);
                    float wd = f.paint(st, lvl).measureText(t, a, b);
                    if (b >= w.e && j == lw.size() - 1 && hyphen) { txt = txt + "-"; wd += hw; }
                    segs.add(new Seg(txt, st, span == null ? null : span.href, j > 0 && firstPiece, wd));
                    firstPiece = false;
                    a = b;
                }
            }
            int spaces = lw.size() - 1;
            float extra = 0;
            if (justify && !lastLine && spaces > 0) {
                float e = (lineAvail - used) / spaces;
                if (e >= 0 && e <= sp * 3f) extra = e;
            }
            float natural = used + extra * spaces;
            float x0 = left + gutter + ind;
            if (align == 1) x0 = left + gutter + (avail - natural) / 2;
            else if (align == 2) x0 = left + gutter + avail - natural;
            Line l = new Line(segs.toArray(new Seg[segs.size()]), p.offset + lw.get(0).s, lvl, x0, extra, sp, lh,
                    baseline, first ? before : 0, first ? p.label : null, left + 6);
            out.add(l);
            first = false;
        }
        int cnt = out.size() - startIdx;
        for (int k = 0; k < cnt; k++) {
            Line l = out.get(startIdx + k);
            l.lip = k;
            l.pl = cnt;
        }
    }

    private static int findCut(Fonts f, Para p, int[] spanOf, int lvl, W w, float room, float hw) {
        int[] pts = Hyph.points(p.text, w.s, w.e);
        for (int i = pts.length - 1; i >= 0; i--) {
            int c = pts[i], k = Math.abs(c);
            float need = width(f, p, spanOf, lvl, w.s, k) + (c > 0 ? hw : 0);
            if (need <= room) return c;
        }
        return 0;
    }
}
