package io.github.kennethfan.mpes.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import io.github.kennethfan.mpes.config.EsProperties;
import io.github.kennethfan.mpes.core.EsEntityRegistry;
import io.github.kennethfan.mpes.metadata.EntityMetadata;
import io.github.kennethfan.mpes.metadata.FieldMetadata;
import io.github.kennethfan.mpes.support.EsOpsException;
import io.github.kennethfan.mpes.support.MismatchPolicy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Index 托管（对标 MP 的表结构托管）：
 * 不存在则按实体元数据创建；已存在则校验 mapping 一致性，策略由 mp-es.index.mismatch-policy 决定。
 */
@Slf4j
public class IndexManager {

    private final ElasticsearchClient client;
    private final EsEntityRegistry registry;
    private final EsProperties properties;

    public IndexManager(ElasticsearchClient client, EsEntityRegistry registry, EsProperties properties) {
        this.client = client;
        this.registry = registry;
        this.properties = properties;
    }

    /** 对所有已注册实体执行 create-if-absent / 校验 */
    public void ensureAll() {
        for (EntityMetadata md : registry.all()) {
            ensureIndex(md);
        }
    }

    void ensureIndex(EntityMetadata md) {
        String index = md.getIndexName();
        try {
            boolean exists = client.indices().exists(e -> e.index(index)).value();
            if (exists) {
                validate(md);
            } else {
                create(md);
            }
        } catch (IOException e) {
            throw new EsOpsException("Index 托管失败: " + index, e);
        }
    }

    private void create(EntityMetadata md) throws IOException {
        client.indices().create(c -> c.index(md.getIndexName()).mappings(m -> m.properties(propertiesOf(md))));
        log.info("[mp-es] 已创建索引 {}（{} 个字段）", md.getIndexName(), md.getFields().size());
    }

    /** 实体元数据 → ES mapping properties（nested 字段递归展开子实体）；EsIndexOps 运维操作复用 */
    static Map<String, Property> propertiesOf(EntityMetadata md) {
        Map<String, Property> props = new LinkedHashMap<>();
        for (FieldMetadata f : md.getFields()) {
            props.put(f.getEsFieldName(), propertyOf(f));
        }
        return props;
    }

    private static Property propertyOf(FieldMetadata f) {
        return switch (f.getEsType()) {
            case "keyword" -> Property.of(p -> p.keyword(k -> k));
            case "text" -> Property.of(p -> p.text(t -> t.analyzer(f.getAnalyzer())));
            case "long" -> Property.of(p -> p.long_(l -> l));
            case "integer" -> Property.of(p -> p.integer(i -> i));
            case "short" -> Property.of(p -> p.short_(s -> s));
            case "byte" -> Property.of(p -> p.byte_(b -> b));
            case "double" -> Property.of(p -> p.double_(d -> d));
            case "float" -> Property.of(p -> p.float_(fl -> fl));
            case "boolean" -> Property.of(p -> p.boolean_(b -> b));
            case "date" -> Property.of(p -> p.date(d -> d));
            case "geo_point" -> Property.of(p -> p.geoPoint(g -> g));
            case "nested" -> Property.of(p -> p.nested(n -> n
                    .properties(propertiesOf(f.getNestedMetadata()))));
            default -> throw new EsOpsException("未知 ES 字段类型: " + f.getEsType());
        };
    }

    private void validate(EntityMetadata md) throws IOException {
        GetMappingResponse resp = client.indices().getMapping(g -> g.index(md.getIndexName()));
        // 实体索引名可能是 alias（rebuild 后升格）：响应 key 为物理索引名，取任一指向索引的 mapping
        Map<String, Property> existing;
        if (resp.result().containsKey(md.getIndexName())) {
            existing = resp.result().get(md.getIndexName()).mappings().properties();
        } else if (!resp.result().isEmpty()) {
            existing = resp.result().values().iterator().next().mappings().properties();
        } else {
            throw new EsOpsException("Index 托管校验失败: " + md.getIndexName() + " 无可用 mapping");
        }

        List<String> problems = new ArrayList<>();
        for (FieldMetadata f : md.getFields()) {
            Property p = existing.get(f.getEsFieldName());
            if (p == null) {
                problems.add(f.getEsFieldName() + " 字段缺失");
                continue;
            }
            String actual = p._kind().jsonValue();
            if (!actual.equals(f.getEsType())) {
                problems.add(f.getEsFieldName() + " 类型不一致：期望 " + f.getEsType() + "，实际 " + actual);
            }
        }
        if (problems.isEmpty()) {
            return;
        }
        String msg = "[mp-es] 索引 " + md.getIndexName() + " mapping 与实体定义不一致: " + String.join("; ", problems);
        if (properties.getIndex().getMismatchPolicy() == MismatchPolicy.FAIL) {
            throw new EsOpsException(msg);
        }
        log.warn(msg);
    }
}
