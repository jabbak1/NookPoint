package org.nookpoint.reader;

import java.util.ArrayList;

/** Tiny tolerant XHTML -> paragraphs converter. */
class Html {
    private final ArrayList<Para> out = new ArrayList<Para>();
    private final StringBuilder cur = new StringBuilder();
    private boolean head;
    private int off;

    static ArrayList<Para> parse(String s) {
        Html h = new Html();
        h.run(s);
        h.flush();
        return h.out;
    }

    private void flush() {
        int e = cur.length();
        while (e > 0 && cur.charAt(e - 1) == ' ') e--;
        int b = 0;
        while (b < e && cur.charAt(b) == ' ') b++;
        if (e > b) {
            String t = cur.substring(b, e);
            out.add(new Para(t, head, off));
            off += t.length() + 1;
        }
        cur.setLength(0);
    }

    private void add(char c) {
        if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == 0xA0) {
            if (cur.length() > 0 && cur.charAt(cur.length() - 1) != ' ') cur.append(' ');
        } else if (c == 0xFEFF || c == 0x200B || c == 0xAD || c == 0x200C || c == 0x200D) {
            // invisible, drop
        } else {
            cur.append(c);
        }
    }

    private void addStr(String s) {
        for (int i = 0; i < s.length(); i++) add(s.charAt(i));
    }

    private static int find(String s, String pat, int from) {
        int m = pat.length();
        int i = s.indexOf('<', from);
        while (i >= 0) {
            if (s.regionMatches(true, i, pat, 0, m)) return i;
            i = s.indexOf('<', i + 1);
        }
        return -1;
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
                boolean close = tag.startsWith("/");
                if (close) tag = tag.substring(1);
                int k = 0;
                while (k < tag.length() && !Character.isWhitespace(tag.charAt(k)) && tag.charAt(k) != '/') k++;
                String name = tag.substring(0, k).toLowerCase();
                if (!close && (name.equals("script") || name.equals("style") || name.equals("head")
                        || name.equals("title"))) {
                    if (!tag.endsWith("/")) {
                        int ce = find(s, "</" + name, i);
                        if (ce < 0) ce = n;
                        int ge = s.indexOf('>', ce);
                        i = ge < 0 ? n : ge + 1;
                    }
                    continue;
                }
                tagEffect(name, close);
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

    private void tagEffect(String n, boolean close) {
        if (n.length() == 2 && n.charAt(0) == 'h' && n.charAt(1) >= '1' && n.charAt(1) <= '6') {
            flush();
            head = !close;
            return;
        }
        if (n.equals("br") || n.equals("p") || n.equals("div") || n.equals("li") || n.equals("tr")
                || n.equals("blockquote") || n.equals("section") || n.equals("article") || n.equals("ul")
                || n.equals("ol") || n.equals("table") || n.equals("hr") || n.equals("dt") || n.equals("dd")
                || n.equals("pre") || n.equals("body") || n.equals("figure") || n.equals("figcaption")
                || n.equals("aside") || n.equals("header") || n.equals("footer") || n.equals("nav")) {
            flush();
            return;
        }
        if (n.equals("td") || n.equals("th")) add(' ');
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
        if (e.equals("nbsp")) return " ";
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
