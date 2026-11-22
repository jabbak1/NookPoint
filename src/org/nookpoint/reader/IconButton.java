package org.nookpoint.reader;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** An icon with an optional small label; inverts (black background) while pressed. */
class IconButton extends LinearLayout {
    private final IconView icon;
    private TextView label;

    IconButton(Context c, int kind, String text) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setPadding(6, text == null ? 10 : 8, 6, text == null ? 10 : 8);
        icon = new IconView(c, kind);
        icon.setDuplicateParentStateEnabled(true);
        addView(icon, new LinearLayout.LayoutParams(40, 40));
        if (text != null) {
            label = Ui.text(c, text, 13, false);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setDuplicateParentStateEnabled(true);
            label.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_pressed}, {}},
                    new int[]{Color.WHITE, Color.BLACK}));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
            lp.topMargin = 4;
            addView(label, lp);
        }
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, Ui.box(Color.BLACK, 2, 18));
        sl.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        setBackgroundDrawable(sl);
    }

    void setKind(int k) { icon.setKind(k); }

    void setLabel(String s) { if (label != null) label.setText(s); }
}
