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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VoiceWebSocketTicketService {

    private static final String SUBJECT = "voice-websocket";
    private static final int MAX_USED_TICKETS = 8192;

    private final String secretKey;
    private final VoicePolicyProperties policy;
    // 현재 단일 BE 인스턴스에서만 발급 티켓을 수락한다. 재시작 뒤 이전 티켓은 다시 쓸 수 없다.
    private final String issuer = UUID.randomUUID().toString();
    private final Map<String, Instant> usedTickets = new HashMap<>();
    private Instant lastCleanup = Instant.EPOCH;

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
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
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

    public TicketClaims consume(String ticket, String expectedSessionId, VoiceChannel expectedChannel) {
        if (ticket == null || ticket.isBlank()) {
            throw invalidTicket("Voice WebSocket ticket is missing.");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .requireIssuer(issuer)
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
            if (!expectedSessionId.equals(sessionId) || !expectedChannel.name().equals(channelValue)) {
                throw invalidTicket(
                        "Invalid Voice WebSocket ticket scope."
                );
            }

            // 서명·만료·경로를 확인한 뒤에만 동시 handshake 중 하나에 사용 권한을 준다.
            String ticketId = claims.getId();
            Date expiration = claims.getExpiration();
            if (ticketId == null || expiration == null) {
                throw invalidTicket("Voice WebSocket ticket identity is missing.");
            }
            consumeOnce(ticketId, expiration.toInstant());
            return new TicketClaims(number.longValue(), sessionId, expectedChannel);
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidTicket(
                    "Voice WebSocket ticket is invalid or expired."
            );
        }
    }

    private synchronized void consumeOnce(String ticketId, Instant expiration) {
        Instant now = Instant.now();
        // 시계가 뒤로 움직여도 정리한 티켓이 다시 유효해지지 않도록 거절한다.
        if (now.isBefore(lastCleanup) || !now.isBefore(expiration)) {
            throw invalidTicket("Voice WebSocket ticket cannot be used.");
        }
        if (!now.isBefore(lastCleanup.plusSeconds(1))) {
            usedTickets.values().removeIf(usedUntil -> !now.isBefore(usedUntil));
            lastCleanup = now;
        }
        // 한도가 찼을 때 살아 있는 기록을 버리면 재사용이 가능하므로 요청을 거절한다.
        if (usedTickets.containsKey(ticketId) || usedTickets.size() >= MAX_USED_TICKETS) {
            throw invalidTicket("Voice WebSocket ticket cannot be used.");
        }
        usedTickets.put(ticketId, expiration);
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
