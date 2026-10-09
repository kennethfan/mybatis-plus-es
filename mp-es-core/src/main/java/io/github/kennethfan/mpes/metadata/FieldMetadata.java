package io.github.kennethfan.mpes.metadata;

import io.github.kennethfan.mpes.support.EsOpsException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;

/**
 * 实体单个字段的 ES 映射元数据。
 */
@Getter
@RequiredArgsConstructor
public class FieldMetadata {

    /** 实体字段（读取值时反射访问） */
    private final Field field;

    /** 属性名（Lambda 引用所用） */
    private final String property;

    /** ES 文档字段名（@TableField 可覆盖，默认为属性名原样） */
    private final String esFieldName;

    /** ES 字段类型：keyword / text / integer / long / double / float / boolean / date */
    private final String esType;

    /** 分词器，仅 esType=text 时非空 */
    private final String analyzer;

    /** 是否为主键字段（@TableId，映射为 ES 文档 _id） */
    private final boolean idField;

    /** 从实体实例读取该字段值 */
    public Object readValue(Object entity) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new EsOpsException("读取字段失败: " + field.getName(), e);
        }
    }
}
