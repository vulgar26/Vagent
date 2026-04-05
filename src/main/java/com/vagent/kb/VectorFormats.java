package com.vagent.kb;

/**
 * pgvector 字面量与 Java 数组互操作（供 SQL {@code CAST(... AS vector)} 使用）。
 */
public final class VectorFormats {

    private VectorFormats() {
    }

    /** 生成 PostgreSQL 可识别的 {@code '[a,b,...]'} 字面量。 */
    public static String toPgVectorLiteral(float[] v) {
        if (v == null || v.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
