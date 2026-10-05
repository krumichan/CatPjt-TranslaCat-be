package jp.co.translacat.infrastructure.languagelearning.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "language-learning")
public class LanguageLearningClientProperties {

    private String url;
    private Remote remote = new Remote();
    private InternalJwt internalJwt = new InternalJwt();
    private LevelTest levelTest = new LevelTest();
    private Writing writing = new Writing();
    private Speaking speaking = new Speaking();

    @Getter
    @Setter
    public static class Speaking {
        // 동기 STT·대화·TTS의 기본 단계별 기한과 응답 전달 시간을 포함한다.
        private int readTimeoutMs = 360000;
    }

    @Getter
    @Setter
    public static class Writing {
        // LL 생성의 기존 240초 deadline 뒤 응답 전달 여유만 둔다.
        private int readTimeoutMs = 270000;
    }

    @Getter
    @Setter
    public static class LevelTest {
        private int readTimeoutMs = 360000;
    }

    @Getter
    @Setter
    public static class Remote {
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }

    @Getter
    @Setter
    public static class InternalJwt {
        private String issuer = "translacat-be";
        private String audience = "translacat-ll";
        private String callerService = "translacat-be";
        private String secretBase64;
        private long ttlSeconds = 120;
    }
}
