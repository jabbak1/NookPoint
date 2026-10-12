package org.nookpoint.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import java.util.ArrayList;

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
                    new AlertDialog.Builder(SettingsActivity.this).setTitle("NookPoint 0.1")
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
        ArrayList<String> rows = new ArrayList<String>();
        for (int i = 0; i < opts.length; i++) rows.add(opts[i].label + ":  " + opts[i].get());
        rows.add("Key test (find your button codes)");
        rows.add("About");
        list.setAdapter(Ui.adapter(this, rows));
        list.setSelection(first);
    }
}
