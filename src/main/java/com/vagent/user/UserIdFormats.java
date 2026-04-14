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
     * 解析 API / 路径中的 UUID 字符串（支持 32 位无连字符与标准形式）。
     *
     * @throws IllegalArgumentException 无法解析
     */
    public static UUID parseUuid(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("uuid string is null");
        }
        String subject = raw.trim();
        if (subject.isEmpty()) {
            throw new IllegalArgumentException("uuid string is blank");
        }
        try {
            if (subject.length() == 32 && subject.indexOf('-') < 0) {
                String s = subject.toLowerCase(Locale.ROOT);
                return UUID.fromString(
                        s.substring(0, 8)
                                + "-"
                                + s.substring(8, 12)
                                + "-"
                                + s.substring(12, 16)
                                + "-"
                                + s.substring(16, 20)
                                + "-"
                                + s.substring(20));
            }
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid uuid: " + raw, e);
        }
    }

    /**
     * @deprecated 使用 {@link #canonical(UUID)}；旧名曾对应 32 位无连字符形式。
     */
    @Deprecated
    public static String compact(UUID uuid) {
        return canonical(uuid);
    }
}
