package com.example.ticketing.ai.query;

import java.text.Normalizer;

final class Text {

    private Text() { }

    /** "Đang mở" -> "Dang mo". */
    static String fold(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }
}
