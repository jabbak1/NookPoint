package org.nookpoint.reader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/** Finds installed dictionaries (tolerant about folder names, archives, formats) and looks words up. */
class Dicts {
    /** Reports index building; return false to cancel. */
    interface Progress { boolean update(String dictionary, int percent); }

    static class Result {
        final ArrayList<String[]> hits = new ArrayList<String[]>();   // {dictionary, headword, text}
        final ArrayList<String> similar = new ArrayList<String>();
        final ArrayList<String> errors = new ArrayList<String>();
        boolean noDicts;
    }

    private static ArrayList<Dictionary> cache;

    /** Dictionaries found so far (searched again while none were found, or after invalidate()). */
    static synchronized ArrayList<Dictionary> cached() {
        if (cache == null || cache.size() == 0) cache = report().dicts;
        return cache;
    }

    static synchronized void invalidate() { cache = null; }

    static class Report {
        final ArrayList<Dictionary> dicts = new ArrayList<Dictionary>();
        final ArrayList<String> folders = new ArrayList<String>();     // folders that were searched
        final ArrayList<String[]> problems = new ArrayList<String[]>();  // {file, reason}
        final ArrayList<String> others = new ArrayList<String>();      // other files in dictionary folders
    }

    private static final String[] FOLDER_NAMES = {"dictionaries", "dictionary", "dicts", "dict", "stardict",
            "slovari", "\u0441\u043B\u043E\u0432\u0430\u0440\u0438", "\u0441\u043B\u043E\u0432\u0430\u0440\u044C"};
    private static final String[] UNSUPPORTED = {".dsl", ".dsl.dz", ".mdx", ".mdd", ".bgl", ".xz", ".bz2", ".tbz2",
            ".7z", ".rar", ".xdxf", ".txz", ".lzma"};
    private static final String BAD_FORMAT = "Unsupported format. Convert it to StarDict on a computer (for example with PyGlossary).";

