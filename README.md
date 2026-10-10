# mybatis-plus-es

[![CI](https://github.com/kennethfan/mybatis-plus-es/actions/workflows/ci.yml/badge.svg)](https://github.com/kennethfan/mybatis-plus-es/actions/workflows/ci.yml)

以 **MyBatis-Plus 风格 API 操作 Elasticsearch** 的适配层：沿用 MP 的 Mapper / Wrapper 写法，底层直连 Elasticsearch 官方 Java API Client，**不经过任何 MyBatis 执行机制**（ADR-0002）。

📖 **在线文档**：<https://kennethfan.github.io/mybatis-plus-es/>（源码在 [website/](website/)，push 到 main 后 GitHub Actions 自动发布）

- 构建状态：`mvn clean verify` 全绿（单元测试 6/6 + 集成测试 16/16 @ 真实 ES 8.19.0）
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

// 条件删除 / 条件更新（空条件一律拒绝，防全量误删误改）
mapper.delete(new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, false));

Product patch = new Product();
patch.setPrice(new BigDecimal("500"));
mapper.update(patch, new EsLambdaQueryWrapper<Product>()     // update_by_query，在售全部改价
        .eq(Product::getOnSale, true));
mapper.updateBatchById(List.of(u1, u2));                     // bulk 按主键部分更新，每条取非 null 字段

// 条件查询（like=keyword 通配；match=显式分词；AND 优先于 OR）
List<Product> list = mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .eq(Product::getOnSale, true)
        .gt(Product::getPrice, new BigDecimal("200"))
        .like(Product::getProductName, "键盘")
        .match(Product::getDescription, "静音")
        .orderByDesc(Product::getPrice));

// 查询增强
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .multiMatch("静音", Product::getProductName, Product::getDescription)   // multi_match 跨字段
        .multiMatch(2.0f, "静音", Product::getProductName, Product::getDescription)); // 带权重
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .fuzzy(Product::getProductName, "机械键盘 K871")          // 容错（keyword，默认 AUTO 编辑距离）
        .fuzzy(Product::getProductName, "K871", 1));              // 显式最大编辑距离
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .prefix(Product::getProductName, "机械"));                // 原生前缀（keyword，优于 like("机械*")）
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .match(Product::getDescription, "键盘", 10f));            // match 带权重（or 场景调相关性）

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
- **fuzzy / prefix** 仅作用于 keyword 字段（text 使用直接报错，提示用 match）；fuzzy 默认 AUTO 编辑距离（3-5 字符容 1 级、6+ 字符容 2 级），可显式指定；prefix 为原生 prefix 查询，性能优于 `like("v*")` 通配符
- **match / multiMatch** 为打分检索：`match(col, value, boost)` 与 `multiMatch(boost, value, cols...)` 提供权重重载，用于 or 场景调整相关性排序；multiMatch 默认 best_fields；**注意 match 打在 keyword 字段上为整词匹配**（keyword 无分词），前缀场景请用 prefix、包含场景请用 like
- **AND 优先级高于 OR**：`eq(1).or().eq(2).eq(3)` → `(1 OR 2) AND 3`；嵌套分组用 `and(w -> ...) / or(w -> ...)`
- **wrapper 可传 null**：`selectCount(null)` / `selectPage(page, null)` 即全量语义（match_all）
- 分页为 `from+size`，超出 10000 抛异常；**search_after** 无窗口限制（每批上限 10000，强制追加主键字段兜底排序保证全序，`_id` 禁止 fielddata 排序故用 `_source` 主键）
- **limit(n) 控制返回条数**：`wrapper.limit(n)` 对 selectList / selectHighlighted 生效（上限 10000，超出提示改用 search_after）；未设置时默认取 1000 条，且**命中数超过 1000 直接报错**（不做静默截断，报错信息含总命中数）
- **terms 聚合桶数可配**：`EsAgg.terms(col).size(n)` 显式指定分桶返回条数（仅 terms 可用，默认 100）；高基数字段聚合被截桶时调大即可
- **date_histogram 时间分桶**：`EsAgg.dateHistogram(col, "month")`（second/minute/hour/day/week/month/quarter/year），可链式 `.format(...)` / `.minDocCount(n)`；**ES 默认 minDocCount=0**（数据区间内空时间桶也返回），只要非空桶传 `.minDocCount(1)`；桶 key 为 epoch 毫秒（Long）
- **range 数值区间分桶**：`EsAgg.range(col, EsAggRange.of(0.0, 1000.0).key("budget"), ...)`，端点可空（null = 开区间，from ≥ to 拒绝）；桶 key 为区间命名（未命名时自动「from-to」串），边界经 `bucket.getFrom()/getTo()` 取
- **top_hits 桶内/顶层取文档**：`EsAgg.topHits(size, cols...)`（降序）/ `topHitsAsc(...)`（升序），默认聚合名 topHits，多个时 `.as()` 区分；结果经 `EsAggResult.hits(name, Entity.class)` 反序列化为实体，terms 桶内经 `bucket.getAggs().hits(...)` 取
- **terms 分桶排序**：`EsAgg.terms(col).orderBy(metric, desc)`，metric 支持 `_count` / `_key` / 子聚合名（须已 subAgg 挂载，否则执行前报错）
- **multi_match type/operator 可配**：`multiMatch(EsMultiMatch.type(MatchType.MOST_FIELDS).operatorAnd(), value, cols...)`，五型（BEST_FIELDS/MOST_FIELDS/CROSS_FIELDS/PHRASE/PHRASE_PREFIX）+ AND/OR + minimumShouldMatch；PHRASE 系仅 text 字段；原两个重载（默认 best_fields）行为不变
- **script 过滤**：`wrapper.script("doc['stock'].value > params.min", Map.of("min", 50))`（params 可空），painless filter context 不打分，可与普通条件组合；source 为用户自写脚本，注入风险自担
- `selectOne` 命中多条直接抛异常（不静默取首条）
- `deleteBatchIds` / `deleteByQuery` 使用 `conflicts=proceed`：删除目标刚被更新时跳过该条而非整体 409 失败
- 主键（@TableId）同时作为 ES 文档 `_id` 与 `_source` 字段
- ES 字段名默认 = 属性名原样（camelCase），无隐式下划线转换
- 类型推导：String→keyword / Long→long / BigDecimal→double / LocalDate 等→date / 枚举→keyword；不支持的字段类型用 `@TableField(exist = false)` 排除
- **BigDecimal 数值精度**：经 ES double 往返后 scale 可能变化（399.00 → 399.0），比较请用 `compareTo` 而非 `equals`
- **写入可见性**：insert/update 后默认 1s refresh 才可被 search 检索到（selectById 为 realtime get 不受影响）；测试中请手动 `client.indices().refresh(...)`
- **条件删除/更新**：`delete(wrapper)` 走 delete_by_query、`update(patch, wrapper)` 走 update_by_query + painless script（patch 非 null 字段 → params），均 `conflicts=proceed + refresh` 并返回实际影响条数；**wrapper 为 null 或空条件直接抛异常**（拒绝全量误删误改）；条件更新与 nested/geo 条件可自由组合
- **updateBatchById**：bulk API 逐条部分更新（每条取非 null 字段），部分失败抛 EsOpsException（对齐 insertBatch）；实体缺主键 / 全 null 字段直接报错
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
- **mp-es-sample 集成测试**（16 个）：CRUD 生命周期、批量读写、条件查询（eq/gt/like/match/between/or 嵌套/selectOne）、分页与越界、高亮、聚合、子聚合、search_after 深分页、Geo（geo_distance 过滤/距离排序/组合条件）、Nested（子文档条件/多条件 AND/往返还原/geo+nested 组合）、条件删除/条件更新/批量部分更新、查询增强（multiMatch/boost 排序翻转/fuzzy 容错/prefix）——需本地 ES 运行，测试内通过 `indices().refresh` 保证写入可见性

