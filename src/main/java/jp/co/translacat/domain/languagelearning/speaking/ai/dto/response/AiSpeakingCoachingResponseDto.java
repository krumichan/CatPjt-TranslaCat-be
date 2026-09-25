package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingCoachingItemDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind;

import java.util.List;

public record AiSpeakingCoachingResponseDto(
        String requestId,
        String sessionId,
        SpeakingResultKind resultKind,
        String resultPolicyVersion,
        String schemaVersion,
        String sourceSnapshotHash,
        String contentStatus,
        List<String> limitationReasons,
        List<AiSpeakingCoachingItemDto> items,
        String promptVersion,
        AiSpeakingUsageDto usage
) {
}
