#!/usr/bin/env python3
"""mybatis-plus-es 文档站生成器：python3 gen.py 生成 15 个静态 HTML 页。
内容单点维护于本文件 PAGES，侧边栏/顶栏由模板统一注入。"""
import html
import os

OUT = os.path.dirname(os.path.abspath(__file__))

# 页面顺序（slug, 标题, 所属分组）
NAV = [
    ("开始", [
        ("index", "概览"),
        ("quick-start", "快速开始"),
    ]),
    ("核心", [
        ("entity-mapping", "实体映射"),
        ("index-management", "Index 托管"),
        ("crud", "CRUD 与条件写"),
        ("wrapper", "条件查询 Wrapper"),
        ("search-enhanced", "查询增强"),
        ("pagination", "分页与深分页"),
    ]),
    ("进阶", [
        ("highlight", "高亮"),
        ("aggregation", "聚合"),
        ("geo", "Geo"),
        ("nested", "Nested"),
    ]),
    ("参考", [
        ("configuration", "配置参考"),
        ("faq", "语义边界与 FAQ"),
        ("roadmap", "Roadmap"),
    ]),
]

TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>__TITLE__ · mybatis-plus-es 文档</title>
<link rel="stylesheet" href="assets/guide.css">
</head>
<body>
<header class="topbar">
  <a class="brand" href="index.html">mybatis-plus<span class="es">-es</span></a>
  <span class="tagline">MyBatis-Plus 风格 API 操作 Elasticsearch</span>
  <div class="top-links">
    <a href="https://github.com/kennethfan/mybatis-plus-es" target="_blank">GitHub</a>
    <a href="https://github.com/kennethfan/mybatis-plus-es/issues" target="_blank">Issues</a>
  </div>
</header>
<div class="layout">
  <nav class="sidebar">__SIDEBAR__</nav>
  <main class="content">__CONTENT__<nav class="pager">__PAGER__</nav></main>
</div>
</body>
</html>
"""


def esc(s: str) -> str:
    return html.escape(s, quote=False)


def code(s: str) -> str:
    return f"<pre><code>{esc(s)}</code></pre>"


def render_sidebar(active: str) -> str:
    out = []
    for group, items in NAV:
        out.append('<div class="nav-group">')
        out.append(f'<div class="nav-label">{group}</div>')
        for slug, title in items:
            cls = ' class="active"' if slug == active else ""
            out.append(f'<a href="{slug}.html"{cls}>{title}</a>')
        out.append("</div>")
    return "\n".join(out)


def render_pager(slug: str) -> str:
    slugs = [s for _, items in NAV for s, _ in items]
    idx = slugs.index(slug)
    parts = []
    if idx > 0:
        prev = slugs[idx - 1]
        prev_title = next(t for g, items in NAV for s, t in items if s == prev)
        parts.append(f'<a href="{prev}.html">← {prev_title}</a>')
    else:
        parts.append("<span></span>")
    if idx < len(slugs) - 1:
        nxt = slugs[idx + 1]
        nxt_title = next(t for g, items in NAV for s, t in items if s == nxt)
        parts.append(f'<a href="{nxt}.html">{nxt_title} →</a>')
    else:
        parts.append("<span></span>")
    return "\n".join(parts)


def page(slug: str, title: str, content: str) -> str:
    return (TEMPLATE
            .replace("__TITLE__", title)
            .replace("__SIDEBAR__", render_sidebar(slug))
            .replace("__CONTENT__", content)
            .replace("__PAGER__", render_pager(slug)))


# ---------------- 页面内容 ----------------

P = {}

P["index"] = ("概览", """
<h1>mybatis-plus-es</h1>
<p class="lead">以 <b>MyBatis-Plus 风格 API 操作 Elasticsearch</b>：沿用 MP 的 Mapper / Wrapper 写法，底层直连 Elasticsearch 官方 Java API Client，<b>不经过任何 MyBatis 执行机制</b>。</p>

<h2>特性</h2>
<div class="grid">
  <div class="card"><b>API 对齐 MP</b><span>EsBaseMapper 签名与 LambdaQueryWrapper 写法对齐 BaseMapper，MyBatis-Plus 用户零学习成本</span></div>
  <div class="card"><b>无 MyBatis 管线</b><span>JDK 动态代理直连 elasticsearch-java，仅复用 MP 注解作映射来源</span></div>
  <div class="card"><b>Index 托管</b><span>启动时 create-if-absent + mapping 一致性校验（WARN / FAIL）</span></div>
  <div class="card"><b>完整写路径</b><span>insert / update / delete 全家桶：按主键、批量、按条件（update_by_query / delete_by_query）</span></div>
  <div class="card"><b>深分页</b><span>search_after 游标翻页，无 10000 窗口限制，不重不漏</span></div>
  <div class="card"><b>聚合与高亮</b><span>7 种聚合 + 任意深度子聚合；selectHighlighted 返回实体 + 片段</span></div>
  <div class="card"><b>Geo 与 Nested</b><span>geo_point 过滤/距离排序；@EsNested 子文档条件、双向字段重命名、inner 查询全路径自动前缀</span></div>
  <div class="card"><b>库即 Starter</b><span>依赖即生效（AutoConfiguration.imports），客户端专用 ObjectMapper 不污染应用配置</span></div>
</div>

