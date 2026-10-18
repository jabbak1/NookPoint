package org.nookpoint.reader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

/** The library: all books on the card, with search, sorting, filters and list/grid views. */
public class BrowserActivity extends Activity implements Library.Listener {
    private static final String[] SORT_NAMES = {"Title A-Z", "Author", "Recently read", "Progress", "Date added", "Series"};
    private static final String[] FILTER_NAMES = {"All books", "Reading", "New", "Finished"};

    private ListView list;
    private TextView status, clear, btnView;
    private ArrayList<LibItem> all = new ArrayList<LibItem>();
    private ArrayList<LibItem> shown = new ArrayList<LibItem>();
    private int sort, filter, gen;
    private String collection = "";           // "", "series:Name" or "shelf:Name"
    private boolean grid;
    private String query = "";

    private static int intPref(String key) {
        try { return Integer.parseInt(Prefs.get(key, "0")); } catch (Exception e) { return 0; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        sort = Math.min(Math.max(intPref("lib_sort"), 0), SORT_NAMES.length - 1);
        filter = Math.min(Math.max(intPref("lib_filter"), 0), FILTER_NAMES.length - 1);
        collection = Prefs.get("lib_coll", "");
        grid = Prefs.get("lib_view", "list").equals("grid");
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
        reload();
    }

    // ------------------------------------------------------------------ screen
    private TextView toolButton(String text, View.OnClickListener l) {
        TextView t = Ui.button(this, text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17);
        t.setPadding(2, 16, 2, 16);
        t.setSingleLine(true);
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams toolLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMargins(3, 0, 3, 0);
        return lp;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(Ui.title(this, T.t("Library")));
        root.addView(Ui.rule(this, 3));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(5, 8, 5, 4);
        bar.addView(toolButton(T.t("Search"), new View.OnClickListener() {
            public void onClick(View v) { askSearch(); }
        }), toolLp());
        bar.addView(toolButton(T.t("Sort"), new View.OnClickListener() {
            public void onClick(View v) { sortMenu(); }
        }), toolLp());
        bar.addView(toolButton(T.t("Filter"), new View.OnClickListener() {
            public void onClick(View v) { filterMenu(); }
        }), toolLp());
        btnView = toolButton(viewLabel(), new View.OnClickListener() {
            public void onClick(View v) {
                grid = !grid;
                Prefs.put("lib_view", grid ? "grid" : "list");
                btnView.setText(viewLabel());
                apply(false);
            }
        });
        bar.addView(btnView, toolLp());
        root.addView(bar);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(14, 4, 8, 6);
        status = Ui.text(this, "", 17, false);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(status, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        clear = Ui.text(this, "X", 20, true);
        clear.setGravity(Gravity.CENTER);
        clear.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
        clear.setClickable(true);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { query = ""; apply(false); }
        });
        row.addView(clear, new LinearLayout.LayoutParams(44, 40));
        root.addView(row);
        root.addView(Ui.rule(this, 1));

        list = Ui.list(this);
        root.addView(list, new LinearLayout.LayoutParams(Ui.FILL, 0, 1f));
        setContentView(root);
    }

    private String viewLabel() {
        return grid ? T.t("\u2630 List") : T.t("\u25A6 Grid");
    }

    private void reload() {
        all = Library.scan();
        apply(false);
        final int g = ++gen;
        Library.indexMeta(all, new Runnable() {
            public void run() {
                if (g != gen || isFinishing()) return;
                for (int i = 0; i < all.size(); i++) Library.fill(all.get(i));
                apply(true);
            }
        });
    }