## CI

GitHub Actions（[.github/workflows/ci.yml](.github/workflows/ci.yml)）：push 到 main / PR 触发，JDK 17 + `services` 起 ES 8.19.0 容器（healthcheck 等待就绪），执行 `mvn verify` 全量验证——与本地验证语义一致。

## 发布（Maven Central）

发布元数据已就位（licenses / developers / scm，License Apache-2.0）。**打 tag 自动发布**：

```bash
git tag v0.2.0 && git push origin v0.2.0
# GitHub Actions（.github/workflows/release.yml）自动：
#   标签名 → 版本号（versions:set 全模块替换）→ GPG 签名 → Central 上传 → autoPublish 自动发布 → 建 GitHub Release
```

前置 secrets（仓库 Settings → Secrets and variables → Actions）：

| Secret | 内容 |
|---|---|
| `GPG_PRIVATE_KEY` | `gpg --armor --export-secret-keys <指纹>` 的完整输出 |
| `GPG_PASSPHRASE` | GPG 密钥口令 |
| `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` | Central Portal 的 User Token |

本地手动发布（等价路径，需要 `~/.m2/settings.xml` 配 `id=central` 的 token）：

```bash
mvn -Prelease deploy        # sources + javadoc + gpg 签名 + Central 上传，仅 mp-es-core 发布
```

发布校验（无需账号，本地可跑）：

```bash
mvn -B -ntp javadoc:javadoc -pl mp-es-core   # Javadoc 可生成（质量门槛）
```

## Roadmap

**已完成**

| 期 | 内容 |
|---|---|
| 一期 | Mapper 代理 / CRUD / 条件查询（Wrapper）/ from+size 分页 / Index 托管 |
| 二期 | 高亮（selectHighlighted）、聚合（terms/avg/max/min/sum/stats/cardinality） |
| 三期 | search_after 深分页、子聚合（subAgg）、Geo（geo_point 过滤/距离排序）、Nested（子文档条件/双向重命名） |
| 四期 | 条件删除（delete）、条件更新（update + painless script）、批量部分更新（updateBatchById） |
| 五期 | 查询增强：multiMatch、fuzzy、prefix、boost 权重 |
| 六期 | 工程化：GitHub Actions CI、Javadoc 质量门槛、Maven Central 发布准备 |
| 七期 | 上限与可控性：limit(n) 返回条数控制、selectList 超 1000 显式报错、terms 聚合 size 可配 |
| 八期 | 聚合扩展：date_histogram、range、top_hits（含 Asc 重载）、terms 分桶排序（orderBy sub-agg） |

**候选方向**（按需排期，欢迎提 issue 讨论）

| 方向 | 内容 |
|---|---|
| 索引运维 | alias 切换、reindex 重建 mapping、索引模板——解决「改实体必须删索引」的痛点 |
| nested 进阶 | nested 排序（NestedSortValue）、nested 聚合、inner_hits（返回命中的子文档） |
| 查询增强续 | multi_match 的 type/operator 配置、script 查询、collapse 去重 |
| 发布落地 | **进行中**：发布链路已就绪（central-publishing-maven-plugin + tag 触发 CI），待 Central Portal secrets 配置与首次发布 |
