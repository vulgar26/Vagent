package com.vagent.mybatis.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import java.util.UUID;

/**
 * {@link UUID} 与 PostgreSQL {@code uuid} 列映射。
 * <p>
 * 仅放在本包供 {@code mybatis-plus.type-handlers-package} 扫描；勿与 {@code UuidStringTypeHandler}（String）混扫。
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class UuidNativeTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUuid(rs.getObject(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUuid(rs.getObject(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUuid(cs.getObject(columnIndex));
    }

    private static UUID toUuid(Object v) throws SQLException {
        if (v == null) {
            return null;
        }
        if (v instanceof UUID u) {
            return u;
        }
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new SQLException("invalid uuid column value: " + v, e);
        }
    }
}
