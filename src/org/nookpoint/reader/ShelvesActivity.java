package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;

/** Visual shelves: your own shelves, automatic shelves (reading / new / finished) and series, as tiles. */
public class ShelvesActivity extends Activity {
    private LinearLayout col;
    private int gen;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(Ui.FILL, Ui.WRAP));
        setContentView(Ui.page(this, T.t("Shelves"), sv));
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void openLibrary(String collection, int filter) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra("collection", collection);
        i.putExtra("filter", filter);
        i.putExtra("view", "shelf");
        startActivity(i);
    }

    private ShelfTile tile(String name, ArrayList<String> covers, int count, boolean plus) {
        ShelfTile t = new ShelfTile(this);
        t.bind(name, plus ? "" : count + " " + T.books(count), covers, plus);
        return t;
    }

    private void addTiles(ArrayList<View> tiles) {
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(10, 6, 10, 6);
            for (int k = 0; k < 2; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 250, 1f);
                lp.setMargins(4, 0, 4, 0);
                row.addView(i + k < tiles.size() ? tiles.get(i + k) : new View(this), lp);
            }
            col.addView(row);
        }
    }

    private void build() {
        col.removeAllViews();
        col.addView(Ui.section(this, T.t("MY SHELVES")));
        ArrayList<View> mine = new ArrayList<View>();
        ArrayList<String> names = Shelves.names();
        for (int i = 0; i < names.size(); i++) {
            final String n = names.get(i);
            ArrayList<String> ps = Shelves.existing(n);
            ShelfTile t = tile(n, new ArrayList<String>(ps.subList(0, Math.min(3, ps.size()))), ps.size(), false);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openLibrary("shelf:" + n, 0); }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { shelfMenu(n); return true; }
            });
            mine.add(t);
        }
        ShelfTile plus = tile("+ " + T.t("New shelf"), new ArrayList<String>(), 0, true);
        plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Dlg.input(ShelvesActivity.this, T.t("New shelf"), "", T.t("Save"), T.t("Cancel"), new Dlg.Text() {
                    public void onText(String s) { Shelves.create(s); build(); }
                });
            }
        });
        mine.add(plus);
        addTiles(mine);

        final int g = ++gen;
        new AsyncTask<Void, Void, ArrayList<LibItem>>() {
            @Override
            protected ArrayList<LibItem> doInBackground(Void... v) { return Library.scan(); }
            @Override
            protected void onPostExecute(ArrayList<LibItem> items) {
                if (g == gen && !isFinishing()) automatic(items);
            }
        }.execute();
    }

    private ArrayList<String> covers(ArrayList<LibItem> l) {
        ArrayList<String> r = new ArrayList<String>();
        for (int i = 0; i < l.size() && i < 3; i++) r.add(l.get(i).path);
        return r;
    }

    private void automatic(ArrayList<LibItem> items) {
        ArrayList<LibItem> reading = new ArrayList<LibItem>(), fresh = new ArrayList<LibItem>(), done = new ArrayList<LibItem>();
        for (int i = 0; i < items.size(); i++) {
            LibItem it = items.get(i);
            if (it.finished) done.add(it); else if (it.pct > 0) reading.add(it); else fresh.add(it);
        }
        Collections.sort(reading, new Comparator<LibItem>() {
            public int compare(LibItem a, LibItem b) { return a.opened < b.opened ? 1 : (a.opened > b.opened ? -1 : 0); }
        });
        ArrayList<View> auto = new ArrayList<View>();
        Object[][] defs = {{"Reading", reading, 1}, {"New", fresh, 2}, {"Finished", done, 3}};
        for (int i = 0; i < defs.length; i++) {
            @SuppressWarnings("unchecked") ArrayList<LibItem> l = (ArrayList<LibItem>) defs[i][1];
            if (l.size() == 0) continue;
            final int filter = ((Integer) defs[i][2]).intValue();
            ShelfTile t = tile(T.t((String) defs[i][0]), covers(l), l.size(), false);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openLibrary("", filter); }
            });
            auto.add(t);
        }
        if (auto.size() > 0) {
            col.addView(Ui.rule(this, 1));
            col.addView(Ui.section(this, T.t("AUTOMATIC")));
            addTiles(auto);
        }
        TreeMap<String, ArrayList<LibItem>> series = new TreeMap<String, ArrayList<LibItem>>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < items.size(); i++) {
            LibItem it = items.get(i);
            if (it.series.length() == 0) continue;
            ArrayList<LibItem> l = series.get(it.series);
            if (l == null) { l = new ArrayList<LibItem>(); series.put(it.series, l); }
            l.add(it);
        }
        ArrayList<View> st = new ArrayList<View>();
        for (final java.util.Map.Entry<String, ArrayList<LibItem>> e : series.entrySet()) {
            ArrayList<LibItem> l = e.getValue();
            Collections.sort(l, new Comparator<LibItem>() {
                public int compare(LibItem a, LibItem b) { return a.seriesNo < b.seriesNo ? -1 : (a.seriesNo > b.seriesNo ? 1 : 0); }
            });
            ShelfTile t = tile(e.getKey(), covers(l), l.size(), false);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openLibrary("series:" + e.getKey(), 0); }
            });
            st.add(t);
        }
        if (st.size() > 0) {
            col.addView(Ui.rule(this, 1));
            col.addView(Ui.section(this, T.t("SERIES")));
            addTiles(st);
        }
    }

    private void shelfMenu(final String name) {
        ArrayList<String> items = new ArrayList<String>();
        items.add(T.t("Open"));
        items.add(T.t("Rename"));
        items.add(T.t("Delete"));
        Dlg.menu(this, name, items, new Dlg.Pick() {
            public void onPick(int i) {
                if (i == 0) openLibrary("shelf:" + name, 0);
                else if (i == 1) {
                    Dlg.input(ShelvesActivity.this, T.t("Rename"), name, T.t("Save"), T.t("Cancel"), new Dlg.Text() {
                        public void onText(String s) { Shelves.rename(name, s); build(); }
                    });
                } else {
                    Dlg.message(ShelvesActivity.this, T.t("Delete"), T.f("Delete the shelf \"{0}\"? The books stay in the library.", name),
                            T.t("Delete"), new Runnable() {
                                public void run() { Shelves.delete(name); build(); }
                            }, T.t("Cancel"), null);
                }
            }
        });
    }
}
