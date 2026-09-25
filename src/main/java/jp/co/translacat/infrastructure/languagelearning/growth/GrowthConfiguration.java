package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GrowthProperties.class)
public class GrowthConfiguration {
    @Bean
    public GrowthOutboxStore growthOutboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                               GrowthProperties properties) {
        properties.validate();
        var store = new GrowthOutboxStore(jdbc, manager, Clock.systemUTC());
        if (properties.isEnabled()) {
            store.verifySchema();
            store.verifyOrigin(properties.getSourceInstanceId());
        }
        return store;
    }

    @Bean
    public CoreGrowthCollector coreGrowthCollector(GrowthOutboxStore store, GrowthProperties properties,
                                                   ObjectMapper mapper, JdbcTemplate jdbc) {
        return new CoreGrowthCollector(store, properties, mapper, jdbc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "language-learning.remote", name = "enabled", havingValue = "true")
    public GrowthHttpClient growthHttpClient(@Qualifier("languageLearningRestClient") RestClient client,
                                             LanguageLearningInternalJwtProvider jwt, ObjectMapper mapper) {
        return new GrowthHttpClient(client, jwt, mapper);
    }

    @Bean
    public GrowthDispatcher growthDispatcher(GrowthOutboxStore store, GrowthProperties properties,
                                             ObjectProvider<GrowthHttpClient> clients) {
        return new GrowthDispatcher(store, properties, clients);
    }

    @Bean
    public GrowthReadGateway growthReadGateway(CoreGrowthCollector collector, GrowthProperties properties,
                                               GrowthOutboxStore store,
                                               GrowthDispatcher dispatcher, ObjectProvider<GrowthHttpClient> clients,
                                               PlatformTransactionManager manager) {
        return new RemoteGrowthGateway(collector, properties, store, dispatcher, clients, manager);
    }
}
