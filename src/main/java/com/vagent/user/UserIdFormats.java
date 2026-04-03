package com.vagent.user;

import java.util.UUID;

/**
 * 用户主键在库中存为 32 位十六进制（无连字符，与 MyBatis-Plus {@code ASSIGN_UUID} 一致；PostgreSQL {@code CHAR(36)}）；
 * Java {@link UUID#toString()} 带连字符，查询前需归一化。
 */
public final class UserIdFormats {

    private UserIdFormats() {
    }

    /** 与数据库 {@code users.id} / {@code conversations.user_id} 一致的字符串形式。 */
    public static String compact(UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
