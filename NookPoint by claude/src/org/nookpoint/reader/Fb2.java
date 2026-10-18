package org.nookpoint.reader;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;

/** FictionBook 2 reader: metadata, chapters converted to simple XHTML (parsed later by Html), notes, images. */
class Fb2 {
    String title = "", author = "", lang = "", annotation = "", series = "", seriesNo = "", coverId;
    final ArrayList<String> chapters = new ArrayList<String>();
    final ArrayList<String> titles = new ArrayList<String>();
    final HashMap<String, Integer> anchors = new HashMap<String, Integer>();      // id -> chapter index
    final HashMap<String, String> images = new HashMap<String, String>();         // id -> base64 data

    static final String CSS = ".epigraph{font-style:italic;margin-left:3em}.cite{margin-left:3em}"
            + ".poem{margin-left:3em}.stanza{text-indent:0}.subtitle{text-align:center;font-weight:bold}"
            + ".ta{text-align:right;font-style:italic}";

    static String decodeBytes(byte[] d) {
        String head = "";
        try { head = new String(d, 0, Math.min(d.length, 300), "ISO-8859-1"); } catch (Exception e) { }
        String enc = "UTF-8";
        int i = head.indexOf("encoding");
        if (i >= 0) {
            int q = head.indexOf('"', i), q2 = head.indexOf('\'', i);
            if (q < 0 || (q2 >= 0 && q2 < q)) q = q2;
            if (q >= 0) {
                int e = head.indexOf(head.charAt(q), q + 1);
                if (e > q) enc = head.substring(q + 1, e).trim();
            }
        }
        try {
            return new String(d, Charset.forName(enc).name());
        } catch (Exception e) {
            try { return new String(d, "UTF-8"); } catch (Exception x) { return ""; }
        }
    }

    static Fb2 parse(byte[] data) {
        Fb2 f = new Fb2();
        f.run(new MiniXml(decodeBytes(data)));
        return f;
    }

    // ------------------------------------------------------------------ conversion state
    private final ArrayList<String> path = new ArrayList<String>();
    private StringBuilder cur = new StringBuilder();
    private String curTitle;
    private boolean inBody, notesBody, inTitle, stanzaFirst, authorDone, inAnnotation;
    private int secDepth, titleLevel, titleP, poem, cite, epigraph;
    private String noteLabel, capture, binId;
    private StringBuilder capBuf = new StringBuilder(), titleText = new StringBuilder(), binText, annBuf = new StringBuilder();
    private String first = "", mid = "", last = "";
    private boolean noteFirstP;

    private boolean in(String n) { return path.contains(n); }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escAttr(String s) { return esc(s).replace("\"", "&quot;"); }

    private static String href(MiniXml x) {
        String h = x.attr("l:href");
        if (h == null) h = x.attr("xlink:href");
        if (h == null) h = x.attr("href");
        return h;
    }

    private void pushChapter() {
        String h = cur.toString();
        if (h.replaceAll("<[^>]*>", "").trim().length() > 0 || h.indexOf("<img") >= 0) {
            chapters.add(h);
            titles.add(curTitle == null ? "" : curTitle);
        }
        cur = new StringBuilder();
        curTitle = null;
    }

    private void idAttr(MiniXml x, StringBuilder out) {
        String id = x.attr("id");
        if (id != null && id.length() > 0) {
            out.append(" id=\"").append(escAttr(id)).append('"');
            if (!anchors.containsKey(id)) anchors.put(id, Integer.valueOf(chapters.size()));
        }
    }

    private void run(MiniXml x) {
        while (x.next() != MiniXml.EOF) {
            if (x.type == MiniXml.START) { start(x); path.add(x.name); }
            else if (x.type == MiniXml.END) {
                while (path.size() > 0 && !path.get(path.size() - 1).equals(x.name)) path.remove(path.size() - 1);
                if (path.size() > 0) path.remove(path.size() - 1);
                end(x.name);
            } else text(x.text);
        }
        pushChapter();
        if (chapters.size() == 0) { chapters.add(""); titles.add(""); }
        if (title.length() == 0 && series.length() == 0) title = "";
    }

