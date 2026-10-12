package org.nookpoint.reader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

public class StatsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(Ui.FILL, Ui.WRAP));

        long[] today = Stats.day(Stats.today());
        int streak = Stats.streak();
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setPadding(10, 14, 10, 4);
        tiles.addView(tile(Stats.fmt(today[0]), "TODAY"), tileLp());
        tiles.addView(tile(streak + (streak == 1 ? " day" : " days"), "STREAK"), tileLp());
        tiles.addView(tile(String.valueOf(Stats.finishedCount()), "FINISHED"), tileLp());
        col.addView(tiles);

        col.addView(Ui.section(this, "LAST 7 DAYS"));
        BarChart ch = new BarChart(this);
        for (int i = 0; i < 7; i++) {
            ch.vals[i] = Stats.day(Stats.daysAgo(6 - i))[0];
            ch.labels[i] = Stats.dayLabel(6 - i);
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.FILL, 230);
        cl.setMargins(8, 0, 8, 0);
        col.addView(ch, cl);

        long[] t = Stats.totals();
        col.addView(Ui.rule(this, 1));
        col.addView(Ui.section(this, "TOTALS"));
        col.addView(Ui.kv(this, "Reading time", Stats.fmt(t[0])));
        col.addView(Ui.kv(this, "Pages turned", String.valueOf(t[1])));
        col.addView(Ui.kv(this, "Active days", String.valueOf(t[2])));
        if (t[2] > 0) col.addView(Ui.kv(this, "Average per active day", Stats.fmt(t[0] / t[2])));
        if (t[0] >= 60) col.addView(Ui.kv(this, "Reading speed", (t[1] * 3600 / t[0]) + " pages/h"));

        ArrayList<Stats.BookStat> top = Stats.top(5);
        if (top.size() > 0) {
            col.addView(Ui.rule(this, 1));
            col.addView(Ui.section(this, "MOST READ"));
            for (int i = 0; i < top.size(); i++) {
                col.addView(Ui.kv(this, Prefs.title(top.get(i).path), Stats.fmt(top.get(i).secs)));
            }
        }
        if (t[0] == 0) {
            TextView e = Ui.text(this, "Nothing yet. Open a book and statistics will appear here.", 20, false);
            e.setPadding(16, 24, 16, 24);
            col.addView(e);
        }
        setContentView(Ui.page(this, "Statistics", sv));
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        lp.setMargins(4, 0, 4, 0);
        return lp;
    }

    private LinearLayout tile(String value, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(6, 18, 6, 14);
        t.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
        TextView v = Ui.text(this, value, 30, true);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        t.addView(v);
        TextView l = Ui.text(this, label, 15, false);
        l.setGravity(Gravity.CENTER);
        t.addView(l);
        return t;
    }
}
