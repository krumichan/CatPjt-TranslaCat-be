package jp.co.translacat.infrastructure.novel.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ConfigurationProperties(prefix = "novel.gateway")
@Component
public class NovelGatewayProperties {
    private String baseUrl;
    private String secretBase64;
    private int timeoutSeconds = 40;
}
