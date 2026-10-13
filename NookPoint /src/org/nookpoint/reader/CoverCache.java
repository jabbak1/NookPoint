package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cover thumbnails: memory + disk cache, generated on a background thread; placeholder if no cover. */
class CoverCache {
    interface Callback { void onCover(String path, Bitmap bm); }

    static final int W = 240, H = 336;
    private static final HashMap<String, Bitmap> mem = new HashMap<String, Bitmap>();
    private static final ExecutorService ex = Executors.newSingleThreadExecutor();
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static Bitmap peek(String path) {
        synchronized (mem) { return mem.get(path); }
    }

    static void load(Context ctx, final String path, final Callback cb) {
        Bitmap b = peek(path);
        if (b != null) { cb.onCover(path, b); return; }
        final File dir = ctx.getApplicationContext().getCacheDir();
        ex.execute(new Runnable() {
            public void run() {
                final Bitmap bm = build(dir, path);
                synchronized (mem) {
                    if (mem.size() > 20) mem.clear();
                    mem.put(path, bm);
                }
                ui.post(new Runnable() {
                    public void run() { cb.onCover(path, bm); }
                });
            }
        });
    }

    private static Bitmap build(File dir, String path) {
        File f = new File(path);
        File cf = new File(dir, "cv_" + Integer.toHexString((path + f.lastModified() + f.length()).hashCode()) + ".png");
        try {
            if (cf.exists()) {
                Bitmap b = BitmapFactory.decodeFile(cf.getAbsolutePath());
                if (b != null) return b;
            }
        } catch (Throwable t) { }

        String title = null, author = null;
        Bitmap bm = null;
        if (path.toLowerCase().endsWith(".epub")) {
            Book bk = null;
            try {
                bk = Book.open(f);
                title = bk.title;
                author = bk.author;
                Prefs.putMany(new String[]{"title:" + path, title, "author:" + path, author == null ? "" : author});
                Bitmap raw = bk.cover(W, H);
                if (raw != null) bm = fit(raw);
            } catch (Throwable t) {
                bm = null;
            } finally {
                if (bk != null) bk.close();
            }
        }
        if (bm == null) bm = placeholder(title != null ? title : Prefs.title(path), author);
        try {
            FileOutputStream fo = new FileOutputStream(cf);
            bm.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
        } catch (Throwable t) { }
        return bm;
    }

    private static Bitmap fit(Bitmap raw) {
        float s = Math.min(W / (float) raw.getWidth(), H / (float) raw.getHeight());
        int nw = Math.max(1, Math.round(raw.getWidth() * s)), nh = Math.max(1, Math.round(raw.getHeight() * s));
        Bitmap out = Bitmap.createScaledBitmap(raw, nw, nh, true);
        if (out != raw) raw.recycle();
        return out;
    }

    private static Bitmap placeholder(String title, String author) {
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bm);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        c.drawRect(3, 3, W - 3, H - 3, p);
        p.setStrokeWidth(2);
        c.drawRect(14, 14, W - 14, H - 14, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(26);
        ArrayList<String> ls = wrap(title == null ? "" : title, p, W - 52);
        float y = 70;
        for (int i = 0; i < ls.size() && i < 6; i++) {
            String s = ls.get(i);
            if (i == 5 && ls.size() > 6) s = s + "...";
            c.drawText(s, 26, y, p);
            y += 32;
        }
        if (author != null && author.length() > 0) {
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(20);
            ArrayList<String> as = wrap(author, p, W - 52);
            float ay = H - 28 - (Math.min(as.size(), 2) - 1) * 24;
            for (int i = 0; i < as.size() && i < 2; i++) { c.drawText(as.get(i), 26, ay, p); ay += 24; }
        }
        return bm;
    }

    private static ArrayList<String> wrap(String t, Paint p, float w) {
        ArrayList<String> out = new ArrayList<String>();
        String[] words = t.split(" ");
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String cand = cur.length() == 0 ? words[i] : cur + " " + words[i];
            if (p.measureText(cand) <= w || cur.length() == 0) {
                cur.setLength(0);
                cur.append(cand);
            } else {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(words[i]);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
