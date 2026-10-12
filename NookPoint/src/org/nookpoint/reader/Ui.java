package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

/** E-ink friendly widgets: white, black, no gradients. */
class Ui {
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

    static TextView title(Context c, String t) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(Color.BLACK);
        v.setBackgroundColor(Color.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30);
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(16, 14, 16, 14);
        v.setSingleLine(true);
        return v;
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
        View rule = new View(c);
        rule.setBackgroundColor(Color.BLACK);
        root.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 2));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 0, 1f));
        return root;
    }
}
