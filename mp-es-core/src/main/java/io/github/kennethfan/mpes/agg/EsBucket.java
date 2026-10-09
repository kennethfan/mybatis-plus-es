package io.github.kennethfan.mpes.agg;

/**
 * terms 分组桶：分组键 + 文档数 + 桶内子聚合结果。
 */
public class EsBucket {

    private final Object key;
    private final long count;
    private final EsAggResult aggs;

    public EsBucket(Object key, long count, EsAggResult aggs) {
        this.key = key;
        this.count = count;
        this.aggs = aggs;
    }

    /** 分组键（String / Long / Double，取决于字段类型；boolean 字段为 1/0） */
    public Object getKey() {
        return key;
    }

    public long getCount() {
        return count;
    }

    /** 桶内子聚合结果（未挂子聚合时为空结果集，取值会抛 EsOpsException） */
    public EsAggResult getAggs() {
        return aggs;
    }
}
