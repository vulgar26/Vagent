package com.vagent.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

/**
 * Java {@link String}（规范小写带连字符 UUID）与 JDBC {@code UUID} 列（PostgreSQL / H2）互转。
 * <p>
 * 放在 {@code com.vagent.persistence.typehandler}，<strong>不要</strong>列入
 * {@code mybatis-plus.type-handlers-package}，否则会被全局注册，误处理普通 {@link String} 参数（如 {@code username}）。
 * 仅在实体 {@code @TableField(typeHandler = …)} 上显式引用。
 */
public class UuidStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parseUuid(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toCanonicalString(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toCanonicalString(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toCanonicalString(cs.getObject(columnIndex));
    }

    static UUID parseUuid(String raw) throws SQLException {
        if (raw == null) {
            throw new SQLException("uuid string is null");
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw new SQLException("uuid string is blank");
        }
        if (s.length() == 32 && s.indexOf('-') < 0) {
            s = s.substring(0, 8)
                    + "-"
                    + s.substring(8, 12)
                    + "-"
                    + s.substring(12, 16)
                    + "-"
                    + s.substring(16, 20)
                    + "-"
                    + s.substring(20);
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new SQLException("invalid uuid: " + raw, e);
        }
    }

    private static String toCanonicalString(Object v) throws SQLException {
        if (v == null) {
            return null;
        }
        if (v instanceof UUID u) {
            return u.toString().toLowerCase(Locale.ROOT);
        }
        return parseUuid(v.toString()).toString().toLowerCase(Locale.ROOT);
    }
}
