package jp.co.translacat.domain.user.pushinstallation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PushInstallationProperties.class)
public class PushInstallationConfiguration {
    @Bean("pushInstallationClock")
    Clock pushInstallationClock() { return Clock.systemUTC(); }
}
