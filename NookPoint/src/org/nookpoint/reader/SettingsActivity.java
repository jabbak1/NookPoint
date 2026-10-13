package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private Cfg.Opt[] opts;
    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        build();
    }

    private void build() {
        opts = Cfg.opts();
        list = Ui.list(this);
        setContentView(Ui.page(this, T.t("Settings"), list));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < opts.length) {
                    opts[pos].cycle();
                    if (opts[pos].key.equals("lang")) {
                        T.reload();
                        build();
                        list.setSelection(Math.max(0, pos - 2));
                    } else {
                        refresh();
                    }
                } else if (pos == opts.length) {
                    startActivity(new Intent(SettingsActivity.this, KeyTestActivity.class));
                } else if (pos == opts.length + 1) {
                    startActivity(new Intent(SettingsActivity.this, DictsActivity.class));
                } else {
                    Dlg.message(SettingsActivity.this, "NookPoint 0.6",
                            T.f("Minimal EPUB/TXT reader for Nook Simple Touch, inspired by CrossPoint Reader.\n\nBooks: {0}\nFonts: {1}\nDictionaries: {2}\n\nBundled fonts: Lora, IBM Plex Serif (SIL OFL), DejaVu (free license).",
                                    Prefs.booksDir().getAbsolutePath(), Prefs.fontsDir().getAbsolutePath(),
                                    Prefs.dictDir().getAbsolutePath()), "OK", null, null, null);
                }
            }
        });
        refresh();
    }

    private void refresh() {
        int first = list.getFirstVisiblePosition();
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return opts.length + 3; }
            public Object getItem(int i) { return null; }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout r = new LinearLayout(SettingsActivity.this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(16, 16, 16, 16);
                String label = pos < opts.length ? T.t(opts[pos].label)
                        : (pos == opts.length ? T.t("Key test (button codes)")
                        : (pos == opts.length + 1 ? T.t("Dictionaries") : T.t("About")));
                TextView l = Ui.text(SettingsActivity.this, label, 22, false);
                r.addView(l, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                if (pos < opts.length) {
                    String val = opts[pos].get();
                    TextView v = Ui.text(SettingsActivity.this, T.t(val), 21, true);
                    if (opts[pos].key.equals("font")) {          // preview the font in its own face
                        try { v.setTypeface(Cfg.fontSet(val).get(1)); } catch (Throwable t) { }
                    }
                    v.setPadding(16, 8, 16, 8);
                    v.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
                    r.addView(v, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
                } else {
                    r.addView(Ui.text(SettingsActivity.this, ">", 24, true));
                }
                return r;
            }
        });
        list.setSelection(first);
    }
}
