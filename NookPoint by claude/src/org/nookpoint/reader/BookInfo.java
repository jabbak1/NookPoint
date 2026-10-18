package org.nookpoint.reader;

import android.content.Context;
import java.io.File;

/** "Book information" window: author, reading statistics and the annotation (description). */
class BookInfo {
    static void show(final Context c, final String path) {
        if (Prefs.get("meta2:" + path, null) == null) {          // description not read yet
            Library.fetchMeta(path, new Runnable() {
                public void run() { show(c, path); }
            });
            return;
        }
        String author = Prefs.author(path);
        String desc = Prefs.get("desc:" + path, "");
        String series = Prefs.get("series:" + path, "");
        StringBuilder sb = new StringBuilder();
        if (author.length() > 0) sb.append(author).append('\n');
        if (series.length() > 0) {
            String no = Prefs.get("serno:" + path, "");
            sb.append(T.t("Series")).append(": ").append(series).append(no.length() > 0 ? " #" + no : "").append('\n');
        }
        sb.append('\n');
        int pc = Prefs.pct(path);
        sb.append(T.t("Progress")).append(": ").append(Stats.isFinished(path) ? T.t("Finished") : pc + "%").append('\n');
        sb.append(T.t("Reading time")).append(": ").append(Stats.fmt(Stats.bookSecs(path))).append('\n');
        long pages = Stats.bookPages(path);
        if (pages > 0) sb.append(T.t("Pages turned")).append(": ").append(pages).append('\n');
        int bm = Bookmarks.list(path).size();
        if (bm > 0) sb.append(T.t("Bookmarks")).append(": ").append(bm).append('\n');
        sb.append('\n').append(desc.length() > 0 ? desc : T.t("No description."));
        Dlg.message(c, Prefs.title(path), sb, T.t("Close"), null, null, null);
    }
}
