package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Grid cell: cover, progress bar, title. */
class BookCell extends LinearLayout {
    private final ImageView img;
    private final TextView title;
    private final Bar bar;

    BookCell(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(4, 10, 4, 6);
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(img, new LinearLayout.LayoutParams(132, 186));
        bar = new Bar(c);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(124, 9);
        bl.topMargin = 6;
        addView(bar, bl);
        title = Ui.text(c, "", 15, false);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(2, 6, 2, 0);
        addView(title, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
    }

    void bind(final LibItem it, final Library.Listener l) {
        img.setTag(it.path);
        title.setText(it.title);
        bar.set(it.finished ? 1f : it.pct / 100f);
        img.setImageBitmap(CoverCache.peek(it.path));
        CoverCache.load(getContext(), it.path, new CoverCache.Callback() {
            public void onCover(String p, Bitmap bm) {
                if (p.equals(img.getTag())) {
                    img.setImageBitmap(bm);
                    title.setText(Prefs.title(p));
                }
            }
        });
        setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { l.open(it); }
        });
        setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { l.menu(it); return true; }
        });
        setVisibility(VISIBLE);
    }
}

/** Three cells per row. */
class GridRow extends LinearLayout {
    private final BookCell[] cells = new BookCell[3];

    GridRow(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setPadding(6, 0, 6, 0);
        for (int i = 0; i < 3; i++) {
            cells[i] = new BookCell(c);
            addView(cells[i], new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        }
    }

    void bind(ArrayList<LibItem> items, int start, Library.Listener l) {
        for (int i = 0; i < 3; i++) {
            if (start + i < items.size()) cells[i].bind(items.get(start + i), l);
            else cells[i].setVisibility(INVISIBLE);
        }
    }
}
