package io.github.kennethfan.mpes.agg;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.util.List;
import java.util.Map;

/**
 * 聚合结果：按聚合名取值。分桶类（terms/dateHistogram）取 {@link #buckets(String)}，
 * 单值类（avg/max/min/sum/cardinality）取 {@link #value(String)}，stats 取 {@link #stats(String)}。
 */
public class EsAggResult {

    private final Map<String, Aggregate> aggregates;

    public EsAggResult(Map<String, Aggregate> aggregates) {
        this.aggregates = Map.copyOf(aggregates);
    }

    /** 分桶聚合（terms / dateHistogram）取桶；不存在或其他类型抛异常 */
    public List<EsBucket> buckets(String name) {
        Aggregate agg = require(name);
        return switch (agg._kind()) {
            case Sterms -> agg.sterms().buckets().array().stream()
                    .map(b -> new EsBucket(b.key(), b.docCount(), new EsAggResult(b.aggregations()))).toList();
            case Lterms -> agg.lterms().buckets().array().stream()
                    .map(b -> new EsBucket(b.key(), b.docCount(), new EsAggResult(b.aggregations()))).toList();
            case Dterms -> agg.dterms().buckets().array().stream()
                    .map(b -> new EsBucket(b.key(), b.docCount(), new EsAggResult(b.aggregations()))).toList();
            case DateHistogram -> agg.dateHistogram().buckets().array().stream()
                    .map(b -> new EsBucket(b.key(), b.docCount(), new EsAggResult(b.aggregations()))).toList();
            // range 聚合固定 keyed(true)，响应为 keyed map（key = 区间命名或自动「from-to」串）
            case Range -> agg.range().buckets().keyed().entrySet().stream()
                    .map(e -> new EsBucket(e.getKey(), e.getValue().from(), e.getValue().to(),
                            e.getValue().docCount(), new EsAggResult(e.getValue().aggregations()))).toList();
            default -> throw new EsOpsException("聚合 " + name + " 不是分桶类型: " + agg._kind());
        };
    }

    /** 单值聚合（avg/max/min/sum/cardinality）取 Double */
    public Double value(String name) {
        Aggregate agg = require(name);
        return switch (agg._kind()) {
            case Avg -> agg.avg().value();
            case Max -> agg.max().value();
            case Min -> agg.min().value();
            case Sum -> agg.sum().value();
            case Cardinality -> (double) agg.cardinality().value();
            default -> throw new EsOpsException("聚合 " + name + " 不是单值类型: " + agg._kind());
        };
    }

    /** stats 聚合取五元组 */
    public EsStats stats(String name) {
        Aggregate agg = require(name);
        if (agg._kind() != Aggregate.Kind.Stats) {
            throw new EsOpsException("聚合 " + name + " 不是 stats 类型: " + agg._kind());
        }
        var s = agg.stats();
        return new EsStats(s.count(), s.min(), s.max(), s.avg(), s.sum());
    }

    /**
     * nested 聚合取单桶：key=null、count=nested 文档总数，
     * 子聚合经 {@code bucket.getAggs().value(...)} 取；不存在或非 nested 抛异常。
     */
    public EsBucket nested(String name) {
        Aggregate agg = require(name);
        if (agg._kind() != Aggregate.Kind.Nested) {
            throw new EsOpsException("聚合 " + name + " 不是 nested 类型: " + agg._kind());
        }
        return new EsBucket(null, agg.nested().docCount(), new EsAggResult(agg.nested().aggregations()));
    }

    /**
     * top_hits 取文档列表，source 反序列化为给定实体类型；不存在或非 top_hits 抛异常。
     * 顶层聚合与 terms 桶内子聚合（bucket.getAggs().hits(...)）均可用。
     */
    public <T> List<T> hits(String name, Class<T> type) {
        Aggregate agg = require(name);
        if (agg._kind() != Aggregate.Kind.TopHits) {
            throw new EsOpsException("聚合 " + name + " 不是 topHits 类型: " + agg._kind());
        }
        return agg.topHits().hits().hits().stream()
                .map(h -> {
                    if (h.source() == null) {
                        throw new EsOpsException("top_hits 命中缺少 _source（聚合名 " + name + "）");
                    }
                    return h.source().to(type);
                })
                .toList();
    }

    private Aggregate require(String name) {
        Aggregate agg = aggregates.get(name);
        if (agg == null) {
            throw new EsOpsException("聚合结果不存在: " + name + "，可用: " + aggregates.keySet());
        }
        return agg;
    }
}