<h2>最小示例</h2>
""" + code('''@SpringBootApplication
@EsMapperScan("com.example.mapper")          // 对齐 @MapperScan 体验
public class App { ... }

@Data
@TableName("mpes_product")                   // 索引名
public class Product {
    @TableId                                 // 文档 _id
    private Long id;
    @TableField("product_name")
    private String productName;              // String → keyword
    @EsText(analyzer = "standard")
    private String description;              // → text
    private BigDecimal price;                // → double
    @EsGeoPoint
    private GeoPoint location;               // → geo_point
    @EsNested
    private List<Sku> skus;                  // → nested
}

public interface ProductMapper extends EsBaseMapper<Product> {}

// 与 MP 一致的写法
List<Product> list = mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .eq(Product::getOnSale, true)
        .gt(Product::getPrice, new BigDecimal("200"))
        .match(Product::getDescription, "键盘")
        .orderByDesc(Product::getPrice));''') + """

<h2>版本基线</h2>
<table>
  <tr><th>组件</th><th>版本</th></tr>
  <tr><td>JDK</td><td>17+</td></tr>
  <tr><td>Spring Boot</td><td>3.5.x</td></tr>
  <tr><td>elasticsearch-java</td><td>8.19.x（锚定 ES 8.x 服务端）</td></tr>
  <tr><td>mybatis-plus-annotation</td><td>3.5.x（仅注解载荷）</td></tr>
</table>

<div class="tip"><b>💡</b> 设计决策与术语见仓库 <code>docs/adr/</code>（ADR-0001 ~ 0004）与 <code>CONTEXT.md</code>。</div>
""")

P["quick-start"] = ("快速开始", """
<h1>快速开始</h1>
<p class="lead">5 分钟跑通：起 ES → 引依赖 → 声明实体与 Mapper → 配置 → 使用。</p>

<h2>1. 启动 ES（本地开发）</h2>
""" + code('''docker compose -f mp-es-sample/docker/docker-compose.yml up -d
# ES 8.19 单节点（:9200），数据持久化到命名卷 mpes-es-data
# + Kibana（:5601，已关闭安全认证）''') + """
<h2>2. 引入依赖</h2>
""" + code('''<dependency>
    <groupId>io.github.kennethfan</groupId>
    <artifactId>mp-es-core</artifactId>
    <version>0.2.0</version>
</dependency>''') + """
<div class="warn"><b>⚠️</b> <code>mybatis-plus-annotation</code> 将 <code>org.mybatis:mybatis</code> 声明为 optional，
但其注解默认值引用了 <code>JdbcType</code>——运行时解析注解必须能加载该类。mp-es-core 已显式引入 mybatis 3.5.19，
<b>仅作为注解解析载荷，无任何 MyBatis 执行路径</b>。</div>

<h2>3. 声明实体与 Mapper</h2>
""" + code('''@Data
@TableName("mpes_product")
public class Product {
    @TableId
    private Long id;
    @TableField("product_name")
    private String productName;
    @EsText(analyzer = "standard")
    private String description;
    private BigDecimal price;
    private Integer stock;
    private LocalDate launchDate;
    private Boolean onSale;
    @EsGeoPoint
    private GeoPoint location;
    @EsNested
    private List<Sku> skus;
}

public interface ProductMapper extends EsBaseMapper<Product> {}''') + """
<h2>4. 启用扫描与配置</h2>
""" + code('''@SpringBootApplication
@EsMapperScan("com.example.mapper")
public class App { ... }''') + code('''mp-es:
  uris: [http://localhost:9200]
  index:
    auto-create: true        # 启动时 create-if-absent
    mismatch-policy: WARN    # mapping 不一致：WARN（默认）/ FAIL''') + """
<h2>5. 使用</h2>
""" + code('''ProductMapper mapper = applicationContext.getBean(ProductMapper.class);

mapper.insert(product);                       // 增
mapper.updateById(patch);                     // 改（非 null 字段部分更新）
mapper.deleteById(id);                        // 删
Product p = mapper.selectById(id);            // 查

List<Product> list = mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .eq(Product::getOnSale, true)
        .like(Product::getProductName, "键盘")
        .match(Product::getDescription, "静音")
        .orderByDesc(Product::getPrice));''') + """
<div class="tip"><b>💡</b> 集成测试参考 <code>mp-es-sample</code>：16 个测试覆盖全部能力，可直接对照学习。</div>
""")

P["entity-mapping"] = ("实体映射", """
<h1>实体映射</h1>
<p class="lead">MP 注解即 ES 映射（ADR-0004）：不新增一套映射体系，注解在编译期就在、运行期直接解析。</p>

<h2>注解总览</h2>
<table>
  <tr><th>注解</th><th>来源</th><th>映射语义</th></tr>
  <tr><td>@TableName("mpes_product")</td><td>MP</td><td>索引名（缺省为类名首字母小写）</td></tr>
  <tr><td>@TableId</td><td>MP</td><td>文档 _id（同时保留在 _source）</td></tr>
  <tr><td>@TableField("product_name")</td><td>MP</td><td>ES 字段名（缺省为属性名原样 camelCase）；<code>exist=false</code> 排除字段</td></tr>
  <tr><td>@EsText(analyzer = "standard")</td><td>mp-es</td><td>String 覆盖为 text 分词字段</td></tr>
  <tr><td>@EsGeoPoint</td><td>mp-es</td><td>geo_point 标注（类型必须是 GeoPoint，否则启动报错）</td></tr>
  <tr><td>@EsNested</td><td>mp-es</td><td>nested 子文档（<code>List&lt;子实体&gt;</code> 或单个子实体）</td></tr>
