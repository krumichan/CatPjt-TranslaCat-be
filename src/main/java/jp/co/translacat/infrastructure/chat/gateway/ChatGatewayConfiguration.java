package jp.co.translacat.infrastructure.chat.gateway;

import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.MyUserDetailsService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)

@EnableConfigurationProperties(ChatGatewayProperties.class)
public class ChatGatewayConfiguration {
    @Bean
    ChatGatewayTarget chatGatewayTarget(ChatGatewayProperties properties) {
        return new ChatGatewayTarget(properties);
    }

    @Bean
    ChatGatewayTokenIssuer chatGatewayTokenIssuer(ChatGatewayProperties properties) {
        return new ChatGatewayTokenIssuer(properties, Clock.systemUTC());
    }

    @Bean
    ChatGatewayUserAuthenticator chatGatewayUserAuthenticator(JWTService jwt, MyUserDetailsService users) {
        return new ChatGatewayUserAuthenticator(jwt, users);
    }
}
