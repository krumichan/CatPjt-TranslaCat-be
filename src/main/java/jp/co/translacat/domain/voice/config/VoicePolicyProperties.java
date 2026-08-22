package jp.co.translacat.domain.voice.config;

import lombok.Getter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class VoicePolicyProperties {

    private final boolean enabled;
    private final long maxSessionMs;
    private final long dailyLimitMs;
    private final int maxAudioFrameBytes;
    private final int maxRelayBufferedAudioMs;
    private final int frameDurationMs;
    private final int websocketTicketTtlSeconds;
    private final int aiConnectTimeoutMs;
    private final int aiCircuitOpenSeconds;
    private final int aiCircuitFailureThreshold;
    private final int sessionCompleteTimeoutMs;
    private final int staleSessionMinutes;
    private final int endpointingSilenceMs;
    private final int minUtteranceDurationMs;
    private final int maxUtteranceDurationMs;
    private final double languageLockConfidence;
    private final double languageSwitchConfidence;
    private final int languageSwitchConsecutiveCount;

    public VoicePolicyProperties(
            @Value("${translacat.voice.enabled:true}")
            boolean enabled,
            @Value("${translacat.voice.max-session-ms:3600000}")
            long maxSessionMs,
            @Value("${translacat.voice.daily-limit-ms:7200000}")
            long dailyLimitMs,
            @Value("${translacat.voice.max-audio-frame-bytes:6400}")
            int maxAudioFrameBytes,
            @Value("${translacat.voice.max-relay-buffered-audio-ms:3000}")
            int maxRelayBufferedAudioMs,
            @Value("${translacat.voice.frame-duration-ms:100}")
            int frameDurationMs,
            @Value("${translacat.voice.websocket-ticket-ttl-seconds:30}")
            int websocketTicketTtlSeconds,
            @Value("${translacat.voice.ai-connect-timeout-ms:3000}")
            int aiConnectTimeoutMs,
            @Value("${translacat.voice.ai-circuit-open-seconds:10}")
            int aiCircuitOpenSeconds,
            @Value("${translacat.voice.ai-circuit-failure-threshold:3}")
            int aiCircuitFailureThreshold,
            @Value("${translacat.voice.session-complete-timeout-ms:5000}")
            int sessionCompleteTimeoutMs,
            @Value("${translacat.voice.stale-session-minutes:90}")
            int staleSessionMinutes,
            @Value("${translacat.voice.endpointing-silence-ms:300}")
            int endpointingSilenceMs,
            @Value("${translacat.voice.min-utterance-duration-ms:250}")
            int minUtteranceDurationMs,
            @Value("${translacat.voice.max-utterance-duration-ms:10000}")
            int maxUtteranceDurationMs,
            @Value("${translacat.voice.language-lock-confidence:0.80}")
            double languageLockConfidence,
            @Value("${translacat.voice.language-switch-confidence:0.85}")
            double languageSwitchConfidence,
            @Value("${translacat.voice.language-switch-consecutive-count:3}")
            int languageSwitchConsecutiveCount
    ) {
        this.enabled = enabled;
        this.maxSessionMs = maxSessionMs;
        this.dailyLimitMs = dailyLimitMs;
        this.maxAudioFrameBytes = maxAudioFrameBytes;
        this.maxRelayBufferedAudioMs = maxRelayBufferedAudioMs;
        this.frameDurationMs = frameDurationMs;
        this.websocketTicketTtlSeconds = websocketTicketTtlSeconds;
        this.aiConnectTimeoutMs = aiConnectTimeoutMs;
        this.aiCircuitOpenSeconds = aiCircuitOpenSeconds;
        this.aiCircuitFailureThreshold = aiCircuitFailureThreshold;
        this.sessionCompleteTimeoutMs = sessionCompleteTimeoutMs;
        this.staleSessionMinutes = staleSessionMinutes;
        this.endpointingSilenceMs = endpointingSilenceMs;
        this.minUtteranceDurationMs = minUtteranceDurationMs;
        this.maxUtteranceDurationMs = maxUtteranceDurationMs;
        this.languageLockConfidence = languageLockConfidence;
        this.languageSwitchConfidence = languageSwitchConfidence;
        this.languageSwitchConsecutiveCount = languageSwitchConsecutiveCount;
    }
}
