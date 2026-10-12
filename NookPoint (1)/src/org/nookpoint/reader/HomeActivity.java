package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;

public class HomeActivity extends Activity {
    private LinearLayout col;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        Prefs.booksDir();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(Ui.FILL, Ui.WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(Ui.FILL, 0, 1f));

        root.addView(Ui.rule(this, 2));
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(6, 8, 6, 8);
        nav.addView(navButton("Library", BrowserActivity.class), navLp());
        nav.addView(navButton("Stats", StatsActivity.class), navLp());
        nav.addView(navButton("Wi-Fi", TransferActivity.class), navLp());
        nav.addView(navButton("Settings", SettingsActivity.class), navLp());
        root.addView(nav, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
        setContentView(root);
    }

    private LinearLayout.LayoutParams navLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMargins(4, 0, 4, 0);
        return lp;
    }

    private TextView navButton(String s, final Class<?> cls) {
        TextView t = Ui.button(this, s);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, cls)); }
        });
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void open(String path) {
        Intent i = new Intent(this, ReaderActivity.class);
        i.putExtra("path", path);
        startActivity(i);
    }

    private void refresh() {
        col.removeAllViews();

        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(16, 14, 16, 12);
        hd.addView(Ui.text(this, "NookPoint", 32, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        long[] d = Stats.day(Stats.today());
        int goal = Stats.goalMinutes();
        String today = "Today  " + Stats.fmt(d[0]);
        if (goal > 0) today += (d[0] >= goal * 60L ? "  - goal reached" : "  /  " + goal + " min");
        hd.addView(Ui.text(this, today, 18, false));
        col.addView(hd);
        col.addView(Ui.rule(this, 3));

        String last = Prefs.get("last", null);
        if (last != null && !new File(last).exists()) last = null;
        if (last != null) col.addView(card(last));
        else {
            TextView t = Ui.text(this, "Welcome!\n\nNo books yet.\nOpen Wi-Fi to upload books from your "
                    + "phone or computer, or Library to browse the card.", 22, false);
            t.setPadding(20, 40, 20, 40);
            col.addView(t);
        }

        ArrayList<String> rec = new ArrayList<String>();
        ArrayList<String> all = Prefs.recents();
        for (int i = 0; i < all.size() && rec.size() < 3; i++) {
            String p = all.get(i);
            if (!p.equals(last) && new File(p).exists()) rec.add(p);
        }
        if (rec.size() > 0) {
            col.addView(Ui.rule(this, 1));
            col.addView(Ui.section(this, "RECENT"));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 0, 8, 14);
            for (int i = 0; i < 3; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                row.addView(i < rec.size() ? cell(rec.get(i)) : new View(this), lp);
            }
            col.addView(row);
        }
    }

    private View card(final String path) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(16, 16, 16, 16);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { open(path); }
        });

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setTag(path);
        card.addView(iv, new LinearLayout.LayoutParams(168, 236));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(18, 0, 0, 0);
        info.addView(Ui.text(this, "CONTINUE READING", 15, true));
        final TextView title = Ui.text(this, Prefs.title(path), 28, true);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, 8, 0, 4);
        info.addView(title);
        final TextView author = Ui.text(this, Prefs.author(path), 20, false);
        author.setMaxLines(2);
        info.addView(author);

        int pc = Prefs.pct(path);
        Bar bar = new Bar(this);
        bar.set(pc / 100f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.FILL, 16);
        lp.topMargin = 18;
        info.addView(bar, lp);
        TextView st = Ui.text(this, (Stats.isFinished(path) ? "Finished" : pc + "%") + "   \u00B7   "
                + Stats.fmt(Stats.bookSecs(path)) + " read", 18, false);
        st.setPadding(0, 8, 0, 0);
        info.addView(st);
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
        return card;
    }

    private View cell(final String path) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setClickable(true);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { open(path); }
        });
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setTag(path);
        c.addView(iv, new LinearLayout.LayoutParams(120, 168));
        Bar bar = new Bar(this);
        bar.set(Prefs.pct(path) / 100f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(120, 10);
        lp.topMargin = 6;
        c.addView(bar, lp);
        final TextView t = Ui.text(this, Prefs.title(path), 16, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(4, 6, 4, 0);
        c.addView(t, new LinearLayout.LayoutParams(150, Ui.WRAP));
        iv.setImageBitmap(CoverCache.peek(path));
        CoverCache.load(this, path, new CoverCache.Callback() {
            public void onCover(String p, Bitmap bm) {
                if (p.equals(iv.getTag())) {
                    iv.setImageBitmap(bm);
                    t.setText(Prefs.title(p));
                }
            }
        });
        return c;
    }
}
