package org.nookpoint.reader;

/** Escaping for tab/newline separated records stored in preferences and files. */
class Esc {
    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String unesc(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 't' ? '\t' : (n == 'n' ? '\n' : (n == 'r' ? '\r' : n)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
