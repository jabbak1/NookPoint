package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Bitmap;
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
        void onWord(String word, int offset);
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
    int[][] highlights = new int[0][];
    String[] searchTokens;
    String leftText = "";
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
            WordHit w = wordAt(downX, downY);
            if (w != null && listener != null) listener.onWord(w.word, w.off);
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
        fonts = new Fonts(c.fs, c.size, c.weight == 2);
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

    private static class WordHit { String word; int off; }

    /** The word under the finger and its position in the chapter, or null. */
    private WordHit wordAt(float x, float y) {
        if (lines == null || pageStart == null) return null;
        int start = pageStart[page];
        int end = page + 1 < pageStart.length ? pageStart[page + 1] : lines.size();
        float ly = cfg.margin;
        for (int i = start; i < end; i++) {
            Line l = lines.get(i);
            if (i > start) ly += l.before;
            if (l.img == null && y >= ly - 4 && y <= ly + l.h + 4) {
                float lx = cfg.margin + l.x;
                for (int k = 0; k < l.segs.length; k++) {
                    Seg s = l.segs[k];
                    if (s.space) lx += l.space + l.extra;
                    if (x >= lx - 6 && x <= lx + s.w + 6) return wordIn(s, x - lx, fonts.paint(s.style, l.level));
                    lx += s.w;
                }
                return null;
            }
            ly += l.h;
        }
        return null;
    }

    private static WordHit wordIn(Seg sg, float relx, Paint p) {
        String text = sg.text;
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
        WordHit w = new WordHit();
        w.word = text.substring(a, b);
        w.off = sg.off + Math.min(idx, Math.max(sg.len - 1, 0));
        return w;
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
            String right = (leftText.length() > 0 ? leftText + "   " : "") + (page + 1) + "/" + pages + "   " + pct + "%";
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

    private Paint imgPaint;

    private void drawImage(Canvas canvas, Line l, float y) {
        float x = cfg.margin + l.x;
        float top = y + 4;
        if (imgPaint == null) {
            imgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            android.graphics.ColorMatrix gm = new android.graphics.ColorMatrix();
            gm.setSaturation(0);
            gm.postConcat(new android.graphics.ColorMatrix(new float[]{1.2f, 0, 0, 0, -25, 0, 1.2f, 0, 0, -25, 0, 0, 1.2f, 0, -25, 0, 0, 0, 1, 0}));
            imgPaint.setColorFilter(new android.graphics.ColorMatrixColorFilter(gm));
        }
        Bitmap bm = ImageCache.peek(ImageCache.key(l.img, (int) l.iw));
        android.graphics.RectF dst = new android.graphics.RectF(x, top, x + l.iw, top + l.ih);
        if (bm != null) {
            canvas.drawBitmap(bm, null, dst, imgPaint);
        } else {
            rule.setColor(0xFF999999);
            canvas.drawRect(x, top, x + l.iw, top + 2, rule);
            canvas.drawRect(x, top + l.ih - 2, x + l.iw, top + l.ih, rule);
            canvas.drawRect(x, top, x + 2, top + l.ih, rule);
            canvas.drawRect(x + l.iw - 2, top, x + l.iw, top + l.ih, rule);
        }
        Hit h = new Hit();
        h.l = x; h.r = x + l.iw; h.t = top; h.b = top + l.ih;
        h.href = "img:" + l.img;
        hits.add(h);
    }

    private void drawText(Canvas c, String t, float x, float y, Paint p) {
        c.drawText(t, x, y, p);
        if (cfg.weight == 1) c.drawText(t, x + 0.7f, y, p);        // "Medium": a second pass thickens thin fonts
    }

    private boolean matchesSearch(String t) {
        if (searchTokens == null) return false;
        String lt = t.toLowerCase();
        for (int i = 0; i < searchTokens.length; i++) {
            if (searchTokens[i].length() >= 2 && lt.indexOf(searchTokens[i]) >= 0) return true;
        }
        return false;
    }

    private void drawLine(Canvas canvas, Line l, float y) {
        if (l.img != null) { drawImage(canvas, l, y); return; }
        float by = y + l.base;
        if (l.label != null) drawText(canvas, l.label, cfg.margin + l.labelX, by, fonts.paint(0, 0));
        float x = cfg.margin + l.x;
        for (int i = 0; i < l.segs.length; i++) {
            Seg s = l.segs[i];
            float gap = s.space ? l.space + l.extra : 0;
            if (s.space) x += gap;
            Paint p = fonts.paint(s.style, l.level);
            float yy = (s.style & Span.SUP) != 0 ? by - fonts.size * 0.33f : by;
            drawText(canvas, s.text, x, yy, p);
            for (int k = 0; k < highlights.length; k++) {                     // highlights: thick bar under the text
                int hs = highlights[k][0], he = highlights[k][1];
                if (s.off < he && s.off + s.len > hs) {
                    float bx = (s.space && s.off > hs) ? x - gap : x;
                    canvas.drawRect(bx, yy + 4, x + s.w, yy + 7, ink);
                    if (highlights[k][2] == 1 && s.off + s.len >= he) canvas.drawRect(x + s.w + 3, yy - 14, x + s.w + 10, yy - 7, ink);
                }
            }
            if (matchesSearch(s.text)) {                                       // search hit: frame around the word
                float t0 = y + 2, t1 = y + l.h - 2;
                canvas.drawRect(x - 2, t0, x + s.w + 2, t0 + 2, ink);
                canvas.drawRect(x - 2, t1 - 2, x + s.w + 2, t1, ink);
                canvas.drawRect(x - 2, t0, x, t1, ink);
                canvas.drawRect(x + s.w, t0, x + s.w + 2, t1, ink);
            }
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