</table>

<h2>类型推导</h2>
<table>
  <tr><th>Java 类型</th><th>ES 类型</th></tr>
  <tr><td>String</td><td>keyword（标注 @EsText 覆盖为 text）</td></tr>
  <tr><td>Long / long</td><td>long</td></tr>
  <tr><td>Integer / int</td><td>integer</td></tr>
  <tr><td>Short / Byte</td><td>short / byte</td></tr>
  <tr><td>Double / Float</td><td>double / float</td></tr>
  <tr><td>BigDecimal</td><td>double</td></tr>
  <tr><td>Boolean</td><td>boolean</td></tr>
  <tr><td>LocalDate / LocalDateTime 等</td><td>date</td></tr>
  <tr><td>枚举</td><td>keyword</td></tr>
  <tr><td>GeoPoint（@EsGeoPoint）</td><td>geo_point</td></tr>
  <tr><td>子实体（@EsNested）</td><td>nested（递归解析子实体字段）</td></tr>
</table>

<h2>Nested 子实体</h2>
""" + code('''@Data
public class Sku {
    @TableId
    private String skuCode;      // 子实体无需 @TableName（不独立成索引），但需主键字段
    private String spec;
    private Integer quantity;
}

@EsNested
private List<Sku> skus;          // List<子实体> 或单个子实体均可''') + """
<ul>
  <li>子实体字段类型推导规则与顶层一致，支持递归（nested 套 nested）</li>
  <li>循环引用直接报错（解析期 visited 检测）</li>
  <li>写入/读取时子文档字段名与 ES 字段名<b>双向重命名可逆</b>（@TableField 在子实体上同样生效）</li>
</ul>

<h2>GeoPoint 值对象</h2>
""" + code('''// 构造时校验：lat ∈ [-90, 90]，lon ∈ [-180, 180]
p.setLocation(new GeoPoint(39.90, 116.40));
// Jackson 序列化为 ES 原生格式 {"lat":39.9,"lon":116.4}''') + """

<div class="warn"><b>⚠️</b> 不支持的字段类型用 <code>@TableField(exist = false)</code> 排除，否则启动报「不支持的字段类型」。</div>
""")

P["index-management"] = ("Index 托管", """
<h1>Index 托管</h1>
<p class="lead">对标 MP 的表结构托管：应用启动时保证索引存在且 mapping 与实体一致。</p>

<h2>行为</h2>
<ul>
  <li><b>create-if-absent</b>：索引不存在 → 按实体元数据创建（含 nested / geo_point 递归展开）</li>
  <li><b>mapping 校验</b>：索引已存在 → 逐字段比对类型；nested / geo_point 一并参与校验</li>
  <li>开关：<code>mp-es.index.auto-create</code>（默认 true）</li>
</ul>

<h2>mismatch-policy</h2>
<table>
  <tr><th>策略</th><th>行为</th></tr>
  <tr><td><code>WARN</code>（默认）</td><td>mapping 不一致时打印警告，不阻断启动</td></tr>
  <tr><td><code>FAIL</code></td><td>直接抛异常，阻断启动（适合强约束环境）</td></tr>
</table>

<h2>mapping 演进：索引运维（EsIndexOps）</h2>
<p>ES 不支持修改已有字段类型。十一期起容器注入 <code>EsIndexOps</code>，一行完成平滑重建，期间查询写入不中断：</p>
""" + code('''
@Autowired
private EsIndexOps indexOps;

// 建时间戳新索引 → reindex 全量搬迁 → alias 切换 → 删旧索引
RebuildResult result = indexOps.rebuild(Product.class);''') + """
<p><code>rebuild(entity)</code> 三种起点自动识别：</p>
<table>
  <tr><th>起点</th><th>行为</th></tr>
  <tr><td>索引不存在</td><td>建新索引并挂 alias（空索引起步）</td></tr>
  <tr><td>物理索引（存量首次）</td><td>建新 → reindex → 删旧物理索引 → 同名升格 alias</td></tr>
  <tr><td>已是 alias</td><td>建新 → reindex 旧物理索引 → aliasSwap 原子切换 → 删旧</td></tr>
</table>

<h2>基础件</h2>
<table>
  <tr><th>方法</th><th>语义</th></tr>
  <tr><td><code>exists(index)</code></td><td>索引 / alias 是否存在</td></tr>
  <tr><td><code>drop(index)</code></td><td>删除物理索引（不存在报错）</td></tr>
  <tr><td><code>createNew(entity)</code></td><td>按实体最新 mapping 建「索引名-毫秒时间戳」新物理索引</td></tr>
  <tr><td><code>create(index, entity)</code></td><td>按实体 mapping 建指定名物理索引</td></tr>
  <tr><td><code>reindex(from, to)</code></td><td>全量搬迁（同步等待，完成后自动 refresh），返回 <code>ReindexReport</code></td></tr>
</table>

