package io.github.kennethfan.mpes.annotation;

import io.github.kennethfan.mpes.config.EsMapperScannerRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扫描继承 {@link io.github.kennethfan.mpes.core.EsBaseMapper} 的 Mapper 接口并注册为 Bean，
 * 使用体验对齐 MP 的 @MapperScan。
 * <p>
 * 示例：{@code @EsMapperScan("com.example.mapper")}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(EsMapperScannerRegistrar.class)
public @interface EsMapperScan {

    /** 扫描的基础包（别名，对齐 MP 写法） */
    String[] value() default {};

    /** 扫描的基础包 */
    String[] basePackages() default {};
}
