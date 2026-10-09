# MyBatis-Plus ES

一个以 MyBatis-Plus 风格 API 操作 Elasticsearch 的适配层项目：开发者沿用 MP 的 Mapper / Wrapper 写法，底层落在 Elasticsearch 上，而非关系型数据库。

## Language

**适配层（Adapter）**:
以 MyBatis-Plus API 风格驱动 Elasticsearch 的代码层；只对齐 API 形态，不进入 MyBatis 执行管线。
_Avoid_: SQL 翻译、方言（dialect）、插件

**API 对齐**:
复用 MyBatis-Plus 的方法签名与使用体验，而非复用其实现机制（SqlSession / StatementHandler 均不参与）。
_Avoid_: 兼容 MyBatis、内核扩展

**EsBaseMapper**:
实体的泛型 CRUD 入口，方法签名与 MP 的 `BaseMapper` 一一对应。
_Avoid_: EsMapper（该词是 Easy-ES 的类名，为避免混淆而回避）

**EsLambdaQueryWrapper**:
Lambda 链式条件构造器；`like` 仅作用于 keyword 字段（通配符语义），分词检索一律使用显式 `match` 方法，两种语义不隐式互换。
_Avoid_: like 分词、模糊查询（歧义词）

**Index 托管**:
应用启动时依据实体定义校验并创建索引及其 mapping，对标 MP 的表结构托管体验；mapping 来源为 MP 注解（ADR-0004）。
_Avoid_: 自动建表（沿用于 MySQL 语境的词）

**文档（Document）**:
与实体实例一一对应的 Elasticsearch 文档；实体字段与文档字段经 MP 注解对应。
_Avoid_: 记录（record / row）
