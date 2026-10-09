package io.github.kennethfan.mpes.page;

import java.util.List;

/**
 * search_after 分页结果：当前批记录 + 下一页游标（{@code getNext() == null} 表示已到底）。
 *
 * @param <T> 实体类型
 */
public class EsAfterResult<T> {

    private final List<T> records;
    private final EsAfter next;
    private final long total;

    public EsAfterResult(List<T> records, EsAfter next, long total) {
        this.records = List.copyOf(records);
        this.next = next;
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    /** 下一页游标；null 表示没有更多数据 */
    public EsAfter getNext() {
        return next;
    }

    /** 命中总数（track_total_hits，非当前批条数） */
    public long getTotal() {
        return total;
    }
}
