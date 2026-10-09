package io.github.kennethfan.mpes.core;

import java.io.Serializable;

/**
 * 可序列化的取值函数，用于 Lambda 风格字段引用（对齐 MP 的 SFunction 形态）。
 * <p>
 * 示例：{@code wrapper.eq(Product::getName, "foo")}
 */
@FunctionalInterface
public interface SFunction<T, R> extends Serializable {

    R apply(T t);
}
