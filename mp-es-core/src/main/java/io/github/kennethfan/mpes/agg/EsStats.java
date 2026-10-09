package io.github.kennethfan.mpes.agg;

/**
 * stats 聚合结果：count/min/max/avg/sum 一次获取。
 */
public class EsStats {

    private final long count;
    private final Double min;
    private final Double max;
    private final Double avg;
    private final Double sum;

    public EsStats(long count, Double min, Double max, Double avg, Double sum) {
        this.count = count;
        this.min = min;
        this.max = max;
        this.avg = avg;
        this.sum = sum;
    }

    public long getCount() {
        return count;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Double getAvg() {
        return avg;
    }

    public Double getSum() {
        return sum;
    }
}
