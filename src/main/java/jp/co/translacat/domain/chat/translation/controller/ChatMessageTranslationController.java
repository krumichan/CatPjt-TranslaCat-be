package jp.co.translacat.domain.chat.translation.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageTranslationResponseDto;
import jp.co.translacat.domain.chat.translation.service.ChatMessageTranslationRetryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms/{chatRoomId}/messages/{messageId}/translations")
@RequiredArgsConstructor
public class ChatMessageTranslationController {

    private final ChatMessageTranslationRetryService chatMessageTranslationRetryService;

    @PostMapping("/{languageCode}/retry")
    @Operation(
            summary = "채팅 메시지 번역 수동 재시도",
            description = "FAILED 상태의 채팅 메시지 번역을 PENDING으로 되돌리고 비동기 번역 처리를 다시 요청한다."
    )
    public ResponseDto<ChatMessageTranslationResponseDto> retryTranslation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long chatRoomId,
            @PathVariable Long messageId,
            @PathVariable String languageCode
    ) {
        return ResponseUtil.ok(
                chatMessageTranslationRetryService.retry(
                        userPrincipal.getId(),
                        chatRoomId,
                        messageId,
                        languageCode
                )
        );
    }
}
