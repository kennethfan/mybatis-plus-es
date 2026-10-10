package io.github.kennethfan.mpes.wrapper;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;
import io.github.kennethfan.mpes.geo.GeoPoint;
import io.github.kennethfan.mpes.metadata.EntityMetadata;
import io.github.kennethfan.mpes.metadata.FieldMetadata;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        return toQuery(wrapper, resolver, "");
    }

    /**
     * @param prefix nested 上下文的字段前缀（顶层为空串；nested 内部为 "skus." 这类完整路径）。
     *               nested 子查询内的字段必须使用全路径，否则 ES 匹配不到。
     */
    private static Query toQuery(EsLambdaQueryWrapper<?> wrapper, FieldResolver resolver, String prefix) {
        if (wrapper == null || wrapper.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        // AND 优先：按 OR 边界切分为若干 AND 桶，桶间以 should 连接
        List<Query> buckets = new ArrayList<>();
        List<Query> current = new ArrayList<>();
        for (EsLambdaQueryWrapper.Node node : wrapper.getNodes()) {
            Query q;
            if (node.content() instanceof EsLambdaQueryWrapper.Leaf leaf) {
                q = leafQuery(leaf, resolver, prefix);
            } else if (node.content() instanceof EsLambdaQueryWrapper.MultiMatchLeaf mm) {
                q = multiMatchQuery(mm, resolver, prefix);
            } else if (node.content() instanceof EsLambdaQueryWrapper.NestedLeaf nested) {
                q = nestedQuery(nested, resolver, prefix);
            } else if (node.content() instanceof EsLambdaQueryWrapper.ScriptLeaf script) {
                q = scriptQuery(script);
            } else {
                q = toQuery((EsLambdaQueryWrapper<?>) node.content(), resolver, prefix);
            }
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

    private static Query leafQuery(EsLambdaQueryWrapper.Leaf leaf, FieldResolver resolver, String prefix) {
        FieldMetadata fm = resolver.apply(leaf.property());
        String field = prefix + fm.getEsFieldName();
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
            case MATCH -> Query.of(q -> q.match(m -> {
                m.field(field).query(String.valueOf(values.get(0)));
                if (leaf.boost() != null) {
                    m.boost(leaf.boost());
                }
                return m;
            }));
            case IS_NULL -> Query.of(q -> q.bool(b -> b.mustNot(
                    m -> m.exists(e -> e.field(field)))));
            case FUZZY -> {
                rejectText(isText, field, "fuzzy（容错仅适用 keyword 字段，text 请使用 match）");
                String fuzziness = String.valueOf(values.get(1));
                yield Query.of(q -> q.fuzzy(f -> f
                        .field(field)
                        .value(String.valueOf(values.get(0)))
                        .fuzziness(fuzziness)));
            }
            case PREFIX -> {
                rejectText(isText, field, "prefix（前缀仅适用 keyword 字段，text 请使用 match）");
                yield Query.of(q -> q.prefix(p -> p
                        .field(field)
                        .value(String.valueOf(values.get(0)))));
            }
            case GEO_DISTANCE -> {
                if (!"geo_point".equals(fm.getEsType())) {
                    throw new EsOpsException("字段 " + field + " 不是 geo_point 类型，不能使用 geoDistance");
                }
                GeoPoint origin = (GeoPoint) values.get(1);
                yield Query.of(q -> q.geoDistance(g -> g
                        .field(field)
                        .distance(String.valueOf(values.get(0)))
                        .location(l -> l.latlon(ll -> ll.lat(origin.getLat()).lon(origin.getLon())))));
            }
        };
    }

    /** multi_match 跨字段检索：字段名经 resolver 转 ES 字段名（支持 nested 前缀） */
    private static Query multiMatchQuery(EsLambdaQueryWrapper.MultiMatchLeaf mm,
                                         FieldResolver resolver, String prefix) {
        List<String> fields = mm.properties().stream()
                .map(p -> prefix + resolver.apply(p).getEsFieldName())
                .toList();
        Float boost = mm.boost();
        EsMultiMatch options = mm.options();
        return Query.of(q -> q.multiMatch(m -> {
            m.fields(fields).query(String.valueOf(mm.value()));
            if (boost != null) {
                m.boost(boost);
            }
            if (options != null) {
                // ES 枚举常量为驼峰（BestFields/PhrasePrefix），按名映射
                m.type(TextQueryType.valueOf(toEnumName(options.type().name())));
                if (options.operator() == EsMultiMatch.Operator.AND) {
                    m.operator(Operator.And);
                }
                if (options.minimumShouldMatch() != null) {
                    m.minimumShouldMatch(options.minimumShouldMatch());
                }
            }
            return m;
        }));
    }

    /** UPPER_SNAKE → UpperCamel（BEST_FIELDS → BestFields），用于映射 ES 枚举常量 */
    private static String toEnumName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** script 过滤：painless source + params（JsonData 包装），filter context */
    private static Query scriptQuery(EsLambdaQueryWrapper.ScriptLeaf script) {
        return Query.of(q -> q.script(s -> s.script(sc -> {
            sc.source(script.source());
            if (!script.params().isEmpty()) {
                Map<String, JsonData> params = new java.util.LinkedHashMap<>();
                script.params().forEach((k, v) -> params.put(k, JsonData.of(v)));
                sc.params(params);
            }
            return sc;
        })));
    }

    /** nested 子文档条件：path 为 nested 字段的完整路径，内部用子实体元数据递归翻译（字段带 path 前缀） */
    private static Query nestedQuery(EsLambdaQueryWrapper.NestedLeaf nested, FieldResolver resolver, String prefix) {
        FieldMetadata fm = resolver.apply(nested.property());
        if (!"nested".equals(fm.getEsType()) || fm.getNestedMetadata() == null) {
            throw new EsOpsException("字段 " + prefix + fm.getEsFieldName()
                    + " 不是 nested 类型（@EsNested），不能使用 nested() 条件");
        }
        String path = prefix + fm.getEsFieldName();
        Query inner = toQuery(nested.inner(), fm.getNestedMetadata()::fieldByProperty, path + ".");
        return Query.of(q -> q.nested(n -> n
                .path(path)
                .query(inner)));
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

    /** Wrapper 排序 → ES SortOptions（property → es 字段名经 resolver 转换；含 geo_distance 排序） */
    public static List<SortOptions> toSorts(EsLambdaQueryWrapper<?> wrapper, FieldResolver resolver) {
        if (wrapper == null) {
            return List.of();
        }
        List<SortOptions> result = new ArrayList<>();
        for (EsLambdaQueryWrapper.SortSpec s : wrapper.getSorts()) {
            String field = resolver.apply(s.property()).getEsFieldName();
            SortOrder order = s.asc() ? SortOrder.Asc : SortOrder.Desc;
            result.add(SortOptions.of(so -> so.field(f -> f.field(field).order(order))));
        }
        for (EsLambdaQueryWrapper.GeoDistanceSortSpec s : wrapper.getGeoSorts()) {
            String field = resolver.apply(s.property()).getEsFieldName();
            if (!"geo_point".equals(resolver.apply(s.property()).getEsType())) {
                throw new EsOpsException("字段 " + field + " 不是 geo_point 类型，不能按距离排序");
            }
            SortOrder order = s.asc() ? SortOrder.Asc : SortOrder.Desc;
            GeoPoint origin = s.origin();
            result.add(SortOptions.of(so -> so.geoDistance(g -> g
                    .field(field)
                    .location(l -> l.latlon(ll -> ll.lat(origin.getLat()).lon(origin.getLon())))
                    .order(order))));
        }
        for (EsLambdaQueryWrapper.NestedSortSpec s : wrapper.getNestedSorts()) {
            FieldMetadata fm = resolver.apply(s.property());
            if (!"nested".equals(fm.getEsType()) || fm.getNestedMetadata() == null) {
                throw new EsOpsException("字段 " + fm.getEsFieldName()
                        + " 不是 nested 类型（@EsNested），不能使用 orderByNested");
            }
            String path = fm.getEsFieldName();
            String sortField = path + "."
                    + fm.getNestedMetadata().fieldByProperty(s.sortProperty()).getEsFieldName();
            SortOrder order = s.asc() ? SortOrder.Asc : SortOrder.Desc;
            EsLambdaQueryWrapper<?> filter = s.filter();
            result.add(SortOptions.of(so -> so.field(f -> {
                f.field(sortField).order(order)
                        .nested(n -> {
                            n.path(path);
                            if (filter != null && !filter.isEmpty()) {
                                n.filter(toQuery(filter, fm.getNestedMetadata()::fieldByProperty, path + "."));
                            }
                            return n;
                        });
                return f;
            })));
        }
        return result;
    }
}
