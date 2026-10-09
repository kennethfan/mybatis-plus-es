# mybatis-plus-es

以 **MyBatis-Plus 风格 API 操作 Elasticsearch** 的适配层：沿用 MP 的 Mapper / Wrapper 写法，底层直连 Elasticsearch 官方 Java API Client，**不经过任何 MyBatis 执行机制**（ADR-0002）。

- 构建状态：`mvn clean verify` 全绿（单元测试 6/6 + 集成测试 10/10 @ 真实 ES 8.19.0）
- 版本基线：Spring Boot 3.5.16 / elasticsearch-java 8.19.23 / mybatis-plus-annotation 3.5.17 / JDK 17

## 架构决策（docs/adr/）

| ADR | 决策 |
|---|---|
| [0001](docs/adr/0001-self-built-mp-style-es-adapter.md) | 自研适配层，不采用 Easy-ES / 双写 |
| [0002](docs/adr/0002-api-alignment-not-mybatis-pipeline.md) | 只对齐 API 层，不进 MyBatis 执行管线 |
| [0003](docs/adr/0003-elasticsearch-java-api-client-8.md) | ES Java API Client 8.x，锚定 ES 8.x |
| [0004](docs/adr/0004-reuse-mp-annotations-as-mapping-source.md) | 复用 MP 注解作为映射来源 |

术语表见 [CONTEXT.md](CONTEXT.md)。

## 模块与结构

```
mybatis-plus-es/
├── mp-es-core/                      # 适配层核心库（库即 starter，依赖即生效）
│   └── io.github.kennethfan.mpes
│       ├── annotation/              # @EsText（text 分词）、@EsGeoPoint（坐标校验）、@EsNested（子文档）、@EsMapperScan
│       ├── config/                  # EsAutoConfiguration / EsProperties / Mapper 扫描注册
│       ├── core/                    # EsBaseMapper 接口 + EsMapperProxy 执行引擎（JDK 动态代理）
│       ├── geo/                     # GeoPoint 值对象（lat/lon，序列化为 ES geo_point 原生格式）
│       ├── index/                   # IndexManager：启动时 create-if-absent + mapping 校验
│       ├── metadata/                # MP 注解解析 → 实体元数据（nested 递归解析）；Java 类型 → ES 类型推导
│       ├── page/                    # Page（API 对齐 MP）+ EsAfter/EsAfterResult（search_after 游标）
│       ├── support/                 # LambdaUtils / MismatchPolicy / EsOpsException
│       └── wrapper/                 # EsLambdaQueryWrapper + ES DSL 翻译器
├── mp-es-sample/                    # 示例应用（Product 实体 + 集成测试）
│   └── docker/                      # 本地环境：docker-compose.yml + kibana.yml（仅 sample 测试需要）
└── docs/adr/                        # 架构决策记录
```

## 快速上手

### 1. 启动 ES（仅 sample 需要）

```bash
docker compose -f mp-es-sample/docker/docker-compose.yml up -d
# ES 8.19 单节点（:9200，数据持久化到命名卷 mpes-es-data）
# + Kibana（:5601，配置见 mp-es-sample/docker/kibana.yml，已关闭安全认证）
```

### 2. 引入依赖

```java
@SpringBootApplication
@EsMapperScan("com.example.mapper")   // 唯一必需注解，对齐 @MapperScan 体验
public class App { ... }
```

> ⚠️ **依赖注意**：`mybatis-plus-annotation` 将 `org.mybatis:mybatis` 声明为 `optional`，但其注解默认值引用了 `JdbcType`——运行时解析注解必须能加载该类。mp-es-core 已显式引入 `org.mybatis:mybatis 3.5.19`，**仅作为注解解析载荷，无任何 MyBatis 执行路径**（ADR-0002/0004 已记录此例外）。

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

    @EsGeoPoint                            // GeoPoint → geo_point（注解仅做类型校验）
    private GeoPoint location;

    @EsNested                              // nested 子文档（List<子实体> 或单个子实体）
    private List<Sku> skus;
}

