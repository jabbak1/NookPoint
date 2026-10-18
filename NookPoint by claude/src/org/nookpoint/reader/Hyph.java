package org.nookpoint.reader;

import java.util.ArrayList;

/**
 * Rule based hyphenation for Russian and Latin-script words (no dictionaries, API 7 friendly).
 * Returns break positions: k > 0 = break before char k and print a hyphen;
 * k < 0 = break after an already existing hyphen at index -k-1 (no extra hyphen printed).
 */
class Hyph {
    private static final int[] NONE = new int[0];
    private static final String CYR_V = "\u0430\u0435\u0451\u0438\u043E\u0443\u044B\u044D\u044E\u044F"
            + "\u0410\u0415\u0401\u0418\u041E\u0423\u042B\u042D\u042E\u042F";
    private static final String LAT_V = "aeiouyAEIOUY";

    private static boolean vowel(char c) { return CYR_V.indexOf(c) >= 0 || LAT_V.indexOf(c) >= 0; }

    private static boolean sonorant(char c) {
        return "\u0440\u043Blr".indexOf(Character.toLowerCase(c)) >= 0;
    }

    private static int[] toArray(ArrayList<Integer> r) {
        int[] a = new int[r.size()];
        for (int i = 0; i < a.length; i++) a[i] = r.get(i).intValue();
        return a;
    }

    /** Break positions for the word t[s, e) using the hyphenation dictionary of the book language. */
    static int[] points(String t, int s, int e, String lang) {
        if (e - s < 5) return NONE;
        for (int i = s; i < e; i++) if (t.charAt(i) == '-') return points(t, s, e);        // existing hyphen: natural breaks
        int a = s, b = e;
        while (a < b && !Character.isLetter(t.charAt(a))) a++;
        while (b > a && !Character.isLetter(t.charAt(b - 1))) b--;
        if (b - a < 5) return NONE;
        for (int i = a; i < b; i++) if (!Character.isLetter(t.charAt(i))) return NONE;
        Hyb h = Hyb.forLang(lang, t.charAt(a));
        if (h == null) return points(t, s, e);
        boolean latin = t.charAt(a) < 0x250;
        String l = lang == null ? "" : lang.toLowerCase();
        int minSuf = (latin && (l.startsWith("en") || l.startsWith("fr") || l.length() == 0)) ? 3 : 2;
        boolean[] br = h.breaks(t.substring(a, b), 2, minSuf);
        if (br == null) return points(t, s, e);                                           // letters unknown to the dictionary
        ArrayList<Integer> r = new ArrayList<Integer>();
        for (int k = 0; k < br.length; k++) if (br[k]) r.add(Integer.valueOf(a + k + 1));
        return toArray(r);
    }

    /** Rule based fallback (no dictionary). */
    static int[] points(String t, int s, int e) {
        if (e - s < 6) return NONE;
        ArrayList<Integer> r = new ArrayList<Integer>();
        boolean hasHyphen = false;
        for (int i = s; i < e; i++) if (t.charAt(i) == '-') { hasHyphen = true; break; }
        if (hasHyphen) {
            for (int i = s + 2; i < e - 2; i++) {
                if (t.charAt(i) == '-' && Character.isLetter(t.charAt(i - 1)) && Character.isLetter(t.charAt(i + 1))) {
                    r.add(Integer.valueOf(-(i + 1)));
                }
            }
            return toArray(r);
        }
        int a = s, b = e;
        while (a < b && !Character.isLetter(t.charAt(a))) a++;
        while (b > a && !Character.isLetter(t.charAt(b - 1))) b--;
        if (b - a < 6) return NONE;
        for (int i = a; i < b; i++) if (!Character.isLetter(t.charAt(i))) return NONE;
        int minAfter = t.charAt(a) < 0x250 ? 3 : 2;
        int[] v = new int[b - a];
        int vn = 0;
        for (int i = a; i < b; i++) if (vowel(t.charAt(i))) v[vn++] = i;
        for (int q = 0; q + 1 < vn; q++) {
            int v1 = v[q], v2 = v[q + 1];
            int c = v2 - v1 - 1;
            if (c == 0) continue;
            int k;
            if (c == 1) {
                k = v1 + 1;
                char ch = t.charAt(k);
                if (ch == '\u0439' || ch == '\u0419') k++;            // y-short stays with the vowel
            } else if (c == 2 && sonorant(t.charAt(v1 + 2)) && !sonorant(t.charAt(v1 + 1))
                    && Character.toLowerCase(t.charAt(v1 + 1)) != '\u0439') {
                k = v1 + 1;                                           // obstruent + sonorant cluster starts the next syllable
            } else {
                k = v1 + 2;
                while (k < v2 && (t.charAt(k) == '\u044C' || t.charAt(k) == '\u044A'
                        || t.charAt(k) == '\u042C' || t.charAt(k) == '\u042A')) k++;   // soft/hard sign
            }
            if (k - a >= 2 && b - k >= minAfter && k < b) r.add(Integer.valueOf(k));
        }
        return toArray(r);
    }
}
