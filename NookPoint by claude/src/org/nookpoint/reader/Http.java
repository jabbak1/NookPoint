package org.nookpoint.reader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Minimal HTTP client (Basic auth, manual redirects) for OPDS catalogs and downloads. */
class Http {
    interface Progress { boolean update(long done, long total); }     // return false to cancel

    static class Resp {
        byte[] body;
        String url;
        String type = "";
    }

    static final String UA = "NookPoint/1.0";

    private static HttpURLConnection open(String url, String user, String pass) throws IOException {
        String cur = url;
        for (int hop = 0; hop < 7; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml, application/epub+zip, */*");
            if (user != null && user.length() > 0) {
                c.setRequestProperty("Authorization", "Basic " + B64.encode((user + ":" + (pass == null ? "" : pass)).getBytes("UTF-8")));
            }
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect without location");
                cur = new URL(new URL(cur), loc).toString();
                continue;
            }
            if (code == 401 || code == 403) { c.disconnect(); throw new IOException("HTTP " + code + ": login required or wrong password"); }
            if (code >= 400) { c.disconnect(); throw new IOException("HTTP " + code); }
            return c;
        }
        throw new IOException("Too many redirects");
    }

    static Resp get(String url, String user, String pass, int maxBytes) throws IOException {
        HttpURLConnection c = open(url, user, pass);
        try {
            Resp r = new Resp();
            r.url = c.getURL().toString();
            String t = c.getContentType();
            r.type = t == null ? "" : t;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > maxBytes) throw new IOException("Response too large");
            }
            in.close();
            r.body = bo.toByteArray();
            return r;
        } finally {
            c.disconnect();
        }
    }

    static void download(String url, String user, String pass, File dst, Progress p) throws IOException {
        HttpURLConnection c = open(url, user, pass);
        File tmp = new File(dst.getParentFile(), dst.getName() + ".part");
        try {
            long total = c.getContentLength();
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(tmp);
            boolean ok = false;
            try {
                byte[] buf = new byte[16384];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (p != null && !p.update(done, total)) throw new IOException("cancelled");
                }
                ok = true;
            } finally {
                out.close();
                in.close();
                if (!ok) tmp.delete();
            }
            if (dst.exists()) dst.delete();
            if (!tmp.renameTo(dst)) throw new IOException("Cannot save " + dst.getName());
        } finally {
            c.disconnect();
        }
    }
}
