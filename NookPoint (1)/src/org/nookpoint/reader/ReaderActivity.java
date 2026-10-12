package org.nookpoint.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

public class ReaderActivity extends Activity implements ReaderView.Listener {
    static Book current;

    private Book book;
    private ReaderView view;
    private Cfg cfg;
    private String sig, path, openError;
    private int chapter, page, pages = 1, rows = 1, turns, gen, pct;
    private ArrayList<Line> lines;
    private boolean loading, started;
    private long sessionStart, lastAct;
    private int sessionPages;

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
        load(ch, pos[1]);
    }

    public void onSize() { tryStart(); }

    /** off >= 0: open the page containing that text offset; off < 0: open the last page. */
    private void load(final int ch, final int off) {
        loading = true;
        final int g = ++gen;
        view.recalc();
        view.message = "Loading...";
        view.lines = null;
        view.invalidate();
        final Cfg c = cfg;
        final int w = view.contentW;
        final int ind = c.indent ? (int) (c.size * 1.5f) : 0;
        new AsyncTask<Void, Void, ArrayList<Line>>() {
            @Override
            protected ArrayList<Line> doInBackground(Void... v) {
                try {
                    return Layouter.layout(book.loadChapter(ch), view.body, view.head, w, ind, c.justify);
                } catch (Throwable t) {
                    return null;
                }
            }
            @Override
            protected void onPostExecute(ArrayList<Line> res) {
                if (g != gen) return;
                loading = false;
                if (res == null) {
                    view.message = "Error reading this section";
                    view.invalidate();
                    return;
                }
                chapter = ch;
                lines = res;
                rows = view.rows;
                pages = Math.max(1, (lines.size() + rows - 1) / rows);
                page = off < 0 ? pages - 1 : pageFor(off);
                show(false);
            }
        }.execute();
    }

    private int pageFor(int off) {
        int idx = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).offset <= off) idx = i; else break;
        }
        return Math.min(pages - 1, idx / rows);
    }

    private int currentOffset() {
        if (lines == null || lines.size() == 0) return 0;
        return lines.get(Math.min(page * rows, lines.size() - 1)).offset;
    }

    private void show(boolean flash) {
        float frac = (page + 1) / (float) pages;
        pct = book.percent(chapter, frac);
        view.lines = lines;
        view.page = page;
        view.pages = pages;
        view.pct = pct;
        view.chapterTitle = book.titleFor(chapter);
        view.battery = battery();
        if (chapter == book.count() - 1 && page == pages - 1) Stats.finished(path);
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
    }

    public void onNext() {
        if (loading || lines == null) return;
        if (page + 1 < pages) { page++; sessionPages++; lastAct = System.currentTimeMillis(); turned(); }
        else if (chapter + 1 < book.count()) { sessionPages++; lastAct = System.currentTimeMillis(); savePosAfter(chapter + 1); load(chapter + 1, 0); }
        else Toast.makeText(this, "End of book", Toast.LENGTH_SHORT).show();
    }

    public void onPrev() {
        if (loading || lines == null) return;
        if (page > 0) { page--; lastAct = System.currentTimeMillis(); turned(); }
        else if (chapter > 0) load(chapter - 1, -1);
    }

    private void savePosAfter(int ch) { Prefs.savePos(path, ch, 0, pct); }

    public void onMenu() {
        if (loading || book == null) return;
        lastAct = System.currentTimeMillis();
        final String[] items = {"Contents", "Text size +", "Text size -", "Statistics", "Settings", "Back to library"};
        new AlertDialog.Builder(this).setTitle(book.title).setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                switch (which) {
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
        load(chapter, off);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && res == RESULT_OK && data != null && book != null) {
            load(data.getIntExtra("chapter", 0), 0);
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
