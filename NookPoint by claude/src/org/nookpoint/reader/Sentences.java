package org.nookpoint.reader;

/** Finds the sentence around a character (for highlights and dictionary context). */
class Sentences {
    private static boolean term(char c) { return c == '.' || c == '!' || c == '?' || c == '\u2026'; }

    private static boolean closer(char c) {
        return c == '"' || c == '\'' || c == ')' || c == ']' || c == '\u00BB' || c == '\u201D' || c == '\u2019' || c == '\u203A';
    }

    private static final String[] ABBR = {"mr", "mrs", "ms", "dr", "st", "prof", "sr", "jr", "vs", "no", "fig", "vol", "cf",
            "mme", "mlle", "m", "sra", "sr", "dra", "av", "pp", "\u0433", "\u0433\u0433", "\u0442", "\u0442\u0442", "\u0441\u0442\u0440",
            "\u0441\u043C", "\u0440\u0438\u0441", "\u0438\u043C", "\u0443\u043B", "\u0434", "\u043A\u0432", "\u043F\u0440\u043E\u0444",
            "\u0442\u043E\u0432", "\u0433\u0440", "\u043D\u0430\u043F\u0440", "\u0441\u0432", "\u0441\u0442", "\u0442\u0430\u0431\u043B",
            "\u0441\u0440", "\u043F\u0440", "\u0447\u0435\u043B"};

    private static boolean abbreviation(String t, int dot) {
        int a = dot;
        while (a > 0 && Character.isLetter(t.charAt(a - 1))) a--;
        if (a == dot) return false;
        String w = t.substring(a, dot).toLowerCase();
        for (int i = 0; i < ABBR.length; i++) if (ABBR[i].equals(w)) return true;
        return false;
    }

    /** True when a sentence ends exactly after index i. */
    private static boolean endsAt(String t, int i) {
        int n = t.length();
        int k = i;
        while (k >= 0 && closer(t.charAt(k))) k--;
        if (k < 0 || !term(t.charAt(k))) return false;
        if (i + 1 >= n) return true;
        if (t.charAt(i + 1) != ' ') return false;
        if (t.charAt(k) == '.' && k >= 1 && Character.isLetter(t.charAt(k - 1)) && Character.isUpperCase(t.charAt(k - 1))
                && (k == 1 || t.charAt(k - 2) == ' ' || t.charAt(k - 2) == '.' || t.charAt(k - 2) == '\u00A0')) {
            return false;                                           // initials: "A. S. Pushkin"
        }
        if (t.charAt(k) == '.' && abbreviation(t, k)) return false;
        int j = i + 1;
        while (j < n && t.charAt(j) == ' ') j++;
        if (j < n && Character.isLowerCase(t.charAt(j))) return false;   // "etc. and", "т. е. что"
        return true;
    }

    /** {start, end} (end exclusive) of the sentence containing index idx. */
    static int[] sentence(String t, int idx) {
        int n = t.length();
        if (n == 0) return new int[]{0, 0};
        idx = Math.max(0, Math.min(idx, n - 1));
        int start = 0;
        for (int i = idx - 1; i >= 0; i--) {
            if (endsAt(t, i)) {
                start = i + 1;
                while (start < n && t.charAt(start) == ' ') start++;
                break;
            }
        }
        int end = n;
        for (int i = Math.max(idx, start); i < n; i++) {
            if (endsAt(t, i)) { end = i + 1; break; }
        }
        if (start > end) start = Math.max(0, end - 1);
        return new int[]{start, end};
    }
}
