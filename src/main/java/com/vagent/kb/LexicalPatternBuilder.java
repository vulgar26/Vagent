package com.vagent.kb;

/**
 * 为 P1-0b 关键词通道构造 ILIKE 模式串，并做最小转义，避免 {@code %} / {@code _} 注入扩大匹配。
 */
final class LexicalPatternBuilder {

    private LexicalPatternBuilder() {}

    /**
     * @param query 原始查询
     * @param maxChars 模式最大长度（防止异常长 query）
     */
    static String buildContainsPattern(String query, int maxChars) {
        if (query == null) {
            return null;
        }
        String t = query.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > maxChars) {
            t = t.substring(0, maxChars);
        }
        return "%" + escapeLike(t) + "%";
    }

    static String escapeLike(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
