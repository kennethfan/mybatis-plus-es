package io.github.kennethfan.mpes.metadata;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.kennethfan.mpes.annotation.EsText;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
            String esType = EsTypeResolver.resolve(f.getType(), esText);
            String analyzer = esText != null ? esText.analyzer() : null;

            fields.add(new FieldMetadata(f, property, esFieldName, esType, analyzer, isId));
        }

        if (fields.isEmpty()) {
            throw new EsOpsException("实体 " + entityClass.getName() + " 没有可映射字段");
        }
        FieldMetadata idField = fields.stream().filter(FieldMetadata::isIdField).findFirst()
                .orElseThrow(() -> new EsOpsException(
                        "实体 " + entityClass.getName() + " 缺少主键：请标注 @TableId，或定义名为 id 的字段"));

        return new EntityMetadata(entityClass, indexName, idField, fields);
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
