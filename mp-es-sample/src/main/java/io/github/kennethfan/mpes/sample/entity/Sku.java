package io.github.kennethfan.mpes.sample.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * SKU 子文档：作为 Product.skus 的 nested 子实体（@EsNested）。
 * 子实体无需 @TableName（不独立成索引），但需保留主键字段供 search_after 兜底排序等语义复用。
 */
@Data
public class Sku {

    @TableId
    private String skuCode;

    private String spec;

    private Integer quantity;
}
