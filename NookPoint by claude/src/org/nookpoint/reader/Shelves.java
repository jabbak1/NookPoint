package org.nookpoint.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/** User shelves (collections of books), stored in preferences. */
class Shelves {
    static ArrayList<String> names() {
        ArrayList<String> r = new ArrayList<String>();
        String s = Prefs.get("shelves", "");
        if (s.length() > 0) {
            String[] p = s.split("\n");
            for (int i = 0; i < p.length; i++) if (p[i].length() > 0) r.add(p[i]);
        }
        Collections.sort(r, String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    private static void saveNames(ArrayList<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(l.get(i));
        }
        Prefs.put("shelves", sb.toString());
    }

    static String clean(String name) { return name.replace('\n', ' ').replace('\t', ' ').trim(); }

    static void create(String name) {
        name = clean(name);
        if (name.length() == 0) return;
        ArrayList<String> l = names();
        for (int i = 0; i < l.size(); i++) if (l.get(i).equalsIgnoreCase(name)) return;
        l.add(name);
        saveNames(l);
    }

    static void delete(String name) {
        ArrayList<String> l = names();
        l.remove(name);
        saveNames(l);
        Prefs.remove("shelf:" + name);
    }

    static HashSet<String> paths(String name) {
        HashSet<String> r = new HashSet<String>();
        String s = Prefs.get("shelf:" + name, "");
        if (s.length() > 0) {
            String[] p = s.split("\n");
            for (int i = 0; i < p.length; i++) if (p[i].length() > 0) r.add(p[i]);
        }
        return r;
    }

    static boolean contains(String name, String path) { return paths(name).contains(path); }

    static void toggle(String name, String path) {
        HashSet<String> set = paths(name);
        if (!set.remove(path)) set.add(path);
        StringBuilder sb = new StringBuilder();
        for (String p : set) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p);
        }
        Prefs.put("shelf:" + name, sb.toString());
    }
}
