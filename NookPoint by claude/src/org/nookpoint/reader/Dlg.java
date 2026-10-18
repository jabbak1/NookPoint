package org.nookpoint.reader;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Dialogs drawn with the bundled UI font and E-ink friendly borders (no system dialog chrome). */
class Dlg {
    interface Pick { void onPick(int index); }
    interface Text { void onText(String s); }

    private static Dialog make(Context c, String title, View body, String[] labels, final Runnable[] actions) {
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundDrawable(Ui.box(Color.WHITE, 3));
        if (title != null) {
            TextView t = Ui.text(c, title, 26, true);
            t.setPadding(18, 14, 18, 12);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(t);
            box.addView(Ui.rule(c, 2));
        }
        box.addView(body);
        if (labels != null && labels.length > 0) {
            box.addView(Ui.rule(c, 2));
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 8, 8, 8);
            for (int i = 0; i < labels.length; i++) {
                TextView b = Ui.button(c, labels[i]);
                if (labels.length >= 3) b.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17);
                final int k = i;
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        d.dismiss();
                        if (actions != null && k < actions.length && actions[k] != null) actions[k].run();
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
                lp.setMargins(4, 0, 4, 0);
                row.addView(b, lp);
            }
            box.addView(row);
        }
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(22, 22, 22, 22);
        wrap.addView(box);
        d.setContentView(wrap);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setLayout(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        d.setCancelable(true);
        return d;
    }

    /** List of choices. */
    static Dialog menu(Context c, String title, List<String> items, final Pick cb) {
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        for (int i = 0; i < items.size(); i++) {
            TextView t = Ui.text(c, items.get(i), 23, false);
            t.setPadding(20, 14, 20, 14);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setClickable(true);
            final int k = i;
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    holder[0].dismiss();
                    cb.onPick(k);
                }
            });
            list.addView(t);
            if (i < items.size() - 1) list.addView(Ui.rule(c, 1));
        }
        ScrollView sv = new ScrollView(c);
        sv.addView(list);
        sv.setLayoutParams(new LinearLayout.LayoutParams(Ui.FILL, Math.min(items.size() * 56 + 4, 640)));
        holder[0] = make(c, title, sv, null, null);
        holder[0].show();
        return holder[0];
    }

    /** Scrollable text with up to two buttons. Pass null for an unused button. */
    static Dialog message(Context c, String title, CharSequence text, String b1, Runnable a1, String b2, Runnable a2) {
        TextView tv = Ui.text(c, "", 22, false);
        tv.setText(text);
        tv.setPadding(20, 16, 20, 16);
        ScrollView sv = new ScrollView(c);
        sv.addView(tv);
        sv.setLayoutParams(new LinearLayout.LayoutParams(Ui.FILL, text.length() > 260 ? 460 : Ui.WRAP));
        String[] labels = b2 == null ? new String[]{b1} : new String[]{b1, b2};
        Runnable[] acts = b2 == null ? new Runnable[]{a1} : new Runnable[]{a1, a2};
        Dialog d = make(c, title, sv, labels, acts);
        d.show();
        return d;
    }

    /** Scrollable text with any number of buttons. */
    static Dialog message(Context c, String title, CharSequence text, String[] labels, Runnable[] actions) {
        TextView tv = Ui.text(c, "", 22, false);
        tv.setText(text);
        tv.setPadding(20, 16, 20, 16);
        ScrollView sv = new ScrollView(c);
        sv.addView(tv);
        sv.setLayoutParams(new LinearLayout.LayoutParams(Ui.FILL, text.length() > 260 ? 460 : Ui.WRAP));
        Dialog d = make(c, title, sv, labels, actions);
        d.show();
        return d;
    }

    interface Form { void onForm(String[] values); }

    /** Several labelled text fields (e.g. catalog name, address, user, password). */
    static Dialog form(Context c, String title, String[] labels, String[] initial, boolean[] secret,
                       String ok, String cancel, final Form cb) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(18, 12, 18, 12);
        final EditText[] ets = new EditText[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView l = Ui.text(c, labels[i], 17, true);
            l.setPadding(0, 8, 0, 3);
            body.addView(l);
            EditText et = new EditText(c);
            et.setSingleLine(true);
            et.setText(initial != null && i < initial.length ? initial[i] : "");
            et.setInputType(secret != null && i < secret.length && secret[i]
                    ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
            et.setTextColor(Color.BLACK);
            et.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21);
            et.setTypeface(Cfg.uiFace(false));
            et.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
            et.setPadding(12, 8, 12, 8);
            ets[i] = et;
            body.addView(et, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
        }
        ScrollView sv = new ScrollView(c);
        sv.addView(body);
        sv.setLayoutParams(new LinearLayout.LayoutParams(Ui.FILL, Math.min(labels.length * 96 + 20, 520)));
        Runnable go = new Runnable() {
            public void run() {
                String[] v = new String[ets.length];
                for (int i = 0; i < v.length; i++) v[i] = ets[i].getText().toString().trim();
                cb.onForm(v);
            }
        };
        Dialog d = make(c, title, sv, new String[]{ok, cancel}, new Runnable[]{go, null});
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        return d;
    }

    /** Progress window with a cancel button; create and update it on the UI thread. */
    static class ProgressDlg {
        private final Dialog d;
        private final TextView text, pct;
        private final Bar bar;
        ProgressDlg(Dialog d, TextView text, TextView pct, Bar bar) { this.d = d; this.text = text; this.pct = pct; this.bar = bar; }
        void set(String t, int p) {
            text.setText(t);
            pct.setText(p + "%");
            bar.set(p / 100f);
        }
        void dismiss() { try { d.dismiss(); } catch (Exception e) { } }
    }

    static ProgressDlg progress(Context c, String title, String cancel, final Runnable onCancel) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(20, 18, 20, 18);
        TextView text = Ui.text(c, "", 21, false);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(text);
        Bar bar = new Bar(c);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.FILL, 22);
        bl.topMargin = 14;
        body.addView(bar, bl);
        TextView pct = Ui.text(c, "0%", 19, true);
        pct.setPadding(0, 8, 0, 0);
        body.addView(pct);
        Dialog d = make(c, title, body, new String[]{cancel}, new Runnable[]{onCancel});
        d.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            public void onCancel(android.content.DialogInterface x) { if (onCancel != null) onCancel.run(); }
        });
        d.show();
        return new ProgressDlg(d, text, pct, bar);
    }

    /** One-line text input. */
    static Dialog input(Context c, String title, String ok, String cancel, final Text cb) {
        return input(c, title, "", ok, cancel, cb);
    }

    static Dialog input(Context c, String title, String initial, String ok, String cancel, final Text cb) {
        final EditText et = new EditText(c);
        et.setText(initial);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setTextColor(Color.BLACK);
        et.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
        et.setTypeface(Cfg.uiFace(false));
        et.setBackgroundDrawable(Ui.box(Color.WHITE, 2));
        et.setPadding(14, 12, 14, 12);
        LinearLayout holder = new LinearLayout(c);
        holder.setPadding(18, 18, 18, 18);
        holder.addView(et, new LinearLayout.LayoutParams(Ui.FILL, Ui.WRAP));
        Runnable go = new Runnable() {
            public void run() { cb.onText(et.getText().toString().trim()); }
        };
        Dialog d = make(c, title, holder, new String[]{ok, cancel}, new Runnable[]{go, null});
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        et.requestFocus();
        return d;
    }
}
