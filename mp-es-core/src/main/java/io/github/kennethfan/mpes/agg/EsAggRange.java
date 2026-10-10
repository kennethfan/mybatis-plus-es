package io.github.kennethfan.mpes.agg;

/**
 * range 聚合的单个区间：from（含）~ to（不含），任一端为 null 表示开区间。
 * 可选 {@link #key(String)} 命名桶；未命名时 ES 自动生成「from-to」串 key（开端为 *）。
 */
public record EsAggRange(Double from, Double to, String key) {

    public static EsAggRange of(Double from, Double to) {
        return new EsAggRange(from, to, null);
    }

    /** 命名桶（返回新 record，不可变风格） */
    public EsAggRange key(String key) {
        return new EsAggRange(from, to, key);
    }

    public EsAggRange {
        if (from == null && to == null) {
            throw new IllegalArgumentException("range 区间的 from 与 to 不能同时为空");
        }
        if (from != null && to != null && from >= to) {
            throw new IllegalArgumentException("range 区间要求 from < to，实际 [" + from + ", " + to + ")");
        }
    }
}