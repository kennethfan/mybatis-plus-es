package io.github.kennethfan.mpes.metadata;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.kennethfan.mpes.annotation.EsGeoPoint;
import io.github.kennethfan.mpes.annotation.EsNested;
import io.github.kennethfan.mpes.annotation.EsText;
import io.github.kennethfan.mpes.geo.GeoPoint;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析实体类 → {@link EntityMetadata}。
 * <p>
 * 映射来源为 MP 注解（ADR-0004）：@TableName → 索引名，@TableId → 文档 _id，
 * @TableField → ES 字段名（value）与排除（exist=false），@EsText → text 类型补充。
 * ES 字段名默认取属性名原样（camelCase），不做隐式下划线转换。
 */
public final class EntityMetadataParser {

    private EntityMetadataParser() {
    }

    public static EntityMetadata parse(Class<?> entityClass) {
        return parse(entityClass, new java.util.HashSet<>());
    }

    private static EntityMetadata parse(Class<?> entityClass, java.util.Set<Class<?>> visited) {
        if (!visited.add(entityClass)) {
            throw new EsOpsException("nested 实体存在循环引用: " + entityClass.getName());
        }
        String indexName = resolveIndexName(entityClass);
        Field rawIdField = findIdField(entityClass);

        List<FieldMetadata> fields = new ArrayList<>();
        for (Field f : entityClass.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                continue;
            }
            TableField tf = f.getAnnotation(TableField.class);
            if (tf != null && !tf.exist()) {
                continue;
            }

            boolean isId = f.equals(rawIdField);
            String property = f.getName();
            String esFieldName = resolveEsFieldName(f, tf, isId);
            EsText esText = f.getAnnotation(EsText.class);

            EntityMetadata nestedMetadata = null;
            String esType;
            EsNested esNested = f.getAnnotation(EsNested.class);
            if (esNested != null) {
                esType = "nested";
                nestedMetadata = parse(resolveNestedElementType(f, entityClass), visited);
            } else {
                esType = EsTypeResolver.resolve(f.getType(), esText);
            }

            EsGeoPoint esGeoPoint = f.getAnnotation(EsGeoPoint.class);
            if (esGeoPoint != null && f.getType() != GeoPoint.class) {
                throw new EsOpsException("字段 " + entityClass.getSimpleName() + "." + property
                        + " 标注了 @EsGeoPoint，但类型不是 GeoPoint");
            }
            String analyzer = esText != null ? esText.analyzer() : null;

            fields.add(new FieldMetadata(f, property, esFieldName, esType, analyzer, isId, nestedMetadata));
        }

        if (fields.isEmpty()) {
            throw new EsOpsException("实体 " + entityClass.getName() + " 没有可映射字段");
        }
        FieldMetadata idField = fields.stream().filter(FieldMetadata::isIdField).findFirst()
                .orElseThrow(() -> new EsOpsException(
                        "实体 " + entityClass.getName() + " 缺少主键：请标注 @TableId，或定义名为 id 的字段"));

        visited.remove(entityClass);
        return new EntityMetadata(entityClass, indexName, idField, fields);
    }

    /**
     * 解析 nested 字段的子实体类型：支持 List&lt;X&gt; / X 两种声明形式。
     */
    private static Class<?> resolveNestedElementType(Field f, Class<?> entityClass) {
        Type genericType = f.getGenericType();
        if (genericType instanceof ParameterizedType pt
                && pt.getRawType() == List.class
                && pt.getActualTypeArguments().length == 1
                && pt.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        if (f.getType() == List.class || f.getType().isInterface() || f.getType().isPrimitive()) {
            throw new EsOpsException("字段 " + entityClass.getSimpleName() + "." + f.getName()
                    + " 标注了 @EsNested，但无法解析子实体类型（仅支持具体实体类或 List<具体实体类>）");
        }
        return f.getType();
    }

    private static String resolveIndexName(Class<?> entityClass) {
        TableName tn = entityClass.getAnnotation(TableName.class);
        if (tn != null && !tn.value().isEmpty()) {
            return tn.value();
        }
        String simple = entityClass.getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    private static Field findIdField(Class<?> entityClass) {
        for (Field f : entityClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(TableId.class)) {
                return f;
            }
        }
        for (Field f : entityClass.getDeclaredFields()) {
            if ("id".equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    private static String resolveEsFieldName(Field f, TableField tf, boolean isId) {
        if (tf != null && !tf.value().isEmpty()) {
            return tf.value();
        }
        if (isId) {
            TableId tid = f.getAnnotation(TableId.class);
            if (tid != null && !tid.value().isEmpty()) {
                return tid.value();
            }
        }
        return f.getName();
    }
}
