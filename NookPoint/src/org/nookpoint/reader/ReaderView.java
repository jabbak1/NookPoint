package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

class ReaderView extends View {
    interface Listener {
        void onNext(); void onPrev(); void onMenu(); void onSize();
        void onLink(String href, boolean note);
        void onWord(String word);
        void onBookmark();
    }

    private static class Hit { float l, t, r, b; String href; boolean note; }

    Listener listener;
    Cfg cfg;
    Fonts fonts;
    ArrayList<Line> lines;
    int[] pageStart;
    int page, pages = 1, contentW = 100;
    float contentH = 100;
    String message = "";
    String chapterTitle = "";
    int pct, battery;
    boolean bookmarked;
    private final Paint msg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint st = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rule = new Paint();
    private final Paint ink = new Paint();
    private final ArrayList<Hit> hits = new ArrayList<Hit>();
    private static final int STATUS_H = 34;
    private int flash;
    private float downX, downY;
    private boolean longDone;
    private final Handler handler = new Handler();
    private final Runnable longPress = new Runnable() {
        public void run() {
            longDone = true;
            String w = wordAt(downX, downY);
            if (w != null && listener != null) listener.onWord(w);
        }
    };

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
        fonts = new Fonts(c.fs, c.size);
        msg.setTypeface(c.fs.get(0));
        msg.setTextSize(c.size);
        st.setTypeface(Cfg.uiFace(false));
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

    // ------------------------------------------------------------------ touch
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null || cfg == null) return true;
        int a = e.getAction();
        float x = e.getX(), y = e.getY();
        if (a == MotionEvent.ACTION_DOWN) {
            downX = x; downY = y; longDone = false;
            handler.postDelayed(longPress, 650);
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (Math.abs(x - downX) > 20 || Math.abs(y - downY) > 20) handler.removeCallbacks(longPress);
        } else if (a == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(longPress);
        } else if (a == MotionEvent.ACTION_UP) {
            handler.removeCallbacks(longPress);
            if (longDone) return true;
            int w = getWidth();
            if (x > w - 90 && y < 90) { listener.onBookmark(); return true; }
            for (int i = 0; i < hits.size(); i++) {
                Hit h = hits.get(i);
                if (x >= h.l && x <= h.r && y >= h.t && y <= h.b) {
                    listener.onLink(h.href, h.note);
                    return true;
                }
            }
            if (x < w * 0.3f) { if (cfg.touch) listener.onPrev(); }
            else if (x > w * 0.7f) { if (cfg.touch) listener.onNext(); }
            else listener.onMenu();
        }
        return true;
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '-' || c == '\u2019';
    }

    /** The word under the finger (used for dictionary look-up), or null. */
    private String wordAt(float x, float y) {
        if (lines == null || pageStart == null) return null;
        int start = pageStart[page];
        int end = page + 1 < pageStart.length ? pageStart[page + 1] : lines.size();
        float ly = cfg.margin;
        for (int i = start; i < end; i++) {
            Line l = lines.get(i);
            if (i > start) ly += l.before;
            if (y >= ly - 4 && y <= ly + l.h + 4) {
                float lx = cfg.margin + l.x;
                for (int k = 0; k < l.segs.length; k++) {
                    Seg s = l.segs[k];
                    if (s.space) lx += l.space + l.extra;
                    if (x >= lx - 6 && x <= lx + s.w + 6) return wordIn(s.text, x - lx, fonts.paint(s.style, l.level));
                    lx += s.w;
                }
                return null;
            }
            ly += l.h;
        }
        return null;
    }

    private static String wordIn(String text, float relx, Paint p) {
        int n = text.length();
        if (n == 0) return null;
        int idx = n - 1;
        for (int k = 1; k <= n; k++) {
            if (p.measureText(text, 0, k) >= relx) { idx = k - 1; break; }
        }
        if (!wordChar(text.charAt(idx)) && idx > 0 && wordChar(text.charAt(idx - 1))) idx--;
        if (!wordChar(text.charAt(idx))) return null;
        int a = idx, b = idx + 1;
        while (a > 0 && wordChar(text.charAt(a - 1))) a--;
        while (b < n && wordChar(text.charAt(b))) b++;
        return text.substring(a, b);
    }

    // ------------------------------------------------------------------ drawing
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        hits.clear();
        canvas.drawColor(flash == 1 ? Color.BLACK : Color.WHITE);
        if (flash != 0 || cfg == null) return;

        if (lines == null || pageStart == null) {
            if (message.length() > 0) {
                float tw = msg.measureText(message);
                canvas.drawText(message, (w - tw) / 2, h / 2, msg);
            }
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

        if (bookmarked) {
            Path ribbon = new Path();
            ribbon.moveTo(w - 44, 0);
            ribbon.lineTo(w, 0);
            ribbon.lineTo(w, 44);
            ribbon.close();
            canvas.drawPath(ribbon, ink);
        }

        if (cfg.status) {
            rule.setColor(0xFF999999);
            canvas.drawRect(cfg.margin, h - STATUS_H, w - cfg.margin, h - STATUS_H + 1, rule);
            rule.setColor(Color.BLACK);
            canvas.drawRect(cfg.margin, h - STATUS_H - 1, cfg.margin + (w - 2 * cfg.margin) * pct / 100f,
                    h - STATUS_H + 3, rule);
            float rx = w - cfg.margin;
            float ty = h - 10;
            if (cfg.battery == 1) {
                drawBattery(canvas, rx - 32, h - 25);
                rx -= 44;
            } else if (cfg.battery == 2) {
                String b = battery + "%";
                float bw = st.measureText(b);
                canvas.drawText(b, rx - bw, ty, st);
                rx -= bw + 16;
            }
            String right = (page + 1) + "/" + pages + "   " + pct + "%";
            float rw = st.measureText(right);
            canvas.drawText(right, rx - rw, ty, st);
            if (chapterTitle.length() > 0) {
                String left = TextUtils.ellipsize(chapterTitle, st, Math.max(10, rx - rw - cfg.margin - 20),
                        TextUtils.TruncateAt.END).toString();
                canvas.drawText(left, cfg.margin, ty, st);
            }
        }
    }

    private void drawBattery(Canvas c, float x, float y) {
        rule.setColor(Color.BLACK);
        c.drawRect(x, y, x + 28, y + 2, rule);
        c.drawRect(x, y + 12, x + 28, y + 14, rule);
        c.drawRect(x, y, x + 2, y + 14, rule);
        c.drawRect(x + 26, y, x + 28, y + 14, rule);
        c.drawRect(x + 28, y + 4, x + 31, y + 10, rule);
        float fill = 22f * Math.max(0, Math.min(100, battery)) / 100f;
        c.drawRect(x + 3, y + 3, x + 3 + fill, y + 11, rule);
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
