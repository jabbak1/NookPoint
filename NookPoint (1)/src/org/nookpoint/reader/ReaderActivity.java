package org.nookpoint.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.ScrollView;
import android.widget.TextView;
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
    private int chapter, page, pages = 1, turns, gen, pct;
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
                    view.message = "Cannot open book: " + openError;
                    view.invalidate();
                    return;
                }
                book = bk;
                current = bk;
                Prefs.addRecent(path, bk.title, bk.author);
                tryStart();
            }
        }.execute(path);
    }

    private void tryStart() {
        if (started || book == null || view.getWidth() <= 0) return;
        started = true;
        int[] pos = Prefs.getPos(path);
        int ch = pos[0] < book.count() ? pos[0] : 0;
        load(ch, pos[1], null);
    }

    public void onSize() { tryStart(); }

    /** off >= 0: open the page containing that text offset; off < 0: last page; anchor overrides off. */
    private void load(final int ch, final int off, final String anchor) {
        loading = true;
        final int g = ++gen;
        view.recalc();
        view.message = "Loading...";
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
                    view.message = "Error reading this section";
                    view.invalidate();
                    return;
                }
                chapter = ch;
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
        if (chapter == book.count() - 1 && page == pages - 1 && Stats.finished(path)) {
            int g = Stats.goalBooks();
            Toast.makeText(this, "Book finished!" + (g > 0 ? "  " + Stats.booksThisYear() + " of " + g
                    + " this year" : ""), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Daily goal reached: " + g + " min. Well done!", Toast.LENGTH_LONG).show();
        }
    }

    public void onNext() {
        if (loading || lines == null) return;
        if (page + 1 < pages) { page++; sessionPages++; lastAct = System.currentTimeMillis(); turned(); }
        else if (chapter + 1 < book.count()) {
            sessionPages++;
            lastAct = System.currentTimeMillis();
            Prefs.savePos(path, chapter + 1, 0, pct);
            load(chapter + 1, 0, null);
        }
        else Toast.makeText(this, "End of book", Toast.LENGTH_SHORT).show();
    }

    public void onPrev() {
        if (loading || lines == null) return;
        if (page > 0) { page--; lastAct = System.currentTimeMillis(); turned(); }
        else if (chapter > 0) load(chapter - 1, -1, null);
    }

    // ------------------------------------------------------------------ links
    public void onLink(String href, boolean note) {
        if (loading || book == null || chap == null) return;
        lastAct = System.currentTimeMillis();
        String lo = href.toLowerCase();
        if (lo.startsWith("http:") || lo.startsWith("https:") || lo.startsWith("mailto:") || lo.startsWith("ftp:")) {
            Toast.makeText(this, "External link: " + href, Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Link target not found", Toast.LENGTH_SHORT).show();
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
        TextView tv = Ui.text(this, text, 22, false);
        tv.setPadding(20, 16, 20, 16);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle("Note").setView(sv)
                .setPositiveButton("Close", null)
                .setNegativeButton("Go there", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { jump(idx, frag); }
                }).show();
    }

    private void jump(int idx, String frag) {
        back.add(new int[]{chapter, currentOffset()});
        Toast.makeText(this, "Back button returns here", Toast.LENGTH_SHORT).show();
        if (idx == chapter && frag != null && chap.anchors.containsKey(frag)) {
            page = pageOfOffset(chap.anchors.get(frag).intValue());
            show(false);
        } else {
            load(idx, 0, frag);
        }
    }

    private void goBack() {
        int[] b = back.remove(back.size() - 1);
        if (b[0] == chapter && lines != null) { page = pageOfOffset(b[1]); show(false); }
        else load(b[0], b[1], null);
    }

    @Override
    public void onBackPressed() {
        if (back.size() > 0 && !loading) goBack();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------------ menu
    public void onMenu() {
        if (loading || book == null) return;
        lastAct = System.currentTimeMillis();
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<Integer> codes = new ArrayList<Integer>();
        if (back.size() > 0) { labels.add("Back to previous place"); codes.add(Integer.valueOf(9)); }
        labels.add("Contents"); codes.add(Integer.valueOf(0));
        labels.add("Text size +"); codes.add(Integer.valueOf(1));
        labels.add("Text size -"); codes.add(Integer.valueOf(2));
        labels.add("Statistics"); codes.add(Integer.valueOf(3));
        labels.add("Settings"); codes.add(Integer.valueOf(4));
        labels.add("Back to library"); codes.add(Integer.valueOf(5));
        new AlertDialog.Builder(this).setTitle(book.title)
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        switch (codes.get(which).intValue()) {
                            case 9: goBack(); break;
                            case 0:
                                Intent i = new Intent(ReaderActivity.this, TocActivity.class);
                                i.putExtra("current", chapter);
                                startActivityForResult(i, 1);
                                break;
                            case 1: Cfg.stepSize(1); relayout(); break;
                            case 2: Cfg.stepSize(-1); relayout(); break;
                            case 3: startActivity(new Intent(ReaderActivity.this, StatsActivity.class)); break;
                            case 4: startActivity(new Intent(ReaderActivity.this, SettingsActivity.class)); break;
                            case 5: finish(); break;
                        }
                    }
                }).show();
    }

    private void relayout() {
        if (!started) return;
        int off = currentOffset();
        cfg = Cfg.load();
        sig = Cfg.sig();
        view.configure(cfg);
        load(chapter, off, null);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && res == RESULT_OK && data != null && book != null) {
            load(data.getIntExtra("chapter", 0), 0, null);
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
