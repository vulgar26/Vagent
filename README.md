# Vagent

基于 Java 的企业风格对话式 RAG 系统（规划中）。当前仓库包含 **M0**：Spring Boot 可运行骨架、Actuator 健康检查、可替换的 LLM 接口（`noop` 占位实现）。

详细设计说明见：[docs/M0-实现说明.md](docs/M0-实现说明.md)。

## 环境

- JDK 17+
- Maven 3.8+

## 构建与测试

```bash
mvn test
```

## 运行

```bash
mvn spring-boot:run
```

健康检查：<http://localhost:8080/actuator/health>（端口以 `application.yml` 为准）。

## 许可证

（待补充）
