package org.nookpoint.reader;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/** OPDS (Atom) catalog feeds: navigation, search and book acquisition links. */
class Opds {
    static class Link { String href = "", type = "", rel = ""; }

    static class Entry {
        String title = "", author = "", summary = "";
        final ArrayList<Link> links = new ArrayList<Link>();

        /** Link to another feed (a category, a shelf, "new books"...), or null. */
        Link nav() {
            for (int i = 0; i < links.size(); i++) {
                Link l = links.get(i);
                if (l.type.indexOf("atom+xml") >= 0 && l.rel.indexOf("acquisition") < 0 && l.href.length() > 0) return l;
            }
            return null;
        }

        /** Downloadable links in formats the reader understands. */
        ArrayList<Link> books() {
            ArrayList<Link> r = new ArrayList<Link>();
            for (int i = 0; i < links.size(); i++) {
                Link l = links.get(i);
                if (l.href.length() > 0 && l.rel.indexOf("acquisition") >= 0 && extension(l) != null) r.add(l);
            }
            return r;
        }

        boolean hasUnsupported() {
            for (int i = 0; i < links.size(); i++) {
                Link l = links.get(i);
                if (l.rel.indexOf("acquisition") >= 0 && extension(l) == null) return true;
            }
            return false;
        }
    }

    static class Feed {
        String title = "", next, search;
        boolean searchIsDescription;
        final ArrayList<Entry> entries = new ArrayList<Entry>();
    }

    static String extension(Link l) {
        String t = l.type.toLowerCase();
        if (t.indexOf("epub") >= 0) return ".epub";
        if (t.indexOf("fb2") >= 0 || t.indexOf("fictionbook") >= 0) return t.indexOf("zip") >= 0 ? ".fb2.zip" : ".fb2";
        if (t.equals("text/plain")) return ".txt";
        String h = l.href.toLowerCase();
        int q = h.indexOf('?');
        if (q >= 0) h = h.substring(0, q);
        if (t.length() == 0 || t.equals("application/octet-stream")) {
            if (h.endsWith(".epub")) return ".epub";
            if (h.endsWith(".fb2.zip")) return ".fb2.zip";
            if (h.endsWith(".fb2")) return ".fb2";
        }
        return null;
    }

    static String resolve(String base, String href) {
        try {
            return new URL(new URL(base), href).toString();
        } catch (Exception e) {
            return href;
        }
    }

    static Feed parse(String xml) {
        Feed f = new Feed();
        MiniXml x = new MiniXml(xml);
        ArrayList<String> path = new ArrayList<String>();
        Entry cur = null;
        StringBuilder text = new StringBuilder();
        while (x.next() != MiniXml.EOF) {
            if (x.type == MiniXml.START) {
                path.add(x.name);
                text.setLength(0);
                if (x.name.equals("entry")) cur = new Entry();
                else if (x.name.equals("link")) {
                    Link l = new Link();
                    String h = x.attr("href"), t = x.attr("type"), r = x.attr("rel");
                    l.href = h == null ? "" : MiniXml.decode(h);
                    l.type = t == null ? "" : t;
                    l.rel = r == null ? "" : r;
                    if (cur != null) cur.links.add(l);
                    else if (l.rel.equals("next")) f.next = l.href;
                    else if (l.rel.equals("search") && f.search == null) {
                        f.search = l.href;
                        f.searchIsDescription = l.href.indexOf("{searchTerms}") < 0;
                    }
                }
            } else if (x.type == MiniXml.TEXT) {
                text.append(x.text);
            } else if (x.type == MiniXml.END) {
                String n = x.name;
                String t = text.toString().trim();
                if (cur != null) {
                    if (n.equals("title") && path.size() >= 2 && path.get(path.size() - 2).equals("entry")) cur.title = Book.cleanText(t);
                    else if (n.equals("name") && cur.author.length() == 0) cur.author = t;
                    else if ((n.equals("summary") || n.equals("content")) && cur.summary.length() == 0) cur.summary = Book.cleanText(t);
                    else if (n.equals("entry")) { f.entries.add(cur); cur = null; }
                } else if (n.equals("title") && path.size() == 2 && f.title.length() == 0) {
                    f.title = t;
                }
                while (path.size() > 0 && !path.get(path.size() - 1).equals(n)) path.remove(path.size() - 1);
                if (path.size() > 0) path.remove(path.size() - 1);
                text.setLength(0);
            }
        }
        return f;
    }

    /** The atom search template from an OpenSearch description document, or null. */
    static String templateFromDescription(String xml) {
        MiniXml x = new MiniXml(xml);
        String best = null;
        while (x.next() != MiniXml.EOF) {
            if (x.type == MiniXml.START && x.name.equals("url")) {
                String t = x.attr("type"), tpl = x.attr("template");
                if (tpl != null && t != null && t.indexOf("atom") >= 0) { best = MiniXml.decode(tpl); break; }
                if (tpl != null && best == null) best = MiniXml.decode(tpl);
            }
        }
        return best;
    }

    static String fillTemplate(String template, String query) {
        String q;
        try { q = URLEncoder.encode(query, "UTF-8"); } catch (Exception e) { q = query; }
        return template.replace("{searchTerms}", q).replaceAll("\\{[^}]*\\}", "");
    }

    static String fileName(Entry e, Link l) {
        String base = (e.author.length() > 0 ? e.author + " - " : "") + e.title;
        String n = Export.safeName(base);
        String ext = extension(l);
        return n + (ext == null ? "" : ext);
    }
}
