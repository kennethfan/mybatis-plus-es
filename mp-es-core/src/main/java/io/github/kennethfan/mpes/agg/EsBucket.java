package io.github.kennethfan.mpes.agg;

/**
 * 分桶聚合（terms / dateHistogram / range）的桶：分组键 + 文档数 + 桶内子聚合结果。
 */
public class EsBucket {

    private final Object key;
    private final Double from;
    private final Double to;
    private final long count;
    private final EsAggResult aggs;

    public EsBucket(Object key, long count, EsAggResult aggs) {
        this(key, null, null, count, aggs);
    }

    public EsBucket(Object key, Double from, Double to, long count, EsAggResult aggs) {
        this.key = key;
        this.from = from;
        this.to = to;
        this.count = count;
        this.aggs = aggs;
    }

    /**
     * 分组键（String / Long / Double，取决于字段类型；boolean 字段为 1/0；
     * dateHistogram 为 epoch 毫秒；range 为区间命名或「from-to」串）
     */
    public Object getKey() {
        return key;
    }

    /** range 桶下边界（含），非 range 桶为 null */
    public Double getFrom() {
        return from;
    }

    /** range 桶上边界（不含），非 range 桶为 null */
    public Double getTo() {
        return to;
    }

    public long getCount() {
        return count;
    }

    /** 桶内子聚合结果（未挂子聚合时为空结果集，取值会抛 EsOpsException） */
    public EsAggResult getAggs() {
        return aggs;
    }
}
