package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class GrowthConfiguration {
    @Bean

    public GrowthHttpClient growthHttpClient(@Qualifier("languageLearningRestClient") RestClient client,
                                             LanguageLearningInternalJwtProvider jwt, ObjectMapper mapper) {
        return new GrowthHttpClient(client, jwt, mapper);
    }

    @Bean
    public GrowthReadGateway growthReadGateway(GrowthHttpClient client) {
        return new RemoteGrowthGateway(client);
    }
}
