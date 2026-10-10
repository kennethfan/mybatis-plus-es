package io.github.kennethfan.mpes.core;

import java.util.List;

/**
 * nested inner_hits 检索结果：父实体 + 命中的子文档。
 *
 * @param entity 父实体
 * @param hits   该父文档命中的子文档（按 inner_hits 排序，条数 ≤ wrapper nested() 的 innerHitsSize）
 */
public record NestedHit<T, C>(T entity, List<C> hits) {
}