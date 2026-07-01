package jp.co.translacat.domain.chat.message.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.chat.message.dto.websocket.request.ChatMessageSendRequestDto;
import jp.co.translacat.domain.chat.message.service.ChatMessageCommandService;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatMessageWebSocketController {

    private final ChatMessageCommandService chatMessageCommandService;

    @MessageMapping("/chat/rooms/{chatRoomId}/messages")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            @Valid @Payload ChatMessageSendRequestDto request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Long loginUserId = resolveLoginUserId(headerAccessor.getUser());

        /*
         * 메시지 저장, message.created publish, 번역 요청은 CommandService에서 일원화한다.
         * Controller에서 다시 publish하면 WebSocket SEND 정상화 후 중복 메시지가 발생할 수 있다.
         */
        chatMessageCommandService.createTextMessage(
                loginUserId,
                chatRoomId,
                request.toCreateRequest()
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
