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
 * StarDict dictionary (.ifo + .idx[.gz] + .dict[.dz]).
 * The index is sampled (every STRIDE-th entry is remembered, cached next to the dictionary): lookups binary
 * search the samples and then read a few entries sequentially. Index order is detected while building:
 * mode 0 = ASCII case-insensitive bytes (the StarDict rule), mode 1 = Unicode lower-case order,
 * mode 2 = unsorted (slow sequential scan).
 */
class StarDict implements Dictionary {
    String name, seq;
    long wordCount = -1;
    final File ifo;
    File idx, idxGz, dict;
    int offBits = 32;

    private static final int STRIDE = 8;
    private static final int MAGIC = 0x4E504933;
    private int[] samples;
    private int mode;
    private Dicts.Progress progress;

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
    public void setProgress(Dicts.Progress p) { progress = p; }

    private File cacheFile() { return new File(idx.getAbsolutePath() + ".nidx"); }

    public boolean indexReady() {
        if (samples != null) return true;
        File c = cacheFile();
        if (!c.exists() || !idx.exists()) return false;
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(c), 64));
            try {
                return in.readInt() == MAGIC && in.readLong() == idx.length();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ index
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
        if (samples != null) return;
        if (!idx.exists() && idxGz != null) unzipIdx();
        File cache = cacheFile();
        long ilen = idx.length();
        if (cache.exists()) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache), 65536));
            try {
                if (in.readInt() == MAGIC && in.readLong() == ilen) {
                    int count = in.readInt();
                    int m = in.readByte();
                    int ns = in.readInt();
                    if (ns >= 0 && ns < 20000000) {
                        int[] s = new int[ns];
                        for (int i = 0; i < ns; i++) s[i] = in.readInt();
                        wordCount = count;
                        mode = m;
                        samples = s;
                        return;
                    }
                }
            } catch (IOException e) {
                // rebuild below
            } finally {
                in.close();
            }
        }
        buildIndex(cache, ilen);
    }

    private void buildIndex(File cache, long ilen) throws IOException {
        int cap = wordCount > 0 ? (int) (wordCount / STRIDE) + 16 : 1024;
        int[] s = new int[cap];
        int ns = 0;
        long n = 0;
        byte[] prev = new byte[256];
        int pl = 0, lastPct = -1;
        long violA = 0, violB = 0;
        boolean checkB = false;
        String prevLow = null;
        RandomAccessFile f = new RandomAccessFile(idx, "r");
        try {
            IdxStream st = new IdxStream(f, 0, offBits, 65536);
            while (st.next()) {
                // order check on every entry: byte order first; once that fails, Unicode lower-case order too
                if (n > 0 && cmp(prev, pl, st.w, st.wl) > 0) {
                    violA++;
                    if (!checkB) {
                        checkB = true;
                        prevLow = new String(prev, 0, pl, "UTF-8").toLowerCase();
                    }
                }
                if (checkB) {
                    String low = new String(st.w, 0, st.wl, "UTF-8").toLowerCase();
                    if (prevLow != null && prevLow.compareTo(low) > 0) violB++;
                    prevLow = low;
                }
                System.arraycopy(st.w, 0, prev, 0, st.wl);
                pl = st.wl;
                if (n % STRIDE == 0) {
                    if (ns == s.length) {
                        int[] g = new int[ns * 2];
                        System.arraycopy(s, 0, g, 0, ns);
                        s = g;
                    }
                    s[ns++] = (int) st.entryPos;
                    int pct = ilen > 0 ? (int) (st.entryPos * 100 / ilen) : 100;
                    if (pct != lastPct) {
                        lastPct = pct;
                        if (progress != null && !progress.update(name, pct)) throw new IOException("cancelled");
                    }
                }
                n++;
            }
        } finally {
            f.close();
        }
        int[] res = new int[ns];
        System.arraycopy(s, 0, res, 0, ns);
        long tol = Math.max(0, n / 2000);
        int m;
        if (violA == 0) m = 0;
        else if (violB == 0) m = 1;
        else if (Math.min(violA, violB) <= tol) m = violA <= violB ? 0 : 1;   // a few stray entries are tolerated
        else m = 2;
        wordCount = n;
        mode = m;
        samples = res;
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cache), 65536));
            try {
                out.writeInt(MAGIC);
                out.writeLong(ilen);
                out.writeInt((int) n);
                out.writeByte(m);
                out.writeInt(ns);
                for (int i = 0; i < ns; i++) out.writeInt(res[i]);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            // read-only card: keep the in-memory table
        }
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

    private int cmpKey(IdxStream s, byte[] kb, String kl) throws IOException {
        if (mode == 0) return cmp(s.w, s.wl, kb, kb.length);
        return new String(s.w, 0, s.wl, "UTF-8").toLowerCase().compareTo(kl);
    }

    /** File offset of the first entry whose word is >= key (sorted modes). */
    private long seek(RandomAccessFile f, String key) throws IOException {
        if (samples.length == 0) return idx.length();
        byte[] kb = key.getBytes("UTF-8");
        String kl = key.toLowerCase();
        int lo = 0, hi = samples.length - 1, best = 0;
        boolean any = false;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            IdxStream s = new IdxStream(f, samples[mid], offBits, 2048);
            if (!s.next()) break;
            if (cmpKey(s, kb, kl) < 0) { best = mid; any = true; lo = mid + 1; } else hi = mid - 1;
        }
        IdxStream s = new IdxStream(f, samples[any ? best : 0], offBits, 8192);
        while (s.next()) {
            if (cmpKey(s, kb, kl) >= 0) return s.entryPos;
        }
        return idx.length();
    }

    // ------------------------------------------------------------------ lookup
    private ArrayList<String> variants(String w) {
        ArrayList<String> v = new ArrayList<String>();
        String lo = w.toLowerCase();
        if (mode == 0) {
            v.add(w);
            if (!lo.equals(w)) v.add(lo);
            if (lo.length() > 1) {
                String cap = Character.toUpperCase(lo.charAt(0)) + lo.substring(1);
                if (!cap.equals(w)) v.add(cap);
            }
        } else {
            v.add(lo);
        }
        if (lo.endsWith("'s") && lo.length() > 4) v.add(lo.substring(0, lo.length() - 2));
        for (int k = 1; k <= 4; k++) if (lo.length() - k >= 3) v.add(lo.substring(0, lo.length() - k));
        return v;
    }

    public String[] find(String word) throws IOException {
        ensureIndex();
        if (samples.length == 0) return null;
        RandomAccessFile f = new RandomAccessFile(idx, "r");
        DictFile df = new DictFile(dict);
        try {
            return mode == 2 ? findLinear(f, df, word) : findSorted(f, df, word);
        } finally {
            f.close();
            df.close();
        }
    }

    private String[] findSorted(RandomAccessFile f, DictFile df, String word) throws IOException {
        ArrayList<String> vs = variants(word);
        for (int k = 0; k < vs.size(); k++) {
            String v = vs.get(k);
            byte[] kb = v.getBytes("UTF-8");
            String kl = v.toLowerCase();
            IdxStream s = new IdxStream(f, seek(f, v), offBits, 8192);
            StringBuilder sb = new StringBuilder();
            String head = null;
            int cnt = 0;
            while (cnt < 4 && s.next() && cmpKey(s, kb, kl) == 0) {
                if (head == null) head = new String(s.w, 0, s.wl, "UTF-8");
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(decode(df.read(s.off, s.size), seq));
                cnt++;
                if (sb.length() > 3000) break;
            }
            if (head != null) return new String[]{head, sb.toString()};
        }
        // inflected forms: a headword that starts with the stem ("книги" -> "книга")
        String lo = word.toLowerCase();
        for (int k = 1; k <= 3 && lo.length() - k >= 3; k++) {
            String stem = lo.substring(0, lo.length() - k);
            IdxStream s = new IdxStream(f, seek(f, stem), offBits, 8192);
            long bo = 0;
            int bs = 0;
            String bestWord = null;
            for (int i = 0; i < 40 && s.next(); i++) {
                String hw = new String(s.w, 0, s.wl, "UTF-8");
                if (!hw.toLowerCase().startsWith(stem)) break;
                if (hw.length() <= stem.length() + 3 && (bestWord == null || hw.length() < bestWord.length())) {
                    bestWord = hw;
                    bo = s.off;
                    bs = s.size;
                }
            }
            if (bestWord != null) return new String[]{bestWord, decode(df.read(bo, bs), seq)};
        }
        return null;
    }

    /** For indexes that are not sorted at all: one sequential pass. */
    private String[] findLinear(RandomAccessFile f, DictFile df, String word) throws IOException {
        String lo = word.toLowerCase();
        long[] bo = new long[4], exOff = new long[4];
        int[] bs = new int[4], exSize = new int[4];
        String[] bw = new String[4];
        int exN = 0;
        String exHead = null;
        IdxStream s = new IdxStream(f, 0, offBits, 65536);
        long total = idx.length();
        int lastPct = -1, count = 0;
        while (s.next()) {
            String hw = new String(s.w, 0, s.wl, "UTF-8");
            String hl = hw.toLowerCase();
            if (hl.equals(lo)) {
                if (exN < 4) { exOff[exN] = s.off; exSize[exN] = s.size; exN++; }
                if (exHead == null) exHead = hw;
            } else {
                for (int k = 1; k <= 3; k++) {
                    if (lo.length() - k < 3) break;
                    String stem = lo.substring(0, lo.length() - k);
                    if (hl.startsWith(stem) && hw.length() <= stem.length() + 3 && (bw[k] == null || hw.length() < bw[k].length())) {
                        bw[k] = hw; bo[k] = s.off; bs[k] = s.size;
                    }
                }
            }
            if ((++count & 4095) == 0 && progress != null) {
                int pct = (int) (s.entryPos * 100 / Math.max(total, 1));
                if (pct != lastPct) { lastPct = pct; if (!progress.update(name, pct)) throw new IOException("cancelled"); }
            }
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
        if (samples.length == 0 || word.length() < 2) return r;
        String lo = word.toLowerCase();
        String prefix = lo.substring(0, Math.min(lo.length(), 3));
        RandomAccessFile f = new RandomAccessFile(idx, "r");
        try {
            long start = 0;
            int limit = 20000;
            if (mode != 2) { start = seek(f, lo.substring(0, Math.min(lo.length(), 4))); limit = 60; }
            IdxStream s = new IdxStream(f, start, offBits, 8192);
            for (int i = 0; i < limit && r.size() < max && s.next(); i++) {
                String h = new String(s.w, 0, s.wl, "UTF-8");
                if (h.toLowerCase().startsWith(prefix)) { if (!r.contains(h)) r.add(h); }
                else if (mode != 2) break;
            }
        } finally {
            f.close();
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
                    int sz = IdxStream.be32(d, p);
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
