package com.vagent.user;

import java.util.Locale;
import java.util.UUID;

/**
 * 用户主键：PostgreSQL 列为 {@code uuid}；应用内与 JWT {@code sub} 使用<strong>规范字符串</strong>（小写 + 连字符）。
 */
public final class UserIdFormats {

    private UserIdFormats() {
    }

    /** 与数据库 {@code uuid} 列及 JWT {@code sub} 一致的字符串形式。 */
    public static String canonical(UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * @deprecated 使用 {@link #canonical(UUID)}；旧名曾对应 32 位无连字符形式。
     */
    @Deprecated
    public static String compact(UUID uuid) {
        return canonical(uuid);
    }
}
