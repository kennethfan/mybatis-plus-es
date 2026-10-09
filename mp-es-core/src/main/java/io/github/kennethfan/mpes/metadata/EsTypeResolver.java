package io.github.kennethfan.mpes.metadata;

import io.github.kennethfan.mpes.annotation.EsText;
import io.github.kennethfan.mpes.geo.GeoPoint;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * Java 类型 → ES 字段类型推导（Q16-A：以 Java 类型为主，String 默认 keyword，
 * 需要分词检索的字段由 {@link EsText} 显式声明）。
 */
public final class EsTypeResolver {

    private EsTypeResolver() {
    }

    /**
     * @param esText 字段上的 {@link EsText} 注解，可为 null
     * @return ES 字段类型名（keyword / text / long / integer / double / float / boolean / date）
     */
    public static String resolve(Class<?> javaType, EsText esText) {
        if (esText != null) {
            return "text";
        }
        if (javaType == String.class || javaType == UUID.class || javaType.isEnum()
                || CharSequence.class.isAssignableFrom(javaType)) {
            return "keyword";
        }
        if (javaType == Long.class || javaType == long.class || javaType == BigInteger.class) {
            return "long";
        }
        if (javaType == Integer.class || javaType == int.class) {
            return "integer";
        }
        if (javaType == Short.class || javaType == short.class) {
            return "short";
        }
        if (javaType == Byte.class || javaType == byte.class) {
            return "byte";
        }
        if (javaType == Double.class || javaType == double.class || javaType == BigDecimal.class) {
            return "double";
        }
        if (javaType == Float.class || javaType == float.class) {
            return "float";
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return "boolean";
        }
        if (isDateType(javaType)) {
            return "date";
        }
        if (javaType == GeoPoint.class) {
            return "geo_point";
        }
        throw new EsOpsException("不支持的实体字段类型: " + javaType.getName()
                + "（geo 字段用 GeoPoint 类型，nested 字段用 @EsNested + List<子实体>，"
                + "或用 @TableField(exist=false) 排除）");
    }

    private static boolean isDateType(Class<?> type) {
        return type == LocalDate.class || type == LocalDateTime.class || type == LocalTime.class
                || type == Instant.class || type == OffsetDateTime.class || type == ZonedDateTime.class
                || type == Date.class;
    }
}
