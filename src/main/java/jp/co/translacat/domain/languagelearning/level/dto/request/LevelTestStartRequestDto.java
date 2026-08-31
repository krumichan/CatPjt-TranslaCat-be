package jp.co.translacat.domain.languagelearning.level.dto.request;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;

public record LevelTestStartRequestDto(
        LevelTestSessionType type,
        String idempotencyKey
) {
}
