package org.nookpoint.reader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;

/** Drawing of bookshelves: wooden boards, covers standing on them, and shelf tiles with a fan of covers. */
class ShelfArt {
    static final float BOARD = 20f;                 // height of a board including its shadow
    static final float COVER_RATIO = 1.42f;         // height / width of a typical cover

    /** Where a cover is drawn inside its slot: scaled to fit, centred, standing on the bottom edge. */
    static float[] fit(Bitmap bm, float l, float t, float r, float b) {
        float bw = r - l, bh = b - t;
        if (bm == null || bm.getWidth() <= 0 || bm.getHeight() <= 0) {
            float w = Math.min(bw, bh / COVER_RATIO);
            return new float[]{l + (bw - w) / 2, b - w * COVER_RATIO, l + (bw + w) / 2, b};
        }
        float s = Math.min(bw / bm.getWidth(), bh / bm.getHeight());
        float w = bm.getWidth() * s, h = bm.getHeight() * s;
        return new float[]{l + (bw - w) / 2, b - h, l + (bw + w) / 2, b};
    }

    /** A wooden board: dark top edge, grey front, soft shadow underneath. y = top of the board. */
    static void board(Canvas c, float x0, float x1, float y, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000); c.drawRect(x0, y, x1, y + 3, p);
        p.setColor(0xFF666666); c.drawRect(x0, y + 3, x1, y + 11, p);
        p.setColor(0xFF8E8E8E); c.drawRect(x0, y + 11, x1, y + 13, p);
        p.setColor(0xFFC4C4C4); c.drawRect(x0 + 6, y + 13, x1 - 6, y + 16, p);
        p.setColor(0xFFE2E2E2); c.drawRect(x0 + 12, y + 16, x1 - 12, y + 19, p);
    }

    /** A book cover with shadow, outline and a progress strip along its bottom edge. */
    static void cover(Canvas c, Bitmap bm, float l, float t, float r, float b, float progress, boolean finished, Paint p) {
        float[] f = fit(bm, l, t, r, b);
        float dl = f[0], dt = f[1], dr = f[2], db = f[3];
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFBDBDBD);
        c.drawRect(dl + 4, dt + 4, dr + 4, db + 2, p);                    // drop shadow
        if (bm != null) {
            c.drawBitmap(bm, null, new RectF(dl, dt, dr, db), p);
        } else {
            p.setColor(0xFFFFFFFF);
            c.drawRect(dl, dt, dr, db, p);
        }
        p.setColor(0xFF000000);                                           // outline
        c.drawRect(dl, dt, dr, dt + 2, p);
        c.drawRect(dl, db - 2, dr, db, p);
        c.drawRect(dl, dt, dl + 2, db, p);
        c.drawRect(dr - 2, dt, dr, db, p);
        if (progress > 0f || finished) {                                   // reading progress
            float by = db - 9;
            p.setColor(0xFFFFFFFF);
            c.drawRect(dl + 2, by, dr - 2, db - 2, p);
            p.setColor(0xFF000000);
            c.drawRect(dl + 3, by + 1, dl + 3 + (dr - dl - 6) * (finished ? 1f : Math.min(1f, progress)), db - 3, p);
        }
    }

    static String ellipsize(String s, Paint p, float max) {
        if (p.measureText(s) <= max) return s;
        while (s.length() > 1 && p.measureText(s + "\u2026") > max) s = s.substring(0, s.length() - 1);
        return s + "\u2026";
    }

    private static void centered(Canvas c, String s, float w, float y, Paint p) {
        c.drawText(s, Math.max(2, (w - p.measureText(s)) / 2), y, p);
    }

    /** A shelf tile: a fan of up to three covers standing on a board, with the shelf name and count under it. */
    static void tile(Canvas c, float w, float h, ArrayList<Bitmap> covers, boolean plus, String name, String sub,
                     Paint p, Paint nameP, Paint subP) {
        float textH = 56;
        float boardY = h - textH - BOARD + 6;
        float ch = boardY - 14;
        float cw = Math.min(ch / COVER_RATIO, w * 0.46f);
        ch = Math.min(ch, cw * COVER_RATIO);
        int n = covers == null ? 0 : Math.min(3, covers.size());
        if (n == 0) {                                                       // empty shelf / "new shelf"
            float l = (w - cw) / 2, t = boardY - ch;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF000000);
            for (float x = l; x < l + cw; x += 12) { c.drawRect(x, t, Math.min(x + 6, l + cw), t + 2, p); c.drawRect(x, boardY - 3, Math.min(x + 6, l + cw), boardY - 1, p); }
            for (float y = t; y < boardY - 1; y += 12) { c.drawRect(l, y, l + 2, Math.min(y + 6, boardY - 1), p); c.drawRect(l + cw - 2, y, l + cw, Math.min(y + 6, boardY - 1), p); }
            if (plus) {
                float cx = l + cw / 2, cy = t + ch / 2;
                c.drawRect(cx - 18, cy - 2, cx + 18, cy + 2, p);
                c.drawRect(cx - 2, cy - 18, cx + 2, cy + 18, p);
            }
        } else {
            float gap = n > 1 ? Math.min(cw * 0.55f, (w - 24 - cw) / (n - 1)) : 0;
            float start = (w - (cw + (n - 1) * gap)) / 2;
            float mid = (n - 1) / 2f;
            boolean[] drawn = new boolean[n];
            for (int pass = 0; pass < n; pass++) {                         // outer covers first, the middle one in front
                int best = -1;
                float bd = -1;
                for (int k = 0; k < n; k++) {
                    float d = Math.abs(k - mid);
                    if (!drawn[k] && d > bd) { bd = d; best = k; }
                }
                drawn[best] = true;
                float scale = best == (int) mid && n % 2 == 1 || (n == 2 && best == 1) ? 1f : 0.9f;
                float l = start + best * gap;
                cover(c, covers.get(best), l, boardY - ch * scale, l + cw, boardY - 2, 0f, false, p);
            }
        }
        board(c, 2, w - 2, boardY, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        centered(c, ellipsize(name, nameP, w - 12), w, h - 28, nameP);
        centered(c, ellipsize(sub, subP, w - 12), w, h - 6, subP);
    }
}
