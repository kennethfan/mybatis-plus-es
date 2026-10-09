package io.github.kennethfan.mpes.page;

import co.elastic.clients.elasticsearch._types.FieldValue;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.util.List;

/**
 * search_after 深分页游标（突破 from+size 10000 窗口）。
 * <p>
 * 用法：首页 {@code EsAfter.first(size)} → {@code mapper.selectAfter(after, wrapper)}
 * → 用返回结果的 {@code getNext()} 翻页，{@code null} 即到底。
 * 游标内部封装 ES 排序值（进程内使用；跨进程序列化留待需要时扩展）。
 */
public final class EsAfter {

    /** 单批上限对齐 ES max_result_window 语义（单批本身不应过大） */
    public static final int MAX_BATCH_SIZE = 10_000;

    private final int size;
    private final List<FieldValue> searchAfter;

    private EsAfter(int size, List<FieldValue> searchAfter) {
        this.size = size;
        this.searchAfter = searchAfter;
    }

    /** 首页游标 */
    public static EsAfter first(int size) {
        if (size < 1 || size > MAX_BATCH_SIZE) {
            throw new EsOpsException("search_after 单批 size 须在 1.." + MAX_BATCH_SIZE + "，实际: " + size);
        }
        return new EsAfter(size, null);
    }

    /** 由代理层用上一批最后一条命中的排序值构造下一页游标 */
    public static EsAfter of(int size, List<FieldValue> searchAfter) {
        if (size < 1 || size > MAX_BATCH_SIZE) {
            throw new EsOpsException("search_after 单批 size 须在 1.." + MAX_BATCH_SIZE + "，实际: " + size);
        }
        return new EsAfter(size, searchAfter);
    }

    public int getSize() {
        return size;
    }

    /** ES 排序值（首页为 null）；内部翻页结构，业务代码请勿依赖其内容 */
    public List<FieldValue> getSearchAfter() {
        return searchAfter;
    }
}
