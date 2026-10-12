package org.nookpoint.reader;

import android.graphics.Paint;
import java.util.ArrayList;

/** Greedy line breaking. All lines have equal height, so a page is just N lines. */
class Layouter {
    static ArrayList<Line> layout(ArrayList<Para> paras, Paint body, Paint head, int width, int indent,
                                  boolean justify) {
        ArrayList<Line> out = new ArrayList<Line>();
        for (int i = 0; i < paras.size(); i++) {
            Para p = paras.get(i);
            if (p.head) {
                if (out.size() > 0) out.add(new Line("", p.offset, false, false, 0, 0));
                wrap(out, p, head, width, 0, false);
                out.add(new Line("", p.offset + p.text.length(), false, false, 0, 0));
            } else {
                wrap(out, p, body, width, indent, justify);
            }
        }
        if (out.size() == 0) out.add(new Line("(no text in this section)", 0, false, false, 0, 0));
        return out;
    }

    private static void wrap(ArrayList<Line> out, Para p, Paint paint, int width, int indent, boolean justify) {
        String t = p.text;
        int n = t.length();
        int pos = 0;
        boolean first = true;
        while (pos < n) {
            int ind = first ? indent : 0;
            int avail = width - ind;
            int cnt = paint.breakText(t, pos, n, true, avail, null);
            if (cnt <= 0) cnt = 1;
            int end = pos + cnt;
            if (end < n && t.charAt(end) != ' ') {
                int b = end;
                while (b > pos && t.charAt(b - 1) != ' ') b--;
                if (b > pos) end = b;
            }
            int e2 = end;
            while (e2 > pos && t.charAt(e2 - 1) == ' ') e2--;
            boolean last = end >= n;
            out.add(new Line(t.substring(pos, e2), p.offset + pos, p.head, justify && !last && !p.head, ind, avail));
            pos = end;
            while (pos < n && t.charAt(pos) == ' ') pos++;
            first = false;
        }
    }
}
