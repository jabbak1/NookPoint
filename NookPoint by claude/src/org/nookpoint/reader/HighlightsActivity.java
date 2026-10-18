package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Date;

/** Highlights and notes of the open book: go to, edit note, delete, export. */
public class HighlightsActivity extends Activity {
    private String path;
    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        list = Ui.list(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, new LinearLayout.LayoutParams(Ui.FILL, 0, 1f));
        body.addView(Ui.rule(this, 2));
        TextView export = Ui.button(this, T.t("Export"));
        export.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    java.io.File f = NotesExport.exportBook(path);
                    Dlg.message(HighlightsActivity.this, T.t("Export"), T.f("Exported to {0}", f.getAbsolutePath()),
                            T.t("Close"), null, null, null);
                } catch (Throwable t) {
                    Diag.e("export notes", t);
                    Dlg.message(HighlightsActivity.this, T.t("Export"), String.valueOf(t.getMessage()), T.t("Close"), null, null, null);
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
        lp.setMargins(10, 8, 10, 8);
        body.addView(export, lp);
        setContentView(Ui.page(this, T.t("Highlights"), body));
        refresh();
    }

    private void refresh() {
        final ArrayList<Highlights.H> items = Highlights.list(path);
        if (items.size() == 0) {
            ArrayList<String> one = new ArrayList<String>();
            one.add(T.t("No highlights yet.\nLong-press a word and choose Highlight."));
            list.setAdapter(Ui.adapter(this, one));
            return;
        }
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return items.size(); }
            public Object getItem(int i) { return items.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(final int pos, View cv, ViewGroup parent) {
                final Highlights.H h = items.get(pos);
                LinearLayout row = new LinearLayout(HighlightsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(16, 12, 10, 12);
                row.setBackgroundColor(Color.WHITE);
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { menu(pos, h); }
                });
                LinearLayout col = new LinearLayout(HighlightsActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView tx = Ui.text(HighlightsActivity.this, h.text, 20, false);
                tx.setMaxLines(4);
                tx.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(tx);
                if (h.note.length() > 0) {
                    TextView nt = Ui.text(HighlightsActivity.this, "\u270E " + h.note, 18, true);
                    nt.setMaxLines(3);
                    nt.setEllipsize(TextUtils.TruncateAt.END);
                    nt.setPadding(0, 5, 0, 0);
                    col.addView(nt);
                }
                TextView meta = Ui.text(HighlightsActivity.this, h.pct + "%   \u00B7   " + T.dateShort(new Date(h.time)), 16, false);
                meta.setPadding(0, 5, 0, 0);
                col.addView(meta);
                row.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                return row;
            }
        });
    }

    private void menu(final int index, final Highlights.H h) {
        ArrayList<String> items = new ArrayList<String>();
        items.add(T.t("Go to"));
        items.add(T.t("Edit note"));
        items.add(T.t("Delete"));
        Dlg.menu(this, T.t("Highlights"), items, new Dlg.Pick() {
            public void onPick(int i) {
                if (i == 0) {
                    Intent r = new Intent();
                    r.putExtra("chapter", h.chapter);
                    r.putExtra("offset", h.start);
                    setResult(RESULT_OK, r);
                    finish();
                } else if (i == 1) {
                    Dlg.input(HighlightsActivity.this, T.t("Note"), h.note, T.t("Save"), T.t("Cancel"), new Dlg.Text() {
                        public void onText(String s) { Highlights.setNote(path, index, s); refresh(); }
                    });
                } else {
                    Highlights.remove(path, index);
                    refresh();
                }
            }
        });
    }
}
