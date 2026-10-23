package org.nookpoint.reader;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Random access to a dictionary data file, plain (.dict) or dictzip (.dict.dz). */
class DictFile {
    private final RandomAccessFile f;
    private final boolean dz;
    private int chlen, cachedIdx = -1;
    private int[] chunkSize;
    private long[] chunkStart;
    private byte[] cachedChunk;

    DictFile(File file) throws IOException {
        dz = file.getName().toLowerCase().endsWith(".dz");
        f = new RandomAccessFile(file, "r");
        if (dz) {
            try {
                parse();
            } catch (IOException e) {
                f.close();
                throw e;
            }
        }
    }

    void close() {
        try { f.close(); } catch (IOException e) { }
    }

    byte[] read(long off, int size) throws IOException {
        if (size < 0 || size > 4000000) size = 4000000;
        byte[] out = new byte[size];
        if (!dz) {
            f.seek(off);
            int n = f.read(out);
            if (n < size) {
                byte[] s = new byte[Math.max(n, 0)];
                System.arraycopy(out, 0, s, 0, s.length);
                return s;
            }
            return out;
        }
        int done = 0;
        while (done < size) {
            long pos = off + done;
            int ci = (int) (pos / chlen), in = (int) (pos % chlen);
            if (ci >= chunkSize.length) break;
            byte[] chunk = chunk(ci);
            int n = Math.min(size - done, chunk.length - in);
            if (n <= 0) break;
            System.arraycopy(chunk, in, out, done, n);
            done += n;
        }
        if (done < size) {
            byte[] s = new byte[done];
            System.arraycopy(out, 0, s, 0, done);
            return s;
        }
        return out;
    }

    private static int le16(byte[] b, int p) { return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8); }

    private void parse() throws IOException {
        f.seek(0);
        if (f.read() != 0x1f || f.read() != 0x8b) throw new IOException("not gzip");
        f.read();
        int flg = f.read();
        f.skipBytes(6);
        if ((flg & 4) == 0) throw new IOException("not dictzip");
        int xlen = f.read() | (f.read() << 8);
        byte[] extra = new byte[xlen];
        f.readFully(extra);
        int p = 0;
        int[] sizes = null;
        while (p + 4 <= xlen) {
            int len = le16(extra, p + 2);
            if (extra[p] == 'R' && extra[p + 1] == 'A') {
                int q = p + 4;
                chlen = le16(extra, q + 2);
                int cnt = le16(extra, q + 4);
                sizes = new int[cnt];
                for (int i = 0; i < cnt; i++) sizes[i] = le16(extra, q + 6 + 2 * i);
                break;
            }
            p += 4 + len;
        }
        if (sizes == null || chlen <= 0) throw new IOException("no dictzip header");
        int c;
        if ((flg & 8) != 0) while ((c = f.read()) > 0) { }
        if ((flg & 16) != 0) while ((c = f.read()) > 0) { }
        if ((flg & 2) != 0) f.skipBytes(2);
        long[] cs = new long[sizes.length + 1];
        cs[0] = f.getFilePointer();
        for (int i = 0; i < sizes.length; i++) cs[i + 1] = cs[i] + sizes[i];
        chunkSize = sizes;
        chunkStart = cs;
    }

    private byte[] chunk(int ci) throws IOException {
        if (ci == cachedIdx) return cachedChunk;
        f.seek(chunkStart[ci]);
        byte[] comp = new byte[chunkSize[ci] + 1];
        f.readFully(comp, 0, chunkSize[ci]);
        Inflater inf = new Inflater(true);
        inf.setInput(comp);
        byte[] out = new byte[chlen];
        int got = 0;
        try {
            while (got < chlen && !inf.finished()) {
                int n = inf.inflate(out, got, chlen - got);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                got += n;
            }
        } catch (DataFormatException e) {
            throw new IOException("bad dictzip chunk");
        } finally {
            inf.end();
        }
        if (got < chlen) {
            byte[] s = new byte[got];
            System.arraycopy(out, 0, s, 0, got);
            out = s;
        }
        cachedIdx = ci;
        cachedChunk = out;
        return out;
    }
}
