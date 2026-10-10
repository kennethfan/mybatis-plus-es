package io.github.kennethfan.mpes.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import io.github.kennethfan.mpes.core.EsEntityRegistry;
import io.github.kennethfan.mpes.metadata.EntityMetadata;
import io.github.kennethfan.mpes.support.EsOpsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 索引运维（十一期）：基础件。独立于 {@link IndexManager}（启动托管），
 * 面向「改实体必须删索引」痛点的运行期运维能力：探测 / 删除 / 按最新 mapping 建新索引 / 全量搬迁。
 *
 * <p>alias 原子切换与一键重建见 {@link EsIndexOps} 同类后续方法（aliasSwap / rebuild）。
 */
@Slf4j
@RequiredArgsConstructor
public class EsIndexOps {

    /** 时间戳后缀（毫秒精度）：同一秒内连续 rebuild 不撞名 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ElasticsearchClient client;
    private final EsEntityRegistry registry;

    /** 索引（或 alias）是否存在 */
    public boolean exists(String index) {
        try {
            return client.indices().exists(ExistsRequest.of(e -> e.index(index)
                    .allowNoIndices(false).expandWildcards(ExpandWildcard.None))).value();
        } catch (IOException e) {
            throw new EsOpsException("探测索引失败: " + index, e);
        }
    }

    /** 删除物理索引（不存在则报错；alias 不适用） */
    public void drop(String index) {
        if (!exists(index)) {
            throw new EsOpsException("索引不存在: " + index);
        }
        try {
            client.indices().delete(DeleteIndexRequest.of(d -> d.index(index)));
            log.info("[mp-es] 已删除索引 {}", index);
        } catch (IOException e) {
            throw new EsOpsException("删除索引失败: " + index, e);
        }
    }

    /**
     * 按实体当前 mapping 创建「实体索引名-时间戳」新物理索引，返回新索引名。
     * 不写入数据、不挂 alias——纯建索引，供 reindex / rebuild 编排。
     */
    public String createNew(Class<?> entity) {
        EntityMetadata md = registry.get(entity);
        String fresh = md.getIndexName() + "-" + TS.format(LocalDateTime.now());
        createWithMapping(fresh, md);
        return fresh;
    }

    /** 全量搬迁：from → to（同步等待，conflicts=proceed 跳过冲突文档），返回执行报告 */
    public ReindexReport reindex(String from, String to) {
        if (!exists(from)) {
            throw new EsOpsException("源索引不存在: " + from);
        }
        try {
            ReindexResponse resp = client.reindex(r -> r
                    .source(s -> s.index(from))
                    .dest(d -> d.index(to))
                    .conflicts(Conflicts.Proceed));
            // reindex 完成后文档仍在目标索引的写缓冲中（不自动 refresh），主动刷新使立即可查
            client.indices().refresh(rf -> rf.index(to));
            ReindexReport report = new ReindexReport(resp.total(), resp.created(), resp.updated(),
                    resp.versionConflicts());
            log.info("[mp-es] reindex {} -> {} 完成: total={}, created={}, updated={}, conflicts={}",
                    from, to, report.total(), report.created(), report.updated(), report.versionConflicts());
            return report;
        } catch (IOException e) {
            throw new EsOpsException("reindex 失败: " + from + " -> " + to, e);
        }
    }

    /** 按实体当前 mapping 创建指定名物理索引（rebuild 编排复用，与 IndexManager#create 同构） */
    public void create(String index, Class<?> entity) {
        createWithMapping(index, registry.get(entity));
    }

    /** 按实体元数据创建物理索引（供 rebuild 编排复用，与 IndexManager#create 同构） */
    void createWithMapping(String index, EntityMetadata md) {
        try {
            client.indices().create(c -> c.index(index)
                    .mappings(m -> m.properties(IndexManager.propertiesOf(md))));
            log.info("[mp-es] 已创建索引 {}（{} 个字段）", index, md.getFields().size());
        } catch (IOException e) {
            throw new EsOpsException("创建索引失败: " + index, e);
        }
    }

    /** 给物理索引挂 alias（首次接入 alias 语义用） */
    public void aliasAdd(String alias, String index) {
        try {
            client.indices().updateAliases(u -> u.actions(
                    a -> a.add(ad -> ad.index(index).aliases(alias))));
            log.info("[mp-es] alias {} -> {}", alias, index);
        } catch (IOException e) {
            throw new EsOpsException("挂 alias 失败: " + alias + " -> " + index, e);
        }
    }

