package org.nookpoint.reader;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Line icons and rounded shapes for the modern E Ink interface (pure drawing, no views). */
class UiArt {
    static final int LIBRARY = 0, APPS = 1, STATS = 2, WIFI = 3, ONLINE = 4, SETTINGS = 5, SEARCH = 6, SORT = 7,
            FILTER = 8, VIEW_LIST = 9, VIEW_GRID = 10, VIEW_SHELF = 11, BACK = 12, SHELVES = 13;

    static void roundRect(Canvas c, float l, float t, float r, float b, float rad, int fill, int stroke, float sw, Paint p) {
        RectF rf = new RectF(l, t, r, b);
        if (fill != 0) { p.setStyle(Paint.Style.FILL); p.setColor(fill); c.drawRoundRect(rf, rad, rad, p); }
        if (stroke != 0 && sw > 0) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw); p.setColor(stroke);
            c.drawRoundRect(new RectF(l + sw / 2, t + sw / 2, r - sw / 2, b - sw / 2), rad, rad, p);
        }
    }

    private static void rr(Canvas c, float cx, float cy, float h, float x0, float y0, float x1, float y1, float rad, Paint p) {
        c.drawRoundRect(new RectF(cx + x0 * h, cy + y0 * h, cx + x1 * h, cy + y1 * h), rad, rad, p);
    }

    /** A line icon centred at (cx, cy) fitting a square of side s, drawn with the colour of p. */
    static void icon(Canvas c, int kind, float cx, float cy, float s, Paint p) {
        float h = s / 2f, sw = Math.max(2.5f, s * 0.075f), r = sw * 1.2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        switch (kind) {
            case LIBRARY:                                    // three books and a base line
                rr(c, cx, cy, h, -0.78f, -0.45f, -0.38f, 0.55f, r, p);
                rr(c, cx, cy, h, -0.26f, -0.72f, 0.14f, 0.55f, r, p);
                rr(c, cx, cy, h, 0.26f, -0.30f, 0.66f, 0.55f, r, p);
                c.drawLine(cx - 0.9f * h, cy + 0.82f * h, cx + 0.9f * h, cy + 0.82f * h, p);
                break;
            case APPS:
                rr(c, cx, cy, h, -0.8f, -0.8f, -0.1f, -0.1f, r, p);
                rr(c, cx, cy, h, 0.1f, -0.8f, 0.8f, -0.1f, r, p);
                rr(c, cx, cy, h, -0.8f, 0.1f, -0.1f, 0.8f, r, p);
                rr(c, cx, cy, h, 0.1f, 0.1f, 0.8f, 0.8f, r, p);
                break;
            case STATS:
                p.setStyle(Paint.Style.FILL);
                rr(c, cx, cy, h, -0.75f, 0.05f, -0.4f, 0.8f, r / 2, p);
                rr(c, cx, cy, h, -0.17f, -0.35f, 0.18f, 0.8f, r / 2, p);
                rr(c, cx, cy, h, 0.4f, -0.8f, 0.75f, 0.8f, r / 2, p);
                break;
            case WIFI: {
                float bx = cx, by = cy + 0.55f * h;
                for (int i = 1; i <= 3; i++) {
                    float rad = i * 0.42f * h;
                    c.drawArc(new RectF(bx - rad, by - rad, bx + rad, by + rad), 225, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(bx, by, sw * 0.9f, p);
                break;
            }
            case ONLINE:
                c.drawCircle(cx, cy, 0.85f * h, p);
                c.drawOval(new RectF(cx - 0.38f * h, cy - 0.85f * h, cx + 0.38f * h, cy + 0.85f * h), p);
                c.drawLine(cx - 0.85f * h, cy, cx + 0.85f * h, cy, p);
                break;
            case SETTINGS: {
                c.drawCircle(cx, cy, 0.3f * h, p);
                c.drawCircle(cx, cy, 0.58f * h, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    c.drawLine(cx + cs * 0.58f * h, cy + sn * 0.58f * h, cx + cs * 0.88f * h, cy + sn * 0.88f * h, p);
                }
                break;
            }
            case SEARCH:
                c.drawCircle(cx - 0.12f * h, cy - 0.12f * h, 0.58f * h, p);
                c.drawLine(cx + 0.3f * h, cy + 0.3f * h, cx + 0.85f * h, cy + 0.85f * h, p);
                break;
            case SORT:
                c.drawLine(cx - 0.8f * h, cy - 0.6f * h, cx + 0.8f * h, cy - 0.6f * h, p);
                c.drawLine(cx - 0.8f * h, cy, cx + 0.45f * h, cy, p);
                c.drawLine(cx - 0.8f * h, cy + 0.6f * h, cx + 0.1f * h, cy + 0.6f * h, p);
                break;
            case FILTER: {
                Path f = new Path();
                f.moveTo(cx - 0.85f * h, cy - 0.7f * h);
                f.lineTo(cx + 0.85f * h, cy - 0.7f * h);
                f.lineTo(cx + 0.15f * h, cy + 0.1f * h);
                f.lineTo(cx + 0.15f * h, cy + 0.8f * h);
                f.lineTo(cx - 0.15f * h, cy + 0.6f * h);
                f.lineTo(cx - 0.15f * h, cy + 0.1f * h);
                f.close();
                c.drawPath(f, p);
                break;
            }
            case VIEW_LIST:
                for (int i = -1; i <= 1; i++) {
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx - 0.72f * h, cy + i * 0.62f * h, sw * 0.8f, p);
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(cx - 0.35f * h, cy + i * 0.62f * h, cx + 0.85f * h, cy + i * 0.62f * h, p);
                }
                break;
            case VIEW_GRID:
                rr(c, cx, cy, h, -0.8f, -0.8f, -0.1f, -0.1f, r, p);
                rr(c, cx, cy, h, 0.1f, -0.8f, 0.8f, -0.1f, r, p);
                rr(c, cx, cy, h, -0.8f, 0.1f, -0.1f, 0.8f, r, p);
                rr(c, cx, cy, h, 0.1f, 0.1f, 0.8f, 0.8f, r, p);
                break;
            case VIEW_SHELF:
                rr(c, cx, cy, h, -0.8f, -0.6f, -0.35f, 0.5f, r, p);
                rr(c, cx, cy, h, -0.22f, -0.8f, 0.23f, 0.5f, r, p);
                rr(c, cx, cy, h, 0.36f, -0.45f, 0.8f, 0.5f, r, p);
                p.setStrokeWidth(sw * 1.5f);
                c.drawLine(cx - 0.95f * h, cy + 0.78f * h, cx + 0.95f * h, cy + 0.78f * h, p);
                break;
            case BACK: {
                Path b = new Path();
                b.moveTo(cx + 0.3f * h, cy - 0.8f * h);
                b.lineTo(cx - 0.45f * h, cy);
                b.lineTo(cx + 0.3f * h, cy + 0.8f * h);
                c.drawPath(b, p);
                break;
            }
            case SHELVES:
                rr(c, cx, cy, h, -0.7f, -0.8f, -0.25f, 0.2f, r, p);
                rr(c, cx, cy, h, -0.12f, -0.55f, 0.33f, 0.2f, r, p);
                rr(c, cx, cy, h, 0.45f, -0.7f, 0.85f, 0.2f, r, p);
                c.drawLine(cx - 0.95f * h, cy + 0.45f * h, cx + 0.95f * h, cy + 0.45f * h, p);
                c.drawLine(cx - 0.7f * h, cy + 0.8f * h, cx + 0.7f * h, cy + 0.8f * h, p);
                break;
            default:
                break;
        }
        p.setStyle(Paint.Style.FILL);
    }
}
