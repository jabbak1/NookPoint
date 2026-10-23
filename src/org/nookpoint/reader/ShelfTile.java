package org.nookpoint.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayList;

/** A shelf as a tile: a fan of covers on a board, the name and the number of books. */
class ShelfTile extends View {
    private String name = "", sub = "";
    private ArrayList<String> paths = new ArrayList<String>();
    private boolean plus;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nameP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subP = new Paint(Paint.ANTI_ALIAS_FLAG);

    ShelfTile(Context c) {
        super(c);
        setClickable(true);
        nameP.setTextSize(21);
        nameP.setColor(0xFF000000);
        nameP.setTypeface(Cfg.uiFace(true));
        subP.setTextSize(16);
        subP.setColor(0xFF000000);
        subP.setTypeface(Cfg.uiFace(false));
    }

    void bind(String name, String sub, ArrayList<String> coverPaths, boolean plus) {
        this.name = name;
        this.sub = sub;
        this.paths = coverPaths;
        this.plus = plus;
        for (int i = 0; i < paths.size() && i < 3; i++) {
            if (CoverCache.peek(paths.get(i)) == null) {
                CoverCache.load(getContext(), paths.get(i), new CoverCache.Callback() {
                    public void onCover(String path, Bitmap bm) { postInvalidate(); }
                });
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(isPressed() ? 0xFFE6E6E6 : 0xFFFFFFFF);
        ArrayList<Bitmap> covers = new ArrayList<Bitmap>();
        for (int i = 0; i < paths.size() && i < 3; i++) covers.add(CoverCache.peek(paths.get(i)));
        ShelfArt.tile(canvas, getWidth(), getHeight(), covers, plus, name, sub, p, nameP, subP);
    }
}
