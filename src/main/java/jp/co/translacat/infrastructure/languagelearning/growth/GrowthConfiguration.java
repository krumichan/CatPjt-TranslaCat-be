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
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GrowthProperties.class)
public class GrowthConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "language-learning.remote", name = "enabled", havingValue = "true")
    public GrowthHttpClient growthHttpClient(@Qualifier("languageLearningRestClient") RestClient client,
                                             LanguageLearningInternalJwtProvider jwt, ObjectMapper mapper) {
        return new GrowthHttpClient(client, jwt, mapper);
    }

    @Bean
    public GrowthReadGateway growthReadGateway(GrowthProperties properties,
                                               ObjectProvider<GrowthHttpClient> clients) {
        return new RemoteGrowthGateway(properties, clients);
    }
}
