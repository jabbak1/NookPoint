package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;

/** Online catalogs (OPDS): your Calibre server or any other catalog; browse, search, download books. */
public class OpdsActivity extends Activity {
    private static class Cat { String name = "", url = "", user = "", pass = ""; }
    private static class Loaded { Opds.Feed feed; String base; }

    private ListView list;
    private TextView title;
    private LinearLayout toolbar;
    private final ArrayList<Cat> cats = new ArrayList<Cat>();
    private final ArrayList<String> history = new ArrayList<String>();
    private Cat cur;
    private Opds.Feed feed;
    private String feedBase = "";
    private boolean browsing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        title = Ui.title(this, T.t("Online catalogs"));
        root.addView(title);
        root.addView(Ui.rule(this, 3));
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(5, 8, 5, 6);
        root.addView(toolbar);
        root.addView(Ui.rule(this, 1));
        list = Ui.list(this);
        root.addView(list, new LinearLayout.LayoutParams(Ui.FILL, 0, 1f));
        setContentView(root);
        loadCats();
        showCatalogs();
    }

    // ------------------------------------------------------------------ catalog list
    private void loadCats() {
        cats.clear();
        String s = Prefs.get("opds", "");
        if (s.length() == 0) return;
        String[] lines = s.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String[] f = lines[i].split("\t", 4);
            if (f.length < 4) continue;
            Cat c = new Cat();
            c.name = Esc.unesc(f[0]); c.url = Esc.unesc(f[1]); c.user = Esc.unesc(f[2]); c.pass = Esc.unesc(f[3]);
            cats.add(c);
        }
    }

    private void saveCats() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cats.size(); i++) {
            Cat c = cats.get(i);
            if (i > 0) sb.append('\n');
            sb.append(Esc.esc(c.name)).append('\t').append(Esc.esc(c.url)).append('\t').append(Esc.esc(c.user)).append('\t').append(Esc.esc(c.pass));
        }
        Prefs.put("opds", sb.toString());
    }

    private TextView toolButton(String text, View.OnClickListener l) {
        TextView t = Ui.button(this, text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17);
        t.setPadding(2, 16, 2, 16);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMargins(3, 0, 3, 0);
        toolbar.addView(t, lp);
        return t;
    }

    private void showCatalogs() {
        browsing = false;
        feed = null;
        history.clear();
        title.setText(T.t("Online catalogs"));
        toolbar.removeAllViews();
        toolButton(T.t("Add catalog"), new View.OnClickListener() {
            public void onClick(View v) { editCat(null); }
        });
        if (cats.size() == 0) {
            ArrayList<String> one = new ArrayList<String>();
            one.add(T.t("No catalogs yet.\nTap Add catalog. Example for a Calibre server on your home network: http://192.168.1.10:8080/opds"));
            list.setAdapter(Ui.adapter(this, one));
            list.setOnItemClickListener(null);
            list.setOnItemLongClickListener(null);
            return;
        }
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return cats.size(); }
            public Object getItem(int i) { return cats.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                Cat c = cats.get(pos);
                LinearLayout row = new LinearLayout(OpdsActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(16, 16, 16, 16);
                row.addView(Ui.text(OpdsActivity.this, c.name, 23, true));
                TextView u = Ui.text(OpdsActivity.this, c.url, 17, false);
                u.setSingleLine(true);
                u.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(u);
                return row;
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                cur = cats.get(pos);
                history.clear();
                loadFeed(cur.url, true);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, final int pos, long id) {
                ArrayList<String> items = new ArrayList<String>();
                items.add(T.t("Edit note"));
                items.add(T.t("Delete"));
                Dlg.menu(OpdsActivity.this, cats.get(pos).name, items, new Dlg.Pick() {
                    public void onPick(int i) {
                        if (i == 0) editCat(cats.get(pos));
                        else { cats.remove(pos); saveCats(); showCatalogs(); }
                    }
                });
                return true;
            }
        });
    }

    private void editCat(final Cat existing) {
        String[] init = existing == null ? new String[]{"", "http://", "", ""}
                : new String[]{existing.name, existing.url, existing.user, existing.pass};
        Dlg.form(this, T.t("Catalog"), new String[]{T.t("Name"), T.t("Address (URL)"), T.t("Login (optional)"), T.t("Password (optional)")},
                init, new boolean[]{false, false, false, true}, T.t("Save"), T.t("Cancel"), new Dlg.Form() {
                    public void onForm(String[] v) {
                        String url = v[1];
                        if (url.length() == 0 || url.equals("http://")) return;
                        if (!url.toLowerCase().startsWith("http")) url = "http://" + url;
                        Cat c = existing != null ? existing : new Cat();
                        c.name = v[0].length() > 0 ? v[0] : url;
                        c.url = url;
                        c.user = v[2];
                        c.pass = v[3];
                        if (existing == null) cats.add(c);
                        saveCats();
                        showCatalogs();
                    }
                });
    }

    // ------------------------------------------------------------------ feeds
    private String explain(Throwable t) {
        String m = String.valueOf(t.getMessage());
        String lo = (t.getClass().getName() + " " + m).toLowerCase();
        if (lo.indexOf("ssl") >= 0 || lo.indexOf("handshake") >= 0 || lo.indexOf("trust") >= 0 || lo.indexOf("certificate") >= 0) {
            return m + "\n\n" + T.t("This old Android may not support secure (https) connections. Try an http:// address on your home network.");
        }
        return m;
    }

    private void loadFeed(final String url, final boolean push) {
        title.setText("...");
        new AsyncTask<Void, Void, Object>() {
            @Override
            protected Object doInBackground(Void... v) {
                try {
                    Http.Resp r = Http.get(url, cur.user, cur.pass, 3 << 20);
                    Loaded l = new Loaded();
                    l.feed = Opds.parse(Fb2.decodeBytes(r.body));
                    l.base = r.url;
                    return l;
                } catch (Throwable t) {
                    Diag.e("opds " + url, t);
                    return t;
                }
            }
            @Override
            protected void onPostExecute(Object o) {
                if (o instanceof Throwable) {
                    title.setText(browsing ? (feed != null ? feed.title : cur.name) : T.t("Online catalogs"));
                    Dlg.message(OpdsActivity.this, T.t("Online catalogs"), explain((Throwable) o), T.t("Close"), null, null, null);
                    return;
                }
                Loaded l = (Loaded) o;
                feed = l.feed;
                feedBase = l.base;
                if (push) history.add(url);
                showFeed();
            }
        }.execute();
    }

    private void showFeed() {
        browsing = true;
        title.setText(feed.title.length() > 0 ? feed.title : cur.name);
        toolbar.removeAllViews();
        if (feed.search != null) {
            toolButton(T.t("Search"), new View.OnClickListener() {
                public void onClick(View v) { askSearch(); }
            });
        }
        toolButton(T.t("Catalogs"), new View.OnClickListener() {
            public void onClick(View v) { showCatalogs(); }
        });
        final ArrayList<Opds.Entry> items = feed.entries;
        final boolean hasNext = feed.next != null;
        if (items.size() == 0 && !hasNext) {
            ArrayList<String> one = new ArrayList<String>();
            one.add(T.t("No matches."));
            list.setAdapter(Ui.adapter(this, one));
            list.setOnItemClickListener(null);
            return;
        }
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return items.size() + (hasNext ? 1 : 0); }
            public Object getItem(int i) { return null; }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout row = new LinearLayout(OpdsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(16, 14, 12, 14);
                LinearLayout col = new LinearLayout(OpdsActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                String mark;
                if (pos >= items.size()) {
                    col.addView(Ui.text(OpdsActivity.this, T.t("Next page"), 22, true));
                    mark = "\u203A";
                } else {
                    Opds.Entry e = items.get(pos);
                    TextView t = Ui.text(OpdsActivity.this, e.title, 22, true);
                    t.setMaxLines(2);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    col.addView(t);
                    if (e.author.length() > 0) col.addView(Ui.text(OpdsActivity.this, e.author, 18, false));
                    if (e.summary.length() > 0) {
                        TextView sm = Ui.text(OpdsActivity.this, e.summary.replace('\n', ' '), 16, false);
                        sm.setMaxLines(2);
                        sm.setEllipsize(TextUtils.TruncateAt.END);
                        col.addView(sm);
                    }
                    mark = e.books().size() > 0 ? "\u2193" : (e.nav() != null ? "\u203A" : "");
                }
                row.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                row.addView(Ui.text(OpdsActivity.this, mark, 28, true));
                return row;
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= items.size()) { loadFeed(Opds.resolve(feedBase, feed.next), true); return; }
                Opds.Entry e = items.get(pos);
                if (e.books().size() > 0 || e.nav() == null) showBook(e);
                else loadFeed(Opds.resolve(feedBase, e.nav().href), true);
            }
        });
        list.setOnItemLongClickListener(null);
        list.setSelection(0);
    }

    private void askSearch() {
        Dlg.input(this, T.t("Search"), "", T.t("Search"), T.t("Cancel"), new Dlg.Text() {
            public void onText(final String q) {
                if (q.length() == 0) return;
                if (!feed.searchIsDescription) {
                    loadFeed(Opds.resolve(feedBase, Opds.fillTemplate(feed.search, q)), true);
                    return;
                }
                final String descUrl = Opds.resolve(feedBase, feed.search);
                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... v) {
                        try {
                            return Opds.templateFromDescription(Fb2.decodeBytes(Http.get(descUrl, cur.user, cur.pass, 1 << 20).body));
                        } catch (Throwable t) {
                            Diag.e("opds search description", t);
                            return null;
                        }
                    }
                    @Override
                    protected void onPostExecute(String tpl) {
                        if (tpl == null) {
                            Dlg.message(OpdsActivity.this, T.t("Search"), T.t("This catalog does not support search."), T.t("Close"), null, null, null);
                            return;
                        }
                        loadFeed(Opds.resolve(descUrl, Opds.fillTemplate(tpl, q)), true);
                    }
                }.execute();
            }
        });
    }

    // ------------------------------------------------------------------ books
    private void showBook(final Opds.Entry e) {
        final ArrayList<Opds.Link> books = e.books();
        StringBuilder sb = new StringBuilder();
        if (e.author.length() > 0) sb.append(e.author).append("\n\n");
        sb.append(e.summary.length() > 0 ? e.summary : "");
        if (books.size() == 0) {
            sb.append("\n\n").append(T.t("No supported format (EPUB, FB2) is offered for this book."));
            Dlg.message(this, e.title, sb, T.t("Close"), null, null, null);
            return;
        }
        Dlg.message(this, e.title, sb, T.t("Close"), null, T.t("Download"), new Runnable() {
            public void run() {
                if (books.size() == 1) { download(e, books.get(0)); return; }
                ArrayList<String> names = new ArrayList<String>();
                for (int i = 0; i < books.size(); i++) names.add(Opds.extension(books.get(i)));
                Dlg.menu(OpdsActivity.this, T.t("Download"), names, new Dlg.Pick() {
                    public void onPick(int i) { download(e, books.get(i)); }
                });
            }
        });
    }

    private void download(Opds.Entry e, Opds.Link l) {
        final File dst = new File(Prefs.booksDir(), Opds.fileName(e, l));
        final String url = Opds.resolve(feedBase, l.href);
        final Dlg.ProgressDlg[] pd = new Dlg.ProgressDlg[1];
        final AsyncTask<Void, Long, String>[] holder = new AsyncTask[1];
        pd[0] = Dlg.progress(this, T.t("Download"), T.t("Cancel"), new Runnable() {
            public void run() { if (holder[0] != null) holder[0].cancel(true); }
        });
        pd[0].set(dst.getName(), 0);
        holder[0] = new AsyncTask<Void, Long, String>() {
            @Override
            protected String doInBackground(Void... v) {
                try {
                    Http.download(url, cur.user, cur.pass, dst, new Http.Progress() {
                        public boolean update(long done, long total) {
                            publishProgress(Long.valueOf(done), Long.valueOf(total));
                            return !isCancelled();
                        }
                    });
                    return null;
                } catch (Throwable t) {
                    if (!"cancelled".equals(t.getMessage())) Diag.e("opds download " + url, t);
                    return explain(t);
                }
            }
            @Override
            protected void onProgressUpdate(Long... v) {
                int pct = v[1].longValue() > 0 ? (int) (v[0].longValue() * 100 / v[1].longValue()) : 0;
                pd[0].set(dst.getName() + "  " + (v[0].longValue() / 1024) + " KB", pct);
            }
            @Override
            protected void onCancelled() { pd[0].dismiss(); }
            @Override
            protected void onPostExecute(String err) {
                pd[0].dismiss();
                if (err != null) {
                    Dlg.message(OpdsActivity.this, T.t("Download"), err, T.t("Close"), null, null, null);
                    return;
                }
                Dlg.message(OpdsActivity.this, T.t("Download"), T.f("Saved: {0}", dst.getName()), T.t("Close"), null,
                        T.t("Open"), new Runnable() {
                            public void run() {
                                Intent i = new Intent(OpdsActivity.this, ReaderActivity.class);
                                i.putExtra("path", dst.getAbsolutePath());
                                startActivity(i);
                            }
                        });
            }
        }.execute();
    }

    @Override
    public void onBackPressed() {
        if (browsing && history.size() > 1) {
            history.remove(history.size() - 1);
            loadFeed(history.get(history.size() - 1), false);
        } else if (browsing) {
            showCatalogs();
        } else {
            super.onBackPressed();
        }
    }
}
