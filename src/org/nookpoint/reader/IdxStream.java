package org.nookpoint.reader;

import java.io.IOException;
import java.io.RandomAccessFile;

/** Fast sequential reader of StarDict .idx entries (word NUL offset size), reading in blocks. */
class IdxStream {
    private final RandomAccessFile f;
    private final int tail;
    private final boolean wide;
    private byte[] buf;
    private int pos, lim;
    private long base;
    private boolean eof;

    final byte[] w = new byte[256];
    int wl, size;
    long off, entryPos;

    IdxStream(RandomAccessFile f, long start, int offBits, int bufSize) throws IOException {
        this.f = f;
        this.wide = offBits == 64;
        this.tail = wide ? 12 : 8;
        this.buf = new byte[bufSize];
        this.base = start;
        f.seek(start);
    }

    static int be32(byte[] b, int p) {
        return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
    }

    boolean next() throws IOException {
        while (true) {
            int z = pos;
            while (z < lim && buf[z] != 0) z++;
            if (z < lim && z + 1 + tail <= lim) {
                wl = Math.min(z - pos, 256);
                System.arraycopy(buf, pos, w, 0, wl);
                int p = z + 1;
                if (wide) {
                    off = (((long) be32(buf, p)) << 32) | (be32(buf, p + 4) & 0xffffffffL);
                    size = be32(buf, p + 8);
                } else {
                    off = be32(buf, p) & 0xffffffffL;
                    size = be32(buf, p + 4);
                }
                entryPos = base + pos;
                pos = z + 1 + tail;
                return true;
            }
            if (eof) return false;
            int rem = lim - pos;
            if (rem > 0 && pos > 0) System.arraycopy(buf, pos, buf, 0, rem);
            base += pos;
            pos = 0;
            lim = rem;
            if (lim == buf.length) {                       // oversized (corrupt) entry: grow
                byte[] nb = new byte[buf.length * 2];
                System.arraycopy(buf, 0, nb, 0, lim);
                buf = nb;
            }
            int n = f.read(buf, lim, buf.length - lim);
            if (n <= 0) eof = true; else lim += n;
        }
    }
}
