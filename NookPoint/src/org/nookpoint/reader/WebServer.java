package org.nookpoint.reader;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;

/** Minimal single-client HTTP server: list / upload / download / delete books. */
class WebServer extends Thread {
    private final ServerSocket ss;
    private volatile boolean running = true;
    private final File root;

    WebServer(File root, int port) throws IOException {
        this.root = root;
        ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(port));
    }

    void shutdown() {
        running = false;
        try { ss.close(); } catch (IOException e) { }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket s = ss.accept();
                try {
                    s.setSoTimeout(30000);
                    handle(s);
                } catch (Exception e) {
                    // ignore broken client
                } finally {
                    try { s.close(); } catch (IOException x) { }
                }
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    private void handle(Socket s) throws Exception {
        InputStream in = new BufferedInputStream(s.getInputStream(), 8192);
        OutputStream out = s.getOutputStream();
        String req = line(in);
        if (req == null) return;
        String[] rp = req.split(" ");
        if (rp.length < 2) return;
        String method = rp[0], target = rp[1];
        long clen = 0;
        String ctype = "";
        String h;
        while ((h = line(in)) != null && h.length() > 0) {
            String lo = h.toLowerCase();
            if (lo.startsWith("content-length:")) clen = Long.parseLong(h.substring(15).trim());
            else if (lo.startsWith("content-type:")) ctype = h.substring(13).trim();
        }
        String path = target, query = "";
        int q = target.indexOf('?');
        if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }

        if (method.equals("POST") && path.equals("/upload")) {
            upload(in, ctype, clen);
            redirect(out);
        } else if (path.equals("/delete")) {
            File f = new File(root, safe(param(query, "f")));
            if (f.isFile()) f.delete();
            redirect(out);
        } else if (path.equals("/download")) {
            File f = new File(root, safe(param(query, "f")));
            if (!f.isFile()) { send(out, 404, "text/plain", "Not found".getBytes("UTF-8")); return; }
            String hd = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: "
                    + f.length() + "\r\nConnection: close\r\n\r\n";
            out.write(hd.getBytes("UTF-8"));
            FileInputStream fi = new FileInputStream(f);
            byte[] buf = new byte[16384];
            int n;
            while ((n = fi.read(buf)) > 0) out.write(buf, 0, n);
            fi.close();
            out.flush();
        } else if (path.equals("/")) {
            send(out, 200, "text/html; charset=utf-8", page().getBytes("UTF-8"));
        } else {
            send(out, 404, "text/plain", "Not found".getBytes("UTF-8"));
        }
    }

    private String page() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"" + T.code() + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>NookPoint</title><style>body{font-family:sans-serif;max-width:640px;margin:20px auto;"
                + "padding:0 12px}li{margin:8px 0}button{font-size:18px;padding:6px 14px}"
                + "a{color:#000}</style></head><body><h1>NookPoint</h1>"
                + "<form method=\"post\" action=\"/upload\" enctype=\"multipart/form-data\">"
                + "<p><input type=\"file\" name=\"f\" multiple accept=\".epub,.txt,.ttf,.otf,.ifo,.idx,.dict,.dz,.syn\"></p>"
                + "<p><button type=\"submit\">" + T.t("Upload") + "</button></p></form><h2>" + T.t("Books") + "</h2><ul>");
        File[] fs = root.listFiles();
        if (fs != null) {
            Arrays.sort(fs);
            for (int i = 0; i < fs.length; i++) {
                if (!fs[i].isFile()) continue;
                String n = fs[i].getName();
                String enc = URLEncoder.encode(n, "UTF-8");
                sb.append("<li><a href=\"/download?f=").append(enc).append("\">").append(esc(n))
                        .append("</a> (").append(fs[i].length() / 1024).append(" KB) &nbsp; "
                        + "<a href=\"/delete?f=").append(enc)
                        .append("\" onclick=\"return confirm('" + T.t("Delete") + "?')\">" + T.t("Delete") + "</a></li>");
            }
        }
        sb.append("</ul><p>" + T.t("Fonts (.ttf, .otf) go to the fonts folder, StarDict dictionaries (.ifo, .idx, .dict, .dict.dz, .syn) to the dictionaries folder.") + "</p></body></html>");
        return sb.toString();
    }

    private void upload(InputStream in, String ctype, long len) throws Exception {
        int bi = ctype.indexOf("boundary=");
        if (bi < 0 || len <= 0) return;
        String boundary = ctype.substring(bi + 9).trim();
        if (boundary.startsWith("\"")) boundary = boundary.substring(1, boundary.length() - 1);

        root.mkdirs();
        File tmp = new File(root, ".upload.tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[16384];
            long left = len;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                fo.write(buf, 0, n);
                left -= n;
            }
        } finally {
            fo.close();
        }

        RandomAccessFile raf = new RandomAccessFile(tmp, "r");
        try {
            byte[] bnd = ("--" + boundary).getBytes("ISO-8859-1");
            byte[] delim = ("\r\n--" + boundary).getBytes("ISO-8859-1");
            byte[] hend = "\r\n\r\n".getBytes("ISO-8859-1");
            long pos = indexOf(raf, bnd, 0);
            while (pos >= 0) {
                long hs = pos + bnd.length;
                raf.seek(hs);
                int c1 = raf.read(), c2 = raf.read();
                if (c1 == '-' && c2 == '-') break;
                long he = indexOf(raf, hend, hs);
                if (he < 0) break;
                byte[] hb = new byte[(int) (he - hs)];
                raf.seek(hs);
                raf.readFully(hb);
                String headers = new String(hb, "UTF-8");
                long ds = he + 4;
                long de = indexOf(raf, delim, ds);
                if (de < 0) break;
                String fname = null;
                int fi = headers.indexOf("filename=\"");
                if (fi >= 0) {
                    int fe = headers.indexOf('"', fi + 10);
                    if (fe > fi) fname = headers.substring(fi + 10, fe);
                }
                if (fname != null && fname.length() > 0) {
                    fname = safe(fname);
                    String lo = fname.toLowerCase();
                    File dstDir = root;
                    if (lo.endsWith(".ttf") || lo.endsWith(".otf")) dstDir = Prefs.fontsDir();
                    else if (lo.endsWith(".ifo") || lo.endsWith(".idx") || lo.endsWith(".dict") || lo.endsWith(".dz")
                            || lo.endsWith(".syn")) dstDir = Prefs.dictDir();
                    dstDir.mkdirs();
                    copyRange(raf, ds, de, new File(dstDir, fname));
                }
                pos = de + 2;
            }
        } finally {
            raf.close();
            tmp.delete();
        }
    }

    private static void copyRange(RandomAccessFile raf, long from, long to, File dst) throws IOException {
        FileOutputStream fo = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[16384];
            raf.seek(from);
            long left = to - from;
            while (left > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                fo.write(buf, 0, n);
                left -= n;
            }
        } finally {
            fo.close();
        }
    }

    private static long indexOf(RandomAccessFile f, byte[] pat, long from) throws IOException {
        byte[] buf = new byte[65536];
        long len = f.length();
        long pos = from;
        while (pos < len) {
            f.seek(pos);
            int n = f.read(buf, 0, (int) Math.min(buf.length, len - pos));
            if (n <= 0) break;
            for (int i = 0; i <= n - pat.length; i++) {
                boolean ok = true;
                for (int j = 0; j < pat.length; j++) {
                    if (buf[i + j] != pat[j]) { ok = false; break; }
                }
                if (ok) return pos + i;
            }
            if (pos + n >= len) break;
            pos += n - pat.length + 1;
        }
        return -1;
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int c;
        boolean any = false;
        while ((c = in.read()) >= 0) {
            any = true;
            if (c == '\n') break;
            if (c != '\r') bo.write(c);
        }
        if (!any) return null;
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static String param(String query, String key) throws Exception {
        String[] kv = query.split("&");
        for (int i = 0; i < kv.length; i++) {
            int e = kv[i].indexOf('=');
            if (e > 0 && kv[i].substring(0, e).equals(key)) return URLDecoder.decode(kv[i].substring(e + 1), "UTF-8");
        }
        return "";
    }

    private static String safe(String name) {
        name = new File(name.replace('\\', '/')).getName();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        while (name.startsWith(".")) name = name.substring(1);
        return name.length() == 0 ? "file" : name;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void redirect(OutputStream out) throws IOException {
        out.write("HTTP/1.1 303 See Other\r\nLocation: /\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                .getBytes("UTF-8"));
        out.flush();
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String hd = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(hd.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }
}
