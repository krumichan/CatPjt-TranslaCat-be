package jp.co.translacat.infrastructure.languagelearning.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSpeakingClient;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SpeakingClientConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "language-learning.remote", name = "enabled", havingValue = "true")
    public LanguageLearningSpeakingClient languageLearningSpeakingClient(
            @Qualifier("languageLearningRestClient") RestClient client,
            LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        return new LanguageLearningSpeakingClient(client, jwt, json);
    }
}
