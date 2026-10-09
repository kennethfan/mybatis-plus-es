package io.github.kennethfan.mpes.sample.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.kennethfan.mpes.annotation.EsGeoPoint;
import io.github.kennethfan.mpes.annotation.EsNested;
import io.github.kennethfan.mpes.annotation.EsText;
import io.github.kennethfan.mpes.geo.GeoPoint;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 示例实体：商品（Q11，六类字段类型全覆盖）。
 * <p>
 * 映射来源为 MP 注解（ADR-0004）：@TableName → 索引名，@TableId → 文档 _id，
 * @TableField → ES 字段名，@EsText → text 分词字段（ADR 之外唯一的 ES 专属注解）。
 */
@Data
@TableName("mpes_product")
public class Product {

    /** 主键，映射为 ES 文档 _id */
    @TableId
    private Long id;

    /** keyword：@TableField 显式指定 ES 字段名 */
    @TableField("product_name")
    private String productName;

    /** text：@EsText 声明分词检索字段（String 默认 keyword，标注后覆盖为 text） */
    @EsText(analyzer = "standard")
    private String description;

    /** 数值（BigDecimal → double） */
    private BigDecimal price;

    /** 数值（Integer → integer） */
    private Integer stock;

    /** 日期（LocalDate → date） */
    private LocalDate launchDate;

    /** 布尔 */
    private Boolean onSale;

    /** geo_point：坐标（@EsGeoPoint 显式标注，类型必须是 GeoPoint） */
    @EsGeoPoint
    private GeoPoint location;

    /** nested：SKU 子文档（List<子实体> 声明） */
    @EsNested
    private List<Sku> skus;
}
