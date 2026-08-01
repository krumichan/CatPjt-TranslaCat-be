package jp.co.translacat.domain.chat.websocket.interceptor;

import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.MyUserDetailsService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketAuthInterceptorOpenChatBanTest {

    @Mock private JWTService jwtService;
    @Mock private MyUserDetailsService myUserDetailsService;
    @Mock private ChatRoomMemberQueryService memberQueryService;
    @Mock private OpenChatAccessService accessService;
    @Mock private UserPrincipal userPrincipal;

    private ChatWebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ChatWebSocketAuthInterceptor(
                jwtService,
                myUserDetailsService,
                memberQueryService,
                accessService
        );
        when(userPrincipal.getId()).thenReturn(10L);
    }

    @Test
    void bannedUserCannotSubscribeToOpenRoomTopic() {
        doThrow(new BusinessException(
                "banned",
                OpenChatErrorCode.BANNED
        )).when(accessService).validateWebSocketAccess(10L, 100L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> interceptor.preSend(
                        message(
                                StompCommand.SUBSCRIBE,
                                "/topic/chat/rooms/100"
                        ),
                        mock(org.springframework.messaging.MessageChannel.class)
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BANNED));

        verify(memberQueryService, never())
                .getActiveMember(10L, 100L);
    }

    @Test
    void bannedUserCannotSendToOpenRoomDestination() {
        doThrow(new BusinessException(
                "banned",
                OpenChatErrorCode.BANNED
        )).when(accessService).validateWebSocketAccess(10L, 100L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> interceptor.preSend(
                        message(
                                StompCommand.SEND,
                                "/app/chat/rooms/100/messages"
                        ),
                        mock(org.springframework.messaging.MessageChannel.class)
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BANNED));

        verify(memberQueryService, never())
                .getActiveMember(10L, 100L);
    }

    private Message<byte[]> message(
            StompCommand command,
            String destination
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(
                new UsernamePasswordAuthenticationToken(
                        userPrincipal,
                        null,
                        userPrincipal.getAuthorities()
                )
        );
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

}