<h2>alias 原子操作</h2>
<table>
  <tr><th>方法</th><th>语义</th></tr>
  <tr><td><code>aliasAdd(alias, index)</code></td><td>首挂 alias</td></tr>
  <tr><td><code>aliasSwap(alias, removeIndex, addIndex)</code></td><td>单请求 remove+add 原子切换，查询零闪断</td></tr>
  <tr><td><code>aliasIndexes(alias)</code></td><td>alias 当前指向的物理索引列表</td></tr>
</table>

<h2>索引模板</h2>
<table>
  <tr><th>方法</th><th>语义</th></tr>
  <tr><td><code>putTemplate(name, indexPattern, entity)</code></td><td>按实体 mapping 写入模板，pattern 匹配的新索引自动套用</td></tr>
  <tr><td><code>templateExists(name)</code></td><td>模板是否存在</td></tr>
  <tr><td><code>dropTemplate(name)</code></td><td>删除模板</td></tr>
</table>

<div class="tip"><b>💡</b> rebuild 后实体索引名升格为 alias 语义，读写均走 alias，对 Mapper 层透明；alias 指向多个物理索引时拒绝重建（请先收敛为单索引）。</div>
""")

P["crud"] = ("CRUD 与条件写", """
<h1>CRUD 与条件写</h1>
<p class="lead">写路径完整闭环：按主键、批量、按条件三类；条件写复用 Wrapper 语义。</p>

<h2>方法清单</h2>
<table>
  <tr><th>方法</th><th>底层</th><th>语义</th></tr>
  <tr><td>insert(entity)</td><td>index API</td><td>新增一条，主键作为 _id</td></tr>
  <tr><td>insertBatch(coll)</td><td>bulk</td><td>批量新增；部分失败整体抛异常</td></tr>
  <tr><td>updateById(entity)</td><td>update API</td><td>按主键部分更新（非 null 字段）</td></tr>
  <tr><td>updateBatchById(coll)</td><td>bulk update</td><td>批量按主键部分更新（每条取非 null 字段）</td></tr>
  <tr><td>update(patch, wrapper)</td><td>update_by_query + painless script</td><td>按条件部分更新，返回实际更新数</td></tr>
  <tr><td>deleteById(id)</td><td>delete API</td><td>按主键删除，命中 1 否则 0</td></tr>
  <tr><td>deleteBatchIds(ids)</td><td>delete_by_query</td><td>按主键集合删除</td></tr>
  <tr><td>delete(wrapper)</td><td>delete_by_query</td><td>按条件删除，返回实际删除数</td></tr>
</table>

<h2>条件删除 / 条件更新</h2>
""" + code('''// 条件删除：下架商品删除
mapper.delete(new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, false));

// 条件更新：在售商品全部改价（patch 取非 null 字段）
Product patch = new Product();
patch.setPrice(new BigDecimal("500"));
mapper.update(patch, new EsLambdaQueryWrapper<Product>()
        .eq(Product::getOnSale, true));

// 条件更新可与 nested / geo 条件自由组合
mapper.update(patch2, new EsLambdaQueryWrapper<Product>()
        .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-C3")));''') + """

<h2>安全与一致性语义</h2>
<div class="warn"><b>⚠️</b> <code>delete(null)</code> / <code>update(patch, null)</code> / <b>空条件 wrapper</b> 一律抛
<code>EsOpsException</code>——拒绝全量误删误改。</div>
<ul>
  <li>条件删除/更新统一 <code>conflicts=proceed</code>：目标刚被并发修改时跳过该条而非整体 409 失败</li>
  <li>统一 <code>refresh=true</code>：操作完成后立即可检索，返回值为<b>实际影响条数</b></li>
  <li>条件更新的 script：<code>ctx._source['es字段'] = params.pN</code>，字段值格式与 updateById 完全一致</li>
  <li>updateBatchById / insertBatch：bulk 部分失败整体抛 <code>EsOpsException</code>（明确报错而非静默半成功）</li>
</ul>
""")

P["wrapper"] = ("条件查询 Wrapper", """
<h1>条件查询 Wrapper</h1>
<p class="lead">EsLambdaQueryWrapper：Lambda 链式构造，编译期字段安全；语义与 MP 对齐并针对 ES 特性扩展。</p>

<h2>全操作符</h2>
<table>
  <tr><th>方法</th><th>适用字段</th><th>ES DSL</th><th>说明</th></tr>
  <tr><td>eq / ne</td><td>keyword/数值/布尔/date</td><td>term / mustNot term</td><td>text 字段报错，请用 match</td></tr>
  <tr><td>in</td><td>同 eq</td><td>terms</td><td></td></tr>
  <tr><td>gt / ge / lt / le</td><td>数值/date</td><td>range</td><td></td></tr>
  <tr><td>between</td><td>数值/date</td><td>range gte+lte</td><td></td></tr>
  <tr><td>like</td><td>keyword</td><td>wildcard *v*</td><td>通配符；前缀场景建议 prefix（更快）</td></tr>
  <tr><td>match</td><td>text/keyword</td><td>match</td><td>显式分词检索入口</td></tr>
  <tr><td>isNull</td><td>任意</td><td>mustNot exists</td><td></td></tr>
  <tr><td>geoDistance</td><td>geo_point</td><td>geo_distance</td><td>见 <a href="geo.html">Geo</a></td></tr>
  <tr><td>nested(col, Child.class, w)</td><td>nested</td><td>nested</td><td>见 <a href="nested.html">Nested</a></td></tr>
</table>

<h2>优先级：AND 高于 OR</h2>
<p>与 SQL 一致：AND 先结合。引擎按 OR 边界切分为若干 AND 桶，桶间 should 连接：</p>
""" + code('''// a.eq(1).or().eq(2).eq(3)  →  (1 OR 2) AND 3
new EsLambdaQueryWrapper<Product>()
        .eq(Product::getOnSale, false)
        .or()
        .le(Product::getPrice, new BigDecimal("200"))
        .ge(Product::getStock, 50);''') + """
<h2>嵌套分组</h2>
""" + code('''// onSale=false OR (price<=200 AND stock>=50)
new EsLambdaQueryWrapper<Product>()
        .eq(Product::getOnSale, false)
        .or(w -> w.le(Product::getPrice, new BigDecimal("200"))
                  .ge(Product::getStock, 50));''') + """
<h2>排序</h2>
""" + code('''new EsLambdaQueryWrapper<Product>()
        .orderByAsc(Product::getPrice)
        .orderByDesc(Product::getStock)
        .orderByGeoDistance(Product::getLocation, new GeoPoint(39.90, 116.40), true);''') + """
<h2>空 wrapper 语义</h2>
<ul>
  <li>查询侧（selectList / selectCount / selectPage 等）：wrapper 传 <code>null</code> = 全量（match_all）</li>
  <li>写侧（delete / update）：wrapper 为 null 或空条件<b>直接抛异常</b>（防全量误删误改）</li>
</ul>
""")

P["search-enhanced"] = ("查询增强", """
<h1>查询增强</h1>
<p class="lead">multiMatch / fuzzy / prefix / boost：打分与容错场景的检索补强。</p>

<h2>multiMatch 跨字段检索</h2>
""" + code('''// 值在前，字段 varargs 在后
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .multiMatch("静音", Product::getProductName, Product::getDescription));

// 带权重
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .multiMatch(2.0f, "静音", Product::getProductName, Product::getDescription));''') + """
<p>默认 <code>best_fields</code>；字段名经 resolver 转换（nested 内自动带全路径前缀）。</p>

<h2>fuzzy 容错</h2>
""" + code('''// 默认 AUTO：3-5 字符容 1 级编辑距离，6+ 字符容 2 级
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .fuzzy(Product::getProductName, "机械键盘 K871"));   // 距离 1 → 命中 "机械键盘 K870"

// 显式最大编辑距离
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .fuzzy(Product::getProductName, "机械键盘 K871", 1));''') + """
<div class="warn"><b>⚠️</b> fuzzy 为 term 级容错——keyword 字段是<b>整词</b>比较，"K860" 与 "机械键盘 K870" 距离过远不会命中。</div>

<h2>prefix 前缀</h2>
""" + code('''mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .prefix(Product::getProductName, "机械"));   // 原生 prefix 查询，优于 like("机械*") 通配符''') + """
<h2>boost 权重</h2>
""" + code('''// 仅打分类条件提供重载：or 场景调整相关性排序
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .match(Product::getDescription, "键盘", 10f)   // 键盘 加权 → 对应文档排前
        .or()
        .match(Product::getDescription, "鼠标"));''') + """
<p>term / range / like 等过滤类条件不提供 boost（filter 场景无打分意义）。</p>

<h2>keyword / text 适用矩阵</h2>
<table>
  <tr><th>操作</th><th>keyword</th><th>text</th></tr>
  <tr><td>eq / ne / in</td><td>✅</td><td>❌ 用 match</td></tr>
  <tr><td>like（通配）</td><td>✅</td><td>❌ 用 match</td></tr>
  <tr><td>prefix</td><td>✅</td><td>❌ 用 match</td></tr>
  <tr><td>fuzzy</td><td>✅（整词容错）</td><td>❌ 用 match</td></tr>
  <tr><td>match / multiMatch</td><td>⚠️ 整词匹配（少用）</td><td>✅</td></tr>
</table>
""")

P["pagination"] = ("分页与深分页", """
<h1>分页与深分页</h1>
<p class="lead">两种分页：Page（from+size，轻量跳页）与 EsAfter（search_after 游标，深翻页）。</p>

<h2>Page：from+size</h2>
""" + code('''Page<Product> page = mapper.selectPage(new Page<>(1, 10),
        new EsLambdaQueryWrapper<Product>().orderByAsc(Product::getPrice));
page.getTotal();        // 命中总数
page.getRecords();      // 当前页数据''') + """
<ul>
  <li>窗口上限 <code>current × size ≤ 10000</code>（与 ES max_result_window 一致），越界抛异常</li>
  <li>适合管理后台式的<b>浅跳页</b></li>
</ul>

<h2>EsAfter：search_after 游标</h2>
""" + code('''// 首页：EsAfter.first(size)
EsAfterResult<Product> page1 = mapper.selectAfter(EsAfter.first(100),
        new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, true));
page1.getRecords();     // 本批数据
page1.getTotal();       // 命中总数
page1.getNext();        // 下一页游标；null 表示已取完

// 翻页：把 next 传入即可
EsAfterResult<Product> page2 = mapper.selectAfter(page1.getNext(), wrapper);''') + """
<ul>
  <li><b>无 10000 窗口限制</b>，每批上限 10000</li>
  <li>自动追加主键字段兜底排序保证全序（_id 禁止 fielddata 排序，故用 _source 主键）</li>
  <li>取 size+1 判定 <code>hasMore</code>：最后一批 next 为 null，<b>不重不漏</b></li>
