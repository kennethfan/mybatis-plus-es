package io.github.kennethfan.mpes.sample;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.github.kennethfan.mpes.agg.EsAgg;
import io.github.kennethfan.mpes.agg.EsAggRange;
import io.github.kennethfan.mpes.agg.EsAggResult;
import io.github.kennethfan.mpes.agg.EsBucket;
import io.github.kennethfan.mpes.geo.GeoPoint;
import io.github.kennethfan.mpes.highlight.EsHighlight;
import io.github.kennethfan.mpes.highlight.EsHit;
import io.github.kennethfan.mpes.index.EsIndexOps;
import io.github.kennethfan.mpes.index.RebuildResult;
import io.github.kennethfan.mpes.index.ReindexReport;
import io.github.kennethfan.mpes.page.EsAfter;
import io.github.kennethfan.mpes.page.EsAfterResult;
import io.github.kennethfan.mpes.page.Page;
import io.github.kennethfan.mpes.sample.entity.Product;
import io.github.kennethfan.mpes.sample.entity.Sku;
import io.github.kennethfan.mpes.sample.mapper.ProductMapper;
import io.github.kennethfan.mpes.support.EsOpsException;
import io.github.kennethfan.mpes.wrapper.EsLambdaQueryWrapper;
import io.github.kennethfan.mpes.wrapper.EsMultiMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成测试：直连本地 Docker ES（Q12）。ES 未启动时整类自动跳过。
 * 先执行 `docker compose up -d` 再运行本测试。
 */
@SpringBootTest
@EnabledIf(value = "io.github.kennethfan.mpes.sample.ProductMapperIntegrationTest#esAvailable",
        disabledReason = "本地 ES 未启动（docker compose up -d）")
class ProductMapperIntegrationTest {

    private static final long ID_1 = 90001L;
    private static final long ID_2 = 90002L;
    private static final long ID_3 = 90003L;

    @Autowired
    private ProductMapper mapper;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private EsIndexOps ops;

