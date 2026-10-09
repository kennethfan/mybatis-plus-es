package io.github.kennethfan.mpes.config;

import io.github.kennethfan.mpes.index.IndexManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 所有单例就绪后（即全部 Mapper FactoryBean 已登记实体）执行 Index 托管。
 */
@Slf4j
@RequiredArgsConstructor
public class EsIndexBootstrap implements SmartInitializingSingleton {

    private final IndexManager indexManager;
    private final EsProperties properties;

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.getIndex().isAutoCreate()) {
            log.info("[mp-es] Index 托管已关闭（mp-es.index.auto-create=false）");
            return;
        }
        indexManager.ensureAll();
    }
}
