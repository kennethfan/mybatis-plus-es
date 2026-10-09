package io.github.kennethfan.mpes.sample;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.github.kennethfan.mpes.page.Page;
import io.github.kennethfan.mpes.sample.entity.Product;
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
        mapper.insert(product(ID_1, "机械键盘 K870", "客制化机械键盘 红轴", "399.00", 100, "2026-01-15", true));
        mapper.insert(product(ID_2, "无线鼠标 M590", "静音无线鼠标 办公首选", "129.50", 50, "2026-03-01", true));
        mapper.insert(product(ID_3, "显示器 U2723", "4K 专业显示器", "2899.00", 10, "2026-06-20", false));
        refresh();
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
}
