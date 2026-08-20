package cn.interview;

import java.util.Map;

final class Skills {
    static final Map<String, String> ALL = Map.ofEntries(
        Map.entry("Java 后端", "Java集合与并发、JVM、Spring事务、数据库、系统设计"),
        Map.entry("AI Agent", "Prompt、Tool Calling、记忆、任务规划、评估与安全"),
        Map.entry("RAG 工程", "文档切分、Embedding、召回、重排、查询改写、引用与评估"),
        Map.entry("前端开发", "JavaScript、React、浏览器渲染、性能、可访问性"),
        Map.entry("Python 开发", "Python数据模型、异步、测试、Web框架与性能"),
        Map.entry("数据工程", "SQL、ETL、数据质量、批处理与流处理"),
        Map.entry("算法工程", "复杂度、数据结构、机器学习、模型评估"),
        Map.entry("数据库", "索引、事务、MVCC、锁、查询计划和分库分表"),
        Map.entry("云原生", "Docker、Kubernetes、可观测性、服务弹性与部署"),
        Map.entry("测试开发", "测试设计、自动化、集成测试、压测与质量门禁"),
        Map.entry("产品经理", "用户需求、指标、方案取舍、实验与沟通"),
        Map.entry("系统设计", "容量估算、缓存、一致性、限流、队列与容灾")
    );
}
