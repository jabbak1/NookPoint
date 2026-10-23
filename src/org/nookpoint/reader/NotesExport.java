package org.nookpoint.reader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;

/** Writes highlights/bookmarks of a book (text) and the saved words (tab separated, for flash-card apps). */
class NotesExport {
    private static String line(String s) { return s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' '); }

    static File exportBook(String path) throws IOException {
        File f = new File(Export.dir(), Export.safeName(Prefs.title(path)) + ".txt");
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write("# " + Prefs.title(path) + "\n");
            if (Prefs.author(path).length() > 0) w.write(Prefs.author(path) + "\n");
            w.write("\n## Highlights\n");
            ArrayList<Highlights.H> hs = Highlights.list(path);
            for (int i = 0; i < hs.size(); i++) {
                Highlights.H h = hs.get(i);
                w.write("\n> " + h.text.replace("\n", "\n> ") + "\n");
                if (h.note.length() > 0) w.write("Note: " + h.note + "\n");
                w.write("(" + h.pct + "%, " + T.dateShort(new java.util.Date(h.time)) + ")\n");
            }
            ArrayList<Bookmarks.BM> bs = Bookmarks.list(path);
            if (bs.size() > 0) {
                w.write("\n## Bookmarks\n\n");
                for (int i = 0; i < bs.size(); i++) w.write("- " + bs.get(i).pct + "%  " + line(bs.get(i).snippet) + "\n");
            }
        } finally {
            w.close();
        }
        return f;
    }

    /** word TAB definition TAB context TAB book (importable by Anki and similar). */
    static File exportWords() throws IOException {
        File f = new File(Export.dir(), "words.tsv");
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            ArrayList<WordList.W> l = WordList.list();
            for (int i = 0; i < l.size(); i++) {
                WordList.W x = l.get(i);
                String head = x.head.length() > 0 && !x.head.equalsIgnoreCase(x.word) ? x.word + " (" + x.head + ")" : x.word;
                w.write(line(head) + "\t" + line(x.def) + "\t" + line(x.ctx) + "\t" + line(x.book) + "\n");
            }
        } finally {
            w.close();
        }
        return f;
    }
}
