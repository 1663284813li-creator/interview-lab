# Interview Lab · AI 智能面试官平台

围绕简历中的 AI 智能面试官项目实现的全栈学习项目：Java 21、Spring Boot 4.0.8、Spring AI 2.0.1、PostgreSQL 17 + pgvector、Redis Stream、Apache Tika，前端为无构建依赖的响应式页面。

## 启动完整服务

需要 Docker Desktop（Linux containers）和可用的大模型 API。聊天与 Embedding 是两个能力，兼容 OpenAI 的服务不一定同时支持它们。

```powershell
Copy-Item .env.example .env
# 编辑 .env：填写 AI_API_KEY、DB_PASSWORD；按服务商修改模型名和地址
docker compose up --build -d
docker compose logs -f app
```

访问 http://localhost:8080 。首次打开自动生成私人空间的访问凭证，仅在此浏览器保存。刷新页面保留历史。清除浏览器存储会失去凭证；这是个人演示用的匿名凭证机制，未实现手机号登录或账号恢复。

默认聊天模型为 `qwen-plus`，embedding 模型为 `text-embedding-v4`，通过 dimensions 参数输出 1024 维。使用其他服务商时，必须确认实际响应为 1024 维，否则会明确失败，不能混用不同模型生成的向量。Spring AI 2.0 使用新版 SDK，base URL 填写完整的 API 前缀，如 `https://dashscope.aliyuncs.com/compatible-mode/v1`，不用自行添加 chat/completions。

如果终端不在项目目录，使用下面的启动脚本，它会自动定位 compose.yaml，避免“no configuration file provided”错误：

```powershell
& '<项目绝对路径>\scripts\start.ps1'
```

已生成最新 JAR 后，可加 `-LocalJar` 使用快速调试镜像。完整源码构建不加该参数。连通性诊断为 `node scripts/check-ai.mjs`，它使用少量合成文本验证聊天和1024维向量，不输出密钥，结果保存到被忽略的 `tmp/ai-check.json`。

本机未装 Docker 时可预览前端：`npm run preview`，访问 http://localhost:4173。该预览明确提示后端未连接，不生成虚假 AI 回答；完整功能仍需要上述 Docker 服务。

## 功能与体验

- 12 个 Skill 面试方向，初中高级难度，可关联已完成的简历分析；一次一道题，回答后反馈与追问。
- POST + SSE 对话，具名 sources / delta / done / error 事件；前端处理分片与 CRLF。为避免密钥跨 token 泄露，后端按完整句子脱敏后推送，故首个字符可能稍迟。
- 面试会话、成功的问答与结构化报告持久化；取消或失败的本轮不写入历史。报告包含分数、亮点、改善建议与学习计划。
- 简历分析、文档解析、知识库向量化走 Redis Stream 异步消费；上传完成后立即入队，任务会显示正文提取、OCR、模型分析等阶段。支持 PDF/DOCX/TXT/MD，最大5MB/60000字符，PDF最多10页。PDF使用PDFBox提取文字，无文字的页面使用容器内Tesseract中英文OCR；其他格式用Tika解析。任务完成或终态失败后清理原文件字节。
- 知识库多库隔离，TokenTextSplitter 切分、1024 维 embedding、HNSW 索引；Query Rewrite、多轮历史和随问题长度变化的 Top-K/阈值；回答展示可展开的资料来源。知识库聊天上下文在页面内保留，刷新后清空。
- UUID 用户隔离 + 随机 Bearer 凭证（数据库只存 SHA-256）+ 参数化 SQL。三层防护是 Guard 模式校验 + Spring AI SafeGuardAdvisor 关键词前置拦截、不可信资料与系统指令分离、自定义 OutputGuardAdvisor 对同步调用脱敏及 SSE 句子缓冲脱敏，
- Redis Lua 原子固定窗口限流，每身份每分钟 60 请求；不是滑动窗口。依赖 Redis 不可用时返回 503。

## 验证

```shell
mvn verify
npm test
```

JUnit 验证输入拦截、脱敏、向量维度和用户隔离；Node 测试 SSE 分片解析。Docker 构建会执行 Maven 校验。完整服务启动后可执行 `node scripts/e2e.mjs`，使用合成资料验收真实模型、数据库与队列；会产生少量模型调用费用与独立测试用户的数据。已完成的实测记录见 `docs/verification.md`。

手动验收顺序：创建知识库 → 上传文档 → 等任务 DONE → 提问并检查来源 → 提交简历 → 等分析完成 → 关联简历并开始面试 → 作答 → 生成报告 → 在训练记录恢复/查看。用两个浏览器分别创建用户，确认无法读取对方的 job、kb、interview；用另一用户凭证访问已知 UUID 应返回 404。

## 项目结构

```text
src/main/java/cn/interview/
  Api.java                 上传、任务、知识库及历史接口
  ConversationApi.java     面试、结构化评估、RAG SSE
  RagService.java          分块、向量化、改写和检索
  DocumentExtractor.java   PDF文字提取、本地扫描页OCR和文件校验
  Jobs.java                outbox + Redis Stream 消费与重试
  AuthFilter.java          Bearer 认证与用户身份
  RateLimitFilter.java     Redis Lua 固定窗口限流
  Guard.java               输入/输出防护
  Skills.java              12 种面试方向
src/main/resources/
  schema.sql               数据表和索引
  static/                  完整操作界面
docs/                      架构与简历要求对照
```

HNSW 索引已创建，但包含用户/知识库过滤时 PostgreSQL 可能选用 scope 索引和精确排序；
