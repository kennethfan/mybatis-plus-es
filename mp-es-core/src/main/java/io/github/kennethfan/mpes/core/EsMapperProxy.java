package io.github.kennethfan.mpes.core;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kennethfan.mpes.metadata.EntityMetadata;
import io.github.kennethfan.mpes.metadata.FieldMetadata;
import io.github.kennethfan.mpes.page.Page;
import io.github.kennethfan.mpes.support.EsOpsException;
import io.github.kennethfan.mpes.wrapper.EsLambdaQueryWrapper;
import io.github.kennethfan.mpes.wrapper.EsQueryTranslator;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EsBaseMapper} 的执行引擎：JDK 动态代理将 Mapper 方法调用翻译为
 * Elasticsearch Java API Client 调用（ADR-0002：不经过任何 MyBatis 机制）。
 */
public class EsMapperProxy<T> implements InvocationHandler {

    /** selectList 的全量上限 */
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
            case "updateById" -> updateById(args[0]);
            case "selectById" -> selectById((Serializable) args[0]);
            case "selectBatchIds" -> selectBatchIds(args[0]);
            case "selectCount" -> selectCount((EsLambdaQueryWrapper<?>) args[0]);
            case "selectList" -> selectList((EsLambdaQueryWrapper<?>) args[0]);
            case "selectOne" -> selectOne((EsLambdaQueryWrapper<?>) args[0]);
            case "selectPage" -> selectPage((Page<?>) args[0], (EsLambdaQueryWrapper<?>) args[1]);
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

    /** 实体 → 非 null 字段的 Map（写入载荷；键为 ES 字段名，主键字段一并写入 _source 保持完整） */
    private Map<String, Object> toDocument(Object entity) {
        Map<String, Object> raw = objectMapper.convertValue(entity, Map.class);
        Map<String, Object> doc = new LinkedHashMap<>();
        for (FieldMetadata f : metadata.getFields()) {
            Object v = raw.get(f.getProperty());
            if (v != null) {
                doc.put(f.getEsFieldName(), v);
            }
        }
        return doc;
    }

    /** _source（ES 字段名）→ 实体（属性名） */
    @SuppressWarnings("unchecked")
    private <E> E toEntity(Object source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> raw = objectMapper.convertValue(source, Map.class);
        Map<String, Object> renamed = new LinkedHashMap<>();
        for (FieldMetadata f : metadata.getFields()) {
            if (raw.containsKey(f.getEsFieldName())) {
                renamed.put(f.getProperty(), raw.get(f.getEsFieldName()));
            }
        }
        return (E) objectMapper.convertValue(renamed, metadata.getEntityClass());
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
        try {
            var resp = client.search(s -> s
                            .index(metadata.getIndexName())
                            .query(query)
                            .sort(sorts)
                            .size(MAX_LIST_SIZE)
                            .trackTotalHits(t -> t.enabled(true)),
                    (Class<Object>) metadata.getEntityClass());
            List<?> src = resp.hits().hits().stream().map(Hit::source).toList();
            return (List<E>) src;
        } catch (IOException e) {
            throw new EsOpsException("selectList 失败: " + metadata.getIndexName(), e);
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

    private boolean isSuccess(Result result) {
        return result == Result.Created || result == Result.Updated;
    }
}
