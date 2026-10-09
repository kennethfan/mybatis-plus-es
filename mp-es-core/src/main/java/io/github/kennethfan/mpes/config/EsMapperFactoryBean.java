package io.github.kennethfan.mpes.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kennethfan.mpes.core.EsBaseMapper;
import io.github.kennethfan.mpes.core.EsEntityRegistry;
import io.github.kennethfan.mpes.core.EsMapperProxy;
import io.github.kennethfan.mpes.metadata.EntityMetadata;
import io.github.kennethfan.mpes.support.EsOpsException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

import java.lang.reflect.Proxy;

/**
 * Mapper 的 FactoryBean：从 Mapper 接口解析实体泛型，登记实体元数据，
 * 并以 JDK 动态代理产出 {@link EsBaseMapper} 实现。
 *
 * @param <T> Mapper 接口类型
 */
public class EsMapperFactoryBean<T> implements FactoryBean<T>, InitializingBean {

    private final Class<T> mapperInterface;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private EsEntityRegistry esEntityRegistry;

    @Autowired
    @Qualifier("mpEsObjectMapper")
    private ObjectMapper objectMapper;

    private EntityMetadata entityMetadata;

    public EsMapperFactoryBean(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    @Override
    public void afterPropertiesSet() {
        Class<?> entityClass = ResolvableType.forClass(mapperInterface)
                .as(EsBaseMapper.class)
                .getGeneric(0)
                .resolve();
        Assert.notNull(entityClass,
                () -> "无法从 " + mapperInterface.getName() + " 解析实体泛型（应继承 EsBaseMapper<实体>）");
        entityMetadata = esEntityRegistry.register(entityClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        return (T) Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                new Class<?>[]{mapperInterface},
                new EsMapperProxy<>(mapperInterface, entityMetadata, elasticsearchClient, objectMapper));
    }

    @Override
    public Class<?> getObjectType() {
        return mapperInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
