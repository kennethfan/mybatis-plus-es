package io.github.kennethfan.mpes.core;

import io.github.kennethfan.mpes.metadata.EntityMetadata;
import io.github.kennethfan.mpes.metadata.EntityMetadataParser;
import io.github.kennethfan.mpes.support.EsOpsException;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体元数据注册中心：Mapper FactoryBean 创建时登记实体，
 * Index 托管与查询翻译均从这里取元数据（每个实体只解析一次）。
 */
public class EsEntityRegistry {

    private final Map<Class<?>, EntityMetadata> entities = new ConcurrentHashMap<>();

    public EntityMetadata register(Class<?> entityClass) {
        return entities.computeIfAbsent(entityClass, EntityMetadataParser::parse);
    }

    public EntityMetadata get(Class<?> entityClass) {
        EntityMetadata md = entities.get(entityClass);
        if (md == null) {
            throw new EsOpsException("实体未注册: " + entityClass.getName() + "（请确认其 Mapper 已被 @EsMapperScan 扫描）");
        }
        return md;
    }

    public Collection<EntityMetadata> all() {
        return entities.values();
    }
}
