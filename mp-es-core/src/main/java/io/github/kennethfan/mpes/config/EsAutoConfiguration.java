package io.github.kennethfan.mpes.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.kennethfan.mpes.core.EsEntityRegistry;
import io.github.kennethfan.mpes.index.EsIndexOps;
import io.github.kennethfan.mpes.index.IndexManager;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 库即 starter（Q20-A）：依赖即生效。装配 ES 客户端、元数据注册中心、Index 托管。
 */
@AutoConfiguration
@ConditionalOnClass(ElasticsearchClient.class)
@EnableConfigurationProperties(EsProperties.class)
public class EsAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(RestClient.class)
    public RestClient mpEsRestClient(EsProperties properties) {
        HttpHost[] hosts = properties.getUris().stream().map(HttpHost::create).toArray(HttpHost[]::new);
        RestClientBuilder builder = RestClient.builder(hosts);
        if (notBlank(properties.getUsername()) && notBlank(properties.getPassword())) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));
            builder.setHttpClientConfigCallback(b -> b.setDefaultCredentialsProvider(credentialsProvider));
        }
        return builder.build();
    }

    /** 客户端专用 ObjectMapper：支持 java.time，不污染应用自身的 Jackson 配置 */
    @Bean(name = "mpEsObjectMapper")
    @ConditionalOnMissingBean(name = "mpEsObjectMapper")
    public ObjectMapper mpEsObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    @ConditionalOnMissingBean(ElasticsearchClient.class)
    public ElasticsearchClient elasticsearchClient(RestClient mpEsRestClient, ObjectMapper mpEsObjectMapper) {
        ElasticsearchTransport transport = new RestClientTransport(mpEsRestClient, new JacksonJsonpMapper(mpEsObjectMapper));
        return new ElasticsearchClient(transport);
    }

    @Bean
    @ConditionalOnMissingBean
    public EsEntityRegistry esEntityRegistry() {
        return new EsEntityRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexManager esIndexManager(ElasticsearchClient elasticsearchClient,
                                       EsEntityRegistry esEntityRegistry,
                                       EsProperties properties) {
        return new IndexManager(elasticsearchClient, esEntityRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public EsIndexOps esIndexOps(ElasticsearchClient elasticsearchClient,
                                 EsEntityRegistry esEntityRegistry) {
        return new EsIndexOps(elasticsearchClient, esEntityRegistry);
    }

    @Bean
    public EsIndexBootstrap esIndexBootstrap(IndexManager esIndexManager, EsProperties properties) {
        return new EsIndexBootstrap(esIndexManager, properties);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
