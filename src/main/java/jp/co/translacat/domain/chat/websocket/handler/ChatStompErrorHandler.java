package jp.co.translacat.domain.chat.websocket.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.websocket.dto.ChatWebSocketErrorEventDto;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @NotNull
    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            @NotNull Message<byte[]> clientMessage,
            @NotNull Throwable exception
    ) {
        ChatWebSocketErrorEventDto errorEvent =
                toErrorEvent(exception);

        try {
            byte[] payload =
                    objectMapper.writeValueAsBytes(errorEvent);

            StompHeaderAccessor accessor =
                    StompHeaderAccessor.create(StompCommand.ERROR);

            accessor.setMessage(errorEvent.message());
            accessor.setLeaveMutable(true);

            return MessageBuilder.createMessage(
                    payload,
                    accessor.getMessageHeaders()
            );
        } catch (JsonProcessingException jsonProcessingException) {
            return super.handleClientMessageProcessingError(
                    clientMessage,
                    exception
            );
        }
    }

    private ChatWebSocketErrorEventDto toErrorEvent(Throwable exception) {
        Throwable rootCause = findBusinessException(exception);

        if (rootCause instanceof BusinessException businessException) {
            return ChatWebSocketErrorEventDto.unauthorized(
                    businessException.getMessage()
            );
        }

        return ChatWebSocketErrorEventDto.internal();
    }

    private Throwable findBusinessException(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof BusinessException) {
                return current;
            }

            current = current.getCause();
        }

        return exception;
    }
}