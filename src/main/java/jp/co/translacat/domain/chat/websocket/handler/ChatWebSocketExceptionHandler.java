package jp.co.translacat.domain.chat.websocket.handler;

import jp.co.translacat.domain.chat.websocket.dto.ChatWebSocketErrorEventDto;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class ChatWebSocketExceptionHandler {

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ChatWebSocketErrorEventDto handleBusinessException(
            BusinessException exception
    ) {
        return ChatWebSocketErrorEventDto.business(
                exception.getMessage()
        );
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ChatWebSocketErrorEventDto handleException(
            Exception exception
    ) {
        return ChatWebSocketErrorEventDto.internal();
    }
}