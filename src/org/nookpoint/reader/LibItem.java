package org.nookpoint.reader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/** One book in the library list. */
class LibItem {
    File file;
    String path = "", name = "", title = "", author = "", series = "";
    double seriesNo;
    int pct;
    boolean finished;
    long opened, added;
}

/** Library filtering and sorting (pure logic). */
class LibSort {
    static final int SORT_TITLE = 0, SORT_AUTHOR = 1, SORT_RECENT = 2, SORT_PROGRESS = 3, SORT_ADDED = 4, SORT_SERIES = 5;
    static final int F_ALL = 0, F_READING = 1, F_NEW = 2, F_FINISHED = 3;

    static boolean matches(LibItem it, String q) {
        if (q == null || q.length() == 0) return true;
        String hay = (it.title + " " + it.author + " " + it.name).toLowerCase();
        return hay.indexOf(q.toLowerCase().trim()) >= 0;
    }

    static boolean passes(LibItem it, int filter) {
        switch (filter) {
            case F_READING: return !it.finished && it.pct > 0;
            case F_NEW: return !it.finished && it.pct == 0;
            case F_FINISHED: return it.finished;
            default: return true;
        }
    }

    private static int progress(LibItem it) { return it.finished ? 100 : it.pct; }

    static ArrayList<LibItem> apply(ArrayList<LibItem> all, String query, int filter, int sort) {
        return apply(all, query, filter, sort, "", null);
    }

    /** collection: "" (none), "series:Name" or "shelf:Name" (then shelfPaths holds the shelf's books). */
    static ArrayList<LibItem> apply(ArrayList<LibItem> all, String query, int filter, final int sort,
                                    String collection, java.util.HashSet<String> shelfPaths) {
        ArrayList<LibItem> r = new ArrayList<LibItem>();
        for (int i = 0; i < all.size(); i++) {
            LibItem it = all.get(i);
            if (!passes(it, filter) || !matches(it, query)) continue;
            if (collection != null && collection.startsWith("series:") && !it.series.equals(collection.substring(7))) continue;
            if (collection != null && collection.startsWith("shelf:") && (shelfPaths == null || !shelfPaths.contains(it.path))) continue;
            r.add(it);
        }
        Collections.sort(r, new Comparator<LibItem>() {
            public int compare(LibItem a, LibItem b) {
                int c = 0;
                switch (sort) {
                    case SORT_AUTHOR:
                        if (a.author.length() == 0 && b.author.length() > 0) return 1;
                        if (b.author.length() == 0 && a.author.length() > 0) return -1;
                        c = a.author.compareToIgnoreCase(b.author);
                        break;
                    case SORT_RECENT:
                        c = a.opened < b.opened ? 1 : (a.opened > b.opened ? -1 : 0);
                        break;
                    case SORT_PROGRESS:
                        c = progress(b) - progress(a);
                        break;
                    case SORT_ADDED:
                        c = a.added < b.added ? 1 : (a.added > b.added ? -1 : 0);
                        break;
                    case SORT_SERIES:
                        if (a.series.length() == 0 && b.series.length() > 0) return 1;
                        if (b.series.length() == 0 && a.series.length() > 0) return -1;
                        c = a.series.compareToIgnoreCase(b.series);
                        if (c == 0) c = a.seriesNo < b.seriesNo ? -1 : (a.seriesNo > b.seriesNo ? 1 : 0);
                        break;
                    default:
                        break;
                }
                if (c != 0) return c;
                c = a.title.compareToIgnoreCase(b.title);
                return c != 0 ? c : a.path.compareTo(b.path);
            }
        });
        return r;
    }
}
