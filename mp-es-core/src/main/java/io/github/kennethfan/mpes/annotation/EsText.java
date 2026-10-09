package io.github.kennethfan.mpes.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明实体字段为 ES text 类型（分词检索字段）。
 * <p>
 * MP 注解不携带 ES 类型语义（见 ADR-0004），本注解仅补充 ES 特有信息：
 * 未标注时 String 默认推导为 keyword；标注后该字段以 text 存储并可全文检索。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EsText {

    /**
     * 分词器，默认 standard。
     */
    String analyzer() default "standard";
}
