package io.github.kennethfan.mpes.highlight;

import java.util.List;
import java.util.Map;

/**
 * 单条检索命中：实体 + 高亮片段（键为实体属性名）。
 *
 * @param <T> 实体类型
 */
public class EsHit<T> {

    private final T entity;
    private final Map<String, List<String>> highlights;

    public EsHit(T entity, Map<String, List<String>> highlights) {
        this.entity = entity;
        this.highlights = Map.copyOf(highlights);
    }

    public T getEntity() {
        return entity;
    }

    /** 属性名 → 高亮片段（已含前后标签）；未命中高亮的字段不在 Map 中 */
    public Map<String, List<String>> getHighlights() {
        return highlights;
    }

    public List<String> highlightsOf(String property) {
        return highlights.get(property);
    }
}
