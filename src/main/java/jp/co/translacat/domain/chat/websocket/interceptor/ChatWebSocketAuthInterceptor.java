package jp.co.translacat.domain.chat.websocket.interceptor;

import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.MyUserDetailsService;
import jp.co.translacat.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ChatWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern CHAT_ROOM_TOPIC_PATTERN =
            Pattern.compile("^/topic/chat/rooms/(\\d+)$");
    private static final Pattern CHAT_ROOM_SEND_PATTERN =
            Pattern.compile("^/app/chat/rooms/(\\d+)/messages$");

    private final JWTService jwtService;
    private final MyUserDetailsService myUserDetailsService;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;

    @NotNull
    @Override
    public Message<?> preSend(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> {
                ensureAuthenticated(accessor);
                validateSubscribe(accessor);
            }
            case SEND -> {
                ensureAuthenticated(accessor);
                validateSend(accessor);
            }
            default -> {
                // 별도 처리 없음
            }
        }

        return message;
    }

    private void ensureAuthenticated(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();

        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal) {
            return;
        }

        /*
         * CONNECT에서 setUser를 해도 환경/설정에 따라 SUBSCRIBE/SEND 프레임에서
         * accessor.getUser()가 비어 있는 경우가 있다.
         *
         * FE는 SUBSCRIBE/SEND에도 Authorization native header를 보내고 있으므로,
         * Principal이 없을 때는 같은 토큰으로 다시 인증해 준다.
         */
        authenticate(accessor);
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = extractBearerToken(accessor);

        if (token == null) {
            throw new BusinessException("WebSocket 인증 토큰이 없습니다.");
        }

        String username = jwtService.extractUsername(token);
        UserPrincipal userPrincipal =
                (UserPrincipal) myUserDetailsService.loadUserByUsername(username);

        if (!jwtService.validateToken(token, userPrincipal)) {
            throw new BusinessException("유효하지 않은 WebSocket 인증 토큰입니다.");
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities()
        );

        accessor.setUser(authentication);
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders =
                accessor.getNativeHeader(AUTHORIZATION_HEADER);

        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return null;
        }

        String authorizationHeader = authorizationHeaders.get(0);

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    private void validateSubscribe(StompHeaderAccessor accessor) {
        Long chatRoomId = extractChatRoomId(
                accessor.getDestination(),
                CHAT_ROOM_TOPIC_PATTERN
        );

        if (chatRoomId == null) {
            return;
        }

        Long loginUserId = resolveLoginUserId(accessor);

        chatRoomMemberQueryService.getActiveMember(
                loginUserId,
                chatRoomId
        );
    }

    private void validateSend(StompHeaderAccessor accessor) {
        Long chatRoomId = extractChatRoomId(
                accessor.getDestination(),
                CHAT_ROOM_SEND_PATTERN
        );

        if (chatRoomId == null) {
            return;
        }

        Long loginUserId = resolveLoginUserId(accessor);

        chatRoomMemberQueryService.getActiveMember(
                loginUserId,
                chatRoomId
        );
    }

    private Long extractChatRoomId(
            String destination,
            Pattern pattern
    ) {
        if (destination == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(destination);

        if (!matcher.matches()) {
            return null;
        }

        return Long.parseLong(matcher.group(1));
    }

    private Long resolveLoginUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();

        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException("WebSocket 인증 정보가 없습니다.");
        }

        return userPrincipal.getId();
    }
}
