package org.nookpoint.reader;

/** Tiny tolerant XML tokenizer (FB2, OPDS/Atom): START / END / TEXT events, no DOM, no entities table. */
class MiniXml {
    static final int EOF = 0, START = 1, END = 2, TEXT = 3;

    private final String s;
    private int pos;
    private String pendingEnd;
    int type;
    String name = "", attrs = "", text = "";

    MiniXml(String s) {
        this.s = s;
        if (s.length() > 0 && s.charAt(0) == '\uFEFF') pos = 1;
    }

    static String local(String n) {
        int i = n.indexOf(':');
        return (i >= 0 ? n.substring(i + 1) : n).toLowerCase();
    }

    String attr(String key) { return Html.attr("x" + attrs, key); }

    int next() {
        if (pendingEnd != null) {
            type = END;
            name = pendingEnd;
            pendingEnd = null;
            return type;
        }
        int n = s.length();
        while (pos < n) {
            char c = s.charAt(pos);
            if (c == '<') {
                if (s.startsWith("<!--", pos)) {
                    int e = s.indexOf("-->", pos + 4);
                    pos = e < 0 ? n : e + 3;
                    continue;
                }
                if (s.startsWith("<![CDATA[", pos)) {
                    int e = s.indexOf("]]>", pos + 9);
                    if (e < 0) e = n;
                    text = s.substring(pos + 9, e);
                    pos = Math.min(n, e + 3);
                    type = TEXT;
                    return type;
                }
                if (s.startsWith("<?", pos) || s.startsWith("<!", pos)) {
                    int e = s.indexOf('>', pos);
                    pos = e < 0 ? n : e + 1;
                    continue;
                }
                int e = s.indexOf('>', pos);
                if (e < 0) { pos = n; break; }
                String tag = s.substring(pos + 1, e);
                pos = e + 1;
                if (tag.length() == 0) continue;
                if (tag.charAt(0) == '/') {
                    name = local(tag.substring(1).trim());
                    type = END;
                    return type;
                }
                boolean self = tag.endsWith("/");
                if (self) tag = tag.substring(0, tag.length() - 1);
                int k = 0;
                while (k < tag.length() && !Character.isWhitespace(tag.charAt(k))) k++;
                name = local(tag.substring(0, k));
                attrs = tag.substring(k);
                if (self) pendingEnd = name;
                type = START;
                return type;
            }
            int e = s.indexOf('<', pos);
            if (e < 0) e = n;
            text = decode(s.substring(pos, e));
            pos = e;
            type = TEXT;
            return type;
        }
        type = EOF;
        return type;
    }

    static String decode(String t) {
        if (t.indexOf('&') < 0) return t;
        StringBuilder sb = new StringBuilder(t.length());
        int i = 0, n = t.length();
        while (i < n) {
            char c = t.charAt(i);
            if (c == '&') {
                int e = t.indexOf(';', i);
                if (e > i && e - i <= 10) {
                    String ent = t.substring(i + 1, e);
                    String r = null;
                    if (ent.equals("amp")) r = "&";
                    else if (ent.equals("lt")) r = "<";
                    else if (ent.equals("gt")) r = ">";
                    else if (ent.equals("quot")) r = "\"";
                    else if (ent.equals("apos")) r = "'";
                    else if (ent.equals("nbsp")) r = "\u00A0";
                    else if (ent.length() > 1 && ent.charAt(0) == '#') {
                        try {
                            int cp = (ent.charAt(1) == 'x' || ent.charAt(1) == 'X') ? Integer.parseInt(ent.substring(2), 16)
                                    : Integer.parseInt(ent.substring(1));
                            r = cp < 0x10000 ? String.valueOf((char) cp) : new String(Character.toChars(cp));
                        } catch (Exception x) { r = null; }
                    }
                    if (r != null) { sb.append(r); i = e + 1; continue; }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
