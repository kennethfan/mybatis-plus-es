# mybatis-plus-es

以 **MyBatis-Plus 风格 API 操作 Elasticsearch** 的适配层：沿用 MP 的 Mapper / Wrapper 写法，底层直连 Elasticsearch 官方 Java API Client，**不经过任何 MyBatis 机制**（ADR-0002）。

## 架构决策（docs/adr/）

| ADR | 决策 |
|---|---|
| [0001](docs/adr/0001-self-built-mp-style-es-adapter.md) | 自研适配层，不采用 Easy-ES / 双写 |
| [0002](docs/adr/0002-api-alignment-not-mybatis-pipeline.md) | 只对齐 API 层，不进 MyBatis 执行管线 |
| [0003](docs/adr/0003-elasticsearch-java-api-client-8.md) | ES Java API Client 8.x，锚定 ES 8.x |
| [0004](docs/adr/0004-reuse-mp-annotations-as-mapping-source.md) | 复用 MP 注解作为映射来源 |

术语表见 [CONTEXT.md](CONTEXT.md)。

## 模块

- **mp-es-core** — 适配层核心库（库即 starter，依赖即生效）
- **mp-es-sample** — 示例应用（Product 实体 + 集成测试）

## 快速上手

### 1. 启动 ES

```bash
docker compose up -d        # ES 8.19 单节点 + Kibana（:5601），已关闭安全认证
```

### 2. 引入依赖

```java
@SpringBootApplication
@EsMapperScan("com.example.mapper")   // 唯一必需注解，对齐 @MapperScan 体验
public class App { ... }
```

### 3. 声明实体（MP 注解即 ES 映射）

```java
@Data
@TableName("mpes_product")                 // 索引名
public class Product {
    @TableId                               // 文档 _id
    private Long id;

    @TableField("product_name")            // ES 字段名（缺省为属性名原样）
    private String productName;            // String → keyword（默认）

    @EsText(analyzer = "standard")         // 覆盖为 text 分词字段
    private String description;

    private BigDecimal price;              // → double
    private Integer stock;                 // → integer
    private LocalDate launchDate;          // → date
    private Boolean onSale;                // → boolean
}

public interface ProductMapper extends EsBaseMapper<Product> {}
```

### 4. 配置（mp-es.*）

```yaml
mp-es:
  uris: [http://localhost:9200]
  # username / password: 可选 Basic 认证
  index:
    auto-create: true          # 启动时 create-if-absent（Index 托管）
    mismatch-policy: WARN      # mapping 不一致：WARN（默认）/ FAIL
```

### 5. 使用（与 MP 写法一致）

```java
// CRUD
mapper.insert(product);
mapper.updateById(patch);            // 非 null 字段部分更新
mapper.deleteById(id);
mapper.selectById(id);

// 条件查询（like=keyword 通配；match=显式分词；AND 优先于 OR）
List<Product> list = mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .eq(Product::getOnSale, true)
        .gt(Product::getPrice, new BigDecimal("200"))
        .like(Product::getProductName, "键盘")
        .match(Product::getDescription, "静音")
        .orderByDesc(Product::getPrice));

// 分页（from+size，窗口上限 10000）
Page<Product> page = mapper.selectPage(new Page<>(1, 10), wrapper);
```

## 语义与边界（一期）

- **like** 仅作用于 keyword 字段（`*v*` 通配）；text 字段使用 eq/ne/in/like 直接报错，请用 **match**
- **AND 优先级高于 OR**：`eq(1).or().eq(2).eq(3)` → `(1 OR 2) AND 3`；嵌套分组用 `and(w -> ...) / or(w -> ...)`
- 分页为 `from+size`，超出 10000 抛异常；深分页 search_after 留二期
- `selectList` 全量上限 1000 条
- 主键（@TableId）同时作为 ES 文档 `_id` 与 `_source` 字段
- ES 字段名默认 = 属性名原样（camelCase），无隐式下划线转换
- 类型推导：String→keyword / Long→long / BigDecimal→double / LocalDate 等→date / 枚举→keyword；不支持的字段类型用 `@TableField(exist = false)` 排除

## 构建

```bash
mvn clean verify            # 单元测试无需 ES；集成测试在 ES 未启动时自动跳过
```
