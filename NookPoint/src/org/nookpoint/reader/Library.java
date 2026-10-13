package org.nookpoint.reader;

import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Finds the books on the card and fills in titles/authors/progress. */
class Library {
    interface Listener {
        void open(LibItem it);
        void menu(LibItem it);
    }

    private static final ExecutorService ex = Executors.newSingleThreadExecutor();
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static ArrayList<LibItem> scan() {
        ArrayList<LibItem> out = new ArrayList<LibItem>();
        HashSet<String> seen = new HashSet<String>();
        File root = Prefs.root();
        walk(Prefs.booksDir(), 6, out, seen);
        walk(root, 0, out, seen);                               // loose files in the storage root
        walk(new File(root, "Download"), 2, out, seen);
        walk(new File(root, "external_sdcard"), 4, out, seen);   // microSD card
        for (int i = 0; i < out.size(); i++) fill(out.get(i));
        return out;
    }

    private static void walk(File dir, int depth, ArrayList<LibItem> out, HashSet<String> seen) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (int i = 0; i < fs.length; i++) {
            if (out.size() >= 3000) return;
            File f = fs[i];
            String n = f.getName();
            if (n.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (depth > 0) walk(f, depth - 1, out, seen);
            } else {
                String l = n.toLowerCase();
                if ((l.endsWith(".epub") || l.endsWith(".txt")) && seen.add(f.getAbsolutePath())) {
                    LibItem it = new LibItem();
                    it.file = f;
                    it.path = f.getAbsolutePath();
                    it.name = n;
                    out.add(it);
                }
            }
        }
    }

    static void fill(LibItem it) {
        it.title = Prefs.title(it.path);
        it.author = Prefs.author(it.path);
        it.pct = Prefs.pct(it.path);
        it.finished = Stats.isFinished(it.path);
        try { it.opened = Long.parseLong(Prefs.get("opened:" + it.path, "0")); } catch (Exception e) { it.opened = 0; }
        it.added = it.file.lastModified();
    }

    /** Reads titles and authors of books that have never been opened (in the background). */
    static void indexMeta(ArrayList<LibItem> items, final Runnable progress) {
        final ArrayList<LibItem> todo = new ArrayList<LibItem>();
        for (int i = 0; i < items.size(); i++) {
            LibItem it = items.get(i);
            if (it.path.toLowerCase().endsWith(".epub") && Prefs.get("title:" + it.path, null) == null) todo.add(it);
        }
        if (todo.size() == 0) return;
        ex.execute(new Runnable() {
            public void run() {
                int n = 0;
                for (int i = 0; i < todo.size(); i++) {
                    LibItem it = todo.get(i);
                    Book b = null;
                    try {
                        b = Book.open(it.file);
                        Prefs.putMany(new String[]{"title:" + it.path, b.title,
                                "author:" + it.path, b.author == null ? "" : b.author});
                    } catch (Throwable t) {
                        Prefs.putMany(new String[]{"title:" + it.path, Prefs.title(it.path)});
                    } finally {
                        if (b != null) b.close();
                    }
                    if (++n % 20 == 0) ui.post(progress);
                }
                ui.post(progress);
            }
        });
    }
}
