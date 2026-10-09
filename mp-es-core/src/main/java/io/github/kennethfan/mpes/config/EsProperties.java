package io.github.kennethfan.mpes.config;

import io.github.kennethfan.mpes.support.MismatchPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * mp-es.* 配置项（Q21）。
 */
@Data
@ConfigurationProperties(prefix = "mp-es")
public class EsProperties {

    /** ES 节点地址，默认单机 */
    private List<String> uris = List.of("http://localhost:9200");

    /** 可选 Basic 认证 */
    private String username;
    private String password;

    private Index index = new Index();

    @Data
    public static class Index {

        /** 启动时是否托管索引（create-if-absent）。关闭后不做创建与校验 */
        private boolean autoCreate = true;

        /** mapping 与实体定义不一致时的策略：WARN（默认）/ FAIL */
        private MismatchPolicy mismatchPolicy = MismatchPolicy.WARN;
    }
}
