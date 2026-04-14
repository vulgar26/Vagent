package com.vagent.mybatis;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Locale;
import java.util.UUID;

/** MyBatis-Plus：{@link IdentifierGenerator}（{@code IdType.ASSIGN_UUID} 生成规范小写 UUID 字符串）。 */
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
