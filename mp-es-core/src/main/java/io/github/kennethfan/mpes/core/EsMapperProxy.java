package io.github.kennethfan.mpes.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kennethfan.mpes.agg.EsAgg;
import io.github.kennethfan.mpes.agg.EsAggRange;
import io.github.kennethfan.mpes.agg.EsAggResult;
import io.github.kennethfan.mpes.highlight.EsHighlight;
import io.github.kennethfan.mpes.highlight.EsHit;
import io.github.kennethfan.mpes.metadata.EntityMetadata;
import io.github.kennethfan.mpes.metadata.FieldMetadata;
import io.github.kennethfan.mpes.page.EsAfter;
import io.github.kennethfan.mpes.page.EsAfterResult;
import io.github.kennethfan.mpes.page.Page;
import io.github.kennethfan.mpes.support.EsOpsException;
import io.github.kennethfan.mpes.wrapper.EsLambdaQueryWrapper;
import io.github.kennethfan.mpes.wrapper.EsQueryTranslator;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EsBaseMapper} 的执行引擎：JDK 动态代理将 Mapper 方法调用翻译为
 * Elasticsearch Java API Client 调用（ADR-0002：不经过任何 MyBatis 机制）。
 */
public class EsMapperProxy<T> implements InvocationHandler {

    /** selectList / selectHighlighted 未显式 limit 时的默认返回上限 */
    private static final int MAX_LIST_SIZE = 1_000;

    private final Class<T> mapperInterface;
    private final EntityMetadata metadata;
    private final ElasticsearchClient client;
    private final ObjectMapper objectMapper;

    private final EsQueryTranslator.FieldResolver fieldResolver;

    public EsMapperProxy(Class<T> mapperInterface, EntityMetadata metadata,
                         ElasticsearchClient client, ObjectMapper objectMapper) {
        this.mapperInterface = mapperInterface;
        this.metadata = metadata;
        this.client = client;
        this.objectMapper = objectMapper;
        this.fieldResolver = metadata::fieldByProperty;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        return switch (name) {
            case "insert" -> insert(args[0]);
            case "insertBatch" -> insertBatch(args[0]);
            case "deleteById" -> deleteById((Serializable) args[0]);
            case "deleteBatchIds" -> deleteBatchIds(args[0]);
            case "delete" -> deleteByCondition((EsLambdaQueryWrapper<?>) args[0]);
            case "updateById" -> updateById(args[0]);
            case "update" -> updateByCondition(args[0], (EsLambdaQueryWrapper<?>) args[1]);
            case "updateBatchById" -> updateBatchById(args[0]);
            case "selectById" -> selectById((Serializable) args[0]);
            case "selectBatchIds" -> selectBatchIds(args[0]);
            case "selectCount" -> selectCount((EsLambdaQueryWrapper<?>) args[0]);
            case "selectList" -> selectList((EsLambdaQueryWrapper<?>) args[0]);
            case "selectOne" -> selectOne((EsLambdaQueryWrapper<?>) args[0]);
            case "selectPage" -> selectPage((Page<?>) args[0], (EsLambdaQueryWrapper<?>) args[1]);
            case "selectAfter" -> selectAfter((EsAfter) args[0], (EsLambdaQueryWrapper<?>) args[1]);
            case "selectHighlighted" -> selectHighlighted(
                    (EsLambdaQueryWrapper<?>) args[0], (EsHighlight<?>) args[1]);
            case "aggregate" -> aggregate((EsLambdaQueryWrapper<?>) args[0], (EsAgg[]) args[1]);
            case "toString" -> mapperInterface.getSimpleName() + "@" + metadata.getIndexName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new EsOpsException(
                    "Mapper 方法未被适配层支持: " + mapperInterface.getName() + "#" + name);
        };
    }

    // ---------- 写入 ----------

    private int insert(Object entity) {
        try {
            var resp = client.index(i -> i
                    .index(metadata.getIndexName())
                    .id(metadata.idOf(entity))
                    .document(toDocument(entity)));
            return isSuccess(resp.result()) ? 1 : 0;
        } catch (IOException e) {
            throw new EsOpsException("insert 失败: " + metadata.getIndexName(), e);
        }
    }