    private void apply(boolean keepScroll) {
        int first = (keepScroll && list.getAdapter() != null) ? list.getFirstVisiblePosition() : 0;
        shown = LibSort.apply(all, query, filter, sort, collection,
                collection.startsWith("shelf:") ? Shelves.paths(collection.substring(6)) : null);

        StringBuilder sb = new StringBuilder();
        if (query.length() > 0) sb.append('\u00AB').append(query).append('\u00BB').append("  \u00B7  ");
        sb.append(shown.size()).append(' ').append(T.books(shown.size()))
                .append("  \u00B7  ").append(T.t(SORT_NAMES[sort]))
                .append("  \u00B7  ").append(T.t(FILTER_NAMES[filter]));
        if (collection.startsWith("shelf:")) sb.append("  \u00B7  ").append(T.f("Shelf: {0}", collection.substring(6)));
        else if (collection.startsWith("series:")) sb.append("  \u00B7  ").append(T.f("Series: {0}", collection.substring(7)));
        status.setText(sb.toString());
        clear.setVisibility(query.length() > 0 ? View.VISIBLE : View.GONE);

        if (shown.size() == 0) {
            ArrayList<String> msg = new ArrayList<String>();
            msg.add(all.size() == 0 ? T.t("No books found.\nUse Wi-Fi transfer to add some.")
                    : T.t("Nothing matches your search."));
            list.setAdapter(Ui.adapter(this, msg));
        } else {
            list.setAdapter(grid ? new GridAdapter(this) : new ListAdapter(this));
        }
        list.setSelection(first);
    }

    // ------------------------------------------------------------------ search / sort / filter
    private void askSearch() {
        Dlg.input(this, T.t("Search library"), query, T.t("Search"), T.t("Cancel"), new Dlg.Text() {
            public void onText(String s) { query = s; apply(false); }
        });
    }

    private ArrayList<String> choices(String[] names, int current) {
        ArrayList<String> items = new ArrayList<String>();
        for (int i = 0; i < names.length; i++) items.add((i == current ? "> " : "    ") + T.t(names[i]));
        return items;
    }

    private void sortMenu() {
        Dlg.menu(this, T.t("Sort by"), choices(SORT_NAMES, sort), new Dlg.Pick() {
            public void onPick(int i) {
                sort = i;
                Prefs.put("lib_sort", String.valueOf(i));
                apply(false);
            }
        });
    }

    private void setCollection(String c) {
        collection = c;
        Prefs.put("lib_coll", c);
        apply(false);
    }

    private void filterMenu() {
        final ArrayList<String> items = choices(FILTER_NAMES, filter);
        final int n = items.size();
        items.add(T.t("Shelves..."));
        items.add(T.t("Series..."));
        if (collection.length() > 0) items.add(T.t("Show all books"));
        if (collection.startsWith("shelf:")) items.add(T.t("Delete this shelf"));
        Dlg.menu(this, T.t("Show"), items, new Dlg.Pick() {
            public void onPick(int i) {
                if (i < n) {
                    filter = i;
                    Prefs.put("lib_filter", String.valueOf(i));
                    apply(false);
                } else if (i == n) shelvesMenu();
                else if (i == n + 1) seriesMenu();
                else if (i == n + 2 && collection.length() > 0) setCollection("");
                else {
                    Shelves.delete(collection.substring(6));
                    setCollection("");
                }
            }
        });
    }

    private void newShelf(final LibItem addTo) {
        Dlg.input(this, T.t("New shelf"), "", T.t("Save"), T.t("Cancel"), new Dlg.Text() {
            public void onText(String name) {
                name = Shelves.clean(name);
                if (name.length() == 0) return;
                Shelves.create(name);
                if (addTo != null) {
                    if (!Shelves.contains(name, addTo.path)) Shelves.toggle(name, addTo.path);
                    bookShelves(addTo);
                } else {
                    setCollection("shelf:" + name);
                }
            }
        });
    }

    private void shelvesMenu() {
        final ArrayList<String> names = Shelves.names();
        ArrayList<String> items = new ArrayList<String>();
        for (int i = 0; i < names.size(); i++) items.add(names.get(i) + "  (" + Shelves.paths(names.get(i)).size() + ")");
        items.add("+ " + T.t("New shelf"));
        Dlg.menu(this, T.t("Shelves..."), items, new Dlg.Pick() {
            public void onPick(int i) {
                if (i < names.size()) setCollection("shelf:" + names.get(i));
                else newShelf(null);
            }
        });
    }

