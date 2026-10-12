package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class BrowserActivity extends Activity {
    private File dir;
    private ListView list;
    private TextView title;
    private final ArrayList<File> items = new ArrayList<File>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        String d = Prefs.get("browse", null);
        dir = (d != null && new File(d).isDirectory()) ? new File(d) : Prefs.booksDir();
        list = Ui.list(this);
        LinearLayout page = Ui.page(this, "", list);
        title = (TextView) page.getChildAt(0);
        setContentView(page);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                File f = items.get(pos);
                if (f == null) {
                    if (pos == 0 && dir.getParentFile() != null) { dir = dir.getParentFile(); show(); }
                    return;
                }
                if (f.isDirectory()) { dir = f; show(); }
                else {
                    Intent i = new Intent(BrowserActivity.this, ReaderActivity.class);
                    i.putExtra("path", f.getAbsolutePath());
                    startActivity(i);
                }
            }
        });
        show();
    }

    private void show() {
        Prefs.put("browse", dir.getAbsolutePath());
        title.setText(dir.getAbsolutePath());
        ArrayList<File> dirs = new ArrayList<File>(), files = new ArrayList<File>();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (int i = 0; i < fs.length; i++) {
                String n = fs[i].getName();
                if (n.startsWith(".")) continue;
                if (fs[i].isDirectory()) dirs.add(fs[i]);
                else {
                    String l = n.toLowerCase();
                    if (l.endsWith(".epub") || l.endsWith(".txt")) files.add(fs[i]);
                }
            }
        }
        Comparator<File> cmp = new Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        };
        Collections.sort(dirs, cmp);
        Collections.sort(files, cmp);
        items.clear();
        ArrayList<String> rows = new ArrayList<String>();
        if (dir.getParentFile() != null) { items.add(null); rows.add("[ .. ]"); }
        for (int i = 0; i < dirs.size(); i++) { items.add(dirs.get(i)); rows.add("[ " + dirs.get(i).getName() + " ]"); }
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            int pc = Prefs.pct(f.getAbsolutePath());
            items.add(f);
            rows.add(f.getName() + (pc > 0 ? "   " + pc + "%" : ""));
        }
        if (dirs.size() + files.size() == 0) { items.add(null); rows.add("(no books here - use File transfer)"); }
        list.setAdapter(Ui.adapter(this, rows));
    }

    @Override
    public void onBackPressed() {
        File parent = dir.getParentFile();
        if (parent == null || dir.equals(Prefs.root())) finish();
        else { dir = parent; show(); }
    }
}
