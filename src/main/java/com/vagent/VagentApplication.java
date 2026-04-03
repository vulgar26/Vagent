package com.vagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 */
@SpringBootApplication
public class VagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(VagentApplication.class, args);
    }
}
