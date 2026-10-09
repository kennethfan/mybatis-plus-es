---
status: accepted
---

# API 对齐而不进入 MyBatis 执行管线

「把 MP 翻译成 ES」有深浅两种挂钩方式。我们选择**只对齐 API 层**：自研 `EsBaseMapper` / `EsLambdaQueryWrapper`，方法签名与链式写法对齐 MP，底层直连 Elasticsearch 客户端；SqlSession、`StatementHandler`、`Executor` 等 MyBatis / MP 内核机制完全不参与。

## Considered Options

- **MyBatis 插件式**（自定义 StatementHandler/Executor，mapper 中写 DSL 当 "SQL"）：要对抗内核「SQL over JDBC」的全套假设，复杂度高出一个数量级。
- **SQL 拦截翻译**（MP 跑真 SQL 再翻译成 DSL）：需要伪装 JDBC 层，SQL→DSL 语义损耗不可控。
- **反射/字节码替换 MP 内部绑定**：深度耦合 MP 具体版本，升级即碎。

## Consequences

- 用户体验目标（「像写 MP 一样写 ES」）由 API 签名对齐达成，与是否真的经过 MyBatis 管线无关。
- 不引入 mybatis / mybatis-plus 的**执行机制**（SqlSession、Executor 等一概不参与）；但由 ADR-0004 复用 MP 注解派生出一个例外：MP 注解默认值引用 `org.apache.ibatis.type.JdbcType`，注解解析要求 mybatis jar 在 classpath，故 `org.mybatis:mybatis` 以普通依赖存在——它只是注解类型解析的载荷，无任何执行路径。
