package org.nookpoint.reader;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

/** The look of the app: flat, high contrast, rounded cards and pill buttons, made for E Ink. */
class Ui {
    static final int FILL = ViewGroup.LayoutParams.FILL_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int INK = 0xFF000000, MUTED = 0xFF555555, LINE = 0xFFA8A8A8;

    static ListView list(Context c) {
        ListView l = new ListView(c);
        l.setBackgroundColor(Color.WHITE);
        l.setCacheColorHint(Color.WHITE);
        l.setDivider(new ColorDrawable(0xFFC4C4C4));
        l.setDividerHeight(1);
        l.setSelector(new ColorDrawable(0x22000000));
        l.setVerticalFadingEdgeEnabled(false);
        return l;
    }

    static TextView text(Context c, String s, int px, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(INK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTypeface(Cfg.uiFace(bold));
        return t;
    }

    static TextView muted(Context c, String s, int px) {
        TextView t = text(c, s, px, false);
        t.setTextColor(MUTED);
        return t;
    }

    static TextView title(Context c, String t) {
        TextView v = text(c, t, 30, true);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(20, 16, 20, 12);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    /** Page header: a back chevron and the title. */
    static LinearLayout header(final Context c, String text) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(Color.WHITE);
        h.setPadding(6, 8, 16, 8);
        if (c instanceof Activity) {
            IconButton back = new IconButton(c, UiArt.BACK, null);
            back.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { ((Activity) c).onBackPressed(); }
            });
            h.addView(back, new LinearLayout.LayoutParams(64, 64));
        } else {
            h.addView(new View(c), new LinearLayout.LayoutParams(14, 1));
        }
        TextView t = text(c, text, 30, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        h.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return h;
    }

    static TextView headerTitle(LinearLayout header) { return (TextView) header.getChildAt(1); }

    static GradientDrawable box(int fill, int stroke) { return box(fill, stroke, 16); }

    static GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(stroke, INK);
        g.setCornerRadius(radius);
        return g;
    }

    /** A thin separator line (the height argument is kept for old call sites; lines are always hairlines). */
    static View rule(Context c, int h) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(FILL, 1));
        return v;
    }

    private static ColorStateList pressedWhite(int normal) {
        return new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}}, new int[]{Color.WHITE, normal});
    }

    /** Pill button: outlined, turns black when pressed. */
    static TextView button(Context c, String s) {
        TextView t = text(c, s, 20, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(20, 16, 20, 16);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, box(INK, 2, 60));
        sl.addState(new int[]{}, box(Color.WHITE, 2, 60));
        t.setBackgroundDrawable(sl);
        t.setTextColor(pressedWhite(INK));
        t.setClickable(true);
        return t;
    }

    /** Main action: black pill with white text. */
    static TextView primary(Context c, String s) {
        TextView t = text(c, s, 20, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(24, 16, 24, 16);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, box(0xFF666666, 2, 60));
        sl.addState(new int[]{}, box(INK, 2, 60));
        t.setBackgroundDrawable(sl);
        t.setTextColor(Color.WHITE);
        t.setClickable(true);
        return t;
    }

    /** Small rounded label, e.g. the reading goal. */
    static TextView chip(Context c, String s) {
        TextView t = text(c, s, 16, true);
        t.setPadding(16, 6, 16, 6);
        t.setBackgroundDrawable(box(Color.WHITE, 2, 40));
        return t;
    }

    static TextView section(Context c, String s) {
        TextView t = text(c, s, 14, true);
        t.setTextColor(MUTED);
        t.setPadding(20, 22, 20, 8);
        return t;
    }

    static LinearLayout kv(Context c, String k, String v) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(20, 12, 20, 12);
        TextView a = text(c, k, 22, false);
        a.setSingleLine(true);
        a.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(a, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView b = text(c, v, 22, true);
        b.setPadding(12, 0, 0, 0);
        r.addView(b, new LinearLayout.LayoutParams(WRAP, WRAP));
        return r;
    }

    static ArrayAdapter<String> adapter(Context c, List<String> items) {
        return new ArrayAdapter<String>(c, android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                t.setTextColor(INK);
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 23);
                t.setTypeface(Cfg.uiFace(false));
                t.setPadding(20, 22, 20, 22);
                return t;
            }
        };
    }

    static LinearLayout page(Context c, String title, View body) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(header(c, title));
        root.addView(rule(c, 1));
        root.addView(body, new LinearLayout.LayoutParams(FILL, 0, 1f));
        return root;
    }
}
