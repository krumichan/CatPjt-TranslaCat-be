package jp.co.translacat.infrastructure.languagelearning.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningPracticeClient;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PracticeClientConfiguration {
    @Bean

    public LanguageLearningPracticeClient languageLearningPracticeClient(
            @Qualifier("languageLearningRestClient") RestClient client,
            LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        return new LanguageLearningPracticeClient(client, jwt, json);
    }
}
