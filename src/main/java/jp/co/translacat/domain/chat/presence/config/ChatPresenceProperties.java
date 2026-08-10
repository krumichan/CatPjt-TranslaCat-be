package jp.co.translacat.domain.chat.presence.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "translacat.chat.presence")
public class ChatPresenceProperties {

    private String keyPrefix = "translacat:chat:presence";
    private Duration sessionTtl = Duration.ofSeconds(60);
    private Duration refreshInterval = Duration.ofSeconds(20);
    private Duration offlineGrace = Duration.ofSeconds(30);

    public void validate() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalStateException("Presence Redis key prefix must not be blank.");
        }
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalStateException("Presence session TTL must be greater than zero.");
        }
        if (refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalStateException("Presence refresh interval must be greater than zero.");
        }
        if (refreshInterval.compareTo(sessionTtl) >= 0) {
            throw new IllegalStateException("Presence refresh interval must be shorter than session TTL.");
        }
        if (offlineGrace == null || offlineGrace.isNegative()) {
            throw new IllegalStateException("Presence offline grace must not be negative.");
        }
    }
}
