package jp.co.translacat.domain.chat.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;

public record ChatAiMemberUpdateRequestDto(
        @NotBlank
        @Size(max = ChatAiAgent.NICKNAME_MAX_LENGTH)
        String nickname,

        @Size(max = ChatAiAgent.BIO_MAX_LENGTH)
        String bio,

        @NotBlank
        @Size(max = ChatAiAgent.LANGUAGE_CODE_MAX_LENGTH)
        String originalLanguageCode,

        @NotBlank
        @Size(max = ChatAiAgent.PERSONA_PROMPT_MAX_LENGTH)
        String personaPrompt
) {
}