    private int insertBatch(Object arg) {
        Collection<?> entities = (Collection<?>) arg;
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        try {
            BulkRequest.Builder br = new BulkRequest.Builder().index(metadata.getIndexName());
            for (Object entity : entities) {
                br.operations(op -> op.index(io -> io
                        .id(metadata.idOf(entity))
                        .document(toDocument(entity))));
            }
            BulkResponse resp = client.bulk(br.build());
            if (resp.errors()) {
                long failed = resp.items().stream().map(BulkResponseItem::error)
                        .filter(e -> e != null).count();
                throw new EsOpsException("insertBatch 部分失败: " + failed + "/" + entities.size()
                        + "（索引 " + metadata.getIndexName() + "）");
            }
            return entities.size();
        } catch (IOException e) {
            throw new EsOpsException("insertBatch 失败: " + metadata.getIndexName(), e);
        }
    }

    private int deleteById(Serializable id) {
        try {
            var resp = client.delete(d -> d.index(metadata.getIndexName()).id(String.valueOf(id)));
            return "deleted".equals(resp.result().jsonValue()) ? 1 : 0;
        } catch (IOException e) {
            throw new EsOpsException("deleteById 失败: " + metadata.getIndexName() + "#" + id, e);
        }
    }

    private int deleteBatchIds(Object arg) {
        Collection<? extends Serializable> ids = (Collection<? extends Serializable>) arg;
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<String> idStrings = ids.stream().map(String::valueOf).toList();
        try {
            DeleteByQueryResponse resp = client.deleteByQuery(d -> d
                    .index(metadata.getIndexName())
                    .query(q -> q.ids(i -> i.values(idStrings)))
                    // 版本冲突（如删除目标刚被更新）跳过而非整体 409 失败
                    .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
                    .refresh(true));
            return resp.deleted().intValue();
        } catch (IOException e) {
            throw new EsOpsException("deleteBatchIds 失败: " + metadata.getIndexName(), e);
        }
    }

    private int updateById(Object entity) {
        String id = metadata.idOf(entity);
        Map<String, Object> doc = toDocument(entity);
        if (doc.isEmpty()) {
            throw new EsOpsException("updateById 至少需要一个非 null 字段（实体 " + metadata.getEntityClass().getSimpleName() + "）");
        }
        try {
            var resp = client.update(u -> u
                            .index(metadata.getIndexName())
                            .id(id)
                            .doc(doc),
                    Object.class);
            return "updated".equals(resp.result().jsonValue()) ? 1 : 0;
        } catch (IOException e) {
            throw new EsOpsException("updateById 失败: " + metadata.getIndexName() + "#" + id, e);
        }
    }

    /** 条件删除：delete_by_query，conflicts=proceed + refresh，返回实际删除数 */
    private int deleteByCondition(EsLambdaQueryWrapper<?> wrapper) {
        requireCondition(wrapper, "delete");
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        try {
            DeleteByQueryResponse resp = client.deleteByQuery(d -> d
                    .index(metadata.getIndexName())
                    .query(query)
                    .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
                    .refresh(true));
            return resp.deleted().intValue();
        } catch (IOException e) {
            throw new EsOpsException("delete(条件) 失败: " + metadata.getIndexName(), e);
        }
    }

