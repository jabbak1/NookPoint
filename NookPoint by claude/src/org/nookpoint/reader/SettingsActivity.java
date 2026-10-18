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
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

public class SettingsActivity extends Activity {
    private static final String[] EXTRA = {"Dictionaries", "Saved words", "Back up data", "Restore data",
            "Show error log", "Key test (button codes)", "About"};

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
                } else {
                    action(EXTRA[pos - opts.length]);
                }
            }
        });
        refresh();
    }

    private void action(String key) {
        if (key.equals("Dictionaries")) startActivity(new Intent(this, DictsActivity.class));
        else if (key.equals("Saved words")) startActivity(new Intent(this, WordsActivity.class));
        else if (key.equals("Back up data")) backup();
        else if (key.equals("Restore data")) restoreMenu();
        else if (key.equals("Show error log")) showLog();
        else if (key.equals("Key test (button codes)")) startActivity(new Intent(this, KeyTestActivity.class));
        else {
            Dlg.message(this, "NookPoint 0.8",
                    T.f("Reader for Nook Simple Touch (EPUB, FB2, TXT), inspired by CrossPoint Reader.\n\nBooks: {0}\nFonts: {1}\nDictionaries: {2}\nNotes and backups: {3}\n\nBundled fonts: Lora, IBM Plex Serif (SIL OFL), DejaVu (free license). Hyphenation patterns: TeX hyph-utf8 project (see assets/hyph).",
                            Prefs.booksDir().getAbsolutePath(), Prefs.fontsDir().getAbsolutePath(),
                            Prefs.dictDir().getAbsolutePath(), Diag.dir().getAbsolutePath()), "OK", null, null, null);
        }
    }

    private void backup() {
        try {
            File f = Backup.export();
            Dlg.message(this, T.t("Back up data"), T.f("Backup saved: {0}", f.getAbsolutePath()), T.t("Close"), null, null, null);
        } catch (Throwable t) {
            Diag.e("backup", t);
            Dlg.message(this, T.t("Back up data"), String.valueOf(t.getMessage()), T.t("Close"), null, null, null);
        }
    }

    private void restoreMenu() {
        final File[] fs = Backup.list();
        if (fs.length == 0) {
            Dlg.message(this, T.t("Restore data"), T.t("No backups found.") + "\n\n" + Backup.dir().getAbsolutePath(),
                    T.t("Close"), null, null, null);
            return;
        }
        ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < fs.length; i++) names.add(fs[i].getName() + "  (" + (fs[i].length() / 1024 + 1) + " KB)");
        Dlg.menu(this, T.t("Restore data"), names, new Dlg.Pick() {
            public void onPick(final int i) {
                Dlg.message(SettingsActivity.this, T.t("Restore data"),
                        T.t("Restore this backup? Current settings, positions and notes will be replaced.") + "\n\n" + fs[i].getName(),
                        T.t("Restore data"), new Runnable() {
                            public void run() {
                                try {
                                    int n = Backup.restore(fs[i]);
                                    Toast.makeText(SettingsActivity.this, T.f("Restored {0} entries.", n), Toast.LENGTH_LONG).show();
                                    build();
                                } catch (Throwable t) {
                                    Diag.e("restore", t);
                                    Dlg.message(SettingsActivity.this, T.t("Restore data"), String.valueOf(t.getMessage()),
                                            T.t("Close"), null, null, null);
                                }
                            }
                        }, T.t("Cancel"), null);
            }
        });
    }

    private void showLog() {
        String s = Diag.read(3000);
        Dlg.message(this, T.t("Error log"), s.length() == 0 ? T.t("The log is empty.") : s,
                T.t("Close"), null, T.t("Clear"), new Runnable() {
                    public void run() { Diag.clear(); }
                });
    }

    private void refresh() {
        int first = list.getFirstVisiblePosition();
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return opts.length + EXTRA.length; }
            public Object getItem(int i) { return null; }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout r = new LinearLayout(SettingsActivity.this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(16, 16, 16, 16);
                String label = pos < opts.length ? T.t(opts[pos].label) : T.t(EXTRA[pos - opts.length]);
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
