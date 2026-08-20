# 验证记录

验证环境：Windows，使用本机 JetBrains JBR 25 运行 Maven，以 Java 21 release 编译；目标运行环境仍为 Docker Java 21。

- Maven verify：编译、JUnit测试、Spring Boot可执行JAR打包通过。
- JUnit：输入覆盖拦截、联系方式和密钥脱敏、输入长度、1024维约束、知识库与面试用户隔离、OutputGuardAdvisor调用链脱敏。
- Node：SSE换行分片、CRLF、中文分片、心跳与多行data测试通过。
- Edge无头浏览器：12方向渲染、方向选择、四个页面切换、390px手机宽度无横向溢出；桌面和手机截图已目视检查。

2026-10-05 后续调试：Docker Desktop 已运行。以用户配置的兼容接口实测 `qwen-plus` 聊天和 `text-embedding-v4`（1024维），均成功；平台运行在 Docker Java 21。

真实端到端验收已通过8组检查：健康状态、身份认证与12个Skill、Redis Stream消费与向量入库、查询改写与带来源SSE回答、异步简历分析、关联简历多轮面试及结构化报告、数据库历史持久化、跨用户任务/知识库/面试隔离。测试只使用合成资料，不上传私人简历。结果在 `tmp/e2e-results.json`。

调试同时修复消费组已存在时包装异常中的 BUSYGROUP 识别，防止应用重启后消费者无法继续工作。重启后用 `node scripts/recovery-check.mjs` 验证已有消费组继续处理新文档，结果在 `tmp/recovery-check.json`。

**性能压测和检索质量评测仍未执行**，不能据此宣称已达到200ms、99%拦截率等指标。

## PDF分析无响应修复

复现合成扫描PDF：旧接口返回400“未提取到正文”，未创建任务；前端只在页面顶部提示，容易被误认为没有回应。

修复后：上传文件立即创建后台任务；PDFBox提取文字，缺少文字的页在容器内进行Tesseract中英文OCR。任务显示提取/OCR/千问分析阶段，自动刷新；损坏PDF进入FAILED并显示操作建议，不反复重试。提交按钮旁也显示接收/成功/错误提示。未变化的任务列表不会反复重绘，避免阅读结果时滚动被重置。

- Maven verify通过10项JUnit测试；新增文字PDF、损坏PDF与超页数验证。
- 浏览器上传合成扫描PDF：任务创建提示出现后，无需手动刷新便自动显示完整千问分析结果（本次2308字符）。
- 浏览器上传合成损坏PDF：显示“文档无法解析”失败原因。
- 数据库验证：两类终态任务的原文件字节及提取正文均已清理。
- 本轮没有把真实私人简历用于外部模型复测。复测脚本为 `scripts/analysis-ui-check.cjs`。

可执行JAR输出：`target/interview-lab-1.0.0.jar`，Docker构建会自行从源码重新打包。

UI验收脚本需要独立安装 Playwright，使用本机 Edge：

```shell
npm install --no-save playwright
node scripts/ui-check.cjs
```

测试脚本使用静态预览服务，先 `npm run preview`。它验证离线状态与界面交互，不伪造模型回答。
