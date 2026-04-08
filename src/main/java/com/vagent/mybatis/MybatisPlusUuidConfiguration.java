package com.vagent.mybatis;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Locale;
import java.util.UUID;

/**
 * 主键使用与 PostgreSQL {@code uuid} 列一致的规范字符串（小写 + 连字符），与 JWT {@code sub} 对齐。
 */
@Configuration
public class MybatisPlusUuidConfiguration {

    @Bean
    @Primary
    public IdentifierGenerator identifierGenerator() {
        return new IdentifierGenerator() {
            @Override
            public Number nextId(Object entity) {
                return null;
            }

            @Override
            public String nextUUID(Object entity) {
                return UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
            }
        };
    }
}
