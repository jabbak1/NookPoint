package org.nookpoint.reader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/** Finds installed StarDict dictionaries and looks words up in all of them. */
class Dicts {
    static class Result {
        final ArrayList<String[]> hits = new ArrayList<String[]>();   // {dictionary, headword, text}
        final ArrayList<String> similar = new ArrayList<String>();
    }

    static ArrayList<StarDict> list() {
        ArrayList<StarDict> r = new ArrayList<StarDict>();
        scan(Prefs.dictDir(), r, 0);
        return r;
    }

    private static void scan(File dir, ArrayList<StarDict> out, int depth) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            if (f.isDirectory()) {
                if (depth < 2) scan(f, out, depth + 1);
            } else if (f.getName().toLowerCase().endsWith(".ifo")) {
                try { out.add(new StarDict(f)); } catch (Exception e) { }
            }
        }
    }

    /** Strips punctuation around a tapped word. */
    static String clean(String w) {
        int a = 0, b = w.length();
        while (a < b && !Character.isLetterOrDigit(w.charAt(a))) a++;
        while (b > a && !Character.isLetterOrDigit(w.charAt(b - 1))) b--;
        return w.substring(a, b);
    }

    static boolean needsIndex(ArrayList<StarDict> ds) {
        for (int i = 0; i < ds.size(); i++) if (!ds.get(i).indexReady()) return true;
        return false;
    }

    static Result lookup(ArrayList<StarDict> ds, String word) {
        Result r = new Result();
        for (int i = 0; i < ds.size(); i++) {
            StarDict d = ds.get(i);
            try {
                String[] h = d.find(word);
                if (h != null && h[1].length() > 0) r.hits.add(new String[]{d.name, h[0], h[1]});
                else if (r.similar.size() == 0) r.similar.addAll(d.similar(word, 6));
            } catch (Exception e) {
                // skip a broken dictionary
            }
        }
        return r;
    }
}
