package org.nookpoint.reader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Xml;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.xmlpull.v1.XmlPullParser;

/** EPUB (zip + OPF + NCX/nav) or plain TXT book. */
class Book {
    File file;
    boolean txt;
    ZipFile zip;
    String title, author, coverPath, description;
    ArrayList<String> spine = new ArrayList<String>();
    long[] sizes = new long[0];
    long total;
    ArrayList<TocItem> toc = new ArrayList<TocItem>();
    ArrayList<ArrayList<Para>> txtChapters;

    static Book open(File f) throws Exception {
        Book b = new Book();
        b.file = f;
        if (f.getName().toLowerCase().endsWith(".txt")) b.openTxt();
        else b.openEpub();
        if (b.title == null || b.title.length() == 0) {
            String n = f.getName();
            int d = n.lastIndexOf('.');
            b.title = d > 0 ? n.substring(0, d) : n;
        }
        return b;
    }

    int count() { return txt ? txtChapters.size() : spine.size(); }

    private final HashMap<String, Css> cssCache = new HashMap<String, Css>();

    static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(0, i + 1) : "";
    }

    Chapter loadChapter(int i) throws Exception {
        if (txt) return new Chapter(txtChapters.get(i), new HashMap<String, Integer>());
        String path = spine.get(i);
        ZipEntry e = zip.getEntry(path);
        InputStream in = zip.getInputStream(e);
        byte[] data = readAll(in);
        in.close();
        String html = new String(data, "UTF-8");
        return Html.parse(html, cssFor(path, html));
    }

    private Css cssFor(String chapterPath, String html) {
        String dir = dirOf(chapterPath);
        ArrayList<String> paths = new ArrayList<String>();
        StringBuilder key = new StringBuilder();
        ArrayList<String> links = Html.cssLinks(html);
        for (int i = 0; i < links.size(); i++) {
            String h = links.get(i);
            if (h.indexOf(':') >= 0) continue;
            String p = resolve(dir, h);
            paths.add(p);
            key.append(p).append('|');
        }
        Css c = cssCache.get(key.toString());
        if (c == null) {
            c = new Css();
            for (int i = 0; i < paths.size(); i++) {
                try {
                    ZipEntry ze = zip.getEntry(paths.get(i));
                    if (ze == null) continue;
                    InputStream in = zip.getInputStream(ze);
                    byte[] d = readAll(in);
                    in.close();
                    c.addSheet(new String(d, "UTF-8"));
                } catch (Exception ex) {
                    // ignore broken stylesheet
                }
            }
            cssCache.put(key.toString(), c);
        }
        return c;
    }

    String titleFor(int ch) {
        String t = null;
        for (int i = 0; i < toc.size(); i++) {
            if (toc.get(i).chapter <= ch) t = toc.get(i).title;
            else break;
        }
        return t != null ? t : "";
    }

    int percent(int ch, float frac) {
        if (total <= 0 || ch >= sizes.length) return 0;
        long before = 0;
        for (int i = 0; i < ch; i++) before += sizes[i];
        return (int) (100.0 * (before + frac * sizes[ch]) / total);
    }

    void close() {
        try { if (zip != null) zip.close(); } catch (Exception e) { }
    }

    Bitmap cover(int maxW, int maxH) {
        if (txt || coverPath == null) return null;
        try {
            ZipEntry e = zip.getEntry(coverPath);
            if (e == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = zip.getInputStream(e);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int s = 1;
            while (o.outWidth / (s * 2) >= maxW && o.outHeight / (s * 2) >= maxH) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            in = zip.getInputStream(e);
            Bitmap bm = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return bm;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- TXT
    private void openTxt() throws Exception {
        txt = true;
        FileInputStream in = new FileInputStream(file);
        byte[] data = readAll(in);
        in.close();
        String s = new String(data, "UTF-8");
        int bad = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\uFFFD') bad++;
        if (bad > s.length() / 200 + 2) s = new String(data, "windows-1251");
        String[] ls = s.split("\r?\n");
        int blank = 0;
        for (int i = 0; i < ls.length; i++) if (ls[i].trim().length() == 0) blank++;
        boolean join = blank * 10 > ls.length;
        ArrayList<String> paras = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < ls.length; i++) {
            String t = ls[i].trim();
            if (t.length() == 0) {
                if (cur.length() > 0) { paras.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            if (join) {
                if (cur.length() > 0) cur.append(' ');
                cur.append(t);
            } else {
                paras.add(t);
            }
        }
        if (cur.length() > 0) paras.add(cur.toString());

        txtChapters = new ArrayList<ArrayList<Para>>();
        ArrayList<Long> szs = new ArrayList<Long>();
        ArrayList<Para> ch = new ArrayList<Para>();
        int off = 0;
        for (int i = 0; i < paras.size(); i++) {
            String p = paras.get(i).replaceAll("\\s+", " ");
            ch.add(new Para(p, false, off));
            off += p.length() + 1;
            if (off >= 30000) {
                txtChapters.add(ch);
                szs.add(Long.valueOf(off));
                ch = new ArrayList<Para>();
                off = 0;
            }
        }
        if (ch.size() > 0 || txtChapters.size() == 0) {
            txtChapters.add(ch);
            szs.add(Long.valueOf(Math.max(off, 1)));
        }
        sizes = new long[szs.size()];
        for (int i = 0; i < sizes.length; i++) { sizes[i] = szs.get(i).longValue(); total += sizes[i]; }
        if (txtChapters.size() > 1) {
            for (int i = 0; i < txtChapters.size(); i++) toc.add(new TocItem("Part " + (i + 1), i));
        }
    }

    // ---------------------------------------------------------------- EPUB
    private void openEpub() throws Exception {
        zip = new ZipFile(file);
        ZipEntry ce = zip.getEntry("META-INF/container.xml");
        if (ce == null) throw new Exception("Not an EPUB");
        String opf = null;
        InputStream in = zip.getInputStream(ce);
        XmlPullParser p = Xml.newPullParser();
        p.setInput(in, null);
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && local(p.getName()).equals("rootfile")) {
                opf = p.getAttributeValue(null, "full-path");
                break;
            }
        }
        in.close();
        if (opf == null) throw new Exception("No OPF");
        int sl = opf.lastIndexOf('/');
        String dir = sl >= 0 ? opf.substring(0, sl + 1) : "";

        HashMap<String, String[]> man = new HashMap<String, String[]>();
        ArrayList<String> idrefs = new ArrayList<String>();
        String tocId = null, coverId = null;
        ZipEntry oe = zip.getEntry(opf);
        if (oe == null) throw new Exception("OPF missing");
        in = zip.getInputStream(oe);
        p = Xml.newPullParser();
        p.setInput(in, null);
        try {
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev != XmlPullParser.START_TAG) continue;
                String n = local(p.getName());
                if (n.equals("title") && title == null) {
                    title = p.nextText().trim();
                } else if (n.equals("creator") && author == null) {
                    author = p.nextText().trim();
                } else if (n.equals("description") && description == null) {
                    description = cleanText(textUntilEnd(p));
                } else if (n.equals("meta") && "cover".equals(p.getAttributeValue(null, "name"))) {
                    coverId = p.getAttributeValue(null, "content");
                } else if (n.equals("item")) {
                    String id = p.getAttributeValue(null, "id");
                    String href = p.getAttributeValue(null, "href");
                    if (id != null && href != null) {
                        man.put(id, new String[]{href, p.getAttributeValue(null, "media-type"),
                                p.getAttributeValue(null, "properties")});
                    }
                } else if (n.equals("spine")) {
                    tocId = p.getAttributeValue(null, "toc");
                } else if (n.equals("itemref")) {
                    idrefs.add(p.getAttributeValue(null, "idref"));
                }
            }
        } catch (Exception e) {
            // keep whatever was parsed
        }
        in.close();

        ArrayList<Long> szs = new ArrayList<Long>();
        for (int i = 0; i < idrefs.size(); i++) {
            String[] it = man.get(idrefs.get(i));
            if (it == null) continue;
            String path = resolve(dir, it[0]);
            ZipEntry e = zip.getEntry(path);
            if (e == null) continue;
            spine.add(path);
            szs.add(Long.valueOf(Math.max(e.getSize(), 1)));
        }
        if (spine.size() == 0) throw new Exception("Empty spine");
        sizes = new long[szs.size()];
        for (int i = 0; i < sizes.length; i++) { sizes[i] = szs.get(i).longValue(); total += sizes[i]; }

        String ncxPath = null, navPath = null;
        if (tocId != null && man.get(tocId) != null) ncxPath = resolve(dir, man.get(tocId)[0]);
        for (String[] it : man.values()) {
            String props = it[2] == null ? "" : " " + it[2] + " ";
            if (props.indexOf(" nav ") >= 0) navPath = resolve(dir, it[0]);
            if (props.indexOf(" cover-image ") >= 0 && coverPath == null) coverPath = resolve(dir, it[0]);
            if (ncxPath == null && "application/x-dtbncx+xml".equals(it[1])) ncxPath = resolve(dir, it[0]);
        }
        if (coverPath == null && coverId != null && man.get(coverId) != null) {
            coverPath = resolve(dir, man.get(coverId)[0]);
        }
        try { if (navPath != null) parseNav(navPath); } catch (Exception e) { }
        if (toc.size() == 0) {
            try { if (ncxPath != null) parseNcx(ncxPath); } catch (Exception e) { }
        }
    }

    private void parseNcx(String path) throws Exception {
        ZipEntry e = zip.getEntry(path);
        if (e == null) return;
        String dir = path.indexOf('/') >= 0 ? path.substring(0, path.lastIndexOf('/') + 1) : "";
        InputStream in = zip.getInputStream(e);
        XmlPullParser p = Xml.newPullParser();
        p.setInput(in, null);
        String lastText = "";
        int ev;
        try {
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev != XmlPullParser.START_TAG) continue;
                String n = local(p.getName());
                if (n.equals("pageList") || n.equals("navList")) break;
                if (n.equals("text")) {
                    lastText = p.nextText().trim().replaceAll("\\s+", " ");
                } else if (n.equals("content")) {
                    String src = p.getAttributeValue(null, "src");
                    if (src != null) addToc(lastText, resolve(dir, src));
                }
            }
        } finally {
            in.close();
        }
    }

    private void parseNav(String path) throws Exception {
        ZipEntry e = zip.getEntry(path);
        if (e == null) return;
        String dir = path.indexOf('/') >= 0 ? path.substring(0, path.lastIndexOf('/') + 1) : "";
        InputStream in = zip.getInputStream(e);
        XmlPullParser p = Xml.newPullParser();
        p.setInput(in, null);
        boolean inToc = false;
        int ev;
        try {
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = local(p.getName());
                    if (n.equals("nav")) {
                        String t = p.getAttributeValue(null, "epub:type");
                        if (t == null) t = p.getAttributeValue(null, "type");
                        inToc = t != null && t.indexOf("toc") >= 0;
                    } else if (n.equals("a") && inToc) {
                        String href = p.getAttributeValue(null, "href");
                        String text = textUntilEnd(p);
                        if (href != null) addToc(text, resolve(dir, href));
                    }
                } else if (ev == XmlPullParser.END_TAG && inToc && local(p.getName()).equals("nav")) {
                    break;
                }
            }
        } finally {
            in.close();
        }
    }

    private static String textUntilEnd(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.START_TAG) depth++;
            else if (ev == XmlPullParser.END_TAG) depth--;
            else if (ev == XmlPullParser.TEXT) sb.append(p.getText());
            else if (ev == XmlPullParser.END_DOCUMENT) break;
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private void addToc(String title, String path) {
        int idx = spine.indexOf(path);
        if (idx < 0 || title == null || title.length() == 0) return;
        toc.add(new TocItem(title, idx));
    }

    // ---------------------------------------------------------------- utils
    private static String local(String n) {
        int i = n.indexOf(':');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    static String resolve(String dir, String href) {
        int h = href.indexOf('#');
        if (h >= 0) href = href.substring(0, h);
        href = Uri.decode(href);
        String full = href.startsWith("/") ? href.substring(1) : dir + href;
        String[] parts = full.split("/");
        ArrayList<String> st = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s.equals("..")) { if (st.size() > 0) st.remove(st.size() - 1); }
            else if (s.length() > 0 && !s.equals(".")) st.add(s);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < st.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(st.get(i));
        }
        return sb.toString();
    }

    /** Turns a metadata description (often HTML) into plain text. */
    static String cleanText(String s) {
        if (s == null) return "";
        s = s.replaceAll("(?i)<br\\s*/?>|</p>|</div>", "\n").replaceAll("<[^>]*>", "");
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&");
        s = s.replaceAll("[ \\t\u00A0]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").trim();
        return s.length() > 4000 ? s.substring(0, 4000) + "..." : s;
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }
}
