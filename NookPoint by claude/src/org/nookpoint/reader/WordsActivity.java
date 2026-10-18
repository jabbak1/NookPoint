package org.nookpoint.reader;

import android.app.Activity;
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

/** Words looked up in the dictionary: review, delete, export for flash cards. */
public class WordsActivity extends Activity {
    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        list = Ui.list(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, new LinearLayout.LayoutParams(Ui.FILL, 0, 1f));
        body.addView(Ui.rule(this, 2));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(6, 8, 6, 8);
        TextView export = Ui.button(this, T.t("Export"));
        export.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    java.io.File f = NotesExport.exportWords();
                    Dlg.message(WordsActivity.this, T.t("Export"), T.f("Exported to {0}", f.getAbsolutePath()),
                            T.t("Close"), null, null, null);
                } catch (Throwable t) {
                    Diag.e("export words", t);
                }
            }
        });
        TextView clear = Ui.button(this, T.t("Clear all"));
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Dlg.message(WordsActivity.this, T.t("Clear all"), T.t("Delete all saved words?"), T.t("Delete"), new Runnable() {
                    public void run() { WordList.clear(); refresh(); }
                }, T.t("Cancel"), null);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMargins(4, 0, 4, 0);
        bar.addView(export, lp);
        bar.addView(clear, lp);
        body.addView(bar);
        setContentView(Ui.page(this, T.t("Saved words"), body));
        refresh();
    }

    private void refresh() {
        final ArrayList<WordList.W> items = WordList.list();
        if (items.size() == 0) {
            ArrayList<String> one = new ArrayList<String>();
            one.add(T.t("No saved words yet.\nWords you look up in the dictionary are saved here."));
            list.setAdapter(Ui.adapter(this, one));
            return;
        }
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return items.size(); }
            public Object getItem(int i) { return items.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(final int pos, View cv, ViewGroup parent) {
                final WordList.W w = items.get(pos);
                LinearLayout row = new LinearLayout(WordsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(16, 12, 10, 12);
                row.setBackgroundColor(Color.WHITE);
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(w.def);
                        if (w.ctx.length() > 0) sb.append("\n\n\u201C").append(w.ctx).append('\u201D');
                        if (w.book.length() > 0) sb.append("\n\n").append(w.book);
                        Dlg.message(WordsActivity.this, w.head.length() > 0 ? w.head : w.word, sb, T.t("Close"), null, null, null);
                    }
                });
                LinearLayout col = new LinearLayout(WordsActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.addView(Ui.text(WordsActivity.this, w.word, 22, true));
                TextView def = Ui.text(WordsActivity.this, w.def.replace('\n', ' '), 18, false);
                def.setMaxLines(2);
                def.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(def);
                row.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                TextView del = Ui.text(WordsActivity.this, "X", 22, true);
                del.setGravity(Gravity.CENTER);
                del.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
                del.setClickable(true);
                del.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { WordList.remove(pos); refresh(); }
                });
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(60, 60);
                dl.leftMargin = 10;
                row.addView(del, dl);
                return row;
            }
        });
    }
}
