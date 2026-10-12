package org.nookpoint.reader;

import android.graphics.Paint;
import android.graphics.Typeface;

/** Paint cache for body/heading text in regular, bold, italic and superscript variants. */
class Fonts {
    static final float[] SCALE = {1f, 1.32f, 1.18f, 1.08f, 1f, 1f, 1f};
    final Typeface tf;
    final float size;
    private final Paint[] cache = new Paint[7 * 8];

    Fonts(Typeface tf, float size) { this.tf = tf; this.size = size; }

    /** style: Span bits (BOLD, ITALIC, SUP are used); level 0 = body, 1..6 = heading (always bold). */
    Paint paint(int style, int level) {
        if (level < 0 || level > 6) level = 0;
        int bits = (((style & Span.BOLD) != 0 || level > 0) ? 1 : 0) | ((style & Span.ITALIC) != 0 ? 2 : 0)
                | ((style & Span.SUP) != 0 ? 4 : 0);
        int k = level * 8 + bits;
        Paint p = cache[k];
        if (p == null) {
            p = new Paint(Paint.ANTI_ALIAS_FLAG);
            int ts = (bits & 1) != 0 ? ((bits & 2) != 0 ? Typeface.BOLD_ITALIC : Typeface.BOLD)
                    : ((bits & 2) != 0 ? Typeface.ITALIC : Typeface.NORMAL);
            p.setTypeface(Typeface.create(tf, ts));
            p.setTextSize(size * SCALE[level] * ((bits & 4) != 0 ? 0.7f : 1f));
            p.setColor(0xFF000000);
            cache[k] = p;
        }
        return p;
    }
}
