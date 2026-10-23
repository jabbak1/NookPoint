package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Library row: cover, title, author, progress. */
class BookRow extends LinearLayout {
    private final ImageView img;
    private final TextView title, author, status;
    private final Bar bar;

    BookRow(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setPadding(14, 12, 14, 12);
        setBackgroundColor(Color.WHITE);
        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(img, new LinearLayout.LayoutParams(88, 124));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setPadding(16, 0, 0, 0);
        title = Ui.text(c, "", 24, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        author = Ui.text(c, "", 19, false);
        author.setSingleLine(true);
        author.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout prog = new LinearLayout(c);
        prog.setOrientation(HORIZONTAL);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        bar = new Bar(c);
        prog.addView(bar, new LinearLayout.LayoutParams(0, 12, 1f));
        status = Ui.text(c, "", 18, true);
        status.setPadding(12, 0, 0, 0);
        prog.addView(status, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        col.addView(title);
        col.addView(author);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
        lp.topMargin = 12;
        col.addView(prog, lp);
        addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        setGravity(Gravity.CENTER_VERTICAL);
    }

    private static String seriesLine(LibItem it) {
        String a = Prefs.author(it.path);
        if (it.series.length() == 0) return a;
        String no = it.seriesNo > 0 ? " #" + (it.seriesNo == Math.floor(it.seriesNo) ? String.valueOf((long) it.seriesNo) : String.valueOf(it.seriesNo)) : "";
        return (a.length() > 0 ? a + "  \u00B7  " : "") + it.series + no;
    }

    void bind(final LibItem it, final Library.Listener l) {
        final String path = it.path;
        img.setTag(path);
        title.setText(it.title);
        author.setText(seriesLine(it));
        bar.set(it.finished ? 1f : it.pct / 100f);
        status.setText(it.finished ? T.t("Finished") : (it.pct > 0 ? it.pct + "%" : T.t("New")));
        img.setImageBitmap(CoverCache.peek(path));
        CoverCache.load(getContext(), path, new CoverCache.Callback() {
            public void onCover(String p, Bitmap bm) {
                if (p.equals(img.getTag())) {
                    img.setImageBitmap(bm);
                    title.setText(Prefs.title(p));
                    author.setText(seriesLine(it));
                }
            }
        });
        setClickable(true);
        setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) { l.open(it); }
        });
        setOnLongClickListener(new android.view.View.OnLongClickListener() {
            public boolean onLongClick(android.view.View v) { l.menu(it); return true; }
        });
    }
}
