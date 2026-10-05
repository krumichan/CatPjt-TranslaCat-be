package jp.co.translacat.infrastructure.chat.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "chat.core.identity")
public class ChatCoreIdentityProperties {
    private String environment;
    private String issuer = "translacat-chat";
    private String audience = "translacat-be";
    private String service = "translacat-chat";
    private String secretBase64;
}
