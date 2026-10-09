package io.github.kennethfan.mpes.agg;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.util.List;
import java.util.Map;

/**
 * 聚合结果：按聚合名取值。terms 类取 {@link #buckets(String)}，
 * 单值类（avg/max/min/sum/cardinality）取 {@link #value(String)}，stats 取 {@link #stats(String)}。
 */
public class EsAggResult {

    private final Map<String, Aggregate> aggregates;

    public EsAggResult(Map<String, Aggregate> aggregates) {
        this.aggregates = Map.copyOf(aggregates);
    }

    /** terms 分组桶；不存在或非 terms 类聚合抛异常 */
    public List<EsBucket> buckets(String name) {
        Aggregate agg = require(name);
        return switch (agg._kind()) {
            case Sterms -> agg.sterms().buckets().array().stream()
                    .map(b -> new EsBucket(b.key(), b.docCount(), new EsAggResult(b.aggregations()))).toList();
            case Lterms -> agg.lterms().buckets().array().stream()
                    .map(b -> new EsBucket(b.key(), b.docCount(), new EsAggResult(b.aggregations()))).toList();
            case Dterms -> agg.dterms().buckets().array().stream()
                    .map(b -> new EsBucket(b.key(), b.docCount(), new EsAggResult(b.aggregations()))).toList();
            default -> throw new EsOpsException("聚合 " + name + " 不是 terms 类型: " + agg._kind());
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

    private Aggregate require(String name) {
        Aggregate agg = aggregates.get(name);
        if (agg == null) {
            throw new EsOpsException("聚合结果不存在: " + name + "，可用: " + aggregates.keySet());
        }
        return agg;
    }
}
