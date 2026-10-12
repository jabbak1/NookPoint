package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** Outlined progress bar. */
class Bar extends View {
    private float frac;
    private final Paint p = new Paint();

    Bar(Context c) { super(c); }

    void set(float f) {
        frac = Math.max(0f, Math.min(1f, f));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        c.drawRect(1, 1, w - 1, h - 1, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(3, 3, 3 + (w - 6) * frac, h - 3, p);
    }
}

/** Seven-day bar chart (values in seconds). */
class BarChart extends View {
    long[] vals = new long[7];
    long goalSecs;
    String[] labels = new String[7];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tb = new Paint(Paint.ANTI_ALIAS_FLAG);

    BarChart(Context c) {
        super(c);
        for (int i = 0; i < 7; i++) labels[i] = "";
        p.setColor(Color.BLACK);
        tp.setColor(Color.BLACK); tp.setTextSize(18); tp.setTextAlign(Paint.Align.CENTER);
        tb.setColor(Color.BLACK); tb.setTextSize(18); tb.setTextAlign(Paint.Align.CENTER);
        tb.setTypeface(Typeface.DEFAULT_BOLD);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float maxMin = 30;
        for (int i = 0; i < vals.length; i++) maxMin = Math.max(maxMin, vals[i] / 60f);
        maxMin = Math.max(maxMin, goalSecs / 60f);
        float slot = w / (float) vals.length, bw = slot * 0.56f;
        int top = 34, bottom = h - 36;
        c.drawRect(8, bottom, w - 8, bottom + 2, p);
        if (goalSecs > 0) {
            float gy = bottom - (bottom - top) * (goalSecs / 60f) / maxMin;
            for (float gx = 8; gx < w - 8; gx += 16) c.drawRect(gx, gy, Math.min(gx + 9, w - 8), gy + 2, p);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText("goal " + Stats.fmtShort(goalSecs), w - 10, gy - 6, tp);
            tp.setTextAlign(Paint.Align.CENTER);
        }
        for (int i = 0; i < vals.length; i++) {
            float x = slot * i + (slot - bw) / 2;
            float bh = (bottom - top) * (vals[i] / 60f) / maxMin;
            if (vals[i] > 0 && bh < 4) bh = 4;
            if (bh > 0) c.drawRect(x, bottom - bh, x + bw, bottom, p);
            if (vals[i] > 0) c.drawText(Stats.fmtShort(vals[i]), x + bw / 2, bottom - bh - 8, tp);
            c.drawText(labels[i], x + bw / 2, bottom + 27, i == vals.length - 1 ? tb : tp);
        }
    }
}
