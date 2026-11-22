package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** A line icon that turns white when its (clickable) parent is pressed. */
class IconView extends View {
    private int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    IconView(Context c, int kind) {
        super(c);
        this.kind = kind;
    }

    void setKind(int k) {
        kind = k;
        invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    private boolean pressedNow() {
        int[] st = getDrawableState();
        for (int i = 0; i < st.length; i++) if (st[i] == android.R.attr.state_pressed) return true;
        return false;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
    }

    @Override
    protected void onDraw(Canvas c) {
        p.setColor(pressedNow() ? Color.WHITE : Color.BLACK);
        UiArt.icon(c, kind, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * 0.8f, p);
    }
}
