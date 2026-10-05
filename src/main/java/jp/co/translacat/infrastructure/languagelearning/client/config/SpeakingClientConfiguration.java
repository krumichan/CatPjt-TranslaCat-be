package jp.co.translacat.infrastructure.languagelearning.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSpeakingClient;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class SpeakingClientConfiguration {
    @Bean

    public LanguageLearningSpeakingClient languageLearningSpeakingClient(
            RestClient.Builder builder, LanguageLearningClientProperties properties,
            LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        // 음성 발화는 LL이 STT·대화·TTS를 마친 뒤 응답하므로 일반 조회의 5초 기한을 공유하지 않는다.
        LanguageLearningClientConfiguration.validate(properties);
        int timeout = properties.getSpeaking().getReadTimeoutMs();
        if (timeout < 1000 || timeout > 600000) {
            throw new IllegalStateException("Speaking HTTP timeout은 1000~600000ms여야 합니다.");
        }

        var http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRemote().getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        var client = builder.clone().baseUrl(properties.getUrl().replaceAll("/+$", ""))
                .requestFactory(factory).build();
        return new LanguageLearningSpeakingClient(client, jwt, json);
    }
}
