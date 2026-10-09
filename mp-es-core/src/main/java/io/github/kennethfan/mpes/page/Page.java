package io.github.kennethfan.mpes.page;

import io.github.kennethfan.mpes.support.EsOpsException;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页对象：API 形态对齐 MP 的 Page（records/total/current/size）。
 * <p>
 * 不直接复用 MP 的 IPage 接口——它位于被 ADR-0002 排除的内核 artifact 中；
 * 本项目以同形态的自有类型达成同等使用体验。
 */
@Data
public class Page<T> {

    /** from+size 上限（与 ES 默认 max_result_window 一致，Q10） */
    public static final long MAX_RESULT_WINDOW = 10_000;

    /** 当前页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 命中总数 */
    private long total;

    private List<T> records = new ArrayList<>();

    public Page() {
    }

    public Page(long current, long size) {
        if (current < 1) {
            throw new IllegalArgumentException("current 必须 >= 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size 必须 >= 1");
        }
        this.current = current;
        this.size = size;
    }

    /** 校验本页是否落在 ES from+size 窗口内，越界抛异常 */
    public void assertWithinWindow() {
        long end = (current - 1) * size + size;
        if (end > MAX_RESULT_WINDOW) {
            throw new EsOpsException("分页超出 from+size 上限 " + MAX_RESULT_WINDOW
                    + "（current=" + current + ", size=" + size + "），深分页请等待二期 search_after");
        }
    }
}
