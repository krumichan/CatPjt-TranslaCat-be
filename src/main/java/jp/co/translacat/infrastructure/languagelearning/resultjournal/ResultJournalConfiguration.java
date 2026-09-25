package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@EnableConfigurationProperties(ResultDeliveryProperties.class)
public class ResultJournalConfiguration {
    @Bean
    public ResultOutboxStore resultOutboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                               ResultDeliveryProperties properties) {
        properties.validate();
        var store = new ResultOutboxStore(jdbc, manager, Clock.systemUTC());
        if (properties.isEnabled()) store.verifySchema();
        return store;
    }

    @Bean
    public ResultJournalListener resultJournalListener(ResultOutboxStore store, ResultDeliveryProperties properties) {
        return new ResultJournalListener(store, properties);
    }

    @Bean
    public ResultOutboxDispatcher resultOutboxDispatcher(ResultOutboxStore store, ResultDeliveryProperties properties,
                                                         ObjectProvider<ResultJournalClient> clients) {
        return new ResultOutboxDispatcher(store, properties, clients);
    }

    @Bean
    @ConditionalOnProperty(prefix = "language-learning.remote", name = "enabled", havingValue = "true")
    public ResultJournalClient resultJournalClient(@Qualifier("languageLearningRestClient") RestClient client,
                                                   LanguageLearningInternalJwtProvider jwt, ObjectMapper mapper) {
        return new HttpResultJournalClient(client, jwt, mapper);
    }
}
