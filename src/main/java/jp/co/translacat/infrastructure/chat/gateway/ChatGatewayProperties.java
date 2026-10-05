package jp.co.translacat.infrastructure.chat.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "chat.gateway")
public class ChatGatewayProperties {
    private String baseUrl;
    private String environment;
    private String issuer = "translacat-be";
    private String audience = "translacat-chat";
    private String service = "translacat-be";
    private String secretBase64;
    private int timeoutSeconds = 10;
    private int tokenLifetimeSeconds = 60;
}
