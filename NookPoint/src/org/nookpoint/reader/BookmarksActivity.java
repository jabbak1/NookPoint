package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class BookmarksActivity extends Activity {
    private String path;
    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        list = Ui.list(this);
        setContentView(Ui.page(this, T.t("Bookmarks"), list));
        refresh();
    }

    private void refresh() {
        final ArrayList<Bookmarks.BM> items = Bookmarks.list(path);
        if (items.size() == 0) {
            ArrayList<String> one = new ArrayList<String>();
            one.add(T.t("No bookmarks yet.\nTap the top-right corner of a page to add one."));
            list.setAdapter(Ui.adapter(this, one));
            list.setOnItemClickListener(null);
            return;
        }
        final SimpleDateFormat df = new SimpleDateFormat("d MMM", T.locale());
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return items.size(); }
            public Object getItem(int i) { return items.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(final int pos, View cv, ViewGroup parent) {
                final Bookmarks.BM bm = items.get(pos);
                LinearLayout row = new LinearLayout(BookmarksActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(16, 14, 10, 14);
                row.setBackgroundColor(Color.WHITE);
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent r = new Intent();
                        r.putExtra("chapter", bm.chapter);
                        r.putExtra("offset", bm.offset);
                        setResult(RESULT_OK, r);
                        finish();
                    }
                });
                LinearLayout col = new LinearLayout(BookmarksActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView sn = Ui.text(BookmarksActivity.this, bm.snippet.length() > 0 ? bm.snippet : "-", 21, false);
                sn.setMaxLines(2);
                sn.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(sn);
                TextView meta = Ui.text(BookmarksActivity.this, bm.pct + "%   \u00B7   " + df.format(new Date(bm.time)), 17, false);
                meta.setPadding(0, 4, 0, 0);
                col.addView(meta);
                row.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                TextView del = Ui.text(BookmarksActivity.this, "X", 22, true);
                del.setGravity(Gravity.CENTER);
                del.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
                del.setClickable(true);
                del.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Bookmarks.remove(path, pos);
                        refresh();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(60, 60);
                lp.leftMargin = 10;
                row.addView(del, lp);
                return row;
            }
        });
        list.setOnItemClickListener(null);
    }
}
