package org.nookpoint.reader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Unpacks .zip and .tar.gz dictionary downloads (only the dictionary files, flattened). */
class Archives {
    static boolean isArchive(String lower) {
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
    }

    private static boolean wanted(String name) {
        String l = name.toLowerCase();
        return l.endsWith(".ifo") || l.endsWith(".idx") || l.endsWith(".idx.gz") || l.endsWith(".dict")
                || l.endsWith(".dict.dz") || l.endsWith(".syn") || l.endsWith(".index");
    }

    private static String baseName(String path) {
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    /** Returns the number of dictionary files extracted. */
    static int unpack(File archive, File destDir) throws IOException {
        destDir.mkdirs();
        if (archive.getName().toLowerCase().endsWith(".zip")) return unzip(archive, destDir);
        InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archive), 65536));
        try {
            return untar(in, destDir);
        } finally {
            in.close();
        }
    }

    private static void copy(InputStream in, File dst, long size) throws IOException {
        OutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[32768];
            long left = size;
            while (left != 0) {
                int n = in.read(buf, 0, left < 0 ? buf.length : (int) Math.min(buf.length, left));
                if (n < 0) break;
                out.write(buf, 0, n);
                if (left > 0) left -= n;
            }
        } finally {
            out.close();
        }
    }

    private static int unzip(File archive, File destDir) throws IOException {
        ZipFile zf = new ZipFile(archive);
        int count = 0;
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !wanted(e.getName())) continue;
                InputStream in = zf.getInputStream(e);
                try {
                    copy(in, new File(destDir, baseName(e.getName())), -1);
                } finally {
                    in.close();
                }
                count++;
            }
        } finally {
            zf.close();
        }
        return count;
    }

    private static boolean readFully(InputStream in, byte[] b, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int r = in.read(b, n, len - n);
            if (r < 0) return false;
            n += r;
        }
        return true;
    }

    private static String cstr(byte[] b, int off, int len) {
        int e = off;
        while (e < off + len && b[e] != 0) e++;
        try { return new String(b, off, e - off, "UTF-8"); } catch (Exception x) { return ""; }
    }

    private static long octal(byte[] b, int off, int len) {
        long n = 0;
        for (int i = off; i < off + len; i++) {
            int c = b[i];
            if (c >= '0' && c <= '7') n = n * 8 + (c - '0');
            else if (n > 0 || c == 0) break;
        }
        return n;
    }

    private static void skip(InputStream in, long n) throws IOException {
        byte[] buf = new byte[8192];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) return;
            n -= r;
        }
    }

    private static int untar(InputStream in, File destDir) throws IOException {
        byte[] h = new byte[512];
        int count = 0;
        String longName = null;
        while (readFully(in, h, 512)) {
            if (h[0] == 0) break;
            String name = cstr(h, 0, 100);
            if (h[257] == 'u' && h[258] == 's' && h[259] == 't' && h[260] == 'a' && h[261] == 'r') {
                String prefix = cstr(h, 345, 155);
                if (prefix.length() > 0) name = prefix + "/" + name;
            }
            long size = octal(h, 124, 12);
            int type = h[156];
            long pad = (512 - size % 512) % 512;
            if (type == 'L') {                                   // GNU long file name
                byte[] nb = new byte[(int) size];
                if (!readFully(in, nb, nb.length)) break;
                longName = cstr(nb, 0, nb.length);
                skip(in, pad);
                continue;
            }
            if (longName != null) { name = longName; longName = null; }
            if ((type == '0' || type == 0) && wanted(name)) {
                copy(in, new File(destDir, baseName(name)), size);
                count++;
            } else {
                skip(in, size);
            }
            skip(in, pad);
        }
        return count;
    }
}
