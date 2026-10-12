package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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

/** E-ink friendly widgets: white, black, thin borders, no gradients. */
class Ui {
    static final int FILL = ViewGroup.LayoutParams.FILL_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static ListView list(Context c) {
        ListView l = new ListView(c);
        l.setBackgroundColor(Color.WHITE);
        l.setCacheColorHint(Color.WHITE);
        l.setDivider(new ColorDrawable(0xFF777777));
        l.setDividerHeight(1);
        l.setSelector(new ColorDrawable(0x33000000));
        l.setVerticalFadingEdgeEnabled(false);
        return l;
    }

    static TextView text(Context c, String s, int px, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    static TextView title(Context c, String t) {
        TextView v = text(c, t, 32, true);
        v.setBackgroundColor(Color.WHITE);
        v.setPadding(16, 14, 16, 12);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    static GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(stroke, Color.BLACK);
        return g;
    }

    static View rule(Context c, int h) {
        View v = new View(c);
        v.setBackgroundColor(Color.BLACK);
        v.setLayoutParams(new LinearLayout.LayoutParams(FILL, h));
        return v;
    }

    static TextView button(Context c, String s) {
        TextView t = text(c, s, 20, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(4, 22, 4, 22);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, box(0xFFBBBBBB, 2));
        sl.addState(new int[]{}, box(Color.WHITE, 2));
        t.setBackgroundDrawable(sl);
        t.setClickable(true);
        return t;
    }

    static TextView section(Context c, String s) {
        TextView t = text(c, s, 16, true);
        t.setPadding(16, 18, 16, 6);
        return t;
    }

    static LinearLayout kv(Context c, String k, String v) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(16, 10, 16, 10);
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
                t.setTextColor(Color.BLACK);
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
                t.setPadding(16, 20, 16, 20);
                return t;
            }
        };
    }

    static LinearLayout page(Context c, String title, View body) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(title(c, title));
        root.addView(rule(c, 3));
        root.addView(body, new LinearLayout.LayoutParams(FILL, 0, 1f));
        return root;
    }
}
