package org.nookpoint.reader;

import java.io.File;

/** Folder for exported notes, highlights and word lists. */
class Export {
    static File dir() {
        File d = new File(Diag.dir(), "notes");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static String safeName(String s) {
        String n = s.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", "_").trim();
        if (n.length() > 80) n = n.substring(0, 80);
        return n.length() == 0 ? "book" : n;
    }
}
