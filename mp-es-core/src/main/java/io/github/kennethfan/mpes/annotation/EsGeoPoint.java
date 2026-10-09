package io.github.kennethfan.mpes.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明实体字段为 ES geo_point 类型。字段类型必须为 {@link io.github.kennethfan.mpes.geo.GeoPoint}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EsGeoPoint {
}
