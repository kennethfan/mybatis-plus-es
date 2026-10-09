package io.github.kennethfan.mpes.sample.mapper;

import io.github.kennethfan.mpes.core.EsBaseMapper;
import io.github.kennethfan.mpes.sample.entity.Product;

/**
 * 商品 Mapper：继承 EsBaseMapper 即获得全部 CRUD / 条件查询 / 分页能力，
 * 无需任何实现类（由 EsMapperFactoryBean 动态代理产出）。
 */
public interface ProductMapper extends EsBaseMapper<Product> {
}