    private void seriesMenu() {
        final ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < all.size(); i++) {
            String s = all.get(i).series;
            if (s.length() > 0 && !names.contains(s)) names.add(s);
        }
        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        if (names.size() == 0) {
            Dlg.message(this, T.t("Series..."), T.t("No series found."), T.t("Close"), null, null, null);
            return;
        }
        Dlg.menu(this, T.t("Series..."), names, new Dlg.Pick() {
            public void onPick(int i) { setCollection("series:" + names.get(i)); }
        });
    }

    /** Tick/untick the shelves a book belongs to. */
    private void bookShelves(final LibItem it) {
        final ArrayList<String> names = Shelves.names();
        ArrayList<String> items = new ArrayList<String>();
        for (int i = 0; i < names.size(); i++) items.add((Shelves.contains(names.get(i), it.path) ? "[x]  " : "[  ]  ") + names.get(i));
        items.add("+ " + T.t("New shelf"));
        Dlg.menu(this, T.t("Add to shelf"), items, new Dlg.Pick() {
            public void onPick(int i) {
                if (i < names.size()) {
                    Shelves.toggle(names.get(i), it.path);
                    apply(true);
                    bookShelves(it);
                } else {
                    newShelf(it);
                }
            }
        });
    }

    // ------------------------------------------------------------------ book actions
    public void open(LibItem it) {
        Intent i = new Intent(this, ReaderActivity.class);
        i.putExtra("path", it.path);
        startActivity(i);
    }

    public void menu(final LibItem it) {
        final ArrayList<String> items = new ArrayList<String>();
        items.add(T.t("Open"));
        items.add(T.t("Book info"));
        items.add(T.t("Add to shelf"));
        items.add(T.t(it.finished ? "Mark as unread" : "Mark as read"));
        items.add(T.t("Delete"));
        Dlg.menu(this, it.title, items, new Dlg.Pick() {
            public void onPick(int i) {
                if (i == 0) open(it);
                else if (i == 1) BookInfo.show(BrowserActivity.this, it.path);
                else if (i == 2) bookShelves(it);
                else if (i == 3) {
                    if (it.finished) Prefs.remove("fin:" + it.path, "pos:" + it.path, "pct:" + it.path);
                    else { Stats.finished(it.path); Prefs.setPct(it.path, 100); }
                    Library.fill(it);
                    apply(true);
                } else confirmDelete(it);
            }
        });
    }

    private void confirmDelete(final LibItem it) {
        Dlg.message(this, T.t("Delete"), T.f("Delete \"{0}\" from the card?", it.title),
                T.t("Delete"), new Runnable() {
                    public void run() {
                        it.file.delete();
                        Prefs.removeRecent(it.path);
                        Prefs.remove("pos:" + it.path, "pct:" + it.path, "fin:" + it.path, "bm:" + it.path,
                                "opened:" + it.path);
                        if (it.path.equals(Prefs.get("last", ""))) Prefs.remove("last");
                        all.remove(it);
                        apply(true);
                    }
                }, T.t("Cancel"), null);
    }

    // ------------------------------------------------------------------ adapters
    private class ListAdapter extends BaseAdapter {
        private final Context ctx;
        ListAdapter(Context c) { ctx = c; }
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View cv, ViewGroup parent) {
            BookRow r = (cv instanceof BookRow) ? (BookRow) cv : new BookRow(ctx);
            r.bind(shown.get(pos), BrowserActivity.this);
            return r;
        }
    }

    private class GridAdapter extends BaseAdapter {
        private final Context ctx;
        GridAdapter(Context c) { ctx = c; }
        public int getCount() { return (shown.size() + 2) / 3; }
        public Object getItem(int i) { return null; }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View cv, ViewGroup parent) {
            GridRow r = (cv instanceof GridRow) ? (GridRow) cv : new GridRow(ctx);
            r.bind(shown, pos * 3, BrowserActivity.this);
            return r;
        }
    }
}
