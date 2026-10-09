package io.github.kennethfan.mpes.wrapper;

import io.github.kennethfan.mpes.core.SFunction;
import io.github.kennethfan.mpes.support.LambdaUtils;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lambda 链式条件构造器：API 对齐 MP 的 LambdaQueryWrapper。
 * <p>
 * 语义约定（Q9）：
 * <ul>
 *   <li>{@code like} 仅作用于 keyword 字段（通配符语义），text 字段使用会直接报错</li>
 *   <li>{@code match} 为显式分词检索入口，作用于 text/keyword 均可</li>
 *   <li>AND 优先级高于 OR：{@code a.eq(1).or().eq(2).eq(3)} → (1 OR 2) AND 3</li>
 *   <li>{@code and(w -> ...) / or(w -> ...)} 产生嵌套分组</li>
 * </ul>
 *
 * @param <T> 实体类型
 */
public class EsLambdaQueryWrapper<T> {

    enum Op { EQ, NE, IN, GT, GE, LT, LE, BETWEEN, LIKE, MATCH, IS_NULL }

    /** 叶子条件：操作符 + 属性名 + 值 */
    record Leaf(Op op, String property, List<Object> values) {}

    record SortSpec(String property, boolean asc) {}

    /** 条件节点：orToPrevious 表示与前一节点以 OR 连接；content 为 Leaf 或嵌套 Wrapper（分组） */
    record Node(boolean orToPrevious, Object content) {}

    @Getter
    private final List<Node> nodes = new ArrayList<>();

    @Getter
    private final List<SortSpec> sorts = new ArrayList<>();

    private boolean pendingOr;

    // ---------- 条件 ----------

    public EsLambdaQueryWrapper<T> eq(SFunction<T, ?> col, Object value) {
        return add(Op.EQ, col, value);
    }

    public EsLambdaQueryWrapper<T> ne(SFunction<T, ?> col, Object value) {
        return add(Op.NE, col, value);
    }

    public EsLambdaQueryWrapper<T> in(SFunction<T, ?> col, Collection<?> values) {
        return add(Op.IN, col, values.toArray());
    }

    @SafeVarargs
    public final EsLambdaQueryWrapper<T> in(SFunction<T, ?> col, Object... values) {
        return add(Op.IN, col, values);
    }

    public EsLambdaQueryWrapper<T> gt(SFunction<T, ?> col, Object value) {
        return add(Op.GT, col, value);
    }

    public EsLambdaQueryWrapper<T> ge(SFunction<T, ?> col, Object value) {
        return add(Op.GE, col, value);
    }

    public EsLambdaQueryWrapper<T> lt(SFunction<T, ?> col, Object value) {
        return add(Op.LT, col, value);
    }

    public EsLambdaQueryWrapper<T> le(SFunction<T, ?> col, Object value) {
        return add(Op.LE, col, value);
    }

    public EsLambdaQueryWrapper<T> between(SFunction<T, ?> col, Object from, Object to) {
        return add(Op.BETWEEN, col, from, to);
    }

    /** keyword 字段通配符匹配（*value*），text 字段请用 match */
    public EsLambdaQueryWrapper<T> like(SFunction<T, ?> col, String value) {
        return add(Op.LIKE, col, value);
    }

    /** 显式分词检索 */
    public EsLambdaQueryWrapper<T> match(SFunction<T, ?> col, Object value) {
        return add(Op.MATCH, col, value);
    }

    public EsLambdaQueryWrapper<T> isNull(SFunction<T, ?> col) {
        return add(Op.IS_NULL, col);
    }

    /** 下一条件以 OR 连接 */
    public EsLambdaQueryWrapper<T> or() {
        pendingOr = true;
        return this;
    }

    /** 嵌套分组，组内条件与组外以 AND 连接 */
    public EsLambdaQueryWrapper<T> and(Consumer<EsLambdaQueryWrapper<T>> consumer) {
        nodes.add(new Node(pendingOr, group(consumer)));
        pendingOr = false;
        return this;
    }

    /** 嵌套分组，组与前一节点以 OR 连接 */
    public EsLambdaQueryWrapper<T> or(Consumer<EsLambdaQueryWrapper<T>> consumer) {
        pendingOr = true;
        nodes.add(new Node(true, group(consumer)));
        pendingOr = false;
        return this;
    }

    // ---------- 排序 ----------

    @SafeVarargs
    public final EsLambdaQueryWrapper<T> orderByAsc(SFunction<T, ?>... cols) {
        for (SFunction<T, ?> col : cols) {
            sorts.add(new SortSpec(LambdaUtils.propertyName(col), true));
        }
        return this;
    }

    @SafeVarargs
    public final EsLambdaQueryWrapper<T> orderByDesc(SFunction<T, ?>... cols) {
        for (SFunction<T, ?> col : cols) {
            sorts.add(new SortSpec(LambdaUtils.propertyName(col), false));
        }
        return this;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    // ---------- 内部 ----------

    private EsLambdaQueryWrapper<T> add(Op op, SFunction<T, ?> col, Object... values) {
        nodes.add(new Node(pendingOr, new Leaf(op, LambdaUtils.propertyName(col), Arrays.asList(values))));
        pendingOr = false;
        return this;
    }

    private EsLambdaQueryWrapper<T> group(Consumer<EsLambdaQueryWrapper<T>> consumer) {
        EsLambdaQueryWrapper<T> sub = new EsLambdaQueryWrapper<>();
        consumer.accept(sub);
        return sub;
    }
}
