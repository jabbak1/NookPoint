package org.nookpoint.reader;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small cache of decoded book pictures (decoded in the background). */
class ImageCache {
    private static final int MAX = 4;
    private static final LinkedHashMap<String, Bitmap> map = new LinkedHashMap<String, Bitmap>();
    private static final HashSet<String> loading = new HashSet<String>();
    private static final ExecutorService ex = Executors.newSingleThreadExecutor();
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static String key(String ref, int w) { return ref + "@" + w; }

    static synchronized Bitmap peek(String key) { return map.get(key); }

    static synchronized void clear() { map.clear(); }

    static void load(final Book book, final String ref, final int w, final int h, final Runnable done) {
        final String k = key(ref, w);
        synchronized (ImageCache.class) {
            if (map.containsKey(k) || !loading.add(k)) return;
        }
        ex.execute(new Runnable() {
            public void run() {
                Bitmap bm = book.loadImage(ref, w, h);
                synchronized (ImageCache.class) {
                    loading.remove(k);
                    if (bm != null) {
                        map.put(k, bm);
                        while (map.size() > MAX) {
                            java.util.Iterator<Map.Entry<String, Bitmap>> it = map.entrySet().iterator();
                            it.next();
                            it.remove();
                        }
                    }
                }
                if (bm != null && done != null) ui.post(done);
            }
        });
    }
}
