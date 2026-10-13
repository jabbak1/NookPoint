package org.nookpoint.reader;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;

public class StatsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
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
        tiles.addView(tile(Stats.fmt(today[0]), T.t("TODAY")), tileLp());
        tiles.addView(tile(streak + " " + T.days(streak), T.t("STREAK")), tileLp());
        tiles.addView(tile(String.valueOf(Stats.finishedCount()), T.t("FINISHED")), tileLp());
        col.addView(tiles);

        // ---------------------------------------------------------------- goals
        col.addView(Ui.section(this, T.t("GOALS  (tap a goal to change it)")));
        int gm = Stats.goalMinutes();
        col.addView(goalRow(T.t("Daily reading goal"), gm > 0 ? gm + " " + T.t("min") : T.t("Off"), new View.OnClickListener() {
            public void onClick(View v) { Stats.cycleGoal("goal_min", Stats.GOAL_MIN); build(); }
        }));
        if (gm > 0) {
            col.addView(progress(today[0] / (gm * 60f), T.f("{0} of {1} min today", Stats.fmt(today[0]), gm)));
            int gs = Stats.goalStreak();
            col.addView(note(T.f("Goal streak: {0}   \u00B7   reached {1} of last 7 days", gs + " " + T.days(gs), Stats.goalDaysOfLast7())));
        }
        int gb = Stats.goalBooks();
        int year = Calendar.getInstance().get(Calendar.YEAR);
        col.addView(goalRow(T.f("Books goal {0}", year), gb > 0 ? gb + " " + T.books(gb) : T.t("Off"), new View.OnClickListener() {
            public void onClick(View v) { Stats.cycleGoal("goal_books", Stats.GOAL_BOOKS); build(); }
        }));
        if (gb > 0) {
            int done = Stats.booksThisYear();
            col.addView(progress(done / (float) gb, T.f("{0} of {1} books", done, gb) + "   \u00B7   " + Stats.pace()));
        }

        // ---------------------------------------------------------------- chart
        col.addView(Ui.rule(this, 1));
        col.addView(Ui.section(this, T.t("LAST 7 DAYS")));
        BarChart ch = new BarChart(this);
        for (int i = 0; i < 7; i++) {
            ch.vals[i] = Stats.day(Stats.daysAgo(6 - i))[0];
            ch.labels[i] = Stats.dayLabel(6 - i);
        }
        ch.goalSecs = gm * 60L;
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.FILL, 230);
        cl.setMargins(8, 0, 8, 0);
        col.addView(ch, cl);

        // ---------------------------------------------------------------- totals
        long[] t = Stats.totals();
        col.addView(Ui.rule(this, 1));
        col.addView(Ui.section(this, T.t("TOTALS")));
        col.addView(Ui.kv(this, T.t("Reading time"), Stats.fmt(t[0])));
        col.addView(Ui.kv(this, T.t("Pages turned"), String.valueOf(t[1])));
        col.addView(Ui.kv(this, T.t("Active days"), String.valueOf(t[2])));
        if (t[2] > 0) col.addView(Ui.kv(this, T.t("Average per active day"), Stats.fmt(t[0] / t[2])));
        if (t[0] >= 60) col.addView(Ui.kv(this, T.t("Reading speed"), T.f("{0} pages/h", t[1] * 3600 / t[0])));

        ArrayList<Stats.BookStat> top = Stats.top(5);
        if (top.size() > 0) {
            col.addView(Ui.rule(this, 1));
            col.addView(Ui.section(this, T.t("MOST READ")));
            for (int i = 0; i < top.size(); i++) {
                col.addView(Ui.kv(this, Prefs.title(top.get(i).path), Stats.fmt(top.get(i).secs)));
            }
        }
        if (t[0] == 0) col.addView(note(T.t("Nothing yet. Open a book and statistics will appear here.")));
        setContentView(Ui.page(this, T.t("Statistics"), sv));
    }

    private View goalRow(String label, String value, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(16, 10, 16, 6);
        r.setClickable(true);
        r.setOnClickListener(l);
        r.addView(Ui.text(this, label, 22, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        TextView v = Ui.text(this, value, 21, true);
        v.setPadding(16, 8, 16, 8);
        v.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
        r.addView(v, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        return r;
    }

    private View progress(float frac, String text) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(16, 4, 16, 8);
        Bar bar = new Bar(this);
        bar.set(frac);
        r.addView(bar, new LinearLayout.LayoutParams(Ui.FILL, 18));
        TextView t = Ui.text(this, text, 18, false);
        t.setPadding(0, 6, 0, 0);
        r.addView(t);
        return r;
    }

    private TextView note(String s) {
        TextView e = Ui.text(this, s, 18, false);
        e.setPadding(16, 2, 16, 12);
        return e;
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
