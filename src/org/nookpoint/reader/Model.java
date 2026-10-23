package org.nookpoint.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/** Styled run inside a paragraph (char range in Para.text). */
class Span {
    static final int BOLD = 1, ITALIC = 2, LINK = 4, SUP = 8, NOTE = 16;
    final int s, e, style;
    final String href;
    Span(int s, int e, int style, String href) { this.s = s; this.e = e; this.style = style; this.href = href; }
}

class Para {
    final String text;
    final int head;          // 0 = body text, 1..6 = heading level
    final int offset;        // char offset of this paragraph in the chapter text
    ArrayList<Span> spans;
    int align = -1;          // -1 default, 0 left, 1 center, 2 right
    int quote;               // indent level (blockquote / CSS margin)
    String label;            // list bullet / number
    boolean cont;            // continuation after <br>
    boolean noInd;           // book asks for no first-line indent
    String img, alt;         // picture paragraph: reference (zip path or "fb2:id") and alternative text

    Para(String text, int head, int offset) { this.text = text; this.head = head; this.offset = offset; }
    Para(String text, boolean head, int offset) { this(text, head ? 1 : 0, offset); }
}

class Chapter {
    final ArrayList<Para> paras;
    final HashMap<String, Integer> anchors;
    final HashSet<Integer> anchorOffsets = new HashSet<Integer>();

    Chapter(ArrayList<Para> paras, HashMap<String, Integer> anchors) {
        this.paras = paras;
        this.anchors = anchors;
        for (Integer v : anchors.values()) anchorOffsets.add(v);
    }

    int paraAt(int off) {
        int lo = 0, hi = paras.size() - 1, r = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (paras.get(mid).offset <= off) { r = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return r;
    }

    /** Text of the footnote / note that starts at the given anchor, or null. */
    String noteText(String frag) {
        if (frag == null) return null;
        Integer off = anchors.get(frag);
        if (off == null) return null;
        int i = paraAt(off.intValue());
        if (i < 0) return null;
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int k = i; k < paras.size() && count < 5 && sb.length() < 1200; k++) {
            Para p = paras.get(k);
            if (k > i && (anchorOffsets.contains(Integer.valueOf(p.offset)) || p.head > 0)) break;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(p.text);
            count++;
        }
        return sb.toString();
    }
}

class Seg {
    final String text; final int style; final String href; final boolean space; final float w;
    final int off, len;      // position of the source text in the chapter (for highlights)
    Seg(String text, int style, String href, boolean space, float w, int off, int len) {
        this.text = text; this.style = style; this.href = href; this.space = space; this.w = w;
        this.off = off; this.len = len;
    }
}

class Line {
    final Seg[] segs;
    final int offset, level;
    final float x, extra, space, h, base, before, labelX;
    final String label;
    int lip, pl;   // line index inside its paragraph, number of lines of the paragraph
    String img;    // picture line: reference; iw, ih = displayed size
    float iw, ih;

    Line(Seg[] segs, int offset, int level, float x, float extra, float space, float h, float base,
         float before, String label, float labelX) {
        this.segs = segs; this.offset = offset; this.level = level; this.x = x; this.extra = extra;
        this.space = space; this.h = h; this.base = base; this.before = before; this.label = label;
        this.labelX = labelX;
    }
}

class TocItem {
    final String title; final int chapter;
    TocItem(String title, int chapter) { this.title = title; this.chapter = chapter; }
}
