package jp.co.translacat.domain.chat.ai.port;

import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyRequestDto;
import jp.co.translacat.domain.chat.ai.dto.server.ChatAiReplyResponseDto;

public interface ChatAiReplyClient {
    ChatAiReplyResponseDto generateReply(ChatAiReplyRequestDto request);
}
