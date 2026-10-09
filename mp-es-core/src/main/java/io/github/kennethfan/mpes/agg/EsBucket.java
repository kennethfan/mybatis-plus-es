package io.github.kennethfan.mpes.agg;

/**
 * terms 分组桶：分组键 + 文档数。
 */
public class EsBucket {

    private final Object key;
    private final long count;

    public EsBucket(Object key, long count) {
        this.key = key;
        this.count = count;
    }

    /** 分组键（String / Long / Double，取决于字段类型） */
    public Object getKey() {
        return key;
    }

    public long getCount() {
        return count;
    }
}
