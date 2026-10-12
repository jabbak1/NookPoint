package org.nookpoint.reader;

import android.app.Activity;
import android.app.AlertDialog;
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
        opts = Cfg.opts();
        list = Ui.list(this);
        setContentView(Ui.page(this, "Settings", list));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < opts.length) {
                    opts[pos].cycle();
                    refresh();
                } else if (pos == opts.length) {
                    startActivity(new Intent(SettingsActivity.this, KeyTestActivity.class));
                } else {
                    new AlertDialog.Builder(SettingsActivity.this).setTitle("NookPoint 0.2")
                            .setMessage("Minimal EPUB/TXT reader for Nook Simple Touch, inspired by CrossPoint Reader.\n\n"
                                    + "Books: " + Prefs.booksDir().getAbsolutePath() + "\nFonts: "
                                    + Prefs.fontsDir().getAbsolutePath())
                            .setPositiveButton("OK", null).show();
                }
            }
        });
        refresh();
    }

    private void refresh() {
        int first = list.getFirstVisiblePosition();
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return opts.length + 2; }
            public Object getItem(int i) { return null; }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout r = new LinearLayout(SettingsActivity.this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(16, 16, 16, 16);
                String label = pos < opts.length ? opts[pos].label : (pos == opts.length ? "Key test (button codes)" : "About");
                TextView l = Ui.text(SettingsActivity.this, label, 22, false);
                r.addView(l, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                if (pos < opts.length) {
                    TextView v = Ui.text(SettingsActivity.this, opts[pos].get(), 21, true);
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
