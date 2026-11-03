package org.nookpoint.reader;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** All installed apps (so NookPoint can work as the home screen), plus shortcuts to Wi-Fi and system settings. */
public class AppsActivity extends Activity {
    private static class App {
        String label = "", pkg = "", cls = "";
        Drawable icon;
        int kind;                       // 0 app, 1 Wi-Fi settings, 2 system settings
        String id() { return kind == 0 ? pkg + "/" + cls : "shortcut" + kind; }
    }

    private ListView list;
    private TextView toggle;
    private final ArrayList<App> all = new ArrayList<App>();
    private boolean showHidden;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(Ui.title(this, T.t("Apps")));
        root.addView(Ui.rule(this, 3));
        toggle = Ui.button(this, "");
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17);
        toggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showHidden = !showHidden; show(); }
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP);
        tp.setMargins(8, 8, 8, 6);
        root.addView(toggle, tp);
        root.addView(Ui.rule(this, 1));
        list = Ui.list(this);
        list.setDivider(null);
        root.addView(list, new LinearLayout.LayoutParams(Ui.FILL, 0, 1f));
        setContentView(root);
        load();
    }

    private void load() {
        new AsyncTask<Void, Void, ArrayList<App>>() {
            @Override
            protected ArrayList<App> doInBackground(Void... v) {
                ArrayList<App> out = new ArrayList<App>();
                PackageManager pm = getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
                for (int i = 0; i < ris.size(); i++) {
                    ResolveInfo ri = ris.get(i);
                    if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
                    App a = new App();
                    try { a.label = String.valueOf(ri.loadLabel(pm)); } catch (Throwable t) { a.label = ri.activityInfo.packageName; }
                    a.pkg = ri.activityInfo.packageName;
                    a.cls = ri.activityInfo.name;
                    try { a.icon = ri.loadIcon(pm); } catch (Throwable t) { a.icon = null; }
                    out.add(a);
                }
                Collections.sort(out, new Comparator<App>() {
                    public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
                });
                return out;
            }
            @Override
            protected void onPostExecute(ArrayList<App> apps) {
                all.clear();
                App w = new App();
                w.kind = 1;
                w.label = T.t("Wi-Fi settings");
                App s = new App();
                s.kind = 2;
                s.label = T.t("System settings");
                all.add(w);
                all.add(s);
                all.addAll(apps);
                show();
            }
        }.execute();
    }

    private HashSet<String> hidden() {
        HashSet<String> h = new HashSet<String>();
        String s = Prefs.get("hiddenapps", "");
        if (s.length() > 0) {
            String[] p = s.split("\n");
            for (int i = 0; i < p.length; i++) h.add(p[i]);
        }
        return h;
    }

    private void saveHidden(HashSet<String> h) {
        StringBuilder sb = new StringBuilder();
        for (String x : h) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(x);
        }
        Prefs.put("hiddenapps", sb.toString());
    }

    private void show() {
        toggle.setText(T.t("Show hidden") + ": " + T.t(showHidden ? "On" : "Off"));
        final HashSet<String> hid = hidden();
        final ArrayList<App> shown = new ArrayList<App>();
        for (int i = 0; i < all.size(); i++) if (showHidden || !hid.contains(all.get(i).id())) shown.add(all.get(i));
        int first = list.getFirstVisiblePosition();
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return (shown.size() + 2) / 3; }
            public Object getItem(int i) { return null; }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                LinearLayout row = new LinearLayout(AppsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(8, 10, 8, 4);
                for (int k = 0; k < 3; k++) {
                    int idx = pos * 3 + k;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                    row.addView(idx < shown.size() ? cell(shown.get(idx), hid.contains(shown.get(idx).id())) : new View(AppsActivity.this), lp);
                }
                return row;
            }
        });
        list.setSelection(first);
    }

    private View cell(final App a, boolean isHidden) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(4, 8, 4, 8);
        c.setClickable(true);
        FrameLayout box = new FrameLayout(this);
        if (a.kind == 0 && a.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(iv, new FrameLayout.LayoutParams(76, 76));
        } else {
            TextView g = Ui.text(this, a.kind == 1 ? "Wi-Fi" : "\u2699", a.kind == 1 ? 20 : 44, true);
            g.setGravity(Gravity.CENTER);
            g.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
            box.addView(g, new FrameLayout.LayoutParams(76, 76));
        }
        c.addView(box, new LinearLayout.LayoutParams(76, 76));
        TextView l = Ui.text(this, a.label + (isHidden ? " *" : ""), 16, false);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setMaxLines(2);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setPadding(2, 6, 2, 0);
        c.addView(l, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { launch(a); }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { hideMenu(a); return true; }
        });
        return c;
    }

    private void hideMenu(final App a) {
        final HashSet<String> h = hidden();
        final boolean isHidden = h.contains(a.id());
        ArrayList<String> items = new ArrayList<String>();
        items.add(T.t("Open"));
        items.add(T.t(isHidden ? "Show in Apps" : "Hide from Apps"));
        Dlg.menu(this, a.label, items, new Dlg.Pick() {
            public void onPick(int i) {
                if (i == 0) launch(a);
                else {
                    if (isHidden) h.remove(a.id()); else h.add(a.id());
                    saveHidden(h);
                    show();
                }
            }
        });
    }

    private void launch(App a) {
        try {
            Intent i;
            if (a.kind == 1) {
                i = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
            } else if (a.kind == 2) {
                i = new Intent(android.provider.Settings.ACTION_SETTINGS);
            } else {
                i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setComponent(new ComponentName(a.pkg, a.cls));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }
            startActivity(i);
        } catch (Throwable t) {
            Diag.e("launch " + a.pkg + "/" + a.cls, t);
            Toast.makeText(this, T.t("Cannot open this app"), Toast.LENGTH_SHORT).show();
        }
    }
}
