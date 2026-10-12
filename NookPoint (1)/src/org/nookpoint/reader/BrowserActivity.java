package org.nookpoint.reader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
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
    private final ArrayList<File> items = new ArrayList<File>();   // null = ".." or empty-notice
    private final ArrayList<String> labels = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        String d = Prefs.get("browse", null);
        dir = (d != null && new File(d).isDirectory()) ? new File(d) : Prefs.booksDir();
        list = Ui.list(this);
        LinearLayout page = Ui.page(this, "Library", list);
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

    @Override
    protected void onResume() {
        super.onResume();
        if (list.getAdapter() != null) ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
    }

    private void show() {
        Prefs.put("browse", dir.getAbsolutePath());
        String path = dir.getAbsolutePath();
        title.setText(path.equals(Prefs.booksDir().getAbsolutePath()) ? "Library" : dir.getName());
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
        labels.clear();
        if (dir.getParentFile() != null) { items.add(null); labels.add("[ .. ]   " + path); }
        for (int i = 0; i < dirs.size(); i++) { items.add(dirs.get(i)); labels.add("[ " + dirs.get(i).getName() + " ]"); }
        for (int i = 0; i < files.size(); i++) { items.add(files.get(i)); labels.add(""); }
        if (dirs.size() + files.size() == 0) { items.add(null); labels.add("No books here.\nUse Wi-Fi transfer to add some."); }
        list.setAdapter(new LibAdapter(this));
    }

    @Override
    public void onBackPressed() {
        File parent = dir.getParentFile();
        if (parent == null || dir.equals(Prefs.root())) finish();
        else { dir = parent; show(); }
    }

    private class LibAdapter extends BaseAdapter {
        private final Context ctx;
        LibAdapter(Context c) { ctx = c; }
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) {
            File f = items.get(i);
            return (f != null && f.isFile()) ? 1 : 0;
        }
        public View getView(int pos, View cv, ViewGroup parent) {
            File f = items.get(pos);
            if (f != null && f.isFile()) {
                BookRow r = (cv instanceof BookRow) ? (BookRow) cv : new BookRow(ctx);
                r.bind(f);
                return r;
            }
            TextView t = (cv instanceof TextView) ? (TextView) cv : Ui.text(ctx, "", 24, true);
            t.setPadding(16, 26, 16, 26);
            t.setText(labels.get(pos));
            return t;
        }
    }
}
