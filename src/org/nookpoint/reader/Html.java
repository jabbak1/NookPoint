package org.nookpoint.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/** Tolerant XHTML -> styled paragraphs (bold/italic/links/anchors/lists/quotes/alignment, simple CSS). */
class Html {
    private static class Frame { String name; int pb, pi, ps, pa, pq, pn, ph; }

    private static final HashSet<String> BLOCK = new HashSet<String>(Arrays.asList(
            "p", "div", "li", "tr", "blockquote", "section", "article", "ul", "ol", "table", "hr", "dt", "dd",
            "dl", "pre", "body", "figure", "figcaption", "aside", "header", "footer", "nav", "center",
            "h1", "h2", "h3", "h4", "h5", "h6"));
    private static final HashSet<String> VOID = new HashSet<String>(Arrays.asList(
            "br", "img", "hr", "meta", "link", "input", "col", "wbr"));

    private final ArrayList<Para> out = new ArrayList<Para>();
    private final HashMap<String, Integer> anchors = new HashMap<String, Integer>();
    private final StringBuilder cur = new StringBuilder();
    private final ArrayList<Span> runs = new ArrayList<Span>();
    private final ArrayList<Frame> stack = new ArrayList<Frame>();
    private final ArrayList<int[]> lists = new ArrayList<int[]>();
    private int runStart, off, headLevel, align = -1, quote, hide;
    private boolean bold, italic, sup, noInd, cont, linkNoteref;
    private String href, pendingLabel;
    private Css css;

    static Chapter parse(String s, Css base) { return parse(s, base, ""); }

