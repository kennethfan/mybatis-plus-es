package io.github.kennethfan.mpes.sample;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.github.kennethfan.mpes.index.EsIndexOps;
import io.github.kennethfan.mpes.index.RebuildResult;
import io.github.kennethfan.mpes.sample.entity.Product;
import io.github.kennethfan.mpes.sample.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 索引运维演示（十一期）：EsIndexOps 一键平滑重建。
 * 运行方式：<code>docker compose up -d</code> 后以 index-demo profile 启动：
 * <code>mvn -pl mp-es-sample spring-boot:run -Dspring-boot.run.profiles=index-demo</code>
 */
@Slf4j
@Component
@Profile("index-demo")
@RequiredArgsConstructor
public class IndexOpsDemo implements CommandLineRunner {

    private final EsIndexOps indexOps;
    private final ProductMapper mapper;
    private final ElasticsearchClient client;

    @Override
    public void run(String... args) throws IOException {
        log.info("========== EsIndexOps 索引运维演示 ==========");

        // 1. 存量状态：写入演示数据（mpes_product 为物理索引）
        for (long id = 70001L; id <= 70003L; id++) {
            Product p = new Product();
            p.setId(id);
            p.setProductName("演示商品 " + id);
            p.setDescription("rebuild 演示数据");
            p.setPrice(new BigDecimal("9.90"));
            p.setStock(1);
            p.setLaunchDate(LocalDate.of(2026, 1, 1));
            p.setOnSale(true);
            mapper.insert(p);
        }
        client.indices().refresh(r -> r.index("mpes_product"));
        log.info("[1] 存量状态：mpes_product 物理索引，{} 条数据", mapper.selectCount(null));

        // 2. 一键平滑重建：建时间戳新索引 → reindex 搬迁 → alias 同名升格 → 删旧物理索引
        RebuildResult result = indexOps.rebuild(Product.class);
        log.info("[2] rebuild 完成：新索引={}，旧索引={}，搬迁={} 条",
                result.freshIndex(), result.previousIndex(), result.reindexed());

        // 3. 重建后：mpes_product 已升格为 alias，Mapper 读写完全透明
        log.info("[3] alias 指向：{}，count 走 alias = {}",
                indexOps.aliasIndexes("mpes_product"), mapper.selectCount(null));

        // 4. 二次 rebuild：已是 alias → 原子切换
        RebuildResult second = indexOps.rebuild(Product.class);
        log.info("[4] 二次 rebuild（alias 原子切换）：{} -> {}，alias 指向 = {}",
                second.previousIndex(), second.freshIndex(), indexOps.aliasIndexes("mpes_product"));

        log.info("========== 演示结束（应用即将退出） ==========");
    }
}
