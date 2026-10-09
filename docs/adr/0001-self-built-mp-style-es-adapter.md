---
status: accepted
---

# 自研 MyBatis-Plus 风格 ES 适配层，不采用 Easy-ES

项目的诉求是「用 MyBatis-Plus 的体验操作 Elasticsearch」。Easy-ES（dromara，v3.0.2）可直接满足该诉求，但我们决定自研适配层，并同步排除「MP + MySQL 主库 + ES 搜索层」的双写架构。

## Considered Options

- **Easy-ES 3.0.2**：现成、API 与 MP 一致；但底层依赖已弃用的 `RestHighLevelClient` 7.17.8，对 ES 9 无兼容承诺，且框架演进不受我们控制。
- **MP + MySQL 双写 / CDC 同步**：引入数据一致性这一整类问题，且 MySQL 成为绕不开的强依赖。
- **真·MyBatis 管线适配**：见 ADR-0002，因复杂度被否。

## Consequences

- 底层客户端由我们自选（见 ADR-0003），不受第三方框架的客户端选型拖累。
- 需要自行实现 MP 体验所覆盖的注解、Wrapper、分页等基础设施，工作量实打实落在本项目。
