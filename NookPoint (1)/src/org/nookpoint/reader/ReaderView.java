package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

class ReaderView extends View {
    interface Listener {
        void onNext(); void onPrev(); void onMenu(); void onSize(); void onLink(String href, boolean note);
    }

    private static class Hit { float l, t, r, b; String href; boolean note; }

    Listener listener;
    Cfg cfg;
    Fonts fonts;
    ArrayList<Line> lines;
    int[] pageStart;
    int page, pages = 1, contentW = 100;
    float contentH = 100;
    String message = "Loading...";
    String chapterTitle = "";
    int pct, battery;
    private final Paint msg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint st = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rule = new Paint();
    private final Paint ink = new Paint();
    private final ArrayList<Hit> hits = new ArrayList<Hit>();
    private static final int STATUS_H = 34;
    private int flash;
    private final Handler handler = new Handler();

    ReaderView(Context c) {
        super(c);
        st.setTextSize(18);
        st.setColor(Color.BLACK);
        msg.setColor(Color.BLACK);
        ink.setColor(Color.BLACK);
        setBackgroundColor(Color.WHITE);
    }

    void configure(Cfg c) {
        cfg = c;
        fonts = new Fonts(c.tf, c.size);
        msg.setTypeface(c.tf);
        msg.setTextSize(c.size);
        recalc();
    }

    void recalc() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || cfg == null) return;
        contentW = w - 2 * cfg.margin;
        contentH = h - cfg.margin - (cfg.status ? STATUS_H : cfg.margin);
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
            float x = e.getX(), y = e.getY();
            for (int i = 0; i < hits.size(); i++) {
                Hit h = hits.get(i);
                if (x >= h.l && x <= h.r && y >= h.t && y <= h.b) {
                    listener.onLink(h.href, h.note);
                    return true;
                }
            }
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
        hits.clear();
        canvas.drawColor(flash == 1 ? Color.BLACK : Color.WHITE);
        if (flash != 0 || cfg == null) return;

        if (lines == null || pageStart == null) {
            float tw = msg.measureText(message);
            canvas.drawText(message, (w - tw) / 2, h / 2, msg);
            return;
        }

        int start = pageStart[page];
        int end = page + 1 < pageStart.length ? pageStart[page + 1] : lines.size();
        float y = cfg.margin;
        for (int i = start; i < end; i++) {
            Line l = lines.get(i);
            if (i > start) y += l.before;
            drawLine(canvas, l, y);
            y += l.h;
        }

        if (cfg.status) {
            rule.setColor(0xFF999999);
            canvas.drawRect(cfg.margin, h - STATUS_H, w - cfg.margin, h - STATUS_H + 1, rule);
            rule.setColor(Color.BLACK);
            canvas.drawRect(cfg.margin, h - STATUS_H - 1, cfg.margin + (w - 2 * cfg.margin) * pct / 100f,
                    h - STATUS_H + 3, rule);
            String right = (page + 1) + "/" + pages + "   " + pct + "%   BAT " + battery + "%";
            float rw = st.measureText(right);
            float ty = h - 10;
            canvas.drawText(right, w - cfg.margin - rw, ty, st);
            String left = TextUtils.ellipsize(chapterTitle, st, Math.max(10, contentW - rw - 20),
                    TextUtils.TruncateAt.END).toString();
            canvas.drawText(left, cfg.margin, ty, st);
        }
    }

    private void drawLine(Canvas canvas, Line l, float y) {
        float by = y + l.base;
        if (l.label != null) canvas.drawText(l.label, cfg.margin + l.labelX, by, fonts.paint(0, 0));
        float x = cfg.margin + l.x;
        for (int i = 0; i < l.segs.length; i++) {
            Seg s = l.segs[i];
            if (s.space) x += l.space + l.extra;
            Paint p = fonts.paint(s.style, l.level);
            float yy = (s.style & Span.SUP) != 0 ? by - fonts.size * 0.33f : by;
            canvas.drawText(s.text, x, yy, p);
            if ((s.style & Span.LINK) != 0) {
                if (cfg.links) canvas.drawRect(x, yy + 3, x + s.w, yy + 4, ink);
                if (s.href != null) {
                    Hit h = new Hit();
                    boolean note = (s.style & Span.NOTE) != 0;
                    float padX = note ? 14 : 6, padY = note ? 14 : 6;
                    h.l = x - padX; h.r = x + s.w + padX; h.t = y - padY; h.b = y + l.h + padY;
                    h.href = s.href; h.note = note;
                    hits.add(h);
                }
            }
            x += s.w;
        }
    }
}