    static String percentDecode(String s) {
        if (s.indexOf('%') < 0) return s;
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                try { bo.write(Integer.parseInt(s.substring(i + 1, i + 3), 16)); i += 2; continue; } catch (Exception e) { }
            }
            try { byte[] b = String.valueOf(c).getBytes("UTF-8"); bo.write(b, 0, b.length); } catch (Exception e) { }
        }
        try { return new String(bo.toByteArray(), "UTF-8"); } catch (Exception e) { return s; }
    }

    /** Joins a relative href to a directory inside an archive ("../" handled, #fragment dropped). */
    static String resolvePath(String dir, String href) {
        int h = href.indexOf('#');
        if (h >= 0) href = href.substring(0, h);
        href = percentDecode(href);
        String full = href.startsWith("/") ? href.substring(1) : dir + href;
        String[] parts = full.split("/");
        ArrayList<String> st = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.equals("..")) { if (st.size() > 0) st.remove(st.size() - 1); }
            else if (p.length() > 0 && !p.equals(".")) st.add(p);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < st.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(st.get(i));
        }
        return sb.toString();
    }

    private String dir = "";

    static Chapter parse(String s, Css base, String dir) {
        Html h = new Html();
        h.dir = dir == null ? "" : dir;
        h.css = base != null ? base.copy() : new Css();
        h.run(s);
        h.flush();
        return new Chapter(h.out, h.anchors);
    }

    /** hrefs of linked stylesheets. */
    static ArrayList<String> cssLinks(String s) {
        ArrayList<String> r = new ArrayList<String>();
        int i = 0;
        while (true) {
            i = find(s, "<link", i);
            if (i < 0) break;
            int e = s.indexOf('>', i);
            if (e < 0) break;
            String tag = s.substring(i + 1, e);
            String rel = attr(tag, "rel"), h = attr(tag, "href");
            if (h != null && (rel == null || rel.toLowerCase().indexOf("stylesheet") >= 0)) r.add(h);
            i = e + 1;
        }
        return r;
    }

    static String attr(String tag, String name) {
        int n = tag.length(), i = 0;
        while (i < n) {
            int k = tag.indexOf(name, i);
            if (k < 0) return null;
            if (k > 0 && Character.isWhitespace(tag.charAt(k - 1))) {
                int j = k + name.length();
                while (j < n && tag.charAt(j) == ' ') j++;
                if (j < n && tag.charAt(j) == '=') {
                    j++;
                    while (j < n && tag.charAt(j) == ' ') j++;
                    if (j < n) {
                        char q = tag.charAt(j);
                        if (q == '"' || q == '\'') {
                            int e = tag.indexOf(q, j + 1);
                            return e < 0 ? tag.substring(j + 1) : tag.substring(j + 1, e);
                        }
                        int e = j;
                        while (e < n && !Character.isWhitespace(tag.charAt(e)) && tag.charAt(e) != '/') e++;
                        return tag.substring(j, e);
                    }
                }
            }
            i = k + 1;
        }
        return null;
    }

    static int find(String s, String pat, int from) {
        int m = pat.length();
        int i = s.indexOf('<', from);
        while (i >= 0) {
            if (s.regionMatches(true, i, pat, 0, m)) return i;
            i = s.indexOf('<', i + 1);
        }
        return -1;
    }

    // ------------------------------------------------------------------ paragraphs
    private void endRun() {
        int e = cur.length();
        int st = (bold ? Span.BOLD : 0) | (italic ? Span.ITALIC : 0) | (sup ? Span.SUP : 0)
                | (href != null ? Span.LINK : 0);
        if (e > runStart && st != 0) {
            if (href != null && (linkNoteref || noteLike(cur.substring(runStart, e), sup))) st |= Span.NOTE;
            runs.add(new Span(runStart, e, st, href));
        }
        runStart = e;
    }

    private static boolean noteLike(String t, boolean sup) {
        t = t.trim();
        if (t.length() == 0 || t.length() > 5) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c) || c == '*' || c == '\u2020' || c == '\u2021') return true;
        }
        return sup;
    }

    private void flush() {
        endRun();
        int e = cur.length();
        while (e > 0 && cur.charAt(e - 1) == ' ') e--;
        int b = 0;
        while (b < e && cur.charAt(b) == ' ') b++;
        if (e > b) {
            String t = cur.substring(b, e);
            Para p = new Para(t, headLevel, off);
            p.align = align;
            p.quote = quote + Math.max(0, lists.size() - 1);
            p.label = pendingLabel;
            p.cont = cont;
            p.noInd = noInd;
            for (int i = 0; i < runs.size(); i++) {
                Span sp = runs.get(i);
                int s2 = Math.max(sp.s - b, 0), e2 = Math.min(sp.e - b, t.length());
                if (e2 > s2) {
                    if (p.spans == null) p.spans = new ArrayList<Span>();
                    p.spans.add(new Span(s2, e2, sp.style, sp.href));
                }
            }
            out.add(p);
            off += t.length() + 1;
            pendingLabel = null;
            cont = false;
        }
        cur.setLength(0);
        runs.clear();
        runStart = 0;
    }

    private void add(char c) {
        if (hide > 0) return;
        if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
            if (cur.length() > 0 && cur.charAt(cur.length() - 1) != ' ') cur.append(' ');
        } else if (c == 0xFEFF || c == 0x200B || c == 0xAD || c == 0x200C || c == 0x200D) {
            // invisible, drop
        } else {
            cur.append(c);   // a no-break space (U+00A0) is kept: it must not wrap
        }
    }

    private void addStr(String s) {
        for (int i = 0; i < s.length(); i++) add(s.charAt(i));
    }

    // ------------------------------------------------------------------ tags
    private static boolean isHeading(String n) {
        return n.length() == 2 && n.charAt(0) == 'h' && n.charAt(1) >= '1' && n.charAt(1) <= '6';
    }

    private void openTag(String name, String tag, boolean selfClose) {
        if (BLOCK.contains(name)) flush();
        String id = attr(tag, "id");
        if (id == null && name.equals("a")) id = attr(tag, "name");
        if (id != null && id.length() > 0 && !anchors.containsKey(id)) anchors.put(id, off + cur.length());
        if (name.equals("br")) { flush(); cont = true; return; }
        if (name.equals("img") || name.equals("image")) {
            String src = attr(tag, "src");
            if (src == null) src = attr(tag, "xlink:href");
            if (src == null) src = attr(tag, "href");
            if (src != null && src.length() > 0 && hide == 0 && !src.startsWith("data:")) {
                flush();
                Para ip = new Para("", 0, off);
                ip.img = src.startsWith("fb2:") ? src : resolvePath(dir, src);
                String alt = attr(tag, "alt");
                ip.alt = alt == null ? "" : alt;
                out.add(ip);
                off += 1;
            }
            return;
        }
        if (name.equals("a")) {
            String h = attr(tag, "href");
            if (h != null && !selfClose) {
                endRun();
                href = h;
                String ty = attr(tag, "epub:type"), role = attr(tag, "role");
                linkNoteref = (ty != null && ty.indexOf("noteref") >= 0) || (role != null && role.indexOf("noteref") >= 0);
            }
            return;
        }
        if (VOID.contains(name) || selfClose) return;
        if (isHeading(name)) headLevel = name.charAt(1) - '0';
        else if (name.equals("ul") || name.equals("ol")) { lists.add(new int[]{name.equals("ol") ? 1 : 0, 0}); pendingLabel = null; }
        else if (name.equals("li")) {
            int[] l = lists.size() > 0 ? lists.get(lists.size() - 1) : null;
            if (l != null && l[0] == 1) { l[1]++; pendingLabel = l[1] + "."; } else pendingLabel = "\u2022";
        } else if (name.equals("td") || name.equals("th")) add(' ');
        pushFrame(name, tag);
    }

    private void pushFrame(String name, String tag) {
        Frame f = new Frame();
        f.name = name;
        f.pb = bold ? 1 : 0; f.pi = italic ? 1 : 0; f.ps = sup ? 1 : 0; f.pa = align; f.pq = quote;
        f.pn = noInd ? 1 : 0; f.ph = hide;
        Css.Props p = css.lookup(name, attr(tag, "class"), attr(tag, "style"));
        boolean nb = bold, ni = italic, ns = sup, nn = noInd;
        int na = align, nq = quote, nh = hide;
        if (name.equals("b") || name.equals("strong")) nb = true;
        if (name.equals("i") || name.equals("em") || name.equals("cite") || name.equals("dfn")) ni = true;
        if (name.equals("sup")) ns = true;
        if (name.equals("center")) na = 1;
        if (name.equals("blockquote")) nq++;
        if (p.bold != -1) nb = p.bold == 1;
        if (p.ital != -1) ni = p.ital == 1;
        if (p.align != -1) na = p.align;
        if (p.noInd != -1) nn = p.noInd == 1;
        if (p.indent == 1) nq++;
        if (p.hide == 1) nh++;
        String al = attr(tag, "align");
        if (al != null) {
            al = al.toLowerCase();
            if (al.equals("center")) na = 1; else if (al.equals("right")) na = 2;
        }
        if (nb != bold || ni != italic || ns != sup) endRun();
        bold = nb; italic = ni; sup = ns; align = na; quote = nq; noInd = nn; hide = nh;
        stack.add(f);
    }

    private void closeTag(String name) {
        if (name.equals("a")) { endRun(); href = null; return; }
        if (BLOCK.contains(name)) flush();
        if (isHeading(name)) headLevel = 0;
        if (name.equals("li")) pendingLabel = null;
        if ((name.equals("ul") || name.equals("ol")) && lists.size() > 0) lists.remove(lists.size() - 1);
        for (int k = stack.size() - 1; k >= 0; k--) {
            if (stack.get(k).name.equals(name)) {
                Frame f = stack.get(k);
                endRun();
                bold = f.pb == 1; italic = f.pi == 1; sup = f.ps == 1; align = f.pa; quote = f.pq;
                noInd = f.pn == 1; hide = f.ph;
                while (stack.size() > k) stack.remove(stack.size() - 1);
                break;
            }
        }
    }

    private void run(String s) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '<') {
                if (s.startsWith("<!--", i)) {
                    int e = s.indexOf("-->", i + 4);
                    i = e < 0 ? n : e + 3;
                    continue;
                }
                if (s.startsWith("<![CDATA[", i)) {
                    int e = s.indexOf("]]>", i + 9);
                    if (e < 0) e = n;
                    for (int k = i + 9; k < e; k++) add(s.charAt(k));
                    i = e + 3;
                    continue;
                }
                int e = s.indexOf('>', i);
                if (e < 0) break;
                String tag = s.substring(i + 1, e);
                i = e + 1;
                if (tag.length() == 0) continue;
                boolean close = tag.charAt(0) == '/';
                if (close) tag = tag.substring(1);
                boolean selfClose = tag.endsWith("/");
                int k = 0;
                while (k < tag.length() && !Character.isWhitespace(tag.charAt(k)) && tag.charAt(k) != '/') k++;
                String name = tag.substring(0, k).toLowerCase();
                if (name.length() == 0 || name.charAt(0) == '!' || name.charAt(0) == '?') continue;
                if (!close && (name.equals("script") || name.equals("title") || name.equals("style"))) {
                    if (!selfClose) {
                        int ce = find(s, "</" + name, i);
                        if (ce < 0) ce = n;
                        if (name.equals("style")) css.addSheet(s.substring(i, ce));
                        int ge = s.indexOf('>', ce);
                        i = ge < 0 ? n : ge + 1;
                    }
                    continue;
                }
                if (close) closeTag(name); else openTag(name, tag, selfClose);
                continue;
            }
            if (c == '&') {
                int e = s.indexOf(';', i);
                if (e > i && e - i <= 10) {
                    String r = entity(s.substring(i + 1, e));
                    if (r != null) {
                        addStr(r);
                        i = e + 1;
                        continue;
                    }
                }
                add('&');
                i++;
                continue;
            }
            add(c);
            i++;
        }
    }

    private static String entity(String e) {
        if (e.length() == 0) return null;
        if (e.charAt(0) == '#') {
            try {
                int cp;
                if (e.length() > 1 && (e.charAt(1) == 'x' || e.charAt(1) == 'X')) cp = Integer.parseInt(e.substring(2), 16);
                else cp = Integer.parseInt(e.substring(1));
                return cp < 0x10000 ? String.valueOf((char) cp) : new String(Character.toChars(cp));
            } catch (Exception x) {
                return null;
            }
        }
        if (e.equals("amp")) return "&";
        if (e.equals("lt")) return "<";
        if (e.equals("gt")) return ">";
        if (e.equals("quot")) return "\"";
        if (e.equals("apos")) return "'";
        if (e.equals("nbsp") || e.equals("thinsp") || e.equals("ensp") || e.equals("emsp")) return "\u00A0";
        if (e.equals("mdash")) return "\u2014";
        if (e.equals("ndash")) return "\u2013";
        if (e.equals("hellip")) return "\u2026";
        if (e.equals("lsquo")) return "\u2018";
        if (e.equals("rsquo")) return "\u2019";
        if (e.equals("ldquo")) return "\u201C";
        if (e.equals("rdquo")) return "\u201D";
        if (e.equals("laquo")) return "\u00AB";
        if (e.equals("raquo")) return "\u00BB";
        if (e.equals("copy")) return "\u00A9";
        if (e.equals("shy") || e.equals("zwnj") || e.equals("zwj")) return "";
        return null;
    }
}
