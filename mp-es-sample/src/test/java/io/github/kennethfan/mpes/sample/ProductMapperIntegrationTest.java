package io.github.kennethfan.mpes.sample;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.github.kennethfan.mpes.agg.EsAgg;
import io.github.kennethfan.mpes.agg.EsAggResult;
import io.github.kennethfan.mpes.agg.EsBucket;
import io.github.kennethfan.mpes.geo.GeoPoint;
import io.github.kennethfan.mpes.highlight.EsHighlight;
import io.github.kennethfan.mpes.highlight.EsHit;
import io.github.kennethfan.mpes.page.EsAfter;
import io.github.kennethfan.mpes.page.EsAfterResult;
import io.github.kennethfan.mpes.page.Page;
import io.github.kennethfan.mpes.sample.entity.Product;
import io.github.kennethfan.mpes.sample.entity.Sku;
import io.github.kennethfan.mpes.sample.mapper.ProductMapper;
import io.github.kennethfan.mpes.support.EsOpsException;
import io.github.kennethfan.mpes.wrapper.EsLambdaQueryWrapper;
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
}
