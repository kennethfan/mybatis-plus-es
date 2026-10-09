package io.github.kennethfan.mpes.wrapper;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import io.github.kennethfan.mpes.metadata.FieldMetadata;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 将 {@link EsLambdaQueryWrapper} 的条件树翻译为 ES Query DSL。
 * 语义：AND 优先级高于 OR；组内递归翻译。
 */
public final class EsQueryTranslator {

    private EsQueryTranslator() {
    }

    /** 属性名 → 字段元数据的解析器（由调用方绑定具体实体） */
    public interface FieldResolver extends Function<String, FieldMetadata> {
    }

    public static Query toQuery(EsLambdaQueryWrapper<?> wrapper, FieldResolver resolver) {
        if (wrapper == null || wrapper.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        // AND 优先：按 OR 边界切分为若干 AND 桶，桶间以 should 连接
        List<Query> buckets = new ArrayList<>();
        List<Query> current = new ArrayList<>();
        for (EsLambdaQueryWrapper.Node node : wrapper.getNodes()) {
            Query q = node.content() instanceof EsLambdaQueryWrapper.Leaf leaf
                    ? leafQuery(leaf, resolver)
                    : toQuery((EsLambdaQueryWrapper<?>) node.content(), resolver);
            if (node.orToPrevious() && !current.isEmpty()) {
                buckets.add(andBucket(current));
                current = new ArrayList<>();
            }
            current.add(q);
        }
        if (!current.isEmpty()) {
            buckets.add(andBucket(current));
        }

        if (buckets.size() == 1) {
            return buckets.get(0);
        }
        return Query.of(q -> q.bool(b -> b.should(buckets).minimumShouldMatch("1")));
    }

    private static Query andBucket(List<Query> queries) {
        return queries.size() == 1
                ? queries.get(0)
                : Query.of(q -> q.bool(b -> b.must(queries)));
    }

    private static Query leafQuery(EsLambdaQueryWrapper.Leaf leaf, FieldResolver resolver) {
        FieldMetadata fm = resolver.apply(leaf.property());
        String field = fm.getEsFieldName();
        boolean isText = "text".equals(fm.getEsType());
        List<Object> values = leaf.values();

        return switch (leaf.op()) {
            case EQ -> {
                rejectText(isText, field, "eq（请使用 match）");
                yield Query.of(q -> q.term(t -> t.field(field).value(termValue(values.get(0)))));
            }
            case NE -> {
                rejectText(isText, field, "ne（请使用 match 后取反，或调整字段类型）");
                yield Query.of(q -> q.bool(b -> b.mustNot(
                        m -> m.term(t -> t.field(field).value(termValue(values.get(0)))))));
            }
            case IN -> {
                rejectText(isText, field, "in（请使用 match）");
                yield Query.of(q -> q.terms(t -> t.field(field)
                        .terms(ts -> ts.value(values.stream().map(EsQueryTranslator::termValue).toList()))));
            }
            case GT -> Query.of(q -> q.range(r -> r.untyped(
                    u -> u.field(field).gt(JsonData.of(values.get(0))))));
            case GE -> Query.of(q -> q.range(r -> r.untyped(
                    u -> u.field(field).gte(JsonData.of(values.get(0))))));
            case LT -> Query.of(q -> q.range(r -> r.untyped(
                    u -> u.field(field).lt(JsonData.of(values.get(0))))));
            case LE -> Query.of(q -> q.range(r -> r.untyped(
                    u -> u.field(field).lte(JsonData.of(values.get(0))))));
            case BETWEEN -> Query.of(q -> q.range(r -> r.untyped(u -> u.field(field)
                    .gte(JsonData.of(values.get(0)))
                    .lte(JsonData.of(values.get(1))))));
            case LIKE -> {
                rejectText(isText, field, "like（通配符仅适用 keyword 字段，text 请使用 match）");
                yield Query.of(q -> q.wildcard(w -> w.field(field).wildcard("*" + values.get(0) + "*")));
            }
            case MATCH -> Query.of(q -> q.match(m -> m.field(field).query(String.valueOf(values.get(0)))));
            case IS_NULL -> Query.of(q -> q.bool(b -> b.mustNot(
                    m -> m.exists(e -> e.field(field)))));
        };
    }

    private static void rejectText(boolean isText, String field, String suggestion) {
        if (isText) {
            throw new EsOpsException("字段 " + field + " 为 text 类型，不支持该操作 " + suggestion);
        }
    }

    private static FieldValue termValue(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return FieldValue.of(((Number) value).longValue());
        }
        if (value instanceof Number n) {
            return FieldValue.of(n.doubleValue());
        }
        if (value instanceof Boolean b) {
            return FieldValue.of(b);
        }
        return FieldValue.of(String.valueOf(value));
    }

    /** Wrapper 排序 → ES SortOptions（property → es 字段名经 resolver 转换） */
    public static List<SortOptions> toSorts(EsLambdaQueryWrapper<?> wrapper, FieldResolver resolver) {
        if (wrapper == null) {
            return List.of();
        }
        return wrapper.getSorts().stream()
                .map(s -> {
                    String field = resolver.apply(s.property()).getEsFieldName();
                    SortOrder order = s.asc() ? SortOrder.Asc : SortOrder.Desc;
                    return SortOptions.of(so -> so.field(f -> f.field(field).order(order)));
                })
                .toList();
    }
}