</ul>

<h2>如何选择</h2>
<table>
  <tr><th>场景</th><th>选择</th></tr>
  <tr><td>后台列表、跳页导航</td><td>Page</td></tr>
  <tr><td>导出全量、深翻页、游标流式处理</td><td>EsAfter</td></tr>
  <tr><td>越界报「超出 from+size 上限」</td><td>改用 EsAfter</td></tr>
</table>
""")

P["highlight"] = ("高亮", """
<h1>高亮</h1>
<p class="lead">selectHighlighted 独立方法：返回实体 + 高亮片段，不影响常规查询签名。</p>

<h2>用法</h2>
""" + code('''List<EsHit<Product>> hits = mapper.selectHighlighted(
        new EsLambdaQueryWrapper<Product>().match(Product::getDescription, "无线"),
        EsHighlight.of(Product::getProductName, Product::getDescription)
                .preTag("<b>").postTag("</b>"));

for (EsHit<Product> hit : hits) {
    Product entity = hit.getEntity();            // 完整实体
    Map<String, List<String>> all = hit.getHighlights();      // 全部片段
    List<String> fragments = hit.highlightsOf("description"); // 按属性名取
}''') + """
<h2>语义</h2>
<ul>
  <li>默认标签 <code>&lt;em&gt;&lt;/em&gt;</code>，可 <code>preTag / postTag</code> 自定义</li>
  <li><code>highlightsOf</code> 按属性名取片段（引擎已把 ES 字段名转回属性名）</li>
  <li>高亮字段需与查询条件配合：仅对 match 命中的 text 字段产生片段</li>
</ul>
<div class="tip"><b>💡</b> 高亮走独立方法是有意设计：不污染 selectList / selectPage 的一期签名。</div>
""")

P["aggregation"] = ("聚合", """
<h1>聚合</h1>
<p class="lead">aggregate 独立方法：只聚合不返回文档（size=0）。7 种聚合 + 任意深度子聚合。</p>

<h2>基础聚合</h2>
""" + code('''EsAggResult result = mapper.aggregate(
        new EsLambdaQueryWrapper<Product>().le(Product::getPrice, new BigDecimal("3000")),
        EsAgg.terms(Product::getOnSale),            // 分组计数
        EsAgg.avg(Product::getPrice).as("avgPrice"),// 平均
        EsAgg.max(Product::getPrice),               // 最大
        EsAgg.min(Product::getStock),               // 最小
        EsAgg.sum(Product::getStock),               // 求和
        EsAgg.stats(Product::getStock),             // count/min/max/avg/sum
        EsAgg.cardinality(Product::getProductName));// 去重计数

result.buckets("onSale");        // terms 桶：List<EsBucket>（getKey / getCount / getAggs）
result.value("avgPrice");        // 单值 Double（avg/max/min/sum/cardinality）
result.stats("stock");           // EsStats：getCount/getMin/getMax/getAvg/getSum''') + """
<h2>命名规则</h2>
<ul>
  <li>未 <code>as()</code> 显式命名时，聚合名默认为<b>属性名</b>（非 ES 字段名）</li>
  <li>terms 桶数默认上限 100</li>
</ul>

<h2>子聚合（subAgg）</h2>
""" + code('''EsAggResult sub = mapper.aggregate(null,
        EsAgg.terms(Product::getOnSale).subAgg(
                EsAgg.avg(Product::getPrice).as("avgPrice"),
                EsAgg.max(Product::getPrice).as("maxPrice")));

for (EsBucket bucket : sub.buckets("onSale")) {
    double avg = bucket.getAggs().value("avgPrice");   // 桶内取子结果
}''') + """
<ul>
  <li>子聚合挂在任意聚合上，支持任意深度链式嵌套</li>
  <li>子聚合名在每桶内独立命名</li>
</ul>

<div class="warn"><b>⚠️</b> boolean 字段的 terms 桶键为 ES 原生数值语义：<code>true → 1</code>、<code>false → 0</code>（getKey 为 Long）。</div>
""")

P["geo"] = ("Geo", """
<h1>Geo</h1>
<p class="lead">geo_point 全链路：值对象、距离过滤、距离排序，与其它条件自由组合。</p>

<h2>实体声明</h2>
""" + code('''@EsGeoPoint                          // 显式标注（类型必须是 GeoPoint）
private GeoPoint location;

p.setLocation(new GeoPoint(39.90, 116.40));   // lat, lon（构造期校验范围）''') + """
<h2>距离过滤</h2>
""" + code('''// 距北京 1500km 内（支持 "1500km" / "20mi" 等 ES 距离单位）
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .geoDistance(Product::getLocation, "1500km", new GeoPoint(39.90, 116.40)));''') + """
<h2>距离排序</h2>
""" + code('''// 按到 origin 的距离升序
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .orderByGeoDistance(Product::getLocation, new GeoPoint(39.90, 116.40), true));''') + """
<h2>组合</h2>
""" + code('''// geo + 普通条件 + nested 条件
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .geoDistance(Product::getLocation, "1500km", new GeoPoint(39.90, 116.40))
        .eq(Product::getOnSale, true)
        .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-B2")));''') + """