    /** 条件部分更新：update_by_query + painless script（ctx._source 赋值 + params 传值） */
    private int updateByCondition(Object patch, EsLambdaQueryWrapper<?> wrapper) {
        if (patch == null) {
            throw new EsOpsException("update 的 patch 实体不能为 null");
        }
        requireCondition(wrapper, "update");
        Map<String, Object> doc = toDocument(patch);
        if (doc.isEmpty()) {
            throw new EsOpsException("update 的 patch 至少需要一个非 null 字段（实体 "
                    + metadata.getEntityClass().getSimpleName() + "）");
        }
        // painless：ctx._source['esField'] = params.pN（括号写法安全处理字段名）
        StringBuilder source = new StringBuilder();
        Map<String, co.elastic.clients.json.JsonData> params = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            String key = "p" + i++;
            if (i > 1) {
                source.append("; ");
            }
            source.append("ctx._source['").append(e.getKey()).append("'] = params.").append(key);
            params.put(key, co.elastic.clients.json.JsonData.of(e.getValue()));
        }
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        try {
            var resp = client.updateByQuery(u -> u
                    .index(metadata.getIndexName())
                    .query(query)
                    .script(s -> s.lang("painless").source(source.toString()).params(params))
                    .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
                    .refresh(true));
            return resp.updated().intValue();
        } catch (IOException e) {
            throw new EsOpsException("update(条件) 失败: " + metadata.getIndexName(), e);
        }
    }

    /** 批量按主键部分更新：bulk update，每条取非 null 字段；部分失败整体抛异常（对齐 insertBatch） */
    private int updateBatchById(Object arg) {
        Collection<?> entities = (Collection<?>) arg;
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (Object entity : entities) {
                Object id = metadata.idOf(entity);
                if (id == null) {
                    throw new EsOpsException("updateBatchById 的实体缺少主键（实体 "
                            + metadata.getEntityClass().getSimpleName() + "）");
                }
                Map<String, Object> doc = toDocument(entity);
                if (doc.isEmpty()) {
                    throw new EsOpsException("updateBatchById 至少需要一个非 null 字段: "
                            + metadata.getEntityClass().getSimpleName() + "#" + id);
                }
                br.operations(op -> op.update(uo -> uo
                        .index(metadata.getIndexName())
                        .id(String.valueOf(id))
                        .action(a -> a.doc(doc))));
            }
            BulkResponse resp = client.bulk(br.build());
            if (resp.errors()) {
                long failed = resp.items().stream().map(BulkResponseItem::error)
                        .filter(e -> e != null).count();
                throw new EsOpsException("updateBatchById 部分失败: " + failed + "/" + entities.size()
                        + "（索引 " + metadata.getIndexName() + "）");
            }
            return entities.size();
        } catch (IOException e) {
            throw new EsOpsException("updateBatchById 失败: " + metadata.getIndexName(), e);
        }
    }

    /** 条件删除/更新强制要求非空 wrapper（拒绝全量误删误改） */
    private void requireCondition(EsLambdaQueryWrapper<?> wrapper, String op) {
        if (wrapper == null || wrapper.isEmpty()) {
            throw new EsOpsException(op + " 条件不能为空（拒绝全量" + ("delete".equals(op) ? "删除" : "更新")
                    + "）；请至少附加一个条件，或改用 deleteById/deleteBatchIds/updateById");
        }
    }

    /** 实体 → 非 null 字段的 Map（写入载荷；键为 ES 字段名，主键字段一并写入 _source 保持完整；nested 字段递归重命名） */
    private Map<String, Object> toDocument(Object entity) {
        Map<String, Object> raw = objectMapper.convertValue(entity, Map.class);
        return renameToDocument(raw, metadata);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> renameToDocument(Map<String, Object> raw, EntityMetadata md) {
        Map<String, Object> doc = new LinkedHashMap<>();
        for (FieldMetadata f : md.getFields()) {
            Object v = raw.get(f.getProperty());
            if (v == null) {
                continue;
            }
            if (f.getNestedMetadata() != null) {
                v = renameNestedToDocument(v, f.getNestedMetadata());
            }
            doc.put(f.getEsFieldName(), v);
        }
        return doc;
    }

    /** nested 字段值：List<子实体Map> 或单个子实体Map，逐层递归重命名 */
    @SuppressWarnings("unchecked")
    private Object renameNestedToDocument(Object v, EntityMetadata nestedMd) {
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object element : list) {
                out.add(renameToDocument((Map<String, Object>) element, nestedMd));
            }
            return out;
        }
        return renameToDocument((Map<String, Object>) v, nestedMd);
    }

    /** _source（ES 字段名）→ 实体（属性名）；nested 字段递归重命名 */
    private <E> E toEntity(Object source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> raw = objectMapper.convertValue(source, Map.class);
        Map<String, Object> renamed = renameToEntity(raw, metadata);
        return (E) objectMapper.convertValue(renamed, metadata.getEntityClass());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> renameToEntity(Map<String, Object> raw, EntityMetadata md) {
        Map<String, Object> renamed = new LinkedHashMap<>();
        for (FieldMetadata f : md.getFields()) {
            if (!raw.containsKey(f.getEsFieldName())) {
                continue;
            }
            Object v = raw.get(f.getEsFieldName());
            if (f.getNestedMetadata() != null) {
                v = renameNestedToEntity(v, f.getNestedMetadata());
            }
            renamed.put(f.getProperty(), v);
        }
        return renamed;
    }

    @SuppressWarnings("unchecked")
    private Object renameNestedToEntity(Object v, EntityMetadata nestedMd) {
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object element : list) {
                out.add(renameToEntity((Map<String, Object>) element, nestedMd));
            }
            return out;
        }
        return renameToEntity((Map<String, Object>) v, nestedMd);
    }

    // ---------- 查询 ----------

    @SuppressWarnings("unchecked")
    private <E> E selectById(Serializable id) {
        try {
            var resp = client.get(g -> g
                            .index(metadata.getIndexName())
                            .id(String.valueOf(id)),
                    Map.class);
            return resp.found() ? (E) toEntity(resp.source()) : null;
        } catch (IOException e) {
            throw new EsOpsException("selectById 失败: " + metadata.getIndexName() + "#" + id, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <E> List<E> selectBatchIds(Object arg) {
        Collection<? extends Serializable> ids = (Collection<? extends Serializable>) arg;
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> idStrings = ids.stream().map(String::valueOf).toList();
        try {
            var resp = client.search(s -> s
                            .index(metadata.getIndexName())
                            .query(q -> q.ids(i -> i.values(idStrings)))
                            .size(idStrings.size()),
                    Map.class);
            List<?> src = resp.hits().hits().stream().map(h -> toEntity(h.source())).toList();
            return (List<E>) src;
        } catch (IOException e) {
            throw new EsOpsException("selectBatchIds 失败: " + metadata.getIndexName(), e);
        }
    }

    private Long selectCount(EsLambdaQueryWrapper<?> wrapper) {
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        try {
            return client.count(c -> c.index(metadata.getIndexName()).query(query)).count();
        } catch (IOException e) {
            throw new EsOpsException("selectCount 失败: " + metadata.getIndexName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <E> List<E> selectList(EsLambdaQueryWrapper<?> wrapper) {
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        List<co.elastic.clients.elasticsearch._types.SortOptions> sorts =
                EsQueryTranslator.toSorts(wrapper, fieldResolver);
        Integer limit = wrapper.getLimit();
        int size = limit != null ? limit : MAX_LIST_SIZE;
        try {
            var resp = client.search(s -> s
                            .index(metadata.getIndexName())
                            .query(query)
                            .sort(sorts)
                            .size(size)
                            .trackTotalHits(t -> t.enabled(true)),
                    (Class<Object>) metadata.getEntityClass());
            List<?> src = resp.hits().hits().stream().map(Hit::source).toList();
            checkListCap(wrapper, resp.hits().total(), src.size());
            return (List<E>) src;
        } catch (IOException e) {
            throw new EsOpsException("selectList 失败: " + metadata.getIndexName(), e);
        }
    }

    /**
     * 未显式 limit 时，命中数超过单次上限（{@value #MAX_LIST_SIZE}）即报错，
     * 杜绝静默截断丢数据；显式 limit(n) 视为用户已知并接受的结果规模，不再拦截。
     */
    private void checkListCap(EsLambdaQueryWrapper<?> wrapper, TotalHits total, int fetched) {
        if (wrapper.getLimit() != null) {
            return;
        }
        long totalHits = total != null ? total.value() : fetched;
        if (totalHits > MAX_LIST_SIZE) {
            throw new EsOpsException("命中 " + totalHits + " 条，超过单次上限 " + MAX_LIST_SIZE
                    + "（索引 " + metadata.getIndexName() + "）。请加 .limit(n)（上限 "
                    + EsLambdaQueryWrapper.MAX_RESULT_WINDOW + "）或改用 selectAfter 深分页");
        }
    }

    @SuppressWarnings("unchecked")
    private <E> E selectOne(EsLambdaQueryWrapper<?> wrapper) {
        List<E> list = (List<E>) selectListPage(wrapper, 2, 0);
        if (list.size() > 1) {
            throw new EsOpsException("selectOne 期望 1 条结果，实际命中 " + list.size()
                    + " 条（索引 " + metadata.getIndexName() + "），请收紧条件或改用 selectList");
        }
        return list.isEmpty() ? null : list.get(0);
    }

    @SuppressWarnings("unchecked")
    private <E> Page<E> selectPage(Page<?> page, EsLambdaQueryWrapper<?> wrapper) {
        page.assertWithinWindow();
        long from = (page.getCurrent() - 1) * page.getSize();
        List<E> records = (List<E>) selectListPage(wrapper, (int) page.getSize(), (int) from);
        Page<E> result = new Page<>(page.getCurrent(), page.getSize());
        result.setTotal(totalOf(wrapper));
        result.setRecords(records);
        return result;
    }

    /** 统一的 search 执行：size/from/sort/total 由调用场景决定 */
    private List<?> selectListPage(EsLambdaQueryWrapper<?> wrapper, int size, int from) {
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        List<co.elastic.clients.elasticsearch._types.SortOptions> sorts =
                EsQueryTranslator.toSorts(wrapper, fieldResolver);
        try {
            var resp = client.search(s -> s
                            .index(metadata.getIndexName())
                            .query(query)
                            .sort(sorts)
                            .from(from)
                            .size(size)
                            .trackTotalHits(t -> t.enabled(true)),
                    Map.class);
            return resp.hits().hits().stream().map(h -> toEntity(h.source())).toList();
        } catch (IOException e) {
            throw new EsOpsException("search 失败: " + metadata.getIndexName(), e);
        }
    }

    private long totalOf(EsLambdaQueryWrapper<?> wrapper) {
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        try {
            return client.count(c -> c.index(metadata.getIndexName()).query(query)).count();
        } catch (IOException e) {
            throw new EsOpsException("count 失败: " + metadata.getIndexName(), e);
        }
    }

    // ---------- search_after 深分页 ----------

    @SuppressWarnings("unchecked")
    private <E> EsAfterResult<E> selectAfter(EsAfter after, EsLambdaQueryWrapper<?> wrapper) {
        if (after == null) {
            throw new EsOpsException("selectAfter 需要 EsAfter 游标（首页用 EsAfter.first(size)）");
        }
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        List<co.elastic.clients.elasticsearch._types.SortOptions> sorts =
                new ArrayList<>(EsQueryTranslator.toSorts(wrapper, fieldResolver));
        // search_after 要求全序：追加主键字段兜底排序（_id 禁止 fielddata 排序，主键同时存在于 _source）
        String idField = metadata.getIdField().getEsFieldName();
        boolean hasIdSort = sorts.stream()
                .filter(co.elastic.clients.elasticsearch._types.SortOptions::isField)
                .anyMatch(so -> idField.equals(so.field().field()));
        if (!hasIdSort) {
            sorts.add(co.elastic.clients.elasticsearch._types.SortOptions.of(
                    s -> s.field(f -> f.field(idField).order(co.elastic.clients.elasticsearch._types.SortOrder.Asc))));
        }
        try {
            // 多取一条用于判定是否还有下一批（最后一批恰好填满时 size==hits 无法区分）
            var resp = client.search(s -> {
                s.index(metadata.getIndexName())
                        .query(query)
                        .sort(sorts)
                        .size(after.getSize() + 1)
                        .trackTotalHits(t -> t.enabled(true));
                if (after.getSearchAfter() != null) {
                    s.searchAfter(after.getSearchAfter());
                }
                return s;
            }, Map.class);

            List<Hit<Map>> hits = resp.hits().hits();
            boolean hasMore = hits.size() > after.getSize();
            List<Hit<Map>> page = hasMore ? hits.subList(0, after.getSize()) : hits;
            List<?> records = page.stream().map(h -> toEntity(h.source())).toList();

            EsAfter next = hasMore
                    ? EsAfter.of(after.getSize(), page.get(page.size() - 1).sort())
                    : null;
            long total = resp.hits().total() == null ? records.size() : resp.hits().total().value();
            return new EsAfterResult<>((List<E>) records, next, total);
        } catch (IOException e) {
            throw new EsOpsException("selectAfter 失败: " + metadata.getIndexName(), e);
        }
    }

    // ---------- 高亮 ----------

    @SuppressWarnings("unchecked")
    private <E> List<EsHit<E>> selectHighlighted(EsLambdaQueryWrapper<?> wrapper, EsHighlight<?> highlight) {
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        Map<String, HighlightField> fieldSpecs = new LinkedHashMap<>();
        for (String property : highlight.getProperties()) {
            fieldSpecs.put(fieldResolver.apply(property).getEsFieldName(), HighlightField.of(b -> b));
        }
        Highlight hl = Highlight.of(h -> h
                .preTags(highlight.getPreTag())
                .postTags(highlight.getPostTag())
                .fields(fieldSpecs));
        Integer limit = wrapper.getLimit();
        int size = limit != null ? limit : MAX_LIST_SIZE;
        try {
            var resp = client.search(s -> s
                            .index(metadata.getIndexName())
                            .query(query)
                            .highlight(hl)
                            .size(size)
                            .trackTotalHits(t -> t.enabled(true)),
                    Map.class);
            List<?> src = resp.hits().hits().stream().map(this::toHit).toList();
            checkListCap(wrapper, resp.hits().total(), src.size());
            return (List<EsHit<E>>) src;
        } catch (IOException e) {
            throw new EsOpsException("selectHighlighted 失败: " + metadata.getIndexName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <E> EsHit<E> toHit(Hit<?> hit) {
        // 8.19 客户端：hit.highlight() 已是 Map<ES字段名, List<片段>>
        Map<String, List<String>> highlights = new LinkedHashMap<>();
        hit.highlight().forEach((esFieldName, fragments) ->
                highlights.put(esFieldNameToProperty(esFieldName), fragments));
        return new EsHit<>((E) toEntity(hit.source()), highlights);
    }

    private String esFieldNameToProperty(String esFieldName) {
        return metadata.getFields().stream()
                .filter(f -> f.getEsFieldName().equals(esFieldName))
                .findFirst()
                .map(FieldMetadata::getProperty)
                .orElse(esFieldName);
    }

    // ---------- 聚合 ----------

    private EsAggResult aggregate(EsLambdaQueryWrapper<?> wrapper, EsAgg... aggs) {
        if (aggs == null || aggs.length == 0) {
            throw new EsOpsException("aggregate 至少需要一个 EsAgg");
        }
        Query query = EsQueryTranslator.toQuery(wrapper, fieldResolver);
        Map<String, Aggregation> spec = new LinkedHashMap<>();
        for (EsAgg agg : aggs) {
            spec.put(agg.getName(), toAggregation(agg));
        }
        try {
            var resp = client.search(s -> s
                            .index(metadata.getIndexName())
                            .query(query)
                            .size(0)
                            .aggregations(spec),
                    Void.class);
            return new EsAggResult(resp.aggregations());
        } catch (IOException e) {
            throw new EsOpsException("aggregate 失败: " + metadata.getIndexName(), e);
        }
    }

    private Aggregation toAggregation(EsAgg agg) {
        // top_hits 无属性概念，field 仅对字段类聚合解析
        String field = agg.getProperty() != null
                ? metadata.fieldByProperty(agg.getProperty()).getEsFieldName()
                : null;
        Map<String, Aggregation> sub = new LinkedHashMap<>();
        for (EsAgg child : agg.getChildren()) {
            sub.put(child.getName(), toAggregation(child));
        }
        // 8.19 客户端：a.terms()/a.avg() 等返回 ContainerBuilder，子聚合挂在它上面
        return Aggregation.of(a -> {
            Aggregation.Builder.ContainerBuilder c = switch (agg.getType()) {
                case TERMS -> a.terms(t -> {
                    t.field(field).size(agg.getSize() != null ? agg.getSize() : 100);
                    return t;
                });
                case DATE_HISTOGRAM -> a.dateHistogram(dh -> {
                    dh.field(field).calendarInterval(CalendarInterval.valueOf(
                            Character.toUpperCase(agg.getDateInterval().charAt(0))
                                    + agg.getDateInterval().substring(1)));
                    if (agg.getDateFormat() != null) {
                        dh.format(agg.getDateFormat());
                    }
                    if (agg.getDateMinDocCount() != null) {
                        dh.minDocCount(agg.getDateMinDocCount());
                    }
                    return dh;
                });
                case RANGE -> a.range(r -> {
                    r.field(field).keyed(true);
                    for (EsAggRange range : agg.getRanges()) {
                        r.ranges(ar -> {
                            if (range.from() != null) {
                                ar.from(range.from());
                            }
                            if (range.to() != null) {
                                ar.to(range.to());
                            }
                            if (range.key() != null) {
                                ar.key(range.key());
                            }
                            return ar;
                        });
                    }
                    return r;
                });
                case AVG -> a.avg(v -> v.field(field));
                case MAX -> a.max(v -> v.field(field));
                case MIN -> a.min(v -> v.field(field));
                case SUM -> a.sum(v -> v.field(field));
                case STATS -> a.stats(v -> v.field(field));
                case CARDINALITY -> a.cardinality(v -> v.field(field));
                case TOP_HITS -> a.topHits(th -> {
                    th.size(agg.getTopSize());
                    for (EsAgg.TopSort sort : agg.getTopSorts()) {
                        String sortField = metadata.fieldByProperty(sort.property()).getEsFieldName();
                        th.sort(so -> so.field(f -> f.field(sortField)
                                .order(sort.asc() ? SortOrder.Asc : SortOrder.Desc)));
                    }
                    return th;
                });
            };
            if (!sub.isEmpty()) {
                c.aggregations(sub);
            }
            return c;
        });
    }

    private boolean isSuccess(Result result) {
        return result == Result.Created || result == Result.Updated;
    }
}
