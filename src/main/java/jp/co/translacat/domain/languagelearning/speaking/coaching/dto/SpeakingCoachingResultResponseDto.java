package jp.co.translacat.domain.languagelearning.speaking.coaching.dto;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingCoachingItemDto;

import java.time.LocalDateTime;
import java.util.List;

public record SpeakingCoachingResultResponseDto(
        Long id,
        String resultKind,
        String resultPolicyVersion,
        String schemaVersion,
        String sourceSnapshotHash,
        String contentStatus,
        List<String> limitationReasons,
        List<AiSpeakingCoachingItemDto> items,
        String promptVersion,
        LocalDateTime createdAt
) {
}
