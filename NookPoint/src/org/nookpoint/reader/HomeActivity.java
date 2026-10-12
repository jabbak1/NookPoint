package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import java.io.File;
import java.util.ArrayList;

public class HomeActivity extends Activity {
    private ListView list;
    private ImageView cover;
    private final ArrayList<String> targets = new ArrayList<String>();
    private String coverFor;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        Prefs.booksDir();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(Ui.title(this, "NookPoint"));

        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.setBackgroundColor(Color.WHITE);
        cover.setPadding(0, 8, 0, 8);
        cover.setVisibility(View.GONE);
        root.addView(cover, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 260));

        list = Ui.list(this);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 0, 1f));
        setContentView(root);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String t = targets.get(pos);
                if (t.startsWith("book:")) {
                    Intent i = new Intent(HomeActivity.this, ReaderActivity.class);
                    i.putExtra("path", t.substring(5));
                    startActivity(i);
                } else if (t.equals("browse")) {
                    startActivity(new Intent(HomeActivity.this, BrowserActivity.class));
                } else if (t.equals("wifi")) {
                    startActivity(new Intent(HomeActivity.this, TransferActivity.class));
                } else {
                    startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        ArrayList<String> rows = new ArrayList<String>();
        targets.clear();
        String last = Prefs.get("last", null);
        if (last != null && new File(last).exists()) {
            rows.add("Continue reading\n" + Prefs.title(last) + "   " + Prefs.pct(last) + "%");
            targets.add("book:" + last);
            loadCover(last);
        } else {
            cover.setVisibility(View.GONE);
            last = null;
        }
        int n = 0;
        ArrayList<String> rec = Prefs.recents();
        for (int i = 0; i < rec.size(); i++) {
            String p = rec.get(i);
            if (p.equals(last) || !new File(p).exists()) continue;
            if (n++ >= 4) break;
            rows.add(Prefs.title(p) + "   (" + Prefs.pct(p) + "%)");
            targets.add("book:" + p);
        }
        rows.add("Browse files");        targets.add("browse");
        rows.add("File transfer (Wi-Fi)"); targets.add("wifi");
        rows.add("Settings");            targets.add("settings");
        list.setAdapter(Ui.adapter(this, rows));
    }

    private void loadCover(final String path) {
        if (!path.toLowerCase().endsWith(".epub")) { cover.setVisibility(View.GONE); return; }
        if (path.equals(coverFor) && cover.getDrawable() != null) { cover.setVisibility(View.VISIBLE); return; }
        coverFor = path;
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... v) {
                Book bk = null;
                try {
                    bk = Book.open(new File(path));
                    return bk.cover(400, 520);
                } catch (Throwable t) {
                    return null;
                } finally {
                    if (bk != null) bk.close();
                }
            }
            @Override
            protected void onPostExecute(Bitmap bm) {
                if (bm == null) { cover.setVisibility(View.GONE); return; }
                cover.setImageBitmap(bm);
                cover.setVisibility(View.VISIBLE);
            }
        }.execute();
    }
}