<div class="warn"><b>⚠️</b> geoDistance / orderByGeoDistance 仅用于 geo_point 字段，否则直接报错；
GeoPoint 序列化为 <code>{"lat":..,"lon":..}</code> 原生格式，经 _source 往返无损。</div>
""")

P["nested"] = ("Nested", """
<h1>Nested</h1>
<p class="lead">@EsNested 子文档：声明、查询、回显全链路；inner 条件自动加全路径前缀。</p>

<h2>声明</h2>
""" + code('''@EsNested
private List<Sku> skus;          // List<子实体> 或单个子实体''') + code('''@Data
public class Sku {
    @TableId
    private String skuCode;      // 子实体需主键字段；无需 @TableName
    private String spec;
    private Integer quantity;
}''') + """
<h2>子文档条件</h2>
""" + code('''// childType 为类型见证，保证 lambda 方法引用获得正确类型
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-A1")));

// 子文档内多条件（AND 语义一致）
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .nested(Product::getSkus, Sku.class,
                w -> w.eq(Sku::getSpec, "4K").le(Sku::getQuantity, 5)));

// 与顶层条件组合
mapper.selectList(new EsLambdaQueryWrapper<Product>()
        .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-A1"))
        .eq(Product::getOnSale, true));''') + """
<h2>关键机制</h2>
<ul>
  <li><b>全路径前缀</b>：inner 条件字段自动转为 <code>skus.skuCode</code> 完整路径（ES nested 查询硬性要求），支持 nested 套 nested 递归</li>
  <li><b>双向重命名</b>：子文档写入/读取时字段名与 ES 字段名双向映射（@TableField 在子实体上同样生效），往返无损</li>
  <li><b>类型校验</b>：非 nested 字段使用 nested() 直接报错；循环引用在解析期报错</li>
</ul>

<div class="tip"><b>💡</b> 为什么用 nested 而不是 object：object 类型会拍平数组导致跨对象条件错误匹配；nested 独立索引每个子文档，条件隔离精确。</div>
""")

P["configuration"] = ("配置参考", """
<h1>配置参考</h1>
<p class="lead">前缀 <code>mp-es</code>，由 EsProperties 绑定，库即 starter（依赖即生效）。</p>

