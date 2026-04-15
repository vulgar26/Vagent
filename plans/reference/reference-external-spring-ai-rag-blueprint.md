## 说明

下文为**外部对话中提供的「Java + Spring AI + pgvector + Redis 企业级 RAG」方案摘录**，仅作**参考模板 / 升级清单**，**不是**本仓库 Vagent 的实现说明。

与当前作品集的关系：**`D:\Projects\travel-ai-planner` 已实现其中大部分技术选型**（Spring AI Alibaba、通义、pgvector、Redis、JWT、SSE、Flyway、Docker 等）。建议将其作为 **travel-ai 的功能缺口清单**，而不是在 Vagent 内再复制一套同质项目。

---

## 《Java + Spring AI + pgvector + Redis 企业级 RAG 项目》参考方案（摘录）

### 项目名称（简历可用）

AI 智能文档问答系统（RAG 增强检索生成）

### 一、项目定位

- 基于 RAG（Retrieval-Augmented Generation） 架构
- 面向企业/个人的私有文档智能问答
- 支持 PDF / TXT / MD 等多格式文件上传解析
- 解决大模型幻觉、知识过时、隐私泄露问题

### 二、核心技术栈（非常加分）

- 后端：Spring Boot 3.x
- AI 框架：Spring AI（接入通义千问 / 文心一言 / Claude 等）
- 向量数据库：PostgreSQL + pgvector
- 缓存：Redis（会话、向量缓存、热点结果）
- 文档解析：Apache PDFBox、OpenPDF
- 文本处理：分词、文本分块、Embedding 向量化
- 检索：向量检索 + BM25 关键词混合检索 + Rerank 重排
- 部署：Docker + Docker Compose
- 接口文档：Swagger/Knife4j

### 三、功能模块（照着做就完整）

1. **用户与会话模块**：用户登录/注册；多轮对话会话；会话新建、删除、重命名。
2. **文档管理模块**：文档上传（PDF/TXT/MD）；列表、删除；后台异步解析、分块、向量化、入库；解析进度展示。
3. **RAG 核心引擎**：文本分块；向量化；向量存储；混合检索（向量 + BM25 + 融合 + Rerank）；Prompt 上下文；调用大模型生成。
4. **问答交互模块**：问题输入；SSE 流式；显示参考来源；点赞、点踩、反馈。
5. **后台管理与监控**：文档统计、问答日志；接口耗时；异常与日志。

### 四、核心亮点（面试可背）

1. Spring AI 原生集成，结构优雅，便于扩展多模型  
2. 混合检索 + 重排，提升召回准确率  
3. Redis 缓存会话与向量，降本提速  
4. 异步文档处理，不阻塞前端  
5. SSE 流式回答  
6. Docker 一键部署  
7. 严格基于文档回答，抑制幻觉  

### 五、简历成果描述（可直接改写后使用）

基于 Spring Boot + Spring AI + pgvector 实现企业级私有文档 RAG 问答系统，支持多格式文档解析、文本分块、向量化存储与混合检索，通过向量检索 + BM25 + 重排机制提升知识召回精度，结合 SSE 流式输出实现类 ChatGPT 交互体验，使用 Redis 做会话与缓存优化，Docker 容器化部署，有效解决大模型幻觉、知识滞后与数据隐私问题。

### 六、做到什么程度算「非常好」

- 能正常跑通全流程  
- 回答不胡说，有来源依据  
- 界面简洁可用  
- 代码结构清晰，分层明确  
- 能部署、能演示、能讲清楚原理  

---

## 与 travel-ai 的对照用法（建议）

打开 travel-ai 的 `docs/UPGRADE_PLAN.md`、`docs/eval.md`，将本摘录中 **尚未实现** 的条目（如 BM25、Rerank、点赞点踩、Knife4j）勾选为下一阶段；避免与 Vagent 重复建设同一套「通用文档问答」叙事。