    private void start(MiniXml x) {
        String n = x.name;
        if (!inBody) {
            boolean ti = in("title-info") && !in("src-title-info");
            if (n.equals("body")) {
                inBody = true;
                String bn = x.attr("name");
                notesBody = bn != null && bn.length() > 0;
                secDepth = 0;
                return;
            }
            if (ti) {
                if (n.equals("book-title") || n.equals("lang") || (!authorDone && in("author")
                        && (n.equals("first-name") || n.equals("middle-name") || n.equals("last-name")))) {
                    capture = n;
                    capBuf.setLength(0);
                } else if (n.equals("annotation")) {
                    inAnnotation = true;
                } else if (n.equals("sequence") && series.length() == 0) {
                    String nm = x.attr("name"), no = x.attr("number");
                    series = nm == null ? "" : nm.trim();
                    seriesNo = no == null ? "" : no.trim();
                } else if (n.equals("image") && in("coverpage") && coverId == null) {
                    String h = href(x);
                    if (h != null) coverId = h.startsWith("#") ? h.substring(1) : h;
                }
            }
            if (n.equals("binary")) {
                binId = x.attr("id");
                binText = new StringBuilder();
            }
            return;
        }
        // ---- inside a <body>
        if (n.equals("section")) {
            secDepth++;
            if (!notesBody && secDepth <= 2) pushChapter();
            cur.append("<div");
            idAttr(x, cur);
            cur.append('>');
        } else if (n.equals("title")) {
            inTitle = true;
            titleP = 0;
            titleText.setLength(0);
            titleLevel = 0;
            if (!notesBody) {
                titleLevel = (poem > 0 || cite > 0 || epigraph > 0) ? 4 : (secDepth <= 1 ? 1 : (secDepth == 2 ? 2 : 3));
                cur.append("<h").append(titleLevel).append('>');
            }
        } else if (n.equals("p")) {
            if (inTitle) {
                if (titleP++ > 0 && titleLevel > 0) cur.append("<br/>");
            } else if (notesBody && noteFirstP && noteLabel != null && noteLabel.length() > 0) {
                cur.append("<p><b>").append(esc(noteLabel)).append("</b> ");
                noteFirstP = false;
            } else {
                cur.append("<p");
                idAttr(x, cur);
                cur.append('>');
            }
        } else if (n.equals("v")) {
            if (stanzaFirst) { cur.append("<p class=\"stanza\">"); stanzaFirst = false; } else cur.append("<br/>");
        } else if (n.equals("stanza")) {
            stanzaFirst = true;
        } else if (n.equals("empty-line")) {
            cur.append("<p>&#160;</p>");
        } else if (n.equals("subtitle")) {
            cur.append("<p class=\"subtitle\">");
        } else if (n.equals("text-author")) {
            cur.append("<p class=\"ta\">");
        } else if (n.equals("epigraph")) {
            epigraph++;
            cur.append("<div class=\"epigraph\">");
        } else if (n.equals("cite")) {
            cite++;
            cur.append("<div class=\"cite\">");
        } else if (n.equals("poem")) {
            poem++;
            cur.append("<div class=\"poem\">");
        } else if (n.equals("emphasis")) {
            cur.append("<i>");
        } else if (n.equals("strong")) {
            cur.append("<b>");
        } else if (n.equals("sup")) {
            cur.append("<sup>");
        } else if (n.equals("a")) {
            String h = href(x), ty = x.attr("type");
            cur.append("<a href=\"").append(escAttr(h == null ? "" : h)).append('"');
            if (ty != null && ty.equals("note")) cur.append(" epub:type=\"noteref\"");
            cur.append('>');
        } else if (n.equals("image")) {
            String h = href(x);
            if (h != null && h.length() > 0) cur.append("<img src=\"fb2:").append(escAttr(h.startsWith("#") ? h.substring(1) : h)).append("\"/>");
        } else if (n.equals("table") || n.equals("tr") || n.equals("td") || n.equals("th")) {
            cur.append('<').append(n).append('>');
        }
    }

    private void end(String n) {
        if (!inBody) {
            if (n.equals("binary") && binId != null) {
                images.put(binId, binText.toString());
                binId = null;
                binText = null;
            }
            if (capture != null && n.equals(capture)) {
                String v = capBuf.toString().trim();
                if (n.equals("book-title")) title = v;
                else if (n.equals("lang")) lang = v;
                else if (n.equals("first-name")) first = v;
                else if (n.equals("middle-name")) mid = v;
                else if (n.equals("last-name")) last = v;
                capture = null;
            }
            if (n.equals("author") && !authorDone && in("title-info") && !in("src-title-info")) {
                String a = (first + " " + mid + " " + last).replaceAll("\\s+", " ").trim();
                if (a.length() > 0) { author = a; authorDone = true; }
                first = mid = last = "";
            }
            if (n.equals("annotation")) {
                inAnnotation = false;
                annotation = annBuf.toString().replaceAll("[ \\t]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").trim();
            }
            if (n.equals("p") && inAnnotation) annBuf.append('\n');
            return;
        }
        if (n.equals("body")) {
            if (notesBody && curTitle == null) curTitle = "Notes";
            pushChapter();
            inBody = false;
            notesBody = false;
        } else if (n.equals("section")) {
            int d = secDepth;
            secDepth--;
            cur.append("</div>");
            if (!notesBody && d <= 2) pushChapter();
        } else if (n.equals("title")) {
            inTitle = false;
            String t = titleText.toString().replaceAll("\\s+", " ").trim();
            if (titleLevel > 0) cur.append("</h").append(titleLevel).append('>');
            if (notesBody) {
                if (secDepth == 0) { if (curTitle == null && t.length() > 0) curTitle = t; }
                else { noteLabel = t; noteFirstP = true; }
            } else if (titleLevel > 0 && titleLevel <= 3 && curTitle == null && t.length() > 0) {
                curTitle = t;
            }
            titleLevel = 0;
        } else if (n.equals("p")) {
            if (!inTitle) cur.append("</p>");
        } else if (n.equals("v")) {
            // closed by the stanza
        } else if (n.equals("stanza")) {
            cur.append("</p>");
        } else if (n.equals("subtitle") || n.equals("text-author")) {
            cur.append("</p>");
        } else if (n.equals("epigraph")) {
            epigraph--;
            cur.append("</div>");
        } else if (n.equals("cite")) {
            cite--;
            cur.append("</div>");
        } else if (n.equals("poem")) {
            poem--;
            cur.append("</div>");
        } else if (n.equals("emphasis")) {
            cur.append("</i>");
        } else if (n.equals("strong")) {
            cur.append("</b>");
        } else if (n.equals("sup")) {
            cur.append("</sup>");
        } else if (n.equals("a")) {
            cur.append("</a>");
        } else if (n.equals("table") || n.equals("tr") || n.equals("td") || n.equals("th")) {
            cur.append("</").append(n).append('>');
        }
    }

    private void text(String t) {
        if (binText != null) { binText.append(t); return; }
        if (!inBody) {
            if (capture != null) capBuf.append(t);
            if (inAnnotation) annBuf.append(t);
            return;
        }
        if (inTitle) titleText.append(t).append(' ');
        if (inTitle && notesBody) return;                    // note titles become labels, not text
        if (t.trim().length() == 0 && cur.length() > 0 && cur.charAt(cur.length() - 1) == '>') return;
        cur.append(esc(t));
    }
}
