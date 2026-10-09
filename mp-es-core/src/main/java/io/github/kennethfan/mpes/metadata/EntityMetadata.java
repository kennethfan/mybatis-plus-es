package io.github.kennethfan.mpes.metadata;

import io.github.kennethfan.mpes.support.EsOpsException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 实体级的 ES 映射元数据：索引名 + 全部字段（含主键）。
 */
@Getter
@RequiredArgsConstructor
public class EntityMetadata {

    private final Class<?> entityClass;

    /** ES 索引名（@TableName.value，缺省为类名首字母小写） */
    private final String indexName;

    /** 主键字段元数据 */
    private final FieldMetadata idField;

    /** 全部字段元数据（含主键字段） */
    private final List<FieldMetadata> fields;

    private final Map<String, FieldMetadata> byProperty;

    public EntityMetadata(Class<?> entityClass, String indexName, FieldMetadata idField, List<FieldMetadata> fields) {
        this.entityClass = entityClass;
        this.indexName = indexName;
        this.idField = idField;
        this.fields = List.copyOf(fields);
        this.byProperty = fields.stream()
                .collect(Collectors.toUnmodifiableMap(FieldMetadata::getProperty, Function.identity()));
    }

    /** 按属性名查字段元数据（Wrapper 的 Lambda 属性名入口） */
    public FieldMetadata fieldByProperty(String property) {
        FieldMetadata fm = byProperty.get(property);
        if (fm == null) {
            throw new EsOpsException(
                    "实体 " + entityClass.getSimpleName() + " 不存在属性: " + property);
        }
        return fm;
    }

    /** 主键值 → ES 文档 _id */
    public String idOf(Object entity) {
        Object v = idField.readValue(entity);
        if (v == null) {
            throw new EsOpsException(
                    "实体 " + entityClass.getSimpleName() + " 主键为空，无法作为文档 _id: " + idField.getProperty());
        }
        return String.valueOf(v);
    }
}
