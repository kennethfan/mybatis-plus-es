---
status: accepted
---

# 映射来源复用 MyBatis-Plus 注解

实体的索引/字段映射来源复用 MP 注解（`@TableName` → 索引名，`@TableField` → 字段），而非自研注解体系。动机：同一实体类可同时服务 MySQL 与 ES 两种存储，避免同一字段在两套注解里重复声明。

## Considered Options

- **自研注解**（`@EsDocument` / `@EsField`）：自主可控、无外部依赖；但与 MP 双存储场景下字段需声明两遍。
- **外部管理 mapping**（ES 侧人工维护）：灵活，但丢失「表结构托管」体验，实体与索引结构易漂移。

## Consequences

- 引入 MP 注解依赖；为遵守 ADR-0002（不进 MyBatis 管线），依赖限定为纯注解模块 `mybatis-plus-annotation`，不引入 MyBatis 内核。
- MP 注解不携带 ES 类型语义（keyword / text / analyzer 等），ES 特有信息的表达方式另见后续决策。