    static boolean esAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 9200), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        mapper.deleteBatchIds(List.of(ID_1, ID_2, ID_3));
        refresh();
    }

    private void refresh() {
        try {
            client.indices().refresh(r -> r.index("mpes_product"));
        } catch (IOException e) {
            throw new EsOpsException("refresh 失败", e);
        }
    }

    private Product product(long id, String name, String desc, String price, int stock,
                            String launchDate, boolean onSale) {
        Product p = new Product();
        p.setId(id);
        p.setProductName(name);
        p.setDescription(desc);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setLaunchDate(LocalDate.parse(launchDate));
        p.setOnSale(onSale);
        return p;
    }

    private void seedThree() {
        Product p1 = product(ID_1, "机械键盘 K870", "客制化机械键盘 红轴", "399.00", 100, "2026-01-15", true);
        p1.setLocation(new GeoPoint(39.90, 116.40));    // 北京
        p1.setSkus(List.of(sku("SKU-A1", "红轴 87键", 60)));

        Product p2 = product(ID_2, "无线鼠标 M590", "静音无线鼠标 办公首选", "129.50", 50, "2026-03-01", true);
        p2.setLocation(new GeoPoint(31.23, 121.47));    // 上海
        p2.setSkus(List.of(sku("SKU-B2", "静音款", 80)));

        Product p3 = product(ID_3, "显示器 U2723", "4K 专业显示器", "2899.00", 10, "2026-06-20", false);
        p3.setLocation(new GeoPoint(22.54, 114.06));    // 深圳
        p3.setSkus(List.of(sku("SKU-C3", "4K", 5), sku("SKU-D4", "2K", 10)));

        mapper.insert(p1);
        mapper.insert(p2);
        mapper.insert(p3);
        refresh();
    }

    private Sku sku(String code, String spec, int quantity) {
        Sku s = new Sku();
        s.setSkuCode(code);
        s.setSpec(spec);
        s.setQuantity(quantity);
        return s;
    }

    // ---------- CRUD 生命周期 ----------

    @Test
    void crudLifecycle() {
        // 增
        Product p = product(ID_1, "机械键盘 K870", "客制化机械键盘 红轴", "399.00", 100, "2026-01-15", true);
        assertEquals(1, mapper.insert(p));

        // 查（selectById 走 realtime get，无需 refresh）
        Product loaded = mapper.selectById(ID_1);
        assertEquals("机械键盘 K870", loaded.getProductName());
        assertEquals(new BigDecimal("399.00").compareTo(loaded.getPrice()), 0);
        assertEquals(LocalDate.parse("2026-01-15"), loaded.getLaunchDate());
        assertTrue(loaded.getOnSale());

        // 改（部分更新）
        Product patch = new Product();
        patch.setId(ID_1);
        patch.setPrice(new BigDecimal("459.00"));
        assertEquals(1, mapper.updateById(patch));
        // BigDecimal 经 Jackson 反序列化后 scale 可能变化，用 compareTo 比较数值
        assertEquals(0, mapper.selectById(ID_1).getPrice().compareTo(new BigDecimal("459.00")));
        assertEquals("机械键盘 K870", mapper.selectById(ID_1).getProductName());

        // 删
        assertEquals(1, mapper.deleteById(ID_1));
        assertNull(mapper.selectById(ID_1));
        assertEquals(0, mapper.deleteById(ID_1));
    }

    @Test
    void batchOperations() {
        List<Product> batch = List.of(
                product(ID_1, "机械键盘 K870", "客制化机械键盘 红轴", "399.00", 100, "2026-01-15", true),
                product(ID_2, "无线鼠标 M590", "静音无线鼠标 办公首选", "129.50", 50, "2026-03-01", true),
                product(ID_3, "显示器 U2723", "4K 专业显示器", "2899.00", 10, "2026-06-20", false));

        assertEquals(3, mapper.insertBatch(batch));
        refresh();

        assertEquals(3, mapper.selectBatchIds(List.of(ID_1, ID_2, ID_3)).size());
        assertEquals(3, mapper.deleteBatchIds(List.of(ID_1, ID_2, ID_3)));
        refresh();
        assertEquals(0, mapper.selectCount(null));
    }

    // ---------- 条件查询 ----------

    @Test
    void conditionQueries() {
        seedThree();

        // eq / count
        assertEquals(2L, mapper.selectCount(new EsLambdaQueryWrapper<Product>()
                .eq(Product::getOnSale, true)));

        // gt + orderByDesc（数值范围）
        List<Product> expensive = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .gt(Product::getPrice, new BigDecimal("200"))
                .orderByDesc(Product::getPrice));
        assertEquals(2, expensive.size());
        assertEquals(ID_3, expensive.get(0).getId());

        // like：keyword 通配符
        assertEquals(1, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .like(Product::getProductName, "键盘")).size());

        // match：text 分词检索
        assertEquals(1, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .match(Product::getDescription, "无线")).size());

        // between
        assertEquals(2, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .between(Product::getPrice, new BigDecimal("100"), new BigDecimal("500"))).size());

        // or：AND 优先于 OR —— (onSale=false) OR (price<=200 AND stock>=50)
        assertEquals(2, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .eq(Product::getOnSale, false)
                .or()
                .le(Product::getPrice, new BigDecimal("200"))
                .ge(Product::getStock, 50)).size());

        // 嵌套分组：onSale=false OR (price<=200 AND stock>=50) 等价写法
        assertEquals(2, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .eq(Product::getOnSale, false)
                .or(w -> w.le(Product::getPrice, new BigDecimal("200"))
                        .ge(Product::getStock, 50))).size());

        // selectOne：唯一命中返回，多条抛异常
        Product one = mapper.selectOne(new EsLambdaQueryWrapper<Product>().eq(Product::getId, ID_1));
        assertEquals(ID_1, one.getId());
        assertThrows(EsOpsException.class, () -> mapper.selectOne(
                new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, true)));
    }

    // ---------- 分页 ----------

    @Test
    void pagination() {
        seedThree();

        Page<Product> page = mapper.selectPage(new Page<>(1, 2),
                new EsLambdaQueryWrapper<Product>().orderByAsc(Product::getPrice));
        assertEquals(3, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertEquals(ID_2, page.getRecords().get(0).getId());   // 129.50 最便宜
        assertEquals(ID_1, page.getRecords().get(1).getId());   // 399.00 次之

        Page<Product> second = mapper.selectPage(new Page<>(2, 2),
                new EsLambdaQueryWrapper<Product>().orderByAsc(Product::getPrice));
        assertEquals(1, second.getRecords().size());
        assertEquals(ID_3, second.getRecords().get(0).getId());

        // 越界窗口应抛异常
        assertThrows(EsOpsException.class,
                () -> mapper.selectPage(new Page<>(2, 9000), null));
    }

    // ---------- 高亮 ----------

    @Test
    void highlight() {
        seedThree();

        List<EsHit<Product>> hits = mapper.selectHighlighted(
                new EsLambdaQueryWrapper<Product>().match(Product::getDescription, "无线"),
                EsHighlight.of(Product::getProductName, Product::getDescription).preTag("<b>").postTag("</b>"));

        assertEquals(1, hits.size());
        EsHit<Product> hit = hits.get(0);
        assertEquals(ID_2, hit.getEntity().getId());

        // text 字段高亮：分词片段含自定义 <b> 标签
        List<String> descFragments = hit.highlightsOf("description");
        assertTrue(descFragments != null && descFragments.stream().anyMatch(s -> s.contains("<b>")),
                "description 应含 <b> 标签的高亮片段: " + descFragments);
    }

    // ---------- 聚合 ----------

    @Test
    void aggregation() {
        seedThree();

        EsAggResult result = mapper.aggregate(new EsLambdaQueryWrapper<Product>()
                        .le(Product::getPrice, new BigDecimal("3000")),
                EsAgg.terms(Product::getOnSale),
                EsAgg.avg(Product::getPrice).as("avgPrice"),
                EsAgg.max(Product::getPrice).as("maxPrice"),
                EsAgg.stats(Product::getStock),
                EsAgg.cardinality(Product::getProductName));

        // terms：boolean 字段的桶键为 ES 原生数值语义（true→1 / false→0）
        var buckets = result.buckets("onSale");
        assertEquals(2, buckets.size());
        assertTrue(buckets.stream().anyMatch(b -> Long.valueOf(1L).equals(b.getKey()) && b.getCount() == 2));
        assertTrue(buckets.stream().anyMatch(b -> Long.valueOf(0L).equals(b.getKey()) && b.getCount() == 1));

        // avg：(399 + 129.5 + 2899) / 3 = 1142.5
        assertEquals(1142.5, result.value("avgPrice"), 0.01);
        assertEquals(2899.0, result.value("maxPrice"), 0.01);

        // stats：库存 100 + 50 + 10 = 160
        assertEquals(160L, result.stats("stock").getSum().longValue());

        // cardinality：三个不同商品名（未 as() 时聚合名默认为属性名）
        assertEquals(3.0, result.value("productName"), 0.001);
    }

    @Test
    void termsAggSize() {
        seedThree();

        // productName 有 3 个不同值：不设 size 时默认 100 → 全量 3 桶
        assertEquals(3, mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.terms(Product::getProductName)).buckets("productName").size());

        // size(2) → 只返回前 2 桶（高基数字段可显式控桶数）
        assertEquals(2, mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.terms(Product::getProductName).size(2)).buckets("productName").size());

        // size 仅 terms 可用，其他类型直接拒绝
        assertThrows(IllegalArgumentException.class, () -> EsAgg.avg(Product::getPrice).size(2));
        // 非正数拒绝
        assertThrows(IllegalArgumentException.class, () -> EsAgg.terms(Product::getProductName).size(0));
    }

    // ---------- 查询增强续（九期） ----------

    @Test
    void multiMatchTypeAndOperator() {
        seedThree();

        // PHRASE 短语检索：「无线鼠标」在 ID_2 描述中按序出现 → 1 条；乱序「鼠标 无线」→ 0 条
        assertEquals(1, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .multiMatch(EsMultiMatch.type(EsMultiMatch.MatchType.PHRASE), "无线鼠标",
                        Product::getDescription)).size());
        assertEquals(0, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .multiMatch(EsMultiMatch.type(EsMultiMatch.MatchType.PHRASE), "鼠标 无线",
                        Product::getDescription)).size());

        // operatorAnd：默认 OR 时「静音 4K」命中 ID_2 + ID_3 两条；AND 后无一文档同时含两词 → 0 条
        assertEquals(2, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .multiMatch("静音 4K", Product::getDescription)).size());
        assertEquals(0, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .multiMatch(EsMultiMatch.type(EsMultiMatch.MatchType.BEST_FIELDS).operatorAnd(),
                        "静音 4K", Product::getDescription)).size());

        // operatorAnd 命中场景：「静音 无线」均只在 ID_2 描述出现 → 1 条
        assertEquals(1, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .multiMatch(EsMultiMatch.type(EsMultiMatch.MatchType.BEST_FIELDS).operatorAnd(),
                        "静音 无线", Product::getDescription)).size());
    }

    @Test
    void scriptQuery() {
        seedThree();

        // 带 params：库存 > 50 → 仅 ID_1（100）
        var hits = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .script("doc['stock'].value > params.min", Map.of("min", 50)));
        assertEquals(1, hits.size());
        assertEquals(ID_1, hits.get(0).getId());

        // 无 params：库存 >= 50 → ID_1 + ID_2
        var hits2 = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .script("doc['stock'].value >= 50"));
        assertEquals(2, hits2.size());

        // 与普通条件组合：script 过滤 + keyword 等值
        var combined = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .script("doc['stock'].value >= 10")
                .eq(Product::getOnSale, false));
        assertEquals(1, combined.size());
        assertEquals(ID_3, combined.get(0).getId());

        // 空 source 拒绝
        assertThrows(EsOpsException.class, () -> new EsLambdaQueryWrapper<Product>().script(" "));
    }

    @Test
    void collapseDedup() {
        long id4 = 90004L;
        try {
            seedThree();
            // 再插入一条同名商品（与 ID_1 同 productName）
            mapper.insert(product(id4, "机械键盘 K870", "客制化机械键盘 茶轴", "459.00", 80, "2026-02-10", true));
            refresh();

            // 不折叠：同名 2 条
            assertEquals(2, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                    .eq(Product::getProductName, "机械键盘 K870")).size());

            // collapse 折叠后：每组仅保留 1 条 → 1 条
            var deduped = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                    .eq(Product::getProductName, "机械键盘 K870")
                    .collapse(Product::getProductName));
            assertEquals(1, deduped.size());
            assertNotNull(deduped.get(0));

            // 全量 collapse：4 条文档去重同名 → 3 条
            assertEquals(3, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                    .collapse(Product::getProductName)).size());
        } finally {
            mapper.deleteById(id4);
            refresh();
        }
    }

    // ---------- nested 进阶（十期） ----------

    @Test
    void nestedSort() {
        seedThree();

        // 无过滤：按 SKU 数量降序 → ID_2(80) → ID_1(60) → ID_3(最大 10)
        var byQty = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .orderByNested(Product::getSkus, Sku.class, Sku::getQuantity, false));
        assertEquals(List.of(ID_2, ID_1, ID_3), byQty.stream().map(Product::getId).toList());

        // 带子过滤：仅 spec=4K 的 SKU 参与排序（只有 ID_3 有 4K SKU）→ ID_3 在前，其余 missing 后置
        var filtered = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .orderByNested(Product::getSkus, Sku.class, Sku::getQuantity, false,
                        w -> w.eq(Sku::getSpec, "4K")));
        assertEquals(ID_3, filtered.get(0).getId());
        assertEquals(3, filtered.size());

        // 非 nested 字段直接拒绝
        assertThrows(EsOpsException.class, () -> mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .orderByNested(Product::getPrice, Product.class, Product::getPrice, true)));
    }

    @Test
    void nestedAgg() {
        seedThree();

        // nested 桶：共 4 条 SKU 文档（1+1+2）；avg quantity = (60+80+5+10)/4 = 38.75
        EsAggResult result = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.nested(Product::getSkus, EsAgg.avg(Sku::getQuantity).as("avgQty")));
        EsBucket bucket = result.nested("skus");
        assertNull(bucket.getKey());
        assertEquals(4, bucket.getCount());
        assertEquals(38.75, bucket.getAggs().value("avgQty"), 0.01);

        // 外层条件过滤父文档（仅下架的 ID_3）：2 条 SKU，max quantity = 10
        EsAggResult filtered = mapper.aggregate(new EsLambdaQueryWrapper<Product>()
                        .eq(Product::getOnSale, false),
                EsAgg.nested(Product::getSkus, EsAgg.max(Sku::getQuantity).as("maxQty")));
        assertEquals(2, filtered.nested("skus").getCount());
        assertEquals(10.0, filtered.nested("skus").getAggs().value("maxQty"), 0.01);

        // 非 nested 字段直接拒绝
        assertThrows(EsOpsException.class, () -> mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.nested(Product::getPrice)));
    }

    @Test
    void nestedInnerHits() {
        seedThree();

        // SKU-C3 命中 → 父 ID_3 + 命中子文档 SKU 实体
        var hits = mapper.selectListWithNestedHits(new EsLambdaQueryWrapper<Product>()
                .nested(Product::getSkus, Sku.class, 3, w -> w.eq(Sku::getSkuCode, "SKU-C3")), Sku.class);
        assertEquals(1, hits.size());
        assertEquals(ID_3, hits.get(0).entity().getId());
        assertEquals(1, hits.get(0).hits().size());
        assertEquals("SKU-C3", hits.get(0).hits().get(0).getSkuCode());
        assertEquals(5, hits.get(0).hits().get(0).getQuantity());

        // innerHitsSize 限制生效：命中 4K+2K 两个子文档但 size=1 → 每父文档仅 1 条命中
        var limited = mapper.selectListWithNestedHits(new EsLambdaQueryWrapper<Product>()
                .nested(Product::getSkus, Sku.class, 1, w -> w.eq(Sku::getSkuCode, "SKU-C3")
                        .or().eq(Sku::getSkuCode, "SKU-D4")), Sku.class);
        assertEquals(1, limited.size());
        assertEquals(1, limited.get(0).hits().size());

        // 无 innerHitsSize 的 nested 条件 → 拒绝
        assertThrows(EsOpsException.class, () -> mapper.selectListWithNestedHits(
                new EsLambdaQueryWrapper<Product>().nested(Product::getSkus, Sku.class,
                        w -> w.eq(Sku::getSkuCode, "SKU-A1")), Sku.class));
    }

    @Test
    void dateHistogramByMonth() {
        seedThree();

        // 上线日期跨 2026-01/03/06 三个月：month 分桶默认 minDocCount=0，
        // 数据区间（1 月~6 月）内空月份也返回 → 6 桶，key 为 epoch 毫秒且升序
        var buckets = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.dateHistogram(Product::getLaunchDate, "month")).buckets("launchDate");
        assertEquals(6, buckets.size());
        for (int i = 1; i < buckets.size(); i++) {
            assertTrue((Long) buckets.get(i).getKey() > (Long) buckets.get(i - 1).getKey());
        }
        // 6 桶中恰 3 个非空，每桶 1 条
        assertEquals(3, buckets.stream().filter(b -> b.getCount() > 0).count());
        assertTrue(buckets.stream().allMatch(b -> b.getCount() <= 1));

        // minDocCount(1) 剔除空桶 → 3 桶，每月 1 条
        var nonEmpty = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.dateHistogram(Product::getLaunchDate, "month").minDocCount(1))
                .buckets("launchDate");
        assertEquals(3, nonEmpty.size());
        assertTrue(nonEmpty.stream().allMatch(b -> b.getCount() == 1));

        // 非法 interval 直接拒绝
        assertThrows(IllegalArgumentException.class,
                () -> EsAgg.dateHistogram(Product::getLaunchDate, "fortnight"));
    }

    @Test
    void rangeAggByPrice() {
        seedThree();

        // 价格区间：budget [0,1000) → 键盘+鼠标 2 条；premium [1000,null) → 显示器 1 条
        var buckets = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.range(Product::getPrice,
                        EsAggRange.of(0.0, 1000.0).key("budget"),
                        EsAggRange.of(1000.0, null).key("premium"))).buckets("price");

        assertEquals(2, buckets.size());
        var budget = buckets.stream().filter(b -> "budget".equals(b.getKey())).findFirst().orElseThrow();
        var premium = buckets.stream().filter(b -> "premium".equals(b.getKey())).findFirst().orElseThrow();
        assertEquals(2, budget.getCount());
        assertEquals(0.0, budget.getFrom(), 0.001);
        assertEquals(1000.0, budget.getTo(), 0.001);
        assertEquals(1, premium.getCount());
        assertEquals(1000.0, premium.getFrom(), 0.001);
        assertNull(premium.getTo());

        // from >= to 直接拒绝
        assertThrows(IllegalArgumentException.class, () -> EsAggRange.of(1000.0, 0.0));
        // 双端为空直接拒绝
        assertThrows(IllegalArgumentException.class, () -> EsAggRange.of(null, null));
    }

    @Test
    void topHitsPerBucketAndRoot() {
        seedThree();

        // 桶内 topHits：每组按上线日期取最新 1 条（降序）
        var result = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.terms(Product::getOnSale)
                        .subAgg(EsAgg.topHits(1, Product::getLaunchDate).as("latest")));
        var buckets = result.buckets("onSale");
        var onSale = buckets.stream().filter(b -> Long.valueOf(1L).equals(b.getKey())).findFirst().orElseThrow();
        var offSale = buckets.stream().filter(b -> Long.valueOf(0L).equals(b.getKey())).findFirst().orElseThrow();
        // 在售组：ID_2(2026-03-01) 比 ID_1(2026-01-15) 新；下架组仅 ID_3
        assertEquals(ID_2, onSale.getAggs().hits("latest", Product.class).get(0).getId());
        assertEquals(ID_3, offSale.getAggs().hits("latest", Product.class).get(0).getId());

        // 顶层 topHits：全场价格最高 2 条（price 降序）
        var top2 = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.topHits(2, Product::getPrice)).hits("topHits", Product.class);
        assertEquals(List.of(ID_3, ID_1), top2.stream().map(Product::getId).toList());

        // topHitsAsc 升序重载：价格最低 2 条 → 鼠标(129.5) → 键盘(399)
        var low2 = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.topHitsAsc(2, Product::getPrice)).hits("topHits", Product.class);
        assertEquals(List.of(ID_2, ID_1), low2.stream().map(Product::getId).toList());

        // size<=0 拒绝
        assertThrows(IllegalArgumentException.class, () -> EsAgg.topHits(0));
    }

    @Test
    void termsOrderBySubAgg() {
        seedThree();

        // 按 avgPrice 降序：下架组(2899) 在前 → 桶序 [0, 1]
        var buckets = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.terms(Product::getOnSale)
                        .subAgg(EsAgg.avg(Product::getPrice).as("avgPrice"))
                        .orderBy("avgPrice", true)).buckets("onSale");
        assertEquals(List.of(0L, 1L), buckets.stream().map(b -> (Long) b.getKey()).toList());
        assertEquals(2899.0, buckets.get(0).getAggs().value("avgPrice"), 0.01);

        // 按 _count 降序：在售组(2 条) 在前 → 桶序 [1, 0]
        var byCount = mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.terms(Product::getOnSale).orderBy("_count", true)).buckets("onSale");
        assertEquals(List.of(1L, 0L), byCount.stream().map(b -> (Long) b.getKey()).toList());

        // 引用不存在的子聚合 → 执行前直接报错（防 ES 静默失败）
        assertThrows(EsOpsException.class, () -> mapper.aggregate(new EsLambdaQueryWrapper<Product>(),
                EsAgg.terms(Product::getOnSale).orderBy("nope", true)));

        // 非 terms 聚合调用 orderBy 直接拒绝
        assertThrows(IllegalArgumentException.class, () -> EsAgg.avg(Product::getPrice).orderBy("_count", true));
    }

    // ---------- 嵌套子聚合 ----------

    @Test
    void subAggregation() {
        seedThree();

        EsAggResult result = mapper.aggregate(null,
                EsAgg.terms(Product::getOnSale).subAgg(
                        EsAgg.avg(Product::getPrice).as("avgPrice"),
                        EsAgg.max(Product::getPrice).as("maxPrice")));

        List<EsBucket> buckets = result.buckets("onSale");
        assertEquals(2, buckets.size());
        for (EsBucket bucket : buckets) {
            boolean onSale = Long.valueOf(1L).equals(bucket.getKey());
            if (onSale) {
                // 在售两件：(399.00 + 129.50) / 2 = 264.25，最大 399.00
                assertEquals(264.25, bucket.getAggs().value("avgPrice"), 0.01);
                assertEquals(399.0, bucket.getAggs().value("maxPrice"), 0.01);
            } else {
                assertEquals(2899.0, bucket.getAggs().value("avgPrice"), 0.01);
                assertEquals(2899.0, bucket.getAggs().value("maxPrice"), 0.01);
            }
        }
    }

    // ---------- search_after 深分页 ----------

    @Test
    void searchAfterPaging() {
        seedThree();

        // 在售 2 条，每批 1 条游标翻页：不重不漏，最后 next == null
        EsAfterResult<Product> page1 = mapper.selectAfter(EsAfter.first(1),
                new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, true));
        assertEquals(2, page1.getTotal());
        assertEquals(1, page1.getRecords().size());
        assertTrue(page1.getNext() != null, "还有剩余数据时 next 不应为 null");

        EsAfterResult<Product> page2 = mapper.selectAfter(page1.getNext(),
                new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, true));
        assertEquals(1, page2.getRecords().size());
        assertNull(page2.getNext(), "取完最后一批后 next 应为 null");

        // 两页合并 = 全量且无重复
        List<Long> allIds = List.of(
                page1.getRecords().get(0).getId(),
                page2.getRecords().get(0).getId());
        assertEquals(2, allIds.stream().distinct().count());
        assertTrue(allIds.containsAll(List.of(ID_1, ID_2)));

        // 单批大于总量：一次取完，next == null
        EsAfterResult<Product> all = mapper.selectAfter(EsAfter.first(10),
                new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, true));
        assertEquals(2, all.getRecords().size());
        assertNull(all.getNext());

        // 带 orderBy 的游标翻页（验证强制 _id 二级排序不破坏业务排序）
        EsAfterResult<Product> sorted = mapper.selectAfter(EsAfter.first(1),
                new EsLambdaQueryWrapper<Product>().eq(Product::getOnSale, true)
                        .orderByDesc(Product::getPrice));
        assertEquals(ID_1, sorted.getRecords().get(0).getId());  // 399.00 在售最高
    }

    // ---------- 条件删除 / 条件更新 / 批量部分更新 ----------

    @Test
    void deleteByCondition() {
        seedThree();

        // 空条件拒绝（防全量误删）
        assertThrows(EsOpsException.class, () -> mapper.delete(null));
        assertThrows(EsOpsException.class, () -> mapper.delete(new EsLambdaQueryWrapper<>()));

        // 条件删除：onSale=false → 仅深圳
        assertEquals(1, mapper.delete(new EsLambdaQueryWrapper<Product>()
                .eq(Product::getOnSale, false)));
        refresh();
        assertEquals(2, mapper.selectCount(null));
        assertNull(mapper.selectById(ID_3));
        assertNotNull(mapper.selectById(ID_1));
    }

    @Test
    void updateByCondition() {
        seedThree();

        Product patch = new Product();
        // null patch / 空条件 / 空 patch 均拒绝
        assertThrows(EsOpsException.class, () -> mapper.update(null,
                new EsLambdaQueryWrapper<Product>().eq(Product::getId, ID_1)));
        assertThrows(EsOpsException.class, () -> mapper.update(patch, null));
        assertThrows(EsOpsException.class, () -> mapper.update(patch, new EsLambdaQueryWrapper<>()));

        // 条件更新：在售商品（北京+上海）价格全部改为 500
        patch.setPrice(new BigDecimal("500"));
        assertEquals(2, mapper.update(patch, new EsLambdaQueryWrapper<Product>()
                .eq(Product::getOnSale, true)));
        Product p = mapper.selectById(ID_1);
        assertEquals(0, p.getPrice().compareTo(new BigDecimal("500")));
        // 未提及字段保持原值
        assertEquals("机械键盘 K870", p.getProductName());

        // 条件更新与 nested 条件组合：含 SKU-C3（深圳）库存改为 1
        Product patch2 = new Product();
        patch2.setStock(1);
        assertEquals(1, mapper.update(patch2, new EsLambdaQueryWrapper<Product>()
                .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-C3"))));
        assertEquals(1, mapper.selectById(ID_3).getStock());
    }

    @Test
    void batchPartialUpdate() {
        seedThree();

        // 空集合返回 0；缺主键拒绝
        assertEquals(0, mapper.updateBatchById(List.of()));
        assertThrows(EsOpsException.class, () -> mapper.updateBatchById(List.of(new Product())));

        // 批量部分更新：不同实体改不同字段
        Product u1 = new Product();
        u1.setId(ID_1);
        u1.setPrice(new BigDecimal("459.00"));
        Product u2 = new Product();
        u2.setId(ID_2);
        u2.setStock(999);
        assertEquals(2, mapper.updateBatchById(List.of(u1, u2)));
        refresh();

        assertEquals(0, mapper.selectById(ID_1).getPrice().compareTo(new BigDecimal("459.00")));
        assertEquals(100, mapper.selectById(ID_1).getStock());       // 未提及字段不变
        assertEquals(999, mapper.selectById(ID_2).getStock());
        assertEquals(0, mapper.selectById(ID_2).getPrice().compareTo(new BigDecimal("129.50")));
    }

    // ---------- 查询增强：multiMatch / fuzzy / prefix / boost ----------

    @Test
    void multiMatchAndBoost() {
        seedThree();

        // multi_match 跨字段："静音" 命中 ID_2 的 description
        List<Product> hits = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .multiMatch("静音", Product::getProductName, Product::getDescription));
        assertEquals(1, hits.size());
        assertEquals(ID_2, hits.get(0).getId());

        // boost 调整 or 场景相关性排序：键盘 加权 10 倍 → ID_1 排前（match 仅适用 text 字段）
        List<Product> boosted = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .match(Product::getDescription, "键盘", 10f)
                .or()
                .match(Product::getDescription, "鼠标"));
        assertEquals(2, boosted.size());
        assertEquals(ID_1, boosted.get(0).getId());

        // 反向：鼠标 加权 → ID_2 排前
        List<Product> reversed = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .match(Product::getDescription, "键盘")
                .or()
                .match(Product::getDescription, "鼠标", 10f));
        assertEquals(2, reversed.size());
        assertEquals(ID_2, reversed.get(0).getId());
    }

    @Test
    void fuzzyQuery() {
        seedThree();

        // AUTO 容错：K871 与 "机械键盘 K870" 编辑距离 1 → 命中 ID_1
        List<Product> hits = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .fuzzy(Product::getProductName, "机械键盘 K871"));
        assertEquals(1, hits.size());
        assertEquals(ID_1, hits.get(0).getId());

        // 显式 maxEdits=0：距离 1 不满足 → 无命中
        assertEquals(0, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .fuzzy(Product::getProductName, "机械键盘 K871", 0)).size());

        // 距离过远（"K860" vs 全词）→ 无命中
        assertEquals(0, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .fuzzy(Product::getProductName, "K860")).size());

        // text 字段拒绝
        assertThrows(EsOpsException.class, () -> mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .fuzzy(Product::getDescription, "键盘")));
    }

    @Test
    void prefixQuery() {
        seedThree();

        // keyword 前缀：机械 → 仅 ID_1
        List<Product> hits = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .prefix(Product::getProductName, "机械"));
        assertEquals(1, hits.size());
        assertEquals(ID_1, hits.get(0).getId());

        // 前缀 无线 → 仅 ID_2
        assertEquals(1, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .prefix(Product::getProductName, "无线")).size());

        // text 字段拒绝
        assertThrows(EsOpsException.class, () -> mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .prefix(Product::getDescription, "键盘")));
    }

    // ---------- Geo ----------

    @Test
    void geoDistanceQueryAndSort() {
        seedThree();

        // geo_distance：距北京 1500km 内 → 北京 + 上海（深圳约 1945km 被排除）
        List<Product> near = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .geoDistance(Product::getLocation, "1500km", new GeoPoint(39.90, 116.40)));
        assertEquals(2, near.size());
        assertTrue(near.stream().allMatch(p -> p.getId() == ID_1 || p.getId() == ID_2));

        // 距离升序：北京 → 上海 → 深圳
        List<Product> byDistance = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .orderByGeoDistance(Product::getLocation, new GeoPoint(39.90, 116.40), true));
        assertEquals(List.of(ID_1, ID_2, ID_3), byDistance.stream().map(Product::getId).toList());

        // geo 条件与普通条件组合：1500km 内且在售 → 北京 + 上海（两者均在售）
        List<Product> combined = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .geoDistance(Product::getLocation, "1500km", new GeoPoint(39.90, 116.40))
                .eq(Product::getOnSale, true));
        assertEquals(2, combined.size());
        assertTrue(combined.stream().allMatch(p -> p.getId() == ID_1 || p.getId() == ID_2));
    }

    // ---------- Nested ----------

    @Test
    void nestedQueryAndRoundTrip() {
        seedThree();

        // nested 条件：SKU 编号 SKU-A1 → 仅北京仓商品
        List<Product> hits = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-A1")));
        assertEquals(1, hits.size());
        assertEquals(ID_1, hits.get(0).getId());

        // nested 内多条件（AND）：spec=4K 且 quantity<=5 → 仅深圳
        List<Product> combined = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSpec, "4K").le(Sku::getQuantity, 5)));
        assertEquals(1, combined.size());
        assertEquals(ID_3, combined.get(0).getId());

        // nested 条件与顶层条件组合：SKU-A1 且在售 → 北京
        assertEquals(1, mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-A1"))
                .eq(Product::getOnSale, true)).size());

        // 写入→读取往返：nested 子文档字段重命名可逆，子实体完整还原
        Product loaded = mapper.selectById(ID_3);
        assertEquals(2, loaded.getSkus().size());
        assertEquals("SKU-C3", loaded.getSkus().get(0).getSkuCode());
        assertEquals("4K", loaded.getSkus().get(0).getSpec());
        assertEquals(5, loaded.getSkus().get(0).getQuantity());

        // geo + nested 同时使用：距北京 1500km 内且含 SKU-B2 → 上海
        List<Product> both = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .geoDistance(Product::getLocation, "1500km", new GeoPoint(39.90, 116.40))
                .nested(Product::getSkus, Sku.class, w -> w.eq(Sku::getSkuCode, "SKU-B2")));
        assertEquals(1, both.size());
        assertEquals(ID_2, both.get(0).getId());
    }

    // ---------- limit 与上限保护 ----------

    @Test
    void limitClause() {
        seedThree();

        // limit(2)：3 条命中只取前 2 条
        List<Product> limited = mapper.selectList(new EsLambdaQueryWrapper<Product>().limit(2));
        assertEquals(2, limited.size());

        // limit 与条件组合：在售 2 条 + limit(1) → 1 条
        List<Product> filtered = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                .eq(Product::getOnSale, true)
                .limit(1));
        assertEquals(1, filtered.size());

        // selectHighlighted 同样生效：limit(1) 只返回 1 条高亮
        List<EsHit<Product>> hits = mapper.selectHighlighted(
                new EsLambdaQueryWrapper<Product>().match(Product::getDescription, "键盘").limit(1),
                EsHighlight.of(Product::getDescription));
        assertEquals(1, hits.size());
    }

    @Test
    void limitValidation() {
        // 非正数 → 构建时报错
        assertThrows(EsOpsException.class, () -> new EsLambdaQueryWrapper<Product>().limit(0));
        // 超出 from+size 窗口上限 10000 → 构建时报错并提示深分页
        assertThrows(EsOpsException.class, () -> new EsLambdaQueryWrapper<Product>().limit(20000));
    }

    @Test
    void selectListOverCapThrows() {
        long baseId = 99000L;
        int total = 1001;
        try {
            // 写入 1001 条（超出默认上限 1000）
            List<Product> batch = new java.util.ArrayList<>();
            for (int i = 1; i <= total; i++) {
                batch.add(product(baseId + i, "批量商品 " + i, "压测数据 " + i,
                        "1.00", 1, "2026-01-01", true));
            }
            assertEquals(total, mapper.insertBatch(batch));
            refresh();

            // 未显式 limit 且命中 1001 > 1000 → 显式报错而非静默截断
            EsOpsException ex = assertThrows(EsOpsException.class,
                    () -> mapper.selectList(new EsLambdaQueryWrapper<Product>()
                            .gt(Product::getId, baseId)));
            assertTrue(ex.getMessage().contains("超过单次上限 1000"));

            // 显式 limit(1001) → 视为已知规模，正常返回全部
            List<Product> all = mapper.selectList(new EsLambdaQueryWrapper<Product>()
                    .gt(Product::getId, baseId)
                    .limit(1001));
            assertEquals(total, all.size());
        } finally {
            mapper.delete(new EsLambdaQueryWrapper<Product>().gt(Product::getId, baseId));
            refresh();
        }
    }

    @Test
    void opsExistsDropAndCreateNew() {
        // createNew → 时间戳后缀新物理索引，exists=true → drop 后 false
        String fresh = ops.createNew(Product.class);
        assertTrue(fresh.startsWith("mpes_product-"));
        try {
            assertTrue(ops.exists(fresh));
        } finally {
            ops.drop(fresh);
        }
        assertTrue(!ops.exists(fresh));
        // 不存在的索引 drop → 显式报错
        assertThrows(EsOpsException.class, () -> ops.drop(fresh));
    }

    @Test
    void opsReindexCopiesData() throws IOException {
        seedThree();
        refresh();
        String fresh = ops.createNew(Product.class);
        try {
            ReindexReport report = ops.reindex("mpes_product", fresh);
            assertEquals(3, report.total());
            assertEquals(3, report.created());
            client.indices().refresh(r -> r.index(fresh));
            assertEquals(3L, client.count(c -> c.index(fresh)).count());
        } finally {
            ops.drop(fresh);
        }
    }

    @Test
    void opsAliasAddSwapAndList() {
        String i1 = ops.createNew(Product.class);
        String i2 = ops.createNew(Product.class);
        try {
            // 不存在的 alias → 空列表
            assertTrue(ops.aliasIndexes("mpes_product_alias").isEmpty());

            ops.aliasAdd("mpes_product_alias", i1);
            assertEquals(List.of(i1), ops.aliasIndexes("mpes_product_alias"));

            // 原子切换：单请求 remove + add，指向变化
            ops.aliasSwap("mpes_product_alias", i1, i2);
            assertEquals(List.of(i2), ops.aliasIndexes("mpes_product_alias"));
        } finally {
            ops.drop(i1);
            ops.drop(i2);
        }
    }

    @Test
    void opsRebuildFromPhysicalIndexMigratesData() throws IOException {
        resetToPhysicalIndex();
        try {
            // 存量首次：mpes_product 是物理索引且有数据
            seedThree();
            refresh();
            assertEquals(3L, mapper.selectCount(null));   // 前置校验：reindex 前数据可见

            RebuildResult result = ops.rebuild(Product.class);
            assertTrue(result.freshIndex().startsWith("mpes_product-"));
            assertEquals("mpes_product", result.previousIndex());
            assertEquals(3, result.reindexed());

            // mpes_product 升格为 alias 指向新索引（旧物理索引删除后同名 alias 接管）
            assertEquals(List.of(result.freshIndex()), ops.aliasIndexes("mpes_product"));

            // 查询链路走 alias 正常读
            assertEquals(3, mapper.selectCount(null));
        } finally {
            resetToPhysicalIndex();
        }
    }

    @Test
    void opsRebuildFromAliasAtomicSwap() throws IOException {
        resetToPhysicalIndex();
        try {
            seedThree();
            refresh();
            // 先做一次 rebuild 进入 alias 语义
            String first = ops.rebuild(Product.class).freshIndex();
            // 写入新数据后二次 rebuild（用独立 ID 避免与 seed 冲突）
            mapper.insert(product(90004L, "重建后新增", "rebuild 二次校验", "1.00", 1, "2026-02-01", true));
            refresh();

            RebuildResult result = ops.rebuild(Product.class);
            assertTrue(result.freshIndex().startsWith("mpes_product-"));
            assertEquals(first, result.previousIndex());
            assertEquals(4, result.reindexed());

            // alias 原子切到最新索引，首建索引已退役，数据全量保留
            assertEquals(List.of(result.freshIndex()), ops.aliasIndexes("mpes_product"));
            assertTrue(!ops.exists(first));
            assertEquals(4L, mapper.selectCount(null));
        } finally {
            resetToPhysicalIndex();
        }
    }

    /** 把 mpes_product 恢复为干净空物理索引（拆 alias / 删残留时间戳索引），保证测试顺序无关 */
    private void resetToPhysicalIndex() {
        List<String> pointed = ops.aliasIndexes("mpes_product");
        // rebuild 遗留的孤儿物理索引（不在 alias 指向内的 mpes_product-*）
        try {
            for (String name : client.indices().get(g -> g.index("mpes_product-*")).result().keySet()) {
                if (!name.equals("mpes_product") && !pointed.contains(name)) {
                    ops.drop(name);
                }
            }
        } catch (IOException e) {
            throw new EsOpsException("清理遗留索引失败", e);
        }
        // 拆 alias：删掉指向的物理索引（alias 随之消失），重建同名空物理索引
        for (String idx : pointed) {
            ops.drop(idx);
        }
        if (!ops.exists("mpes_product")) {
            // 必须带实体 mapping 建（空 mapping 会被动态映射污染，破坏后续 nested/keyword 测试）
            ops.create("mpes_product", Product.class);
        }
        mapper.deleteBatchIds(List.of(ID_1, ID_2, ID_3, 90004L));
        refresh();
    }
}
