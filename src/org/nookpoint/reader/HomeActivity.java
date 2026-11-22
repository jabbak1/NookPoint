package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

/** The home screen (and launcher): current book, recent books on a shelf, your shelves, and a dock. */
public class HomeActivity extends Activity {
    private LinearLayout col;
    private boolean dead;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        if (CrashGuard.isLoop()) {                       // a launcher that keeps crashing must not lock the device
            LauncherHelper.stopBeingHome(this);
            CrashGuard.clear();
            Toast.makeText(this, T.t("NookPoint stopped being the home screen after repeated crashes. Press Home to choose another launcher."), Toast.LENGTH_LONG).show();
            dead = true;
            finish();
            return;
        }
        Prefs.booksDir();
        Backup.auto();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    /** As the home screen there is nowhere to go back to. */
    @Override
    public void onBackPressed() {
        if (getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME)) return;
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dead) return;
        build();
        refresh();
        WhatsNew.maybeShow(this);
    }

    // ------------------------------------------------------------------ frame: scrolling content + dock
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(Ui.FILL, Ui.WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(Ui.FILL, 0, 1f));
        root.addView(Ui.rule(this, 1));
        root.addView(dock());
        setContentView(root);
    }

    private View dockItem(int kind, String label, final Class<?> cls) {
        IconButton b = new IconButton(this, kind, label);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, cls)); }
        });
        return b;
    }

    private View dock() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.HORIZONTAL);
        d.setBackgroundColor(Color.WHITE);
        d.setPadding(4, 6, 4, 6);
        Object[][] items = {
                {UiArt.LIBRARY, T.t("Library"), BrowserActivity.class},
                {UiArt.APPS, T.t("Apps"), AppsActivity.class},
                {UiArt.WIFI, T.t("Wi-Fi"), TransferActivity.class},
                {UiArt.ONLINE, T.t("Online"), OpdsActivity.class},
                {UiArt.STATS, T.t("Stats"), StatsActivity.class},
                {UiArt.SETTINGS, T.t("Settings"), SettingsActivity.class}};
        for (int i = 0; i < items.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
            lp.setMargins(2, 0, 2, 0);
            d.addView(dockItem(((Integer) items[i][0]).intValue(), (String) items[i][1], (Class<?>) items[i][2]), lp);
        }
        return d;
    }

    private void open(String path) {
        Intent i = new Intent(this, ReaderActivity.class);
        i.putExtra("path", path);
        startActivity(i);
    }

    // ------------------------------------------------------------------ content
    private void refresh() {
        col.removeAllViews();

        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(20, 24, 20, 6);
        hd.addView(Ui.text(this, "NookPoint", 34, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        int goal = Stats.goalMinutes();
        long[] today = Stats.day(Stats.today());
        if (goal > 0) {
            hd.addView(Ui.chip(this, (today[0] >= goal * 60L ? "\u2713 " : "") + Stats.fmt(today[0]) + " / " + goal + " " + T.t("min")));
        } else if (today[0] > 0) {
            hd.addView(Ui.chip(this, Stats.fmt(today[0])));
        }
        col.addView(hd);

        String last = Prefs.get("last", null);
        if (last != null && !new File(last).exists()) last = null;
        col.addView(last != null ? hero(last) : welcome());

        ArrayList<LibItem> rec = new ArrayList<LibItem>();
        ArrayList<String> all = Prefs.recents();
        for (int i = 0; i < all.size() && rec.size() < 3; i++) {
            String p = all.get(i);
            if (!p.equals(last) && new File(p).exists()) {
                LibItem it = new LibItem();
                it.path = p;
                it.file = new File(p);
                Library.fill(it);
                rec.add(it);
            }
        }
        if (rec.size() > 0) {
            col.addView(Ui.section(this, T.t("RECENT")));
            ShelfRowView row = new ShelfRowView(this);
            row.bind(rec, 0, 3, new Library.Listener() {
                public void open(LibItem it) { HomeActivity.this.open(it.path); }
                public void menu(LibItem it) { BookInfo.show(HomeActivity.this, it.path); }
            });
            col.addView(row, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
        }
        shelvesSection();
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
        lp.setMargins(14, 12, 14, 8);
        return lp;
    }

    private View welcome() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundDrawable(Ui.box(Color.WHITE, 2, 28));
        card.addView(Ui.text(this, T.t("Welcome!\n\nNo books yet.\nOpen Wi-Fi to upload books from your phone or computer, or Library to browse the card."), 20, false));
        TextView add = Ui.primary(this, T.t("Add books over Wi-Fi"));
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, TransferActivity.class)); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
        lp.topMargin = 18;
        card.addView(add, lp);
        TextView lib = Ui.button(this, T.t("Library"));
        lib.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, BrowserActivity.class)); }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
        lp2.topMargin = 10;
        card.addView(lib, lp2);
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(card, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
        wrap.setPadding(14, 12, 14, 8);
        return wrap;
    }

    /** The book you are reading: cover, title, progress, and a big Continue button. */
    private View hero(final String path) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(18, 18, 18, 18);
        card.setBackgroundDrawable(Ui.box(Color.WHITE, 2, 28));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { open(path); }
        });

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setTag(path);
        card.addView(iv, new LinearLayout.LayoutParams(150, 210));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(18, 0, 0, 0);
        info.addView(Ui.muted(this, T.t("CONTINUE READING"), 13));
        final TextView title = Ui.text(this, Prefs.title(path), 26, true);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, 6, 0, 2);
        info.addView(title);
        final TextView author = Ui.muted(this, Prefs.author(path), 18);
        author.setMaxLines(2);
        info.addView(author);

        int pc = Prefs.pct(path);
        Bar bar = new Bar(this);
        bar.set(Stats.isFinished(path) ? 1f : pc / 100f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.FILL, 12);
        bl.topMargin = 16;
        info.addView(bar, bl);
        TextView st = Ui.muted(this, (Stats.isFinished(path) ? T.t("Finished") : pc + "%") + "   \u00B7   "
                + T.f("{0} read", Stats.fmt(Stats.bookSecs(path))), 16);
        st.setPadding(0, 8, 0, 0);
        info.addView(st);

        String d = Prefs.get("meta2:" + path, null) == null ? null : Prefs.get("desc:" + path, "");
        if (d == null) {                                   // read the annotation once, then redraw
            Library.fetchMeta(path, new Runnable() {
                public void run() { if (!isFinishing()) refresh(); }
            });
        } else if (d.length() > 0) {
            TextView ann = Ui.muted(this, d.replace('\n', ' '), 16);
            ann.setMaxLines(3);
            ann.setEllipsize(TextUtils.TruncateAt.END);
            ann.setPadding(0, 10, 0, 0);
            info.addView(ann);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        TextView go = Ui.primary(this, T.t("Continue"));
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { open(path); }
        });
        buttons.addView(go, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        TextView more = Ui.button(this, T.t("Book info"));
        more.setTextSize(TypedValue.COMPLEX_UNIT_PX, 16);
        more.setPadding(18, 12, 18, 12);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { BookInfo.show(HomeActivity.this, path); }
        });
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        ml.leftMargin = 10;
        buttons.addView(more, ml);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        bp.topMargin = 16;
        info.addView(buttons, bp);
        card.addView(info, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));

        iv.setImageBitmap(CoverCache.peek(path));
        CoverCache.load(this, path, new CoverCache.Callback() {
            public void onCover(String p, Bitmap bm) {
                if (p.equals(iv.getTag())) {
                    iv.setImageBitmap(bm);
                    title.setText(Prefs.title(p));
                    author.setText(Prefs.author(p));
                }
            }
        });
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(14, 12, 14, 8);
        wrap.addView(card, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
        return wrap;
    }

    private void openShelf(String collection, int filter) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra("collection", collection);
        i.putExtra("filter", filter);
        i.putExtra("view", "shelf");
        startActivity(i);
    }

    /** Your shelves as tiles (or a "new shelf" tile), plus a link to all shelves. */
    private void shelvesSection() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.section(this, T.t("SHELVES")), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        TextView all = Ui.text(this, T.t("All shelves") + " \u203A", 17, true);
        all.setPadding(12, 16, 20, 4);
        all.setClickable(true);
        all.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, ShelvesActivity.class)); }
        });
        head.addView(all);
        col.addView(head);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 0, 8, 18);
        ArrayList<String> names = Shelves.names();
        int shown = 0;
        for (int i = 0; i < names.size() && shown < 3; i++, shown++) {
            final String n = names.get(i);
            ArrayList<String> ps = Shelves.existing(n);
            ShelfTile t = new ShelfTile(this);
            t.bind(n, ps.size() + " " + T.books(ps.size()), new ArrayList<String>(ps.subList(0, Math.min(3, ps.size()))), false);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openShelf("shelf:" + n, 0); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 230, 1f);
            lp.setMargins(3, 0, 3, 0);
            row.addView(t, lp);
        }
        if (shown < 3) {
            ShelfTile plus = new ShelfTile(this);
            plus.bind("+ " + T.t("New shelf"), "", new ArrayList<String>(), true);
            plus.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(HomeActivity.this, ShelvesActivity.class)); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 230, 1f);
            lp.setMargins(3, 0, 3, 0);
            row.addView(plus, lp);
            shown++;
        }
        for (; shown < 3; shown++) row.addView(new View(this), new LinearLayout.LayoutParams(0, 230, 1f));
        col.addView(row);
    }
}
