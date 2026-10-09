package io.github.kennethfan.mpes.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明实体字段为 ES nested 类型。字段类型必须为 List&lt;子实体&gt;，
 * 子实体字段按同一规则推导 mapping（@TableField / @EsText / @EsGeoPoint / @EsNested 递归生效）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EsNested {
}