    static File sibling(File dir, String name) {
        File f = new File(dir, name);
        if (f.exists()) return f;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (int i = 0; i < fs.length; i++) if (fs[i].getName().equalsIgnoreCase(name)) return fs[i];
        }
        return null;
    }

    static ArrayList<Dictionary> list() { return cached(); }

    // ------------------------------------------------------------------ discovery
    private static void addNamed(File parent, ArrayList<File> out, HashSet<String> seen) {
        File[] fs = parent.listFiles();
        if (fs == null) return;
        for (int i = 0; i < fs.length; i++) {
            if (!fs[i].isDirectory()) continue;
            String n = fs[i].getName().toLowerCase();
            for (int k = 0; k < FOLDER_NAMES.length; k++) {
                if (n.equals(FOLDER_NAMES[k]) && seen.add(fs[i].getAbsolutePath().toLowerCase())) out.add(fs[i]);
            }
        }
    }

    private static boolean isRecognized(String l) {
        return l.endsWith(".ifo") || l.endsWith(".idx") || l.endsWith(".idx.gz") || l.endsWith(".dict")
                || l.endsWith(".dict.dz") || l.endsWith(".syn") || l.endsWith(".nidx") || l.endsWith(".index");
    }

    private static void walk(File dir, int depth, ArrayList<File> found, HashSet<String> seen, Report named, int[] budget) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (int i = 0; i < fs.length; i++) {
            if (budget[0]-- <= 0) return;
            File f = fs[i];
            String l = f.getName().toLowerCase();
            if (f.isDirectory()) {
                if (depth > 0 && !l.startsWith(".")) walk(f, depth - 1, found, seen, named, budget);
                continue;
            }
            if (l.endsWith(".ifo") || l.endsWith(".index")) {
                if (seen.add(f.getAbsolutePath())) found.add(f);
            } else if (named != null) {
                boolean bad = false;
                for (int k = 0; k < UNSUPPORTED.length; k++) if (l.endsWith(UNSUPPORTED[k])) bad = true;
                if (bad) named.problems.add(new String[]{f.getName(), BAD_FORMAT});
                else if (!isRecognized(l) && !l.endsWith(".nomedia") && named.others.size() < 20) named.others.add(f.getName());
            }
        }
    }

    private static void unpackArchives(File dir, int depth, Report r) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            String l = f.getName().toLowerCase();
            if (f.isDirectory()) {
                if (depth > 0) unpackArchives(f, depth - 1, r);
            } else if (Archives.isArchive(l)) {
                String bn = f.getName();
                int dot = bn.toLowerCase().endsWith(".tar.gz") ? bn.length() - 7 : bn.lastIndexOf('.');
                File dest = new File(f.getParentFile(), bn.substring(0, Math.max(dot, 1)));
                String[] existing = dest.list();
                if (existing != null && existing.length > 0) continue;       // already unpacked
                try {
                    if (Archives.unpack(f, dest) == 0) r.problems.add(new String[]{f.getName(), "The archive has no dictionary files"});
                } catch (Exception e) {
                    r.problems.add(new String[]{f.getName(), "Cannot unpack the archive"});
                }
            }
        }
    }

    private static void open(ArrayList<File> found, Report r) {
        for (int i = 0; i < found.size(); i++) {
            File f = found.get(i);
            try {
                if (f.getName().toLowerCase().endsWith(".ifo")) r.dicts.add(new StarDict(f));
                else r.dicts.add(new DictdDict(f));
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage());
                r.problems.add(new String[]{f.getName(), m.startsWith("Missing") ? m : "Cannot read this dictionary"});
            }
        }
    }

    static Report report() {
        Report r = new Report();
        File root = Prefs.root();
        HashSet<String> seenDirs = new HashSet<String>();
        ArrayList<File> named = new ArrayList<File>();
        File dd = Prefs.dictDir();
        if (dd.isDirectory() && seenDirs.add(dd.getAbsolutePath().toLowerCase())) named.add(dd);
        addNamed(root, named, seenDirs);
        addNamed(new File(root, "external_sdcard"), named, seenDirs);

        HashSet<String> seen = new HashSet<String>();
        ArrayList<File> found = new ArrayList<File>();
        int[] budget = {20000};
        for (int i = 0; i < named.size(); i++) {
            File d = named.get(i);
            r.folders.add(d.getAbsolutePath());
            unpackArchives(d, 3, r);
            walk(d, 3, found, seen, r, budget);
        }
        File[] loose = {Prefs.booksDir(), new File(root, "Download"), root};
        for (int i = 0; i < loose.length; i++) {
            if (!loose[i].isDirectory()) continue;
            r.folders.add(loose[i].getAbsolutePath());
            walk(loose[i], loose[i].equals(root) ? 0 : 2, found, seen, null, budget);
        }
        open(found, r);

        if (r.dicts.size() == 0) {                    // last resort: look everywhere
            ArrayList<File> deep = new ArrayList<File>();
            int[] b2 = {20000};
            walk(root, 6, deep, seen, null, b2);
            r.folders.add(root.getAbsolutePath() + " (+ subfolders)");
            open(deep, r);
        }
        return r;
    }

    // ------------------------------------------------------------------ lookup
    /** Strips punctuation around a tapped word. */
    static String clean(String w) {
        int a = 0, b = w.length();
        while (a < b && !Character.isLetterOrDigit(w.charAt(a))) a++;
        while (b > a && !Character.isLetterOrDigit(w.charAt(b - 1))) b--;
        return w.substring(a, b);
    }

    static boolean needsIndex(ArrayList<Dictionary> ds) {
        for (int i = 0; i < ds.size(); i++) if (!ds.get(i).indexReady()) return true;
        return false;
    }

    static Result lookup(ArrayList<Dictionary> ds, String word, Progress p) {
        Result r = new Result();
        for (int i = 0; i < ds.size(); i++) {
            Dictionary d = ds.get(i);
            try {
                d.setProgress(p);
                String[] h = d.find(word);
                if (h != null && h[1].length() > 0) r.hits.add(new String[]{d.getName(), h[0], h[1]});
                else if (r.similar.size() == 0) r.similar.addAll(d.similar(word, 6));
            } catch (java.io.IOException e) {
                if (!"cancelled".equals(e.getMessage())) r.errors.add(d.getName() + ": " + e.getMessage());
            } catch (Throwable t) {
                r.errors.add(d.getName() + ": " + t.getClass().getSimpleName());
            } finally {
                d.setProgress(null);
            }
        }
        return r;
    }
}
