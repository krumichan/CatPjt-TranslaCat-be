package jp.co.translacat.domain.voice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class VoiceWebSocketTicketService {

    private static final String SUBJECT = "voice-websocket";

    private final String secretKey;
    private final VoicePolicyProperties policy;

    public VoiceWebSocketTicketService(
            @Value("${jwt.token.secret-key}") String secretKey,
            VoicePolicyProperties policy
    ) {
        this.secretKey = secretKey;
        this.policy = policy;
    }

    public String issue(
            Long userId,
            String sessionId,
            VoiceChannel channel
    ) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(SUBJECT)
                .claim("uid", userId)
                .claim("sid", sessionId)
                .claim("channel", channel.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusSeconds(
                                policy.getWebsocketTicketTtlSeconds()
                        )
                ))
                .signWith(getKey())
                .compact();
    }

    public TicketClaims parse(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw invalidTicket("Voice WebSocket ticket is missing.");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(ticket)
                    .getPayload();

            if (!SUBJECT.equals(claims.getSubject())) {
                throw invalidTicket(
                        "Invalid Voice WebSocket ticket subject."
                );
            }

            Object rawUserId = claims.get("uid");
            if (!(rawUserId instanceof Number number)) {
                throw invalidTicket(
                        "Invalid Voice WebSocket ticket user."
                );
            }

            String sessionId = claims.get("sid", String.class);
            String channelValue = claims.get("channel", String.class);
            if (sessionId == null || channelValue == null) {
                throw invalidTicket(
                        "Invalid Voice WebSocket ticket scope."
                );
            }

            return new TicketClaims(
                    number.longValue(),
                    sessionId,
                    VoiceChannel.valueOf(channelValue)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidTicket(
                    "Voice WebSocket ticket is invalid or expired."
            );
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secretKey)
        );
    }

    private BusinessException invalidTicket(String message) {
        return new BusinessException(
                message,
                VoiceErrorCode.WEBSOCKET_TICKET_INVALID
        );
    }

    public record TicketClaims(
            Long userId,
            String sessionId,
            VoiceChannel channel
    ) {
    }
}
