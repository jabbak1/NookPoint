package org.nookpoint.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

/**
 * Reader for Android/Chromium ".hyb" hyphenation dictionaries (compiled Liang patterns from TeX hyph-utf8).
 */
class Hyb {
    static File testDir;                                       // tests read files instead of assets
    private static final HashMap<String, Hyb> CACHE = new HashMap<String, Hyb>();

    private byte[] a0;                                         // alphabet table type 0
    private int min0, max0;
    private int[] a1;                                          // alphabet table type 1: (codepoint << 11) | index
    private int[] trie;
    private int charMask, linkShift, linkMask, patShift;
    private int[] pat;
    private byte[] data;
    private int patBuf;

    private static byte[] readAll(String name) throws IOException {
        InputStream in = testDir != null ? new FileInputStream(new File(testDir, "hyph-" + name + ".hyb"))
                : Prefs.assets().open("hyph/hyph-" + name + ".hyb");
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            in.close();
        }
    }

    private Hyb(byte[] b) throws IOException {
        data = b;
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt(0) != 0x62ad7968) throw new IOException("not a hyb file");
        int alphaOff = bb.getInt(8), trieOff = bb.getInt(12), patOff = bb.getInt(16);
        int av = bb.getInt(alphaOff);
        if (av == 0) {
            min0 = bb.getInt(alphaOff + 4);
            max0 = bb.getInt(alphaOff + 8);
            a0 = new byte[max0 - min0];
            System.arraycopy(b, alphaOff + 12, a0, 0, a0.length);
        } else {
            int n = bb.getInt(alphaOff + 4);
            a1 = new int[n];
            for (int i = 0; i < n; i++) a1[i] = bb.getInt(alphaOff + 8 + 4 * i);
        }
        charMask = bb.getInt(trieOff + 4);
        linkShift = bb.getInt(trieOff + 8);
        linkMask = bb.getInt(trieOff + 12);
        patShift = bb.getInt(trieOff + 16);
        int tn = bb.getInt(trieOff + 20);
        trie = new int[tn];
        for (int i = 0; i < tn; i++) trie[i] = bb.getInt(trieOff + 24 + 4 * i);
        int pn = bb.getInt(patOff + 4);
        int poff = bb.getInt(patOff + 8);
        pat = new int[pn];
        for (int i = 0; i < pn; i++) pat[i] = bb.getInt(patOff + 16 + 4 * i);
        patBuf = patOff + poff;
    }

    static synchronized Hyb get(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);
        Hyb h = null;
        try {
            h = new Hyb(readAll(name));
        } catch (Throwable t) {
            h = null;
        }
        CACHE.put(name, h);
        return h;
    }

    /** Dictionary for a book language (e.g. "ru", "en-GB"); the script of the word decides when unknown. */
    static Hyb forLang(String lang, char first) {
        String l = lang == null ? "" : lang.toLowerCase();
        String name = null;
        if (l.startsWith("ru")) name = "ru";
        else if (l.startsWith("uk")) name = "uk";
        else if (l.startsWith("bg")) name = "bg";
        else if (l.startsWith("en-gb") || l.startsWith("en_gb")) name = "en-gb";
        else if (l.startsWith("en")) name = "en-us";
        else if (l.startsWith("fr")) name = "fr";
        else if (l.startsWith("es")) name = "es";
        else if (l.startsWith("de")) name = "de-1901";
        else if (l.startsWith("it")) name = "it";
        else if (l.startsWith("pt")) name = "pt";
        else if (l.startsWith("pl")) name = "pl";
        if (name == null) name = first >= 0x400 && first < 0x530 ? "ru" : "en-us";
        Hyb h = get(name);
        if (h == null && !name.equals("ru") && !name.equals("en-us")) h = get(first >= 0x400 && first < 0x530 ? "ru" : "en-us");
        return h;
    }

    private int alpha(int cp) {
        if (a0 != null) {
            if (cp >= min0 && cp < max0) return a0[cp - min0] & 0xff;
            return 0;
        }
        int lo = 0, hi = a1.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = a1[mid] >>> 11;
            if (c == cp) return a1[mid] & 0x7ff;
            if (c < cp) lo = mid + 1; else hi = mid - 1;
        }
        return 0;
    }

    private int patEntry(int i) { return pat[i]; }

    /** brk[k] is true when a break is allowed after letter k (0-based). Null when the word has unknown letters. */
    boolean[] breaks(String word, int minPre, int minSuf) {
        String w = word.toLowerCase();
        int len = w.length();
        int[] codes = new int[len + 2];
        for (int i = 0; i < len; i++) {
            int c = alpha(w.charAt(i));
            if (c == 0) return null;
            codes[i + 1] = c;
        }
        int n = len + 2;
        int[] res = new int[n + 1];
        for (int i = 0; i < n - 1; i++) {
            int node = 0;
            for (int j = i; j < n; j++) {
                int c = codes[j];
                if (node + c >= trie.length) break;
                int e = trie[node + c];
                if ((e & charMask) != c) break;
                node = (e & linkMask) >>> linkShift;
                int pi = trie[node] >>> patShift;
                if (pi != 0) {
                    int pe = pat[pi];
                    int plen = pe >>> 26, shift = (pe >>> 20) & 0x3f, off = pe & 0xfffff;
                    int offset = j + 1 - (plen + shift);
                    for (int k = 0; k < plen; k++) {
                        int p = offset + k;
                        if (p >= 0 && p < res.length) {
                            int v = data[patBuf + off + k] & 0xff;
                            if (v > res[p]) res[p] = v;
                        }
                    }
                }
            }
        }
        boolean[] brk = new boolean[len];
        for (int k = 0; k + 1 < len; k++) {
            brk[k] = (res[k + 1] & 1) == 1 && k + 1 >= minPre && len - (k + 1) >= minSuf;
        }
        return brk;
    }
}
