package org.nookpoint.reader;

import android.graphics.Paint;
import android.graphics.Typeface;

/** Regular / bold / italic / bold-italic faces of one font family. Index = (bold ? 1 : 0) | (italic ? 2 : 0). */
class FontSet {
    final Typeface[] f = new Typeface[4];
    Typeface get(int bits) { return f[bits & 3]; }
}

/** Paint cache for body/heading text in regular, bold, italic and superscript variants. */
class Fonts {
    static final float[] SCALE = {1f, 1.32f, 1.18f, 1.08f, 1f, 1f, 1f};
    final FontSet fs;
    final float size;
    private final Paint[] cache = new Paint[7 * 8];

    Fonts(FontSet fs, float size) { this.fs = fs; this.size = size; }

    /** style: Span bits (BOLD, ITALIC, SUP are used); level 0 = body, 1..6 = heading (always bold). */
    Paint paint(int style, int level) {
        if (level < 0 || level > 6) level = 0;
        int bits = (((style & Span.BOLD) != 0 || level > 0) ? 1 : 0) | ((style & Span.ITALIC) != 0 ? 2 : 0)
                | ((style & Span.SUP) != 0 ? 4 : 0);
        int k = level * 8 + bits;
        Paint p = cache[k];
        if (p == null) {
            p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(fs.get(bits & 3));
            p.setTextSize(size * SCALE[level] * ((bits & 4) != 0 ? 0.7f : 1f));
            p.setColor(0xFF000000);
            cache[k] = p;
        }
        return p;
    }
}
