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
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** StarDict dictionary (.ifo + .idx + .dict or .dict.dz): binary search on a cached offset table. */
class StarDict {
    String name, seq;
    final File ifo, idx, dict;
    boolean dz;
    int offBits = 32;
    private int[] offsets;
    private int chlen, cachedIdx = -1;
    private int[] chunkSize;
    private long[] chunkStart;
    private byte[] cachedChunk;

    private static class Entry { byte[] word; long off; int size; }

    StarDict(File ifo) throws IOException {
        this.ifo = ifo;
        String p = ifo.getAbsolutePath();
        String base = p.substring(0, p.length() - 4);
        String fn = ifo.getName();
        name = fn.substring(0, fn.length() - 4);
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
            }
        } finally {
            r.close();
        }
        idx = new File(base + ".idx");
        File d = new File(base + ".dict");
        if (d.exists()) {
            dict = d;
        } else {
            dict = new File(base + ".dict.dz");
            dz = true;
        }
        if (!idx.exists() || !dict.exists()) throw new IOException("incomplete dictionary");
    }

    boolean indexReady() {
        return offsets != null || new File(idx.getAbsolutePath() + ".nidx").exists();
    }

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

    synchronized void ensureIndex() throws IOException {
        if (offsets != null) return;
        File cache = new File(idx.getAbsolutePath() + ".nidx");
        long ilen = idx.length();
        if (cache.exists()) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache), 65536));
            try {
                long len = in.readLong();
                int n = in.readInt();
                if (len == ilen && n >= 0 && n < 50000000) {
                    int[] o = new int[n];
                    for (int i = 0; i < n; i++) o[i] = in.readInt();
                    offsets = o;
                    return;
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
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(idx), 65536);
        try {
            long pos = 0;
            while (true) {
                long start = pos;
                int b = in.read();
                if (b < 0) break;
                pos++;
                while (b > 0) { b = in.read(); pos++; }
                if (b < 0) break;
                if (!readFully(in, t)) break;
                pos += tail;
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
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cache), 65536));
            try {
                out.writeLong(ilen);
                out.writeInt(n);
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
    static int cmp(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = a[i] & 0xff, y = b[i] & 0xff;
            if (x >= 'A' && x <= 'Z') x += 32;
            if (y >= 'A' && y <= 'Z') y += 32;
            if (x != y) return x < y ? -1 : 1;
        }
        return a.length == b.length ? 0 : (a.length < b.length ? -1 : 1);
    }

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

    /** {headword, definition text} for the first matching form of the word, or null. */
    String[] find(String word) throws IOException {
        ensureIndex();
        if (offsets.length == 0) return null;
        RandomAccessFile ri = new RandomAccessFile(idx, "r");
        RandomAccessFile rd = new RandomAccessFile(dict, "r");
        try {
            if (dz) parseDz(rd);
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
                    sb.append(decode(readDict(rd, e.off, e.size), seq));
                    if (sb.length() > 3000) break;
                }
                if (head != null) return new String[]{head, sb.toString()};
            }
            // inflected forms: look for a headword that starts with the stem (e.g. "книги" -> "книга")
            String lo = word.toLowerCase();
            for (int k = 1; k <= 3 && lo.length() - k >= 3; k++) {
                String stem = lo.substring(0, lo.length() - k);
                byte[] t = stem.getBytes("UTF-8");
                int lb = lowerBound(ri, t);
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
                if (best != null) return new String[]{bestWord, decode(readDict(rd, best.off, best.size), seq)};
            }
        } finally {
            ri.close();
            rd.close();
        }
        return null;
    }

    /** A few headwords that start like the given word. */
    ArrayList<String> similar(String word, int max) throws IOException {
        ArrayList<String> r = new ArrayList<String>();
        ensureIndex();
        if (offsets.length == 0 || word.length() < 2) return r;
        String lo = word.toLowerCase();
        String prefix = lo.substring(0, Math.min(lo.length(), 3));
        RandomAccessFile ri = new RandomAccessFile(idx, "r");
        try {
            int lb = lowerBound(ri, lo.substring(0, Math.min(lo.length(), 4)).getBytes("UTF-8"));
            for (int i = lb; i < offsets.length && i < lb + 40 && r.size() < max; i++) {
                String h = new String(entry(ri, i).word, "UTF-8");
                String hl = h.toLowerCase();
                if (!hl.startsWith(prefix)) break;
                if (!r.contains(h)) r.add(h);
            }
        } finally {
            ri.close();
        }
        return r;
    }

    // ------------------------------------------------------------------ dictionary data
    private byte[] readDict(RandomAccessFile f, long off, int size) throws IOException {
        if (size < 0 || size > 4000000) size = 4000000;
        byte[] out = new byte[size];
        if (!dz) {
            f.seek(off);
            f.readFully(out);
            return out;
        }
        int done = 0;
        while (done < size) {
            long pos = off + done;
            int ci = (int) (pos / chlen), in = (int) (pos % chlen);
            if (ci >= chunkSize.length) break;
            byte[] chunk = chunk(f, ci);
            int n = Math.min(size - done, chunk.length - in);
            if (n <= 0) break;
            System.arraycopy(chunk, in, out, done, n);
            done += n;
        }
        return out;
    }

    private static int le16(byte[] b, int p) { return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8); }

    private void parseDz(RandomAccessFile f) throws IOException {
        if (chunkSize != null) return;
        f.seek(0);
        if (f.read() != 0x1f || f.read() != 0x8b) throw new IOException("not gzip");
        f.read();
        int flg = f.read();
        f.skipBytes(6);
        if ((flg & 4) == 0) throw new IOException("not dictzip");
        int xlen = f.read() | (f.read() << 8);
        byte[] extra = new byte[xlen];
        f.readFully(extra);
        int p = 0;
        int[] sizes = null;
        while (p + 4 <= xlen) {
            int len = le16(extra, p + 2);
            if (extra[p] == 'R' && extra[p + 1] == 'A') {
                int q = p + 4;
                chlen = le16(extra, q + 2);
                int cnt = le16(extra, q + 4);
                sizes = new int[cnt];
                for (int i = 0; i < cnt; i++) sizes[i] = le16(extra, q + 6 + 2 * i);
                break;
            }
            p += 4 + len;
        }
        if (sizes == null || chlen <= 0) throw new IOException("no dictzip header");
        int c;
        if ((flg & 8) != 0) while ((c = f.read()) > 0) { }
        if ((flg & 16) != 0) while ((c = f.read()) > 0) { }
        if ((flg & 2) != 0) f.skipBytes(2);
        long start = f.getFilePointer();
        long[] cs = new long[sizes.length + 1];
        cs[0] = start;
        for (int i = 0; i < sizes.length; i++) cs[i + 1] = cs[i] + sizes[i];
        chunkSize = sizes;
        chunkStart = cs;
    }

    private byte[] chunk(RandomAccessFile f, int ci) throws IOException {
        if (ci == cachedIdx) return cachedChunk;
        f.seek(chunkStart[ci]);
        byte[] comp = new byte[chunkSize[ci] + 1];
        f.readFully(comp, 0, chunkSize[ci]);
        Inflater inf = new Inflater(true);
        inf.setInput(comp);
        byte[] out = new byte[chlen];
        int got = 0;
        try {
            while (got < chlen && !inf.finished()) {
                int n = inf.inflate(out, got, chlen - got);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                got += n;
            }
        } catch (DataFormatException e) {
            throw new IOException("bad dictzip chunk");
        } finally {
            inf.end();
        }
        if (got < chlen) {
            byte[] s = new byte[got];
            System.arraycopy(out, 0, s, 0, got);
            out = s;
        }
        cachedIdx = ci;
        cachedChunk = out;
        return out;
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