    /**
     * alias 原子切换：单请求内 remove 旧索引 + add 新索引，切换瞬间查询零闪断。
     * rebuild 的核心动作。
     */
    public void aliasSwap(String alias, String removeIndex, String addIndex) {
        try {
            client.indices().updateAliases(u -> u.actions(
                    Action.of(a -> a.remove(r -> r.index(removeIndex).aliases(alias))),
                    Action.of(a -> a.add(ad -> ad.index(addIndex).aliases(alias)))));
            log.info("[mp-es] alias {} 原子切换: {} -> {}", alias, removeIndex, addIndex);
        } catch (IOException e) {
            throw new EsOpsException("alias 原子切换失败: " + alias + " " + removeIndex + " -> " + addIndex, e);
        }
    }

    /** alias 当前指向的物理索引列表（alias 不存在返回空） */
    public List<String> aliasIndexes(String alias) {
        try {
            GetAliasResponse resp = client.indices().getAlias(g -> g.name(alias));
            return resp.result().keySet().stream().sorted().toList();
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                return List.of();
            }
            throw new EsOpsException("查询 alias 失败: " + alias, e);
        } catch (IOException e) {
            throw new EsOpsException("查询 alias 失败: " + alias, e);
        }
    }

    /**
     * 一键平滑重建：按实体最新 mapping 建时间戳新索引 → 全量搬迁 → alias 切换 → 删旧索引。
     * 实体索引名自此升格为 alias 语义，重建期间查询写入不中断。
     * <p>三种起点自动识别：
     * <ol>
     *   <li>索引不存在 → 建新索引并挂 alias（空索引起步）</li>
     *   <li>物理索引（存量首次）→ 建新 → reindex → 删旧物理索引（alias 与同名物理索引不能共存）→ 挂 alias</li>
     *   <li>已是 alias → 建新 → reindex 旧物理索引 → aliasSwap 原子切换 → 删旧</li>
     * </ol>
     *
     * @return 新物理索引名、重建前的旧物理索引名（空索引起步为 null）、搬迁文档数
     */
    public RebuildResult rebuild(Class<?> entity) {
        EntityMetadata md = registry.get(entity);
        String canonical = md.getIndexName();
        String fresh = canonical + "-" + TS.format(LocalDateTime.now());
        createWithMapping(fresh, md);
        if (!exists(canonical)) {
            aliasAdd(canonical, fresh);
            log.info("[mp-es] rebuild {} 完成（空索引起步）: {}", canonical, fresh);
            return new RebuildResult(fresh, null, 0);
        }

        List<String> aliasIdx = aliasIndexes(canonical);
        if (!aliasIdx.isEmpty()) {
            // 已是 alias：原子切换，旧物理索引退役
            if (aliasIdx.size() > 1) {
                drop(fresh);
                throw new EsOpsException("alias " + canonical + " 指向 " + aliasIdx.size()
                        + " 个索引 " + aliasIdx + "，拒绝重建（请先收敛为单索引）");
            }
            String old = aliasIdx.get(0);
            long n = reindex(old, fresh).total();
            aliasSwap(canonical, old, fresh);
            drop(old);
            log.info("[mp-es] rebuild {} 完成（alias 原子切换）: {} -> {}", canonical, old, fresh);
            return new RebuildResult(fresh, old, n);
        }

        // 存量首次：canonical 是物理索引，删除后同名升格为 alias
        long n = reindex(canonical, fresh).total();
        drop(canonical);
        aliasAdd(canonical, fresh);
        log.info("[mp-es] rebuild {} 完成（存量物理索引迁移）: {}", canonical, fresh);
        return new RebuildResult(fresh, canonical, n);
    }

    /**
     * 写入索引模板：index pattern 匹配的新索引自动套用实体 mapping（滚动索引 / 分索引场景）。
     * 同名模板覆盖更新。
     */
    public void putTemplate(String name, String indexPattern, Class<?> entity) {
        EntityMetadata md = registry.get(entity);
        try {
            client.indices().putIndexTemplate(p -> p.name(name)
                    .indexPatterns(indexPattern)
                    .template(t -> t.mappings(m -> m.properties(IndexManager.propertiesOf(md)))));
            log.info("[mp-es] 已写入索引模板 {}（pattern={}，{} 个字段）", name, indexPattern, md.getFields().size());
        } catch (IOException e) {
            throw new EsOpsException("写入索引模板失败: " + name, e);
        }
    }

    /** 模板是否存在 */
    public boolean templateExists(String name) {
        try {
            return client.indices().existsIndexTemplate(e -> e.name(name)).value();
        } catch (IOException e) {
            throw new EsOpsException("探测索引模板失败: " + name, e);
        }
    }

    /** 删除索引模板 */
    public void dropTemplate(String name) {
        try {
            client.indices().deleteIndexTemplate(d -> d.name(name));
            log.info("[mp-es] 已删除索引模板 {}", name);
        } catch (IOException e) {
            throw new EsOpsException("删除索引模板失败: " + name, e);
        }
    }
}
