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

    @Getter
    @Setter
    public static class Remote {
        private boolean enabled = false;
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
