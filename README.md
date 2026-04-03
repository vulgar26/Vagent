# Vagent

基于 Java 的企业风格对话式 RAG 系统（规划中）。

| 阶段 | 内容 |
|------|------|
| **M0** | Spring Boot 可运行骨架、Actuator、可替换 LLM 接口（`noop`） |
| **M1** | 用户注册/登录（JWT）、会话 API；**PostgreSQL + MyBatis-Plus**；测试使用 H2（无需本机 PG） |

- [docs/M0-实现说明.md](docs/M0-实现说明.md)（含 LLM 模块流程图与表）
- [docs/M1-实现说明.md](docs/M1-实现说明.md)（含 M1 各模块图与表）

## 环境

- JDK 17+
- Maven 3.8+
- **本地运行**：PostgreSQL 14+（或兼容版本），已创建库与用户（与 `application.yml` 一致）

### PostgreSQL 准备示例

```sql
CREATE USER vagent WITH PASSWORD 'vagent';
CREATE DATABASE vagent OWNER vagent ENCODING 'UTF8';
```

若库已存在、用户非 owner，需对 `public` schema 授权（示例）：

```sql
\c vagent
GRANT ALL ON SCHEMA public TO vagent;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO vagent;
```

启动应用时会执行 `src/main/resources/schema.sql` 建表（`spring.sql.init.mode=always`）；**生产建议改为迁移工具并关闭 always**。

**M2 pgvector**：在同一库执行 `CREATE EXTENSION IF NOT EXISTS vector;` 后再建向量表（详见后续里程碑文档）。

## 构建与测试

```bash
mvn test
```

测试使用 **Spring profile `test`** + 内存 H2（`MODE=PostgreSQL`），不连接本机 PostgreSQL。

## 运行

```bash
mvn spring-boot:run
```

健康检查：<http://localhost:8080/actuator/health>（端口以 `application.yml` 为准）。

### M1 API 速览（默认 `http://localhost:8080`）

- `POST /api/v1/auth/register` — JSON：`{"username":"...","password":"..."}`（密码 ≥8 位）
- `POST /api/v1/auth/login` — 同上
- `GET /api/v1/conversations` — Header：`Authorization: Bearer <token>`
- `POST /api/v1/conversations` — 可选 body：`{"title":"..."}`

`application-pg.yml` 为可选 profile，用于覆盖数据源 URL（如 Docker 服务名）；默认连接已在 `application.yml` 中配置。

## 许可证

（待补充）
