package com.vagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring 上下文加载冒烟测试。
 * <p>
 * <b>这个测试是干什么的：</b>
 * 启动与生产类似的 Spring 容器，若配置错误、Bean 冲突、缺少依赖，会在 {@code mvn test} 阶段失败，避免「本地 main 能跑、CI 挂」。
 * <p>
 * <b>为什么是空方法：</b>
 * {@code @SpringBootTest} 本身就会尝试刷新上下文；{@code contextLoads} 仅作显式占位，表示「能起来就算过」。
 * <p>
 * <b>后续可加强：</b>
 * 对 {@link com.vagent.llm.LlmClient} 做 mock 注入断言、或对 /actuator/health 做 MockMvc 测试等。
 */
@SpringBootTest
@ActiveProfiles("test")
class VagentApplicationTests {

    @Test
    void contextLoads() {
        // 故意留空：见类注释
    }
}
