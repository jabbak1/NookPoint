package org.nookpoint.reader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import java.util.ArrayList;

public class TocActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Book bk = ReaderActivity.current;
        if (bk == null) { finish(); return; }
        int cur = getIntent().getIntExtra("current", 0);
        final ArrayList<Integer> targets = new ArrayList<Integer>();
        ArrayList<String> rows = new ArrayList<String>();
        int sel = 0;
        if (bk.toc.size() > 0) {
            for (int i = 0; i < bk.toc.size(); i++) {
                TocItem t = bk.toc.get(i);
                rows.add(t.title);
                targets.add(Integer.valueOf(t.chapter));
                if (t.chapter <= cur) sel = i;
            }
        } else {
            for (int i = 0; i < bk.count(); i++) {
                rows.add(T.f("Section {0}", i + 1));
                targets.add(Integer.valueOf(i));
                if (i <= cur) sel = i;
            }
        }
        rows.set(sel, "> " + rows.get(sel));
        ListView list = Ui.list(this);
        list.setAdapter(Ui.adapter(this, rows));
        list.setSelection(sel);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Intent r = new Intent();
                r.putExtra("chapter", targets.get(pos).intValue());
                setResult(RESULT_OK, r);
                finish();
            }
        });
        setContentView(Ui.page(this, T.t("Contents"), list));
    }
}
