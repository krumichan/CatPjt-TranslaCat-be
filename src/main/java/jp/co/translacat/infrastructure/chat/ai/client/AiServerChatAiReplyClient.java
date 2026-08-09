package jp.co.translacat.infrastructure.chat.ai.client;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyResponseDto;
import jp.co.translacat.domain.chat.ai.port.ChatAiReplyClient;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiServerChatAiReplyClient implements ChatAiReplyClient {

    private final AiServerClient aiServerClient;

    @Override
    public ChatAiReplyResponseDto generateReply(
            ChatAiReplyRequestDto request
    ) {
        return aiServerClient.callChatAiReply(request);
    }
}
