package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

/** Full-text search through the open book. */
public class SearchActivity extends Activity {
    private static class Hit { int chapter, offset, pct; String snippet, where; }

    private String query;
    private ListView list;
    private AsyncTask<Void, Integer, ArrayList<Hit>> task;
    private Dlg.ProgressDlg pd;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        query = getIntent().getStringExtra("query");
        final Book book = ReaderActivity.current;
        if (query == null || query.trim().length() == 0 || book == null) { finish(); return; }
        query = query.trim();
        list = Ui.list(this);
        setContentView(Ui.page(this, T.t("Search in book") + ": " + query, list));
        pd = Dlg.progress(this, T.t("Search in book"), T.t("Cancel"), new Runnable() {
            public void run() { if (task != null) task.cancel(true); if (list.getAdapter() == null) finish(); }
        });
        pd.set(T.t("Searching..."), 0);
        final String q = query.toLowerCase();
        task = new AsyncTask<Void, Integer, ArrayList<Hit>>() {
            @Override
            protected ArrayList<Hit> doInBackground(Void... v) {
                ArrayList<Hit> out = new ArrayList<Hit>();
                int n = book.count();
                for (int ch = 0; ch < n && !isCancelled() && out.size() < 300; ch++) {
                    try {
                        Chapter c = book.loadChapter(ch);
                        int total = 1;
                        if (c.paras.size() > 0) { Para last = c.paras.get(c.paras.size() - 1); total = Math.max(1, last.offset + last.text.length()); }
                        String where = book.titleFor(ch);
                        for (int pi = 0; pi < c.paras.size() && out.size() < 300; pi++) {
                            Para p = c.paras.get(pi);
                            if (p.img != null || p.text.length() < q.length()) continue;
                            String low = p.text.toLowerCase();
                            int from = 0, found = 0;
                            while (found < 3 && (from = low.indexOf(q, from)) >= 0) {
                                Hit h = new Hit();
                                h.chapter = ch;
                                h.offset = p.offset + from;
                                h.pct = book.percent(ch, (p.offset + from) / (float) total);
                                h.snippet = snippet(p.text, from, q.length());
                                h.where = where;
                                out.add(h);
                                from += q.length();
                                found++;
                            }
                        }
                    } catch (Throwable t) {
                        Diag.e("search chapter " + ch, t);
                    }
                    publishProgress(Integer.valueOf((ch + 1) * 100 / n));
                }
                return out;
            }
            @Override
            protected void onProgressUpdate(Integer... v) {
                if (pd != null) pd.set(T.t("Searching...") + "  " + v[0] + "%", v[0].intValue());
            }
            @Override
            protected void onPostExecute(ArrayList<Hit> r) {
                if (pd != null) pd.dismiss();
                show(r);
            }
            @Override
            protected void onCancelled() {
                if (pd != null) pd.dismiss();
            }
        }.execute();
    }

    private static String snippet(String t, int at, int len) {
        int s = Math.max(0, at - 45), e = Math.min(t.length(), at + len + 70);
        while (s > 0 && s < at && t.charAt(s) != ' ') s++;
        while (e < t.length() && e > at + len && t.charAt(e) != ' ') e--;
        return (s > 0 ? "\u2026" : "") + t.substring(s, e).trim() + (e < t.length() ? "\u2026" : "");
    }

    private void show(final ArrayList<Hit> r) {
        if (r.size() == 0) {
            ArrayList<String> one = new ArrayList<String>();
            one.add(T.t("No matches."));
            list.setAdapter(Ui.adapter(this, one));
            return;
        }
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return r.size(); }
            public Object getItem(int i) { return r.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                final Hit h = r.get(pos);
                LinearLayout row = new LinearLayout(SearchActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(16, 14, 16, 14);
                row.setBackgroundColor(Color.WHITE);
                TextView sn = Ui.text(SearchActivity.this, h.snippet, 20, false);
                sn.setMaxLines(3);
                sn.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(sn);
                TextView meta = Ui.text(SearchActivity.this, h.pct + "%" + (h.where.length() > 0 ? "   \u00B7   " + h.where : ""), 16, false);
                meta.setPadding(0, 4, 0, 0);
                meta.setSingleLine(true);
                meta.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(meta);
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent res = new Intent();
                        res.putExtra("chapter", h.chapter);
                        res.putExtra("offset", h.offset);
                        res.putExtra("query", query);
                        setResult(RESULT_OK, res);
                        finish();
                    }
                });
                return row;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (task != null) task.cancel(true);
        if (pd != null) pd.dismiss();
    }
}
