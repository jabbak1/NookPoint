package org.nookpoint.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/** dictd format: name.index (word TAB offset TAB length, base64 numbers) + name.dict[.dz]. Linear scan. */
class DictdDict implements Dictionary {
    final File index, dict;
    String name;

    DictdDict(File index) throws IOException {
        this.index = index;
        String fn = index.getName();
        String bn = fn.substring(0, fn.length() - ".index".length());
        File dir = index.getParentFile();
        File d = Dicts.sibling(dir, bn + ".dict");
        if (d == null) d = Dicts.sibling(dir, bn + ".dict.dz");
        if (d == null) throw new IOException("Missing .dict or .dict.dz file");
        dict = d;
        name = bn;
        try {
            String[] n = scan("00-database-short", false, 40);
            if (n != null) {
                String t = n[1].trim();
                int nl = t.indexOf('\n');
                if (t.startsWith("00-database-short") && nl > 0) t = t.substring(nl + 1).trim();
                if (t.length() > 0 && t.length() < 80) name = t;
            }
        } catch (Exception e) {
            // keep the file name
        }
    }

    public String getName() { return name; }
    public String getPath() { return index.getAbsolutePath(); }
    public long getWordCount() { return -1; }
    public boolean indexReady() { return true; }

    private static long b64(String s) {
        long n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int v = (c >= 'A' && c <= 'Z') ? c - 'A' : (c >= 'a' && c <= 'z') ? c - 'a' + 26
                    : (c >= '0' && c <= '9') ? c - '0' + 52 : (c == '+') ? 62 : (c == '/') ? 63 : 0;
            n = n * 64 + v;
        }
        return n;
    }

    /** exact match (case-insensitive) or, if stems is true, the best word that starts with a stem of the query. */
    private String[] scan(String word, boolean stems, int maxLines) throws IOException {
        String lo = word.toLowerCase();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(index), "UTF-8"), 65536);
        DictFile df = new DictFile(dict);
        try {
            String line;
            int lines = 0;
            String[] best = new String[4];
            long[] bo = new long[4], bl = new long[4];
            ArrayList<long[]> exact = new ArrayList<long[]>();
            String exHead = null;
            while ((line = r.readLine()) != null) {
                if (maxLines > 0 && ++lines > maxLines) break;
                int t1 = line.indexOf('\t');
                if (t1 <= 0) continue;
                int t2 = line.indexOf('\t', t1 + 1);
                if (t2 < 0) continue;
                String hw = line.substring(0, t1);
                if (hw.startsWith("00-database") && !hw.equals(word)) continue;
                String hl = hw.toLowerCase();
                if (hl.equals(lo)) {
                    if (exHead == null) exHead = hw;
                    if (exact.size() < 4) exact.add(new long[]{b64(line.substring(t1 + 1, t2)), b64(line.substring(t2 + 1).trim())});
                } else if (stems) {
                    for (int k = 1; k <= 3; k++) {
                        if (lo.length() - k < 3) break;
                        String stem = lo.substring(0, lo.length() - k);
                        if (hl.startsWith(stem) && hw.length() <= stem.length() + 3 && (best[k] == null || hw.length() < best[k].length())) {
                            best[k] = hw;
                            bo[k] = b64(line.substring(t1 + 1, t2));
                            bl[k] = b64(line.substring(t2 + 1).trim());
                        }
                    }
                }
            }
            if (exHead != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < exact.size(); i++) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(new String(df.read(exact.get(i)[0], (int) exact.get(i)[1]), "UTF-8").trim());
                }
                return new String[]{exHead, sb.toString()};
            }
            for (int k = 1; k <= 3; k++) {
                if (best[k] != null) return new String[]{best[k], new String(df.read(bo[k], (int) bl[k]), "UTF-8").trim()};
            }
            return null;
        } finally {
            r.close();
            df.close();
        }
    }

    public String[] find(String word) throws IOException {
        return scan(word, true, 0);
    }

    public ArrayList<String> similar(String word, int max) throws IOException {
        ArrayList<String> out = new ArrayList<String>();
        if (word.length() < 2) return out;
        String prefix = word.toLowerCase().substring(0, Math.min(word.length(), 3));
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(index), "UTF-8"), 65536);
        try {
            String line;
            while ((line = r.readLine()) != null && out.size() < max) {
                int t1 = line.indexOf('\t');
                if (t1 <= 0) continue;
                String hw = line.substring(0, t1);
                if (!hw.startsWith("00-database") && hw.toLowerCase().startsWith(prefix) && !out.contains(hw)) out.add(hw);
            }
        } finally {
            r.close();
        }
        return out;
    }
}
