package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/** One row of a bookcase: up to N covers standing on a wooden board. Tap opens, long press opens the menu. */
class ShelfRowView extends View {
    private static final float PAD = 12f;

    private ArrayList<LibItem> items = new ArrayList<LibItem>();
    private int start, cols = 4;
    private Library.Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private float downX, downY;
    private boolean longDone;
    private final Runnable longPress = new Runnable() {
        public void run() {
            longDone = true;
            LibItem it = itemAt(downX);
            if (it != null && listener != null) listener.menu(it);
        }
    };

    ShelfRowView(Context c) {
        super(c);
        setBackgroundColor(0xFFFFFFFF);
    }

    void bind(ArrayList<LibItem> all, int start, int cols, Library.Listener l) {
        this.items = all;
        this.start = start;
        this.cols = cols;
        this.listener = l;
        for (int i = 0; i < cols && start + i < all.size(); i++) {
            LibItem it = all.get(start + i);
            if (CoverCache.peek(it.path) == null) {
                CoverCache.load(getContext(), it.path, new CoverCache.Callback() {
                    public void onCover(String path, Bitmap bm) { postInvalidate(); }
                });
            }
        }
        requestLayout();
        invalidate();
    }

    private float cellW(int w) { return (w - 2 * PAD) / cols; }
    private float coverW(int w) { return cellW(w) - 22; }
    private float coverH(int w) { return coverW(w) * ShelfArt.COVER_RATIO; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, (int) (coverH(w) + 18 + ShelfArt.BOARD + 6));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        float cw = coverW(w), ch = coverH(w), cell = cellW(w), boardY = 18 + ch;
        for (int i = 0; i < cols && start + i < items.size(); i++) {
            LibItem it = items.get(start + i);
            float l = PAD + i * cell + 11;
            ShelfArt.cover(canvas, CoverCache.peek(it.path), l, boardY - ch - 2, l + cw, boardY - 2, it.pct / 100f, it.finished, p);
        }
        ShelfArt.board(canvas, 0, w, boardY, p);
    }

    private LibItem itemAt(float x) {
        int idx = (int) ((x - PAD) / cellW(getWidth()));
        if (idx < 0 || idx >= cols || start + idx >= items.size()) return null;
        return items.get(start + idx);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            longDone = false;
            handler.postDelayed(longPress, 600);
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (Math.abs(e.getX() - downX) > 15 || Math.abs(e.getY() - downY) > 15) handler.removeCallbacks(longPress);
        } else if (a == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(longPress);
        } else if (a == MotionEvent.ACTION_UP) {
            handler.removeCallbacks(longPress);
            if (!longDone && Math.abs(e.getX() - downX) < 15 && Math.abs(e.getY() - downY) < 15) {
                LibItem it = itemAt(e.getX());
                if (it != null && listener != null) listener.open(it);
            }
        }
        return true;
    }
}
