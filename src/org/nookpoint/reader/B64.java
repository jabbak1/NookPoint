package org.nookpoint.reader;

import java.io.ByteArrayOutputStream;

/** Base64 (android.util.Base64 only exists from API 8). */
class B64 {
    private static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    static byte[] decode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() * 3 / 4);
        int acc = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
            else if (c >= '0' && c <= '9') v = c - '0' + 52;
            else if (c == '+' || c == '-') v = 62;
            else if (c == '/' || c == '_') v = 63;
            else continue;                                  // whitespace, '=' padding
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((acc >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }

    static String encode(byte[] b) {
        StringBuilder sb = new StringBuilder((b.length + 2) / 3 * 4);
        for (int i = 0; i < b.length; i += 3) {
            int n = (b[i] & 0xff) << 16 | (i + 1 < b.length ? (b[i + 1] & 0xff) << 8 : 0) | (i + 2 < b.length ? b[i + 2] & 0xff : 0);
            sb.append(ALPHA.charAt((n >> 18) & 63)).append(ALPHA.charAt((n >> 12) & 63));
            sb.append(i + 1 < b.length ? ALPHA.charAt((n >> 6) & 63) : '=');
            sb.append(i + 2 < b.length ? ALPHA.charAt(n & 63) : '=');
        }
        return sb.toString();
    }
}