@Data
public class Sku {
    @TableId
    private String skuCode;                // 子实体无需 @TableName，但需主键字段
    private String spec;                   // String → keyword
    private Integer quantity;
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

// wrapper 传 null = 全量（match_all）
Long total = mapper.selectCount(null);

// 高亮（默认 <em></em> 标签，可自定义）
List<EsHit<Product>> hits = mapper.selectHighlighted(wrapper,
        EsHighlight.of(Product::getProductName, Product::getDescription).preTag("<b>").postTag("</b>"));
// EsHit<T>: getEntity() + getHighlights()（属性名 → 片段列表）

// 聚合（terms / avg / max / min / sum / stats / cardinality；查询不返回文档）
EsAggResult result = mapper.aggregate(wrapper,
        EsAgg.terms(Product::getOnSale),
        EsAgg.avg(Product::getPrice).as("avgPrice"),
        EsAgg.max(Product::getPrice),
        EsAgg.stats(Product::getStock),
        EsAgg.cardinality(Product::getProductName));
result.buckets("onSale");      // terms 桶（key + count）
result.value("avgPrice");      // 单值（Double）
result.stats("stock");         // count/min/max/avg/sum

// 子聚合（subAgg 挂在任意聚合上，桶内取子结果）
EsAggResult sub = mapper.aggregate(null,
        EsAgg.terms(Product::getOnSale).subAgg(
                EsAgg.avg(Product::getPrice).as("avgPrice"),
                EsAgg.max(Product::getPrice).as("maxPrice")));
sub.buckets("onSale").get(0).getAggs().value("avgPrice");

// search_after 深分页（无 10000 窗口限制；游标翻页不重不漏）
EsAfterResult<Product> page1 = mapper.selectAfter(EsAfter.first(100), wrapper);
List<Product> records = page1.getRecords();
EsAfter next = page1.getNext();          // null 表示已取完
EsAfterResult<Product> page2 = mapper.selectAfter(next, wrapper);

// Geo：geo_distance 过滤 + 距离排序（仅 geo_point 字段）
List<Product> near = mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .geoDistance(Product::getLocation, "1500km", new GeoPoint(39.90, 116.40))
        .orderByGeoDistance(Product::getLocation, new GeoPoint(39.90, 116.40), true));

// Nested：子文档条件（childType 为类型见证，保证 lambda 方法引用类型正确）
List<Product> hits = mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-A1")));
```

## 语义与边界（一期）

- **like** 仅作用于 keyword 字段（`*v*` 通配）；text 字段使用 eq/ne/in/like 直接报错，请用 **match**
- **AND 优先级高于 OR**：`eq(1).or().eq(2).eq(3)` → `(1 OR 2) AND 3`；嵌套分组用 `and(w -> ...) / or(w -> ...)`
- **wrapper 可传 null**：`selectCount(null)` / `selectPage(page, null)` 即全量语义（match_all）
- 分页为 `from+size`，超出 10000 抛异常；**search_after** 无窗口限制（每批上限 10000，强制追加主键字段兜底排序保证全序，`_id` 禁止 fielddata 排序故用 `_source` 主键）
- `selectList` 全量上限 1000 条；`selectOne` 命中多条直接抛异常（不静默取首条）
- `deleteBatchIds` / `deleteByQuery` 使用 `conflicts=proceed`：删除目标刚被更新时跳过该条而非整体 409 失败
- 主键（@TableId）同时作为 ES 文档 `_id` 与 `_source` 字段
- ES 字段名默认 = 属性名原样（camelCase），无隐式下划线转换
- 类型推导：String→keyword / Long→long / BigDecimal→double / LocalDate 等→date / 枚举→keyword；不支持的字段类型用 `@TableField(exist = false)` 排除
- **BigDecimal 数值精度**：经 ES double 往返后 scale 可能变化（399.00 → 399.0），比较请用 `compareTo` 而非 `equals`
- **写入可见性**：insert/update 后默认 1s refresh 才可被 search 检索到（selectById 为 realtime get 不受影响）；测试中请手动 `client.indices().refresh(...)`
- **terms 聚合桶键**：boolean 字段的桶键为 ES 原生数值语义（true→1 / false→0）；terms 桶数默认上限 100
- **聚合命名**：未 `as()` 显式命名时，聚合名默认为属性名（非 ES 字段名）；terms 取 `buckets(name)`，单值取 `value(name)`，stats 取 `stats(name)`
- 高亮走独立方法 `selectHighlighted`（返回 `EsHit<T>`），不改动 `selectList/selectPage` 一期签名
- **子聚合**通过 `subAgg(...)` 挂载，桶内经 `bucket.getAggs()` 取子结果；布尔字段 terms 桶键为数值 1/0 同样适用于子聚合
- **Geo**：`geoDistance` / `orderByGeoDistance` 仅用于 geo_point 字段，否则直接报错；GeoPoint 值对象序列化为 `{"lat":..,"lon":..}` 原生格式
- **Nested**：`@EsNested` 字段递归解析子实体元数据（支持 `List<子实体>` 或单个子实体，循环引用直接报错）；`nested(col, Child.class, w -> ...)` 内部条件自动加全路径前缀，支持 nested 套 nested；子实体字段写入/读取双向重命名可逆
- **mapping 校验**：nested / geo_point 类型参与启动时 mapping 一致性校验（同 WARN / FAIL 策略）

## 构建

```bash
mvn clean verify            # 单元测试无需 ES；集成测试在 ES 未启动时自动跳过（socket 探测 :9200）
```

测试构成：
- **mp-es-core 单元测试**（6 个）：Lambda 属性名解析、类型推导——不依赖 ES
- **mp-es-sample 集成测试**（10 个）：CRUD 生命周期、批量读写、条件查询（eq/gt/like/match/between/or 嵌套/selectOne）、分页与越界、高亮、聚合、子聚合、search_after 深分页、Geo（geo_distance 过滤/距离排序/组合条件）、Nested（子文档条件/多条件 AND/往返还原/geo+nested 组合）——需本地 ES 运行，测试内通过 `indices().refresh` 保证写入可见性
