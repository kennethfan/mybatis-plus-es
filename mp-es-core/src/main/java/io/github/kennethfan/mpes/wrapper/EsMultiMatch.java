package io.github.kennethfan.mpes.wrapper;

import java.util.Objects;

/**
 * multi_match 检索配置（八期 type/operator 的九期扩展）。
 * <p>
 * 用法：
 * <pre>{@code
 * wrapper.multiMatch(EsMultiMatch.type(MatchType.MOST_FIELDS).operatorAnd(), "无线 键盘",
 *         Product::getProductName, Product::getDescription)
 * }</pre>
 * 默认 BEST_FIELDS + OR；PHRASE / PHRASE_PREFIX 仅作用于 text 字段（keyword 混入由 ES 报错）。
 */
public record EsMultiMatch(MatchType type, Operator operator, String minimumShouldMatch) {

    public enum MatchType { BEST_FIELDS, MOST_FIELDS, CROSS_FIELDS, PHRASE, PHRASE_PREFIX }

    public enum Operator { AND, OR }

    public EsMultiMatch {
        Objects.requireNonNull(type, "type 不能为空");
    }

    /** 指定 multi_match 打分类型（operator 默认 OR） */
    public static EsMultiMatch type(MatchType type) {
        return new EsMultiMatch(type, Operator.OR, null);
    }

    /** 分词后取 AND（所有词项都需命中，默认 OR） */
    public EsMultiMatch operatorAnd() {
        return new EsMultiMatch(type, Operator.AND, minimumShouldMatch);
    }

    /** 显式 OR（默认） */
    public EsMultiMatch operatorOr() {
        return new EsMultiMatch(type, Operator.OR, minimumShouldMatch);
    }

    /** minimum_should_match（仅 operator=OR 时对 ES 有意义），如 "75%" */
    public EsMultiMatch minimumShouldMatch(String minimumShouldMatch) {
        return new EsMultiMatch(type, operator, minimumShouldMatch);
    }
}