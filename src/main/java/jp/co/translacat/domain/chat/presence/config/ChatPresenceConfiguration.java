package jp.co.translacat.domain.chat.presence.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatPresenceProperties.class)
public class ChatPresenceConfiguration {
}