<h2>全量配置</h2>
<table>
  <tr><th>配置项</th><th>默认</th><th>说明</th></tr>
  <tr><td>mp-es.uris</td><td>—（必填）</td><td>ES 地址列表，如 <code>[http://localhost:9200]</code>，支持多节点</td></tr>
  <tr><td>mp-es.username</td><td>—</td><td>Basic 认证用户名（username / password 需同时提供）</td></tr>
  <tr><td>mp-es.password</td><td>—</td><td>Basic 认证密码</td></tr>
  <tr><td>mp-es.index.auto-create</td><td>true</td><td>启动时 create-if-absent</td></tr>
  <tr><td>mp-es.index.mismatch-policy</td><td>WARN</td><td>mapping 不一致策略：WARN / FAIL</td></tr>
</table>

<h2>示例</h2>
""" + code('''mp-es:
  uris: [http://localhost:9200, http://localhost:9201]
  # username: elastic
  # password: changeme
  index:
    auto-create: true
    mismatch-policy: FAIL''') + """
<h2>Bean 装配</h2>
<table>
  <tr><th>Bean</th><th>说明</th></tr>
  <tr><td>mpEsRestClient</td><td>RestClient（@ConditionalOnMissingBean，可自定义替换）</td></tr>
  <tr><td>mpEsObjectMapper</td><td>客户端专用 ObjectMapper：JavaTimeModule + 关闭 WRITE_DATES_AS_TIMESTAMPS + FAIL_ON_UNKNOWN_PROPERTIES=false，<b>不污染应用自身的 Jackson 配置</b></td></tr>
  <tr><td>elasticsearchClient</td><td>ElasticsearchClient（RestClientTransport + JacksonJsonpMapper）</td></tr>
  <tr><td>esIndexManager / esIndexBootstrap</td><td>Index 托管与启动引导</td></tr>
</table>

<div class="tip"><b>💡</b> Mapper 注册：<code>@EsMapperScan("com.example.mapper")</code>，基于 ImportBeanDefinitionRegistrar + EsMapperFactoryBean，体验对齐 @MapperScan。</div>
""")

P["faq"] = ("语义边界与 FAQ", """
<h1>语义边界与 FAQ</h1>
<p class="lead">使用前必读的坑点对照表——全部来自真实集成测试验证。</p>

<h2>精度与类型</h2>
<table>
  <tr><th>坑点</th><th>说明</th><th>正确姿势</th></tr>
  <tr><td>BigDecimal 精度</td><td>经 ES double 往返后 scale 可能变化（399.00 → 399.0）</td><td>比较用 <code>compareTo</code>，不要 <code>equals</code></td></tr>
  <tr><td>boolean 聚合桶键</td><td>terms 聚合 boolean 字段桶键为数值 1/0（ES 原生语义）</td><td><code>Long.valueOf(1L).equals(bucket.getKey())</code></td></tr>
</table>

<h2>字段类型适配</h2>
<table>
  <tr><th>坑点</th><th>说明</th><th>正确姿势</th></tr>
  <tr><td>match 打在 keyword 上</td><td>keyword 无分词，match 查询词整词化后与字段值比较，"键盘" ≠ "机械键盘 K870"</td><td>keyword 精确用 eq、包含用 like、前缀用 prefix；match 只用于 text</td></tr>
  <tr><td>text 字段用 eq/like/fuzzy/prefix</td><td>分词后语义错误</td><td>引擎直接报错并提示用 match</td></tr>
  <tr><td>fuzzy 整词比较</td><td>keyword 是整词容错，"K860" ≠ "机械键盘 K870"（距离过远）</td><td>候选词需与整词接近（编辑距离内）</td></tr>
</table>

<h2>可见性与一致性</h2>
<table>
  <tr><th>坑点</th><th>说明</th><th>正确姿势</th></tr>
  <tr><td>写入不可见</td><td>insert/update 后默认 1s refresh 才能被 search 检索（selectById 是 realtime get 不受影响）</td><td>测试中手动 <code>client.indices().refresh(...)</code></td></tr>
  <tr><td>delete 409</td><td>删除目标刚被并发更新会版本冲突</td><td>deleteBatchIds/delete 已设 <code>conflicts=proceed</code> 自动跳过</td></tr>
  <tr><td>selectOne 多条</td><td>命中多条直接抛异常（不静默取首条）</td><td>收紧条件或改用 selectList</td></tr>
</table>

<h2>容量与限制</h2>
<table>
  <tr><th>限制</th><th>值</th><th>说明</th></tr>
  <tr><td>from+size 窗口</td><td>10000</td><td>超出用 selectAfter（search_after）</td></tr>
  <tr><td>selectList 上限</td><td>1000 条</td><td>全量导出用 selectAfter 游标</td></tr>
  <tr><td>terms 桶数</td><td>100</td><td>子聚合同规则</td></tr>
  <tr><td>search_after 每批</td><td>10000</td><td>EsAfter.first(size) 的 size 上限</td></tr>
</table>

<h2>依赖陷阱</h2>
<div class="warn"><b>⚠️</b> <code>mybatis-plus-annotation</code> 把 <code>org.mybatis:mybatis</code> 声明为 optional，
但其注解默认值引用 <code>JdbcType</code>，运行时解析注解必须能加载该类。mp-es-core 已显式引入 mybatis——
<b>仅作为注解解析载荷，无任何 MyBatis 执行路径</b>（ADR-0002/0004 已记录此例外）。</div>
""")

P["roadmap"] = ("Roadmap", """
<h1>Roadmap</h1>
<p class="lead">已完成十一个迭代 + 发布落地，Roadmap 全部完成；新方向欢迎提 issue 讨论。</p>

<h2>已完成</h2>
<table>
  <tr><th>期</th><th>内容</th></tr>
  <tr><td>一期</td><td>Mapper 代理 / CRUD / 条件查询 / from+size 分页 / Index 托管</td></tr>
  <tr><td>二期</td><td>高亮、聚合（terms/avg/max/min/sum/stats/cardinality）</td></tr>
  <tr><td>三期</td><td>search_after 深分页、子聚合、Geo、Nested</td></tr>
  <tr><td>四期</td><td>条件删除、条件更新（painless script）、批量部分更新</td></tr>
  <tr><td>五期</td><td>查询增强：multiMatch、fuzzy、prefix、boost</td></tr>
  <tr><td>六期</td><td>工程化：GitHub Actions CI、Javadoc 质量门槛、Maven Central 发布准备</td></tr>
  <tr><td>七期</td><td>上限与可控性：limit(n)、selectList 超 1000 显式报错、terms 聚合 size 可配</td></tr>
  <tr><td>八期</td><td>聚合扩展：date_histogram、range、top_hits（含 Asc 重载）、terms 分桶排序</td></tr>
  <tr><td>九期</td><td>查询增强续：multi_match type/operator 可配（EsMultiMatch）、script 过滤、collapse 去重</td></tr>
  <tr><td>十期</td><td>nested 进阶：nested 排序（含子过滤）、nested 聚合、inner_hits（selectListWithNestedHits）</td></tr>
  <tr><td>十一期</td><td>索引运维：EsIndexOps——exists/drop/createNew/reindex 基础件、alias 原子操作、rebuild 一键平滑重建（存量迁移）、索引模板</td></tr>
  <tr><td>发布落地</td><td>Maven Central v0.2.0（tag 触发 CI 全自动发布 + 自动建 GitHub Release）</td></tr>
</table>

<h2>候选方向</h2>
<p>Roadmap 全部完成，新方向欢迎提 <a href="https://github.com/kennethfan/mybatis-plus-es/issues">issue</a> 讨论。</p>

<h2>文档站</h2>
<p>本站点为纯静态 HTML，源码位于仓库 <code>website/</code>，push 到 main 后由 GitHub Actions 自动发布到 GitHub Pages。</p>
""")


def main():
    for slug, (title, content) in P.items():
        path = os.path.join(OUT, f"{slug}.html")
        with open(path, "w", encoding="utf-8") as f:
            f.write(page(slug, title, content))
        print(f"  ✓ {slug}.html")
    print(f"共生成 {len(P)} 页 → {OUT}")


if __name__ == "__main__":
    main()
