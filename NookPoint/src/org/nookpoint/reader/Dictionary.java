package org.nookpoint.reader;

import java.io.IOException;
import java.util.ArrayList;

/** A dictionary that can look words up (StarDict or dictd). */
interface Dictionary {
    String getName();
    String getPath();
    long getWordCount();                       // -1 if unknown
    boolean indexReady();
    String[] find(String word) throws IOException;                       // {headword, text} or null
    ArrayList<String> similar(String word, int max) throws IOException;
}
