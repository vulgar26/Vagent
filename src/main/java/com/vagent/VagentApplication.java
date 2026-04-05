package com.vagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Vagent 应用的 Spring Boot 启动入口。
 * <p>
 * <b>这个文件是干什么的：</b>
 * 提供 {@code main} 方法，启动内嵌 Web 容器（默认 Tomcat），扫描 {@code com.vagent} 包下的组件并装配成可运行服务。
 * <p>
 * <b>为什么需要它：</b>
 * 可部署、可健康检查、后续要接 SSE/数据库/pgvector。Spring Boot 统一了配置、依赖注入、Actuator 等，
 * 后续控制器、服务、配置类都通过容器管理，避免手写工厂与生命周期。
 * <p>
 * <b>M1 补充：</b> {@link MapperScan} 扫描 {@code com.vagent} 包下的 {@code *Mapper} 接口；
 * 排除 {@link UserDetailsServiceAutoConfiguration}，认证走 JWT + {@link com.vagent.security.JwtAuthenticationFilter}。
 * <p>
 * <b>M2 补充：</b> 知识库（pgvector）与 {@link com.vagent.embedding.EmbeddingClient} 由配置装配；测试 profile 仅加载 {@code schema-core.sql}，向量表用 Testcontainers 集成测试覆盖。
 * <p>
 * <b>M3 补充：</b> {@link com.vagent.chat} 提供 SSE 流式对话与任务取消；{@link com.vagent.llm.LlmClient} 仍按配置选择实现（{@code noop} / {@code fake-stream}）。
 * <p>
 */
@MapperScan("com.vagent")
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class VagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(VagentApplication.class, args);
    }
}
