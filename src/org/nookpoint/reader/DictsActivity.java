package org.nookpoint.reader;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shows what the dictionary search found, what it could not use, and how to add dictionaries. */
public class DictsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        TextView t = Ui.text(this, "...", 22, false);
        t.setPadding(16, 24, 16, 24);
        setContentView(Ui.page(this, T.t("Dictionaries"), t));
        scan();
    }

    private void scan() {
        new AsyncTask<Void, Void, Dicts.Report>() {
            @Override
            protected Dicts.Report doInBackground(Void... v) {
                Dicts.invalidate();
                return Dicts.report();
            }
            @Override
            protected void onPostExecute(Dicts.Report r) {
                show(r);
            }
        }.execute();
    }

    private TextView small(String s) {
        TextView t = Ui.text(this, s, 18, false);
        t.setPadding(16, 3, 16, 3);
        return t;
    }

    private void show(Dicts.Report r) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(Ui.FILL, Ui.WRAP));

        col.addView(Ui.section(this, T.t("INSTALLED")));
        if (r.dicts.size() == 0) {
            TextView none = Ui.text(this, T.t("No dictionaries found."), 22, true);
            none.setPadding(16, 6, 16, 10);
            col.addView(none);
        }
        for (int i = 0; i < r.dicts.size(); i++) {
            Dictionary d = r.dicts.get(i);
            col.addView(Ui.kv(this, d.getName(), d.getWordCount() >= 0 ? T.f("{0} words", d.getWordCount()) : "dictd"));
            col.addView(small(d.getPath()));
        }

        if (r.problems.size() > 0) {
            col.addView(Ui.rule(this, 1));
            col.addView(Ui.section(this, T.t("NOT USABLE")));
            for (int i = 0; i < r.problems.size(); i++) {
                String[] p = r.problems.get(i);
                TextView f = Ui.text(this, p[0], 20, true);
                f.setPadding(16, 6, 16, 0);
                col.addView(f);
                col.addView(small(T.t(p[1])));
            }
        }
        if (r.others.size() > 0) {
            col.addView(Ui.rule(this, 1));
            col.addView(Ui.section(this, T.t("OTHER FILES IN DICTIONARY FOLDERS")));
            for (int i = 0; i < r.others.size(); i++) col.addView(small(r.others.get(i)));
        }

        col.addView(Ui.rule(this, 1));
        col.addView(Ui.section(this, T.t("FOLDERS SEARCHED")));
        for (int i = 0; i < r.folders.size(); i++) col.addView(small(r.folders.get(i)));

        col.addView(Ui.rule(this, 1));
        col.addView(Ui.section(this, T.t("HOW TO ADD DICTIONARIES")));
        TextView how = Ui.text(this, T.f("Use StarDict dictionaries: a folder (or loose files) with .ifo, .idx and .dict or .dict.dz files, or a dictd pair (.index and .dict.dz). Put them into {0} or upload them with Wi-Fi transfer. Archives (.zip, .tar.gz) are unpacked automatically. Other formats (.dsl, .mdx, .bgl, .xz, .bz2) must be converted on a computer, for example with PyGlossary.",
                Prefs.dictDir().getAbsolutePath()), 19, false);
        how.setPadding(16, 4, 16, 14);
        col.addView(how);

        TextView again = Ui.button(this, T.t("Rescan"));
        again.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TextView t = Ui.text(DictsActivity.this, "...", 22, false);
                t.setPadding(16, 24, 16, 24);
                setContentView(Ui.page(DictsActivity.this, T.t("Dictionaries"), t));
                scan();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
        lp.setMargins(14, 4, 14, 20);
        col.addView(again, lp);
        setContentView(Ui.page(this, T.t("Dictionaries"), sv));
    }
}
