package io.github.kennethfan.mpes.agg;

import io.github.kennethfan.mpes.core.SFunction;
import io.github.kennethfan.mpes.support.LambdaUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 聚合描述（Lambda 静态工厂，API 对齐 MP 风格）。
 * <p>
 * 一期七种：terms / avg / max / min / sum / stats / cardinality；
 * 聚合名默认取属性名，可 {@link #as(String)} 显式命名；三期支持 {@link #subAgg(EsAgg...)} 桶内嵌套。
 */
public class EsAgg {

    public enum Type { TERMS, AVG, MAX, MIN, SUM, STATS, CARDINALITY }

    private final Type type;
    private final String property;
    private final List<EsAgg> children = new ArrayList<>();
    private String name;

    private EsAgg(Type type, String property) {
        this.type = type;
        this.property = property;
    }

    /** 分组计数（桶） */
    public static <T> EsAgg terms(SFunction<T, ?> col) {
        return new EsAgg(Type.TERMS, LambdaUtils.propertyName(col));
    }

    /** 平均值 */
    public static <T> EsAgg avg(SFunction<T, ?> col) {
        return new EsAgg(Type.AVG, LambdaUtils.propertyName(col));
    }

    /** 最大值 */
    public static <T> EsAgg max(SFunction<T, ?> col) {
        return new EsAgg(Type.MAX, LambdaUtils.propertyName(col));
    }

    /** 最小值 */
    public static <T> EsAgg min(SFunction<T, ?> col) {
        return new EsAgg(Type.MIN, LambdaUtils.propertyName(col));
    }

    /** 求和 */
    public static <T> EsAgg sum(SFunction<T, ?> col) {
        return new EsAgg(Type.SUM, LambdaUtils.propertyName(col));
    }

    /** count/min/max/avg/sum 一次获取 */
    public static <T> EsAgg stats(SFunction<T, ?> col) {
        return new EsAgg(Type.STATS, LambdaUtils.propertyName(col));
    }

    /** 去重计数 */
    public static <T> EsAgg cardinality(SFunction<T, ?> col) {
        return new EsAgg(Type.CARDINALITY, LambdaUtils.propertyName(col));
    }

    /** 显式命名聚合结果（默认为属性名） */
    public EsAgg as(String name) {
        this.name = name;
        return this;
    }

    /**
     * 挂载子聚合（仅 terms 类分桶聚合有意义；支持任意深度链式嵌套）。
     * 子聚合名在每个桶内独立命名，与兄弟层级无冲突。
     */
    public EsAgg subAgg(EsAgg... children) {
        if (children == null || children.length == 0) {
            throw new IllegalArgumentException("subAgg 至少需要一个子聚合");
        }
        this.children.addAll(Arrays.asList(children));
        return this;
    }

    public Type getType() {
        return type;
    }

    public String getProperty() {
        return property;
    }

    public List<EsAgg> getChildren() {
        return children;
    }

    public String getName() {
        return name != null ? name : property;
    }
}
