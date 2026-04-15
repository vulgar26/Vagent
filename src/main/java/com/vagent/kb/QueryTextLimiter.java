package com.vagent.kb;

/**
 * 限制全文检索 query 长度，避免异常长输入与 tsquery 解析压力。
 */
final class QueryTextLimiter {

    private QueryTextLimiter() {}

    static String trimAndLimit(String query, int maxChars) {
        if (query == null) {
            return "";
        }
        String t = query.trim();
        if (t.length() > maxChars) {
            return t.substring(0, maxChars);
        }
        return t;
    }
}
