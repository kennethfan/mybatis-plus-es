package io.github.kennethfan.mpes.core;

import io.github.kennethfan.mpes.agg.EsAgg;
import io.github.kennethfan.mpes.agg.EsAggResult;
import io.github.kennethfan.mpes.highlight.EsHighlight;
import io.github.kennethfan.mpes.highlight.EsHit;
import io.github.kennethfan.mpes.page.EsAfter;
import io.github.kennethfan.mpes.page.EsAfterResult;
import io.github.kennethfan.mpes.page.Page;
import io.github.kennethfan.mpes.wrapper.EsLambdaQueryWrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 实体 Mapper 的通用 CRUD 接口：方法签名对齐 MP 的 BaseMapper（ADR-0002：仅 API 对齐，不进 MyBatis 管线）。
 *
 * @param <T> 实体类型
 */
public interface EsBaseMapper<T> {

    /** 新增一条文档（主键必填，作为 ES _id） */
    int insert(T entity);

    /** 批量新增，返回成功数 */
    int insertBatch(Collection<T> entities);

    /** 按主键删除，命中返回 1 否则 0 */
    int deleteById(Serializable id);

    /** 批量删除，返回删除数 */
    int deleteBatchIds(Collection<? extends Serializable> idList);

    /** 条件删除（delete_by_query）：wrapper 不能为 null 或空（拒绝全量删除），返回实际删除数 */
    int delete(EsLambdaQueryWrapper<T> wrapper);

    /** 按主键更新非 null 字段（部分更新），命中返回 1 否则 0 */
    int updateById(T entity);

    /** 批量按主键部分更新（bulk update，每条取非 null 字段）；部分失败抛 EsOpsException */
    int updateBatchById(Collection<T> entities);

    /** 条件部分更新（update_by_query + painless script）：patch 取非 null 字段，wrapper 不能为 null 或空（拒绝全量更新），返回实际更新数 */
    int update(T patch, EsLambdaQueryWrapper<T> wrapper);

    /** 按主键查询 */
    T selectById(Serializable id);

    /** 按主键集合查询 */
    List<T> selectBatchIds(Collection<? extends Serializable> idList);

    /** 条件计数，wrapper 为 null 时全量计数 */
    Long selectCount(EsLambdaQueryWrapper<T> wrapper);

    /**
     * 条件查询，wrapper 为 null 时查全量。默认上限 1000 条：未显式 {@code wrapper.limit(n)} 时
     * 命中数超过 1000 直接报错（不做静默截断）；{@code limit(n)} 可放宽至 10000，
     * 更多结果请用 {@link #selectAfter} 深分页。
     */
    List<T> selectList(EsLambdaQueryWrapper<T> wrapper);

    /** 条件查单条：0 条返回 null，多条抛 {@link io.github.kennethfan.mpes.support.EsOpsException} */
    T selectOne(EsLambdaQueryWrapper<T> wrapper);

    /** 分页查询（from+size，窗口上限 10000） */
    Page<T> selectPage(Page<T> page, EsLambdaQueryWrapper<T> wrapper);

    /** 深分页（search_after 游标，无 10000 窗口限制；首页用 EsAfter.first(size)，翻页用 result.getNext()） */
    EsAfterResult<T> selectAfter(EsAfter after, EsLambdaQueryWrapper<T> wrapper);

    /** 高亮检索：返回实体 + 高亮片段（highlight 中字段需与查询条件配合使用） */
    List<EsHit<T>> selectHighlighted(EsLambdaQueryWrapper<T> wrapper, EsHighlight<T> highlight);

    /** 聚合查询：terms / avg / max / min / sum / stats / cardinality / date_histogram / range / nested / top_hits（ES 查询不返回文档，size=0） */
    EsAggResult aggregate(EsLambdaQueryWrapper<T> wrapper, EsAgg... aggs);

    /**
     * nested 检索 + inner_hits：返回父实体与命中的子文档。
     * 要求 wrapper 恰有一个带 innerHitsSize 的 nested() 条件（如 nested(Product::getSkus, Sku.class, 3, w -> ...)）。
     */
    <C> List<NestedHit<T, C>> selectListWithNestedHits(EsLambdaQueryWrapper<T> wrapper, Class<C> childType);
}
