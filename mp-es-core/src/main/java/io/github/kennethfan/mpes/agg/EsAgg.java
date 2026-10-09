package io.github.kennethfan.mpes.agg;

import io.github.kennethfan.mpes.core.SFunction;
import io.github.kennethfan.mpes.support.LambdaUtils;

/**
 * 聚合描述（Lambda 静态工厂，API 对齐 MP 风格）。
 * <p>
 * 一期七种：terms / avg / max / min / sum / stats / cardinality；
 * 聚合名默认取属性名，可 {@link #as(String)} 显式命名。嵌套子聚合留三期。
 */
public class EsAgg {

    public enum Type { TERMS, AVG, MAX, MIN, SUM, STATS, CARDINALITY }

    private final Type type;
    private final String property;
    private String name;

    private EsAgg(Type type, String property) {
        this.type = type;
        this.property = property;
    }

    /** 分组计数（桶） */
    public static <T> EsAgg terms(SFunction<T, ?> col) {
        return new EsAgg(Type.TERMS, LambdaUtils.propertyName(col));
    }

    public static <T> EsAgg avg(SFunction<T, ?> col) {
        return new EsAgg(Type.AVG, LambdaUtils.propertyName(col));
    }

    public static <T> EsAgg max(SFunction<T, ?> col) {
        return new EsAgg(Type.MAX, LambdaUtils.propertyName(col));
    }

    public static <T> EsAgg min(SFunction<T, ?> col) {
        return new EsAgg(Type.MIN, LambdaUtils.propertyName(col));
    }

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

    public Type getType() {
        return type;
    }

    public String getProperty() {
        return property;
    }

    public String getName() {
        return name != null ? name : property;
    }
}
