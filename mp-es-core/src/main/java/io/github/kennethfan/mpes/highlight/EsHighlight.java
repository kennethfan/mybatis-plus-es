package io.github.kennethfan.mpes.highlight;

import io.github.kennethfan.mpes.core.SFunction;
import io.github.kennethfan.mpes.support.LambdaUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 高亮配置：参与高亮的字段（Lambda 引用）+ 前后标签。
 *
 * @param <T> 实体类型
 */
public class EsHighlight<T> {

    public static final String DEFAULT_PRE_TAG = "<em>";
    public static final String DEFAULT_POST_TAG = "</em>";

    private List<String> properties;
    private String preTag = DEFAULT_PRE_TAG;
    private String postTag = DEFAULT_POST_TAG;

    @SafeVarargs
    public static <T> EsHighlight<T> of(SFunction<T, ?>... cols) {
        if (cols == null || cols.length == 0) {
            throw new IllegalArgumentException("EsHighlight.of 至少需要一个字段");
        }
        List<String> properties = Arrays.stream(cols).map(LambdaUtils::propertyName).toList();
        EsHighlight<T> hl = new EsHighlight<>();
        hl.properties = properties;
        return hl;
    }

    public EsHighlight<T> preTag(String preTag) {
        this.preTag = preTag;
        return this;
    }

    public EsHighlight<T> postTag(String postTag) {
        this.postTag = postTag;
        return this;
    }

    public List<String> getProperties() {
        return properties;
    }

    public String getPreTag() {
        return preTag;
    }

    public String getPostTag() {
        return postTag;
    }
}
