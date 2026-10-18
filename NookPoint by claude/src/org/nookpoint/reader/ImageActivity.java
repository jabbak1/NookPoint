package org.nookpoint.reader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

/** Full-screen picture: tap = zoom, drag = move, hold = rotate, Back = close. */
public class ImageActivity extends Activity {
    private ZoomView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        view = new ZoomView(this);
        setContentView(view);
        final String ref = getIntent().getStringExtra("ref");
        final Book book = ReaderActivity.current;
        if (ref == null || book == null) { finish(); return; }
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... v) {
                int w = getWindowManager().getDefaultDisplay().getWidth();
                int h = getWindowManager().getDefaultDisplay().getHeight();
                int m = Math.max(w, h);
                return book.loadImage(ref, (int) (m * 1.4f), (int) (m * 1.4f));
            }
            @Override
            protected void onPostExecute(Bitmap bm) {
                if (bm == null) { finish(); return; }
                view.set(bm);
            }
        }.execute();
    }

    private static class ZoomView extends View {
        private Bitmap bm;
        private int rot, zoom;                          // zoom 0 = fit, 1 = x2, 2 = x3
        private float panX, panY, downX, downY, lastX, lastY;
        private boolean moved, longDone;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final Handler h = new Handler();
        private final Runnable longPress = new Runnable() {
            public void run() {
                longDone = true;
                rot = (rot + 90) % 360;
                zoom = 0;
                panX = panY = 0;
                invalidate();
            }
        };

        ZoomView(Context c) {
            super(c);
            setBackgroundColor(Color.WHITE);
            hint.setColor(Color.BLACK);
            hint.setTextSize(17);
            hint.setTypeface(Cfg.uiFace(false));
            android.graphics.ColorMatrix gm = new android.graphics.ColorMatrix();
            gm.setSaturation(0);
            gm.postConcat(new android.graphics.ColorMatrix(new float[]{1.2f, 0, 0, 0, -25, 0, 1.2f, 0, 0, -25, 0, 0, 1.2f, 0, -25, 0, 0, 0, 1, 0}));
            paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(gm));
        }

        void set(Bitmap b) { bm = b; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(Color.WHITE);
            if (bm == null) return;
            int vw = getWidth(), vh = getHeight();
            boolean turned = rot == 90 || rot == 270;
            float bw = turned ? bm.getHeight() : bm.getWidth(), bh = turned ? bm.getWidth() : bm.getHeight();
            float fit = Math.min(vw / bw, vh / bh);
            float sc = fit * (zoom == 0 ? 1f : (zoom == 1 ? 2f : 3f));
            float maxPanX = Math.max(0, (bw * sc - vw) / 2), maxPanY = Math.max(0, (bh * sc - vh) / 2);
            panX = Math.max(-maxPanX, Math.min(maxPanX, panX));
            panY = Math.max(-maxPanY, Math.min(maxPanY, panY));
            m.reset();
            m.postTranslate(-bm.getWidth() / 2f, -bm.getHeight() / 2f);
            m.postRotate(rot);
            m.postScale(sc, sc);
            m.postTranslate(vw / 2f + panX, vh / 2f + panY);
            c.drawBitmap(bm, m, paint);
            if (zoom == 0) c.drawText(T.t("Tap: zoom   Hold: rotate   Back: close"), 10, vh - 8, hint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getAction();
            float x = e.getX(), y = e.getY();
            if (a == MotionEvent.ACTION_DOWN) {
                downX = lastX = x;
                downY = lastY = y;
                moved = false;
                longDone = false;
                h.postDelayed(longPress, 700);
            } else if (a == MotionEvent.ACTION_MOVE) {
                if (Math.abs(x - downX) > 12 || Math.abs(y - downY) > 12) { moved = true; h.removeCallbacks(longPress); }
                if (moved && zoom > 0) {
                    panX += x - lastX;
                    panY += y - lastY;
                    invalidate();
                }
                lastX = x;
                lastY = y;
            } else if (a == MotionEvent.ACTION_UP) {
                h.removeCallbacks(longPress);
                if (!moved && !longDone) {
                    zoom = (zoom + 1) % 3;
                    panX = panY = 0;
                    invalidate();
                }
            } else if (a == MotionEvent.ACTION_CANCEL) {
                h.removeCallbacks(longPress);
            }
            return true;
        }
    }
}
