package org.nookpoint.reader;

class Para {
    final String text; final boolean head; final int offset;
    Para(String text, boolean head, int offset) { this.text = text; this.head = head; this.offset = offset; }
}

class Line {
    final String text; final int offset; final boolean head, justify; final int indent, width;
    Line(String text, int offset, boolean head, boolean justify, int indent, int width) {
        this.text = text; this.offset = offset; this.head = head; this.justify = justify;
        this.indent = indent; this.width = width;
    }
}

class TocItem {
    final String title; final int chapter;
    TocItem(String title, int chapter) { this.title = title; this.chapter = chapter; }
}
