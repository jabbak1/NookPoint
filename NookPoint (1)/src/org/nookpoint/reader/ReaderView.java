package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

class ReaderView extends View {
    interface Listener { void onNext(); void onPrev(); void onMenu(); void onSize(); }

    Listener listener;
    Cfg cfg;
    ArrayList<Line> lines;
    int rows = 1, page, pages = 1, contentW = 100, lineH = 20;
    String message = "Loading...";
    String chapterTitle = "";
    int pct, battery;
    Paint body, head;
    private final TextPaint st = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rule = new Paint();
    private static final int STATUS_H = 34;
    private int flash;
    private final Handler handler = new Handler();

    ReaderView(Context c) {
        super(c);
        st.setTextSize(18);
        st.setColor(Color.BLACK);
        rule.setColor(Color.BLACK);
        setBackgroundColor(Color.WHITE);
    }

    void configure(Cfg c) {
        cfg = c;
        body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setTypeface(c.tf);
        body.setTextSize(c.size);
        body.setColor(Color.BLACK);
        head = new Paint(body);
        head.setTypeface(Typeface.create(c.tf, Typeface.BOLD));
        head.setTextSize(c.size * 1.1f);
        recalc();
    }

    void recalc() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || cfg == null) return;
        contentW = w - 2 * cfg.margin;
        int contentH = h - cfg.margin - (cfg.status ? STATUS_H : cfg.margin);
        lineH = Math.max(1, (int) (Math.max(body.getFontSpacing(), head.getFontSpacing() * 0.95f) * cfg.spacing));
        rows = Math.max(1, contentH / lineH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        recalc();
        if (listener != null) listener.onSize();
    }

    void fullRefresh() {
        flash = 1;
        invalidate();
        handler.postDelayed(new Runnable() {
            public void run() {
                flash = 2;
                invalidate();
                handler.postDelayed(new Runnable() {
                    public void run() { flash = 0; invalidate(); }
                }, 250);
            }
        }, 250);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && listener != null && cfg != null) {
            float x = e.getX();
            int w = getWidth();
            if (x < w * 0.3f) { if (cfg.touch) listener.onPrev(); }
            else if (x > w * 0.7f) { if (cfg.touch) listener.onNext(); }
            else listener.onMenu();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(flash == 1 ? Color.BLACK : Color.WHITE);
        if (flash != 0 || cfg == null) return;

        if (lines == null) {
            float tw = body.measureText(message);
            canvas.drawText(message, (w - tw) / 2, h / 2, body);
            return;
        }

        Paint.FontMetrics fm = body.getFontMetrics();
        float base = (lineH - (fm.descent - fm.ascent)) / 2 - fm.ascent;
        int start = page * rows;
        for (int i = 0; i < rows && start + i < lines.size(); i++) {
            Line ln = lines.get(start + i);
            if (ln.text.length() == 0) continue;
            float y = cfg.margin + i * lineH + base;
            drawLine(canvas, ln, cfg.margin + ln.indent, y);
        }

        if (cfg.status) {
            rule.setColor(0xFF999999);
            canvas.drawRect(cfg.margin, h - STATUS_H, w - cfg.margin, h - STATUS_H + 1, rule);
            rule.setColor(Color.BLACK);
            canvas.drawRect(cfg.margin, h - STATUS_H - 1, cfg.margin + (w - 2 * cfg.margin) * pct / 100f,
                    h - STATUS_H + 3, rule);
            String right = (page + 1) + "/" + pages + "   " + pct + "%   BAT " + battery + "%";
            float rw = st.measureText(right);
            float y = h - 10;
            canvas.drawText(right, w - cfg.margin - rw, y, st);
            String left = TextUtils.ellipsize(chapterTitle, st, Math.max(10, contentW - rw - 20),
                    TextUtils.TruncateAt.END).toString();
            canvas.drawText(left, cfg.margin, y, st);
        }
    }

    private void drawLine(Canvas canvas, Line ln, float x0, float y) {
        Paint p = ln.head ? head : body;
        if (ln.justify) {
            String[] w = ln.text.split(" ");
            if (w.length > 1) {
                float sum = 0;
                for (int i = 0; i < w.length; i++) sum += p.measureText(w[i]);
                float gap = (ln.width - sum) / (w.length - 1);
                if (gap >= 0 && gap <= p.measureText(" ") * 4) {
                    float x = x0;
                    for (int i = 0; i < w.length; i++) {
                        canvas.drawText(w[i], x, y, p);
                        x += p.measureText(w[i]) + gap;
                    }
                    return;
                }
            }
        }
        canvas.drawText(ln.text, x0, y, p);
    }
}
