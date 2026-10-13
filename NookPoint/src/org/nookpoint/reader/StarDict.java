package org.nookpoint.reader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

/**
 * StarDict dictionary (.ifo + .idx[.gz] + .dict[.dz]). The word index is turned into an offset table
 * (cached next to the dictionary); sorted indexes use binary search, others fall back to a linear scan.
 */
class StarDict implements Dictionary {
    String name, seq;
    long wordCount = -1;
    final File ifo;
    File idx, idxGz, dict;
    int offBits = 32;
    private int[] offsets;
    private boolean sorted = true;

    private static final int MAGIC = 0x4E504932;

    private static class Entry { byte[] word; long off; int size; }

    StarDict(File ifo) throws IOException {
        this.ifo = ifo;
        String fn = ifo.getName();
        String bn = fn.substring(0, fn.length() - 4);
        name = bn;
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(ifo), "UTF-8"));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                int e = line.indexOf('=');
                if (e <= 0) continue;
                String k = line.substring(0, e).trim(), v = line.substring(e + 1).trim();
                if (k.equals("bookname") && v.length() > 0) name = v;
                else if (k.equals("sametypesequence")) seq = v;
                else if (k.equals("idxoffsetbits")) offBits = v.equals("64") ? 64 : 32;
                else if (k.equals("wordcount")) { try { wordCount = Long.parseLong(v); } catch (Exception x) { } }
            }
        } finally {
            r.close();
        }
        File dir = ifo.getParentFile();
        idx = Dicts.sibling(dir, bn + ".idx");
        if (idx == null) {
            idxGz = Dicts.sibling(dir, bn + ".idx.gz");
            if (idxGz == null) throw new IOException("Missing .idx file");
            idx = new File(dir, bn + ".idx");
        }
        dict = Dicts.sibling(dir, bn + ".dict");
        if (dict == null) dict = Dicts.sibling(dir, bn + ".dict.dz");
        if (dict == null) throw new IOException("Missing .dict or .dict.dz file");
    }

    public String getName() { return name; }
    public String getPath() { return ifo.getAbsolutePath(); }
    public long getWordCount() { return wordCount; }
    public boolean indexReady() { return offsets != null || new File(idx.getAbsolutePath() + ".nidx").exists(); }

    // ------------------------------------------------------------------ index
    private static boolean readFully(InputStream in, byte[] b) throws IOException {
        int n = 0;
        while (n < b.length) {
            int r = in.read(b, n, b.length - n);
            if (r < 0) return false;
            n += r;
        }
        return true;
    }

    private void unzipIdx() throws IOException {
        InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(idxGz), 65536));
        OutputStream out = new BufferedOutputStream(new FileOutputStream(idx), 65536);
        try {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            out.close();
            in.close();
        }
    }

    synchronized void ensureIndex() throws IOException {
        if (offsets != null) return;
        if (!idx.exists() && idxGz != null) unzipIdx();
        File cache = new File(idx.getAbsolutePath() + ".nidx");
        long ilen = idx.length();
        if (cache.exists()) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache), 65536));
            try {
                if (in.readInt() == MAGIC && in.readLong() == ilen) {
                    int n = in.readInt();
                    boolean s = in.readByte() != 0;
                    if (n >= 0 && n < 50000000) {
                        int[] o = new int[n];
                        for (int i = 0; i < n; i++) o[i] = in.readInt();
                        offsets = o;
                        sorted = s;
                        return;
                    }
                }
            } catch (IOException e) {
                // rebuild below
            } finally {
                in.close();
            }
        }
        int tail = offBits == 64 ? 12 : 8;
        byte[] t = new byte[tail];
        int[] o = new int[1024];
        int n = 0;
        byte[] w = new byte[256], prev = new byte[256];
        int wl = 0, pl = 0;
        boolean ordered = true;
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(idx), 65536);
        try {
            long pos = 0;
            while (true) {
                long start = pos;
                int b = in.read();
                if (b < 0) break;
                wl = 0;
                while (b > 0) {
                    if (wl < 256) w[wl++] = (byte) b;
                    b = in.read();
                    pos++;
                }
                pos++;
                if (b < 0) break;
                if (!readFully(in, t)) break;
                pos += tail;
                if (n > 0 && cmp(prev, pl, w, wl) > 0) ordered = false;
                byte[] sw = prev; prev = w; w = sw;
                pl = wl;
                if (n == o.length) {
                    int[] g = new int[n * 2];
                    System.arraycopy(o, 0, g, 0, n);
                    o = g;
                }
                o[n++] = (int) start;
            }
        } finally {
            in.close();
        }
        int[] res = new int[n];
        System.arraycopy(o, 0, res, 0, n);
        offsets = res;
        sorted = ordered;
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cache), 65536));
            try {
                out.writeInt(MAGIC);
                out.writeLong(ilen);
                out.writeInt(n);
                out.writeByte(ordered ? 1 : 0);
                for (int i = 0; i < n; i++) out.writeInt(res[i]);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            // read-only card: keep the in-memory table
        }
    }

    private static int be32(byte[] b, int p) {
        return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
    }

    private Entry entry(RandomAccessFile ri, int i) throws IOException {
        ri.seek(offsets[i]);
        byte[] buf = new byte[256 + 12];
        int n = ri.read(buf);
        int z = 0;
        while (z < n && buf[z] != 0) z++;
        int tail = offBits == 64 ? 12 : 8;
        if (z + 1 + tail > n) throw new IOException("bad index");
        Entry e = new Entry();
        e.word = new byte[z];
        System.arraycopy(buf, 0, e.word, 0, z);
        int p = z + 1;
        if (offBits == 64) {
            e.off = (((long) be32(buf, p)) << 32) | (be32(buf, p + 4) & 0xffffffffL);
            p += 8;
        } else {
            e.off = be32(buf, p) & 0xffffffffL;
            p += 4;
        }
        e.size = be32(buf, p);
        return e;
    }

    /** ASCII case-insensitive, then bytewise (unsigned): the order StarDict indexes use. */
    static int cmp(byte[] a, int al, byte[] b, int bl) {
        int n = Math.min(al, bl);
        for (int i = 0; i < n; i++) {
            int x = a[i] & 0xff, y = b[i] & 0xff;
            if (x >= 'A' && x <= 'Z') x += 32;
            if (y >= 'A' && y <= 'Z') y += 32;
            if (x != y) return x < y ? -1 : 1;
        }
        return al == bl ? 0 : (al < bl ? -1 : 1);
    }

    static int cmp(byte[] a, byte[] b) { return cmp(a, a.length, b, b.length); }

    private int lowerBound(RandomAccessFile ri, byte[] t) throws IOException {
        int lo = 0, hi = offsets.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cmp(entry(ri, mid).word, t) < 0) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    // ------------------------------------------------------------------ lookup
    private static ArrayList<String> variants(String w) {
        ArrayList<String> v = new ArrayList<String>();
        String lo = w.toLowerCase();
        v.add(w);
        if (!lo.equals(w)) v.add(lo);
        if (lo.length() > 1) {
            String cap = Character.toUpperCase(lo.charAt(0)) + lo.substring(1);
            if (!cap.equals(w)) v.add(cap);
        }
        if (lo.endsWith("'s") && lo.length() > 4) v.add(lo.substring(0, lo.length() - 2));
        for (int k = 1; k <= 4; k++) if (lo.length() - k >= 3) v.add(lo.substring(0, lo.length() - k));
        return v;
    }

    public String[] find(String word) throws IOException {
        ensureIndex();
        if (offsets.length == 0) return null;
        RandomAccessFile ri = new RandomAccessFile(idx, "r");
        DictFile df = new DictFile(dict);
        try {
            return sorted ? findSorted(ri, df, word) : findLinear(df, word);
        } finally {
            ri.close();
            df.close();
        }
    }

    private String[] findSorted(RandomAccessFile ri, DictFile df, String word) throws IOException {
        ArrayList<String> vs = variants(word);
        for (int k = 0; k < vs.size(); k++) {
            byte[] t = vs.get(k).getBytes("UTF-8");
            int lb = lowerBound(ri, t);
            StringBuilder sb = new StringBuilder();
            String head = null;
            for (int i = lb; i < offsets.length && i < lb + 4; i++) {
                Entry e = entry(ri, i);
                if (cmp(e.word, t) != 0) break;
                if (head == null) head = new String(e.word, "UTF-8");
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(decode(df.read(e.off, e.size), seq));
                if (sb.length() > 3000) break;
            }
            if (head != null) return new String[]{head, sb.toString()};
        }
        // inflected forms: a headword that starts with the stem ("книги" -> "книга")
        String lo = word.toLowerCase();
        for (int k = 1; k <= 3 && lo.length() - k >= 3; k++) {
            String stem = lo.substring(0, lo.length() - k);
            int lb = lowerBound(ri, stem.getBytes("UTF-8"));
            Entry best = null;
            String bestWord = null;
            for (int i = lb; i < offsets.length && i < lb + 30; i++) {
                Entry e = entry(ri, i);
                String hw = new String(e.word, "UTF-8");
                if (!hw.toLowerCase().startsWith(stem)) break;
                if (hw.length() <= stem.length() + 3 && (bestWord == null || hw.length() < bestWord.length())) {
                    best = e;
                    bestWord = hw;
                }
            }
            if (best != null) return new String[]{bestWord, decode(df.read(best.off, best.size), seq)};
        }
        return null;
    }

    /** For indexes that are not sorted the way StarDict requires: one sequential pass. */
    private String[] findLinear(DictFile df, String word) throws IOException {
        String lo = word.toLowerCase();
        int tail = offBits == 64 ? 12 : 8;
        byte[] t = new byte[tail], w = new byte[256];
        long[] bo = new long[4];
        int[] bs = new int[4];
        String[] bw = new String[4];
        long[] exOff = new long[4];
        int[] exSize = new int[4];
        int exN = 0;
        String exHead = null;
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(idx), 65536);
        try {
            while (true) {
                int b = in.read();
                if (b < 0) break;
                int wl = 0;
                while (b > 0) {
                    if (wl < 256) w[wl++] = (byte) b;
                    b = in.read();
                }
                if (b < 0 || !readFully(in, t)) break;
                String hw = new String(w, 0, wl, "UTF-8");
                String hl = hw.toLowerCase();
                long off;
                int size;
                if (offBits == 64) { off = (((long) be32(t, 0)) << 32) | (be32(t, 4) & 0xffffffffL); size = be32(t, 8); }
                else { off = be32(t, 0) & 0xffffffffL; size = be32(t, 4); }
                if (hl.equals(lo)) {
                    if (exN < 4) { exOff[exN] = off; exSize[exN] = size; exN++; }
                    if (exHead == null) exHead = hw;
                } else {
                    for (int k = 1; k <= 3; k++) {
                        if (lo.length() - k < 3) break;
                        String stem = lo.substring(0, lo.length() - k);
                        if (hl.startsWith(stem) && hw.length() <= stem.length() + 3 && (bw[k] == null || hw.length() < bw[k].length())) {
                            bw[k] = hw; bo[k] = off; bs[k] = size;
                        }
                    }
                }
            }
        } finally {
            in.close();
        }
        if (exHead != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < exN; i++) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(decode(df.read(exOff[i], exSize[i]), seq));
            }
            return new String[]{exHead, sb.toString()};
        }
        for (int k = 1; k <= 3; k++) if (bw[k] != null) return new String[]{bw[k], decode(df.read(bo[k], bs[k]), seq)};
        return null;
    }

    public ArrayList<String> similar(String word, int max) throws IOException {
        ArrayList<String> r = new ArrayList<String>();
        ensureIndex();
        if (offsets.length == 0 || word.length() < 2) return r;
        String lo = word.toLowerCase();
        String prefix = lo.substring(0, Math.min(lo.length(), 3));
        RandomAccessFile ri = new RandomAccessFile(idx, "r");
        try {
            int start = 0, stop = offsets.length;
            if (sorted) {
                start = lowerBound(ri, lo.substring(0, Math.min(lo.length(), 4)).getBytes("UTF-8"));
                stop = Math.min(offsets.length, start + 40);
            } else {
                stop = Math.min(offsets.length, 20000);
            }
            for (int i = start; i < stop && r.size() < max; i++) {
                String h = new String(entry(ri, i).word, "UTF-8");
                if (h.toLowerCase().startsWith(prefix)) { if (!r.contains(h)) r.add(h); }
                else if (sorted) break;
            }
        } finally {
            ri.close();
        }
        return r;
    }

    // ------------------------------------------------------------------ text
    static String decode(byte[] d, String seq) {
        StringBuilder sb = new StringBuilder();
        try {
            int p = 0;
            int fields = (seq != null && seq.length() > 0) ? seq.length() : Integer.MAX_VALUE;
            for (int k = 0; k < fields && p < d.length; k++) {
                char t;
                boolean last;
                if (seq != null && seq.length() > 0) { t = seq.charAt(k); last = k == seq.length() - 1; }
                else { t = (char) d[p++]; last = false; }
                int e;
                if (last) {
                    e = d.length;
                } else if (Character.isLowerCase(t)) {
                    e = p;
                    while (e < d.length && d[e] != 0) e++;
                } else {
                    if (p + 4 > d.length) break;
                    int sz = be32(d, p);
                    p += 4;
                    e = Math.min(d.length, p + Math.max(sz, 0));
                }
                if (Character.isLowerCase(t) || last) {
                    String s = new String(d, p, e - p, "UTF-8");
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(clean(t, s));
                }
                p = e + ((!last && Character.isLowerCase(t)) ? 1 : 0);
            }
        } catch (Exception ex) {
            // return what we have
        }
        return sb.toString().trim();
    }

    private static String clean(char type, String s) {
        if (type == 't' || type == 'y') return "[" + s + "]";
        if (type == 'h' || type == 'g' || type == 'x' || type == 'k') {
            s = s.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>", "\n").replaceAll("(?i)<li[^>]*>", "\n- ");
            s = s.replaceAll("<[^>]*>", "");
            s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&nbsp;", " ")
                    .replace("&apos;", "'").replace("&amp;", "&");
            s = s.replaceAll("\n{3,}", "\n\n");
        }
        return s.trim();
    }
}
