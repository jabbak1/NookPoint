package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

public class ReaderActivity extends Activity implements ReaderView.Listener {
    static Book current;

    private static class Loaded { Chapter c; Layouter.Result r; int off; }

    private Book book;
    private ReaderView view;
    private Cfg cfg;
    private String sig, path, openError;
    private int chapter, page, pages = 1, turns, gen, pct, lastGood;
    private ArrayList<Line> lines;
    private int[] pageStart;
    private Chapter chap;
    private boolean loading, started;
    private long sessionStart, lastAct;
    private int sessionPages;
    private final ArrayList<int[]> back = new ArrayList<int[]>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        path = getIntent().getStringExtra("path");
        if (path == null && getIntent().getData() != null) path = getIntent().getData().getPath();
        cfg = Cfg.load();
        sig = Cfg.sig();
        view = new ReaderView(this);
        view.listener = this;
        view.configure(cfg);
        setContentView(view);
        if (path == null) { finish(); return; }

        new AsyncTask<String, Void, Book>() {
            @Override
            protected Book doInBackground(String... p) {
                try {
                    return Book.open(new File(p[0]));
                } catch (Throwable t) {
                    openError = String.valueOf(t.getMessage());
                    return null;
                }
            }
            @Override
            protected void onPostExecute(Book bk) {
                if (bk == null) {
                    view.message = T.f("Cannot open book: {0}", openError);
                    view.invalidate();
                    return;
                }
                book = bk;
                current = bk;
                Prefs.addRecent(path, bk.title, bk.author);
                Prefs.put("desc:" + path, bk.description == null ? "" : bk.description);
                tryStart();
            }
        }.execute(path);
    }

    private void tryStart() {
        if (started || book == null || view.getWidth() <= 0) return;
        started = true;
        int[] pos = Prefs.getPos(path);
        int ch = pos[0] < book.count() ? pos[0] : 0;
        load(ch, pos[1], null, 1);
    }

    public void onSize() { tryStart(); }

    /**
     * off >= 0: open the page containing that text offset; off < 0: last page; anchor overrides off.
     * dir: direction used to skip sections that have no text (+1 forward, -1 backward).
     */
    private void load(final int ch, final int off, final String anchor, final int dir) {
        loading = true;
        final int g = ++gen;
        view.recalc();
        view.message = "";
        view.lines = null;
        view.invalidate();
        final Cfg c = cfg;
        final Fonts fonts = view.fonts;
        final Layouter.Params prm = new Layouter.Params();
        prm.width = view.contentW;
        prm.height = view.contentH;
        prm.indent = c.indent ? c.size * 1.5f : 0;
        prm.justify = c.justify;
        prm.hyph = c.hyph;
        prm.spacing = c.spacing;
        prm.pgap = c.pgap;
        new AsyncTask<Void, Void, Loaded>() {
            @Override
            protected Loaded doInBackground(Void... v) {
                try {
                    Loaded l = new Loaded();
                    l.c = book.loadChapter(ch);
                    l.off = off;
                    if (anchor != null) {
                        Integer a = l.c.anchors.get(anchor);
                        l.off = a == null ? 0 : a.intValue();
                    }
                    l.r = Layouter.layout(l.c, fonts, prm);
                    return l;
                } catch (Throwable t) {
                    return null;
                }
            }
            @Override
            protected void onPostExecute(Loaded l) {
                if (g != gen) return;
                loading = false;
                if (l == null) {
                    view.message = T.t("Error reading this section");
                    view.invalidate();
                    return;
                }
                if (l.r.empty) {                         // image-only page etc.: skip it
                    int d = dir == 0 ? 1 : dir;
                    int nc = ch + d;
                    if (nc >= 0 && nc < book.count()) { load(nc, d > 0 ? 0 : -1, null, d); return; }
                    if (d > 0 && lines != null) Toast.makeText(ReaderActivity.this, T.t("End of book"), Toast.LENGTH_SHORT).show();
                    load(lastGood, -1, null, 0);
                    return;
                }
                chapter = ch;
                lastGood = ch;
                chap = l.c;
                lines = l.r.lines;
                pageStart = l.r.pageStart;
                pages = pageStart.length;
                page = l.off < 0 ? pages - 1 : pageOfOffset(l.off);
                show(false);
            }
        }.execute();
    }

    private int pageOfOffset(int off) {
        int lo = 0, hi = lines.size() - 1, idx = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).offset <= off) { idx = mid; lo = mid + 1; } else hi = mid - 1;
        }
        int pg = 0;
        for (int k = 0; k < pageStart.length; k++) { if (pageStart[k] <= idx) pg = k; else break; }
        return pg;
    }

    private int currentOffset() {
        if (lines == null || lines.size() == 0 || pageStart == null) return 0;
        return lines.get(Math.min(pageStart[Math.min(page, pageStart.length - 1)], lines.size() - 1)).offset;
    }

    private int pageEndOffset() {
        if (page + 1 < pageStart.length) return lines.get(pageStart[page + 1]).offset;
        return Integer.MAX_VALUE;
    }

    private void show(boolean flash) {
        float frac = (page + 1) / (float) pages;
        pct = book.percent(chapter, frac);
        view.lines = lines;
        view.pageStart = pageStart;
        view.page = page;
        view.pages = pages;
        view.pct = pct;
        view.chapterTitle = book.titleFor(chapter);
        view.battery = battery();
        view.bookmarked = Bookmarks.has(path, chapter, currentOffset(), pageEndOffset());
        if (page == pages - 1 && (chapter == book.count() - 1 || book.percent(chapter, 1f) >= 97) && Stats.finished(path)) {
            int g = Stats.goalBooks();
            Toast.makeText(this, T.t("Book finished!") + (g > 0 ? "  " + T.f("{0} of {1} this year",
                    Stats.booksThisYear(), g) : ""), Toast.LENGTH_LONG).show();
        }
        if (flash) view.fullRefresh(); else view.invalidate();
    }

    private int battery() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int lvl = i.getIntExtra("level", 0), scale = i.getIntExtra("scale", 100);
            return scale > 0 ? lvl * 100 / scale : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private void turned() {
        turns++;
        boolean flash = cfg.refresh > 0 && turns % cfg.refresh == 0;
        show(flash);
        if (turns % 5 == 0) savePos();
        checkGoal();
    }

    private void checkGoal() {
        int g = Stats.goalMinutes();
        if (g <= 0) return;
        String today = Stats.today();
        if (today.equals(Prefs.get("goalshown", ""))) return;
        long now = System.currentTimeMillis();
        long secs = Stats.day(today)[0] + Math.min((now - sessionStart) / 1000, (lastAct - sessionStart) / 1000 + 120);
        if (secs >= g * 60L) {
            Prefs.put("goalshown", today);
            Toast.makeText(this, T.f("Daily goal reached: {0} min. Well done!", g), Toast.LENGTH_LONG).show();
        }
    }

    public void onNext() {
        if (loading || lines == null) return;
        if (page + 1 < pages) { page++; sessionPages++; lastAct = System.currentTimeMillis(); turned(); }
        else if (chapter + 1 < book.count()) {
            sessionPages++;
            lastAct = System.currentTimeMillis();
            Prefs.savePos(path, chapter + 1, 0, pct);
            load(chapter + 1, 0, null, 1);
        }
        else Toast.makeText(this, T.t("End of book"), Toast.LENGTH_SHORT).show();
    }

    public void onPrev() {
        if (loading || lines == null) return;
        if (page > 0) { page--; lastAct = System.currentTimeMillis(); turned(); }
        else if (chapter > 0) load(chapter - 1, -1, null, -1);
    }

    // ------------------------------------------------------------------ links
    public void onLink(String href, boolean note) {
        if (loading || book == null || chap == null) return;
        lastAct = System.currentTimeMillis();
        String lo = href.toLowerCase();
        if (lo.startsWith("http:") || lo.startsWith("https:") || lo.startsWith("mailto:") || lo.startsWith("ftp:")) {
            Toast.makeText(this, T.f("External link: {0}", href), Toast.LENGTH_LONG).show();
            return;
        }
        int h = href.indexOf('#');
        String frag = h >= 0 ? Uri.decode(href.substring(h + 1)) : null;
        String basePart = h >= 0 ? href.substring(0, h) : href;
        int idx = chapter;
        if (basePart.length() > 0) {
            idx = book.spine.indexOf(Book.resolve(Book.dirOf(book.spine.get(chapter)), basePart));
        }
        if (idx < 0) {
            Toast.makeText(this, T.t("Link target not found"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (note) showNote(idx, frag); else jump(idx, frag);
    }

    private void showNote(final int idx, final String frag) {
        if (idx == chapter) { popup(chap, idx, frag); return; }
        new AsyncTask<Void, Void, Chapter>() {
            @Override
            protected Chapter doInBackground(Void... v) {
                try { return book.loadChapter(idx); } catch (Throwable t) { return null; }
            }
            @Override
            protected void onPostExecute(Chapter c) {
                if (c == null) { jump(idx, frag); return; }
                popup(c, idx, frag);
            }
        }.execute();
    }

    private void popup(Chapter c, final int idx, final String frag) {
        String text = c.noteText(frag);
        if (text == null || text.length() == 0) { jump(idx, frag); return; }
        Dlg.message(this, T.t("Note"), text, T.t("Close"), null, T.t("Go there"), new Runnable() {
            public void run() { jump(idx, frag); }
        });
    }

    private void jump(int idx, String frag) {
        back.add(new int[]{chapter, currentOffset()});
        if (!Prefs.get("backhint", "").equals("1")) {
            Prefs.put("backhint", "1");
            Toast.makeText(this, T.t("Back button returns here"), Toast.LENGTH_SHORT).show();
        }
        if (idx == chapter && frag != null && chap.anchors.containsKey(frag)) {
            page = pageOfOffset(chap.anchors.get(frag).intValue());
            show(false);
        } else {
            load(idx, 0, frag, 0);
        }
    }

    private void goBack() {
        int[] b = back.remove(back.size() - 1);
        if (b[0] == chapter && lines != null) { page = pageOfOffset(b[1]); show(false); }
        else load(b[0], b[1], null, 0);
    }

    @Override
    public void onBackPressed() {
        if (back.size() > 0 && !loading) goBack();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------------ bookmarks
    public void onBookmark() {
        if (loading || lines == null || book == null) return;
        lastAct = System.currentTimeMillis();
        int from = currentOffset(), to = pageEndOffset();
        if (Bookmarks.has(path, chapter, from, to)) {
            Bookmarks.removeIn(path, chapter, from, to);
            Toast.makeText(this, T.t("Bookmark removed"), Toast.LENGTH_SHORT).show();
        } else {
            Bookmarks.add(path, chapter, from, pct, snippet());
            Toast.makeText(this, T.t("Bookmark added"), Toast.LENGTH_SHORT).show();
        }
        show(false);
    }

    private String snippet() {
        StringBuilder sb = new StringBuilder();
        int end = page + 1 < pageStart.length ? pageStart[page + 1] : lines.size();
        for (int i = pageStart[page]; i < end && sb.length() < 90; i++) {
            Line l = lines.get(i);
            for (int k = 0; k < l.segs.length; k++) {
                if (l.segs[k].space) sb.append(' ');
                sb.append(l.segs[k].text);
            }
            sb.append(' ');
        }
        String s = sb.toString().trim();
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }

    // ------------------------------------------------------------------ dictionary
    public void onWord(String word) {
        if (loading || book == null) return;
        lastAct = System.currentTimeMillis();
        lookup(word);
    }

    private void askWord() {
        Dlg.input(this, T.t("Look up word"), T.t("Search"), T.t("Cancel"), new Dlg.Text() {
            public void onText(String s) { if (s.length() > 0) lookup(s); }
        });
    }

    /** Dictionary look-up in the background; a progress window appears only if it takes a while. */
    private class LookupTask extends AsyncTask<Void, Integer, Dicts.Result> {
        private final String w;
        private final Handler h = new Handler();
        private Dlg.ProgressDlg pd;
        private volatile String cur = "";
        private boolean finished;
        private final Runnable showDlg = new Runnable() {
            public void run() {
                if (finished) return;
                pd = Dlg.progress(ReaderActivity.this, T.t("Dictionary"), T.t("Cancel"), new Runnable() {
                    public void run() { cancel(true); }
                });
                pd.set(T.t("Preparing dictionary..."), 0);
            }
        };

        LookupTask(String w) { this.w = w; }

        @Override
        protected void onPreExecute() { h.postDelayed(showDlg, 500); }

        @Override
        protected Dicts.Result doInBackground(Void... v) {
            ArrayList<Dictionary> ds = Dicts.cached();
            if (ds.size() == 0) {
                Dicts.Result r = new Dicts.Result();
                r.noDicts = true;
                return r;
            }
            return Dicts.lookup(ds, w, new Dicts.Progress() {
                public boolean update(String name, int pct) {
                    cur = name;
                    publishProgress(Integer.valueOf(pct));
                    return !isCancelled();
                }
            });
        }

        @Override
        protected void onProgressUpdate(Integer... v) {
            if (pd != null) pd.set(T.t("Preparing dictionary...") + "  " + cur, v[0].intValue());
        }

        private void cleanup() {
            finished = true;
            h.removeCallbacks(showDlg);
            if (pd != null) pd.dismiss();
        }

        @Override
        protected void onCancelled() { cleanup(); }

        @Override
        protected void onPostExecute(Dicts.Result r) {
            cleanup();
            if (r.noDicts) {
                Dlg.message(ReaderActivity.this, T.t("Dictionary"), T.f("No dictionaries found.\n\nPut StarDict dictionaries into {0} or upload them with Wi-Fi transfer.",
                        Prefs.dictDir().getAbsolutePath()), T.t("Close"), null, T.t("Details"), new Runnable() {
                            public void run() { startActivity(new Intent(ReaderActivity.this, DictsActivity.class)); }
                        });
                return;
            }
            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (int i = 0; i < r.hits.size(); i++) {
                String[] hit = r.hits.get(i);
                int s = sb.length();
                sb.append(hit[0]);
                if (!hit[1].equalsIgnoreCase(w)) sb.append("  \u2022  ").append(hit[1]);
                sb.setSpan(new StyleSpan(Typeface.BOLD), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append("\n").append(hit[2]).append("\n\n");
            }
            if (r.hits.size() == 0) {
                sb.append(T.t("Not found"));
                if (r.similar.size() > 0) {
                    sb.append("\n\n").append(T.t("Similar:"));
                    for (int i = 0; i < r.similar.size(); i++) sb.append("\n").append(r.similar.get(i));
                }
            }
            for (int i = 0; i < r.errors.size(); i++) sb.append("\n\n").append(T.f("Dictionary error: {0}", r.errors.get(i)));
            Dlg.message(ReaderActivity.this, w, sb, T.t("Close"), null, T.t("Look up word"), new Runnable() {
                public void run() { askWord(); }
            });
        }
    }

    private void lookup(String raw) {
        String w = Dicts.clean(raw);
        if (w.length() == 0) return;
        new LookupTask(w).execute();
    }

    // ------------------------------------------------------------------ menu
    public void onMenu() {
        if (loading || book == null) return;
        lastAct = System.currentTimeMillis();
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<Integer> codes = new ArrayList<Integer>();
        if (back.size() > 0) { labels.add(T.t("Back to previous place")); codes.add(Integer.valueOf(9)); }
        labels.add(T.t("Contents")); codes.add(Integer.valueOf(0));
        labels.add(T.t("Bookmarks")); codes.add(Integer.valueOf(10));
        boolean has = lines != null && Bookmarks.has(path, chapter, currentOffset(), pageEndOffset());
        labels.add(T.t(has ? "Remove bookmark" : "Add bookmark")); codes.add(Integer.valueOf(11));
        labels.add(T.t("Dictionary")); codes.add(Integer.valueOf(12));
        labels.add(T.t("Book info")); codes.add(Integer.valueOf(13));
        labels.add(T.t("Text size +")); codes.add(Integer.valueOf(1));
        labels.add(T.t("Text size -")); codes.add(Integer.valueOf(2));
        labels.add(T.t("Statistics")); codes.add(Integer.valueOf(3));
        labels.add(T.t("Settings")); codes.add(Integer.valueOf(4));
        labels.add(T.t("Back to library")); codes.add(Integer.valueOf(5));
        Dlg.menu(this, book.title, labels, new Dlg.Pick() {
            public void onPick(int which) {
                switch (codes.get(which).intValue()) {
                    case 9: goBack(); break;
                    case 0:
                        Intent i = new Intent(ReaderActivity.this, TocActivity.class);
                        i.putExtra("current", chapter);
                        startActivityForResult(i, 1);
                        break;
                    case 10:
                        Intent b = new Intent(ReaderActivity.this, BookmarksActivity.class);
                        b.putExtra("path", path);
                        startActivityForResult(b, 2);
                        break;
                    case 11: onBookmark(); break;
                    case 12: askWord(); break;
                    case 13: BookInfo.show(ReaderActivity.this, path); break;
                    case 1: Cfg.stepSize(1); relayout(); break;
                    case 2: Cfg.stepSize(-1); relayout(); break;
                    case 3: startActivity(new Intent(ReaderActivity.this, StatsActivity.class)); break;
                    case 4: startActivity(new Intent(ReaderActivity.this, SettingsActivity.class)); break;
                    case 5: finish(); break;
                }
            }
        });
    }

    private void relayout() {
        if (!started) return;
        int off = currentOffset();
        cfg = Cfg.load();
        sig = Cfg.sig();
        view.configure(cfg);
        load(chapter, off, null, 0);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null || book == null) return;
        if (req == 1) {
            load(data.getIntExtra("chapter", 0), 0, null, 1);
        } else if (req == 2) {
            back.add(new int[]{chapter, currentOffset()});
            load(data.getIntExtra("chapter", 0), data.getIntExtra("offset", 0), null, 0);
        }
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK) return super.onKeyDown(k, e);
        // 92 / 93 = PAGE_UP / PAGE_DOWN (named constants only exist from API 9)
        boolean next = k == 93 || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_VOLUME_DOWN;
        boolean prev = k == 92 || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_LEFT
                || k == KeyEvent.KEYCODE_VOLUME_UP;
        if (cfg.swap) { boolean t = next; next = prev; prev = t; }
        if (next || prev) {
            if (e.getRepeatCount() == 0) { if (next) onNext(); else onPrev(); }
            return true;
        }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (e.getRepeatCount() == 0) onMenu();
            return true;
        }
        return super.onKeyDown(k, e);
    }

    // ------------------------------------------------------------------ state
    /** Adds reading time (idle time beyond 2 minutes after the last action is not counted) and pages. */
    private void flushStats() {
        if (book == null || !started) return;
        long now = System.currentTimeMillis();
        long secs = Math.min((now - sessionStart) / 1000, (lastAct - sessionStart) / 1000 + 120);
        Stats.add(path, secs, sessionPages);
        sessionStart = lastAct = now;
        sessionPages = 0;
    }

    private void savePos() {
        if (book == null || lines == null || !started) return;
        Prefs.savePos(path, chapter, currentOffset(), pct);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sessionStart = lastAct = System.currentTimeMillis();
        sessionPages = 0;
        if (started && !Cfg.sig().equals(sig)) relayout();
    }

    @Override
    protected void onPause() {
        super.onPause();
        flushStats();
        savePos();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (book != null && isFinishing()) {
            book.close();
            if (current == book) current = null;
        }
    }
}
