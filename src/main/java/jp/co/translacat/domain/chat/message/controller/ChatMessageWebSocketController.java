package jp.co.translacat.domain.chat.message.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.dto.websocket.request.ChatMessageSendRequestDto;
import jp.co.translacat.domain.chat.message.service.ChatMessageCommandService;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatMessageWebSocketController {

    private final ChatMessageCommandService chatMessageCommandService;
    private final ChatWebSocketEventPublisher chatWebSocketEventPublisher;

    @MessageMapping("/chat/rooms/{chatRoomId}/messages")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            @Valid @Payload ChatMessageSendRequestDto request,
            Principal principal
    ) {
        Long loginUserId = resolveLoginUserId(principal);

        ChatMessageResponseDto message =
                chatMessageCommandService.createTextMessage(
                        loginUserId,
                        chatRoomId,
                        request.toCreateRequest()
                );

        chatWebSocketEventPublisher.publishMessageCreated(
                chatRoomId,
                message
        );
    }

    private Long resolveLoginUserId(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException("WebSocket 인증 정보가 없습니다.");
        }

        return userPrincipal.getId();
    }
}