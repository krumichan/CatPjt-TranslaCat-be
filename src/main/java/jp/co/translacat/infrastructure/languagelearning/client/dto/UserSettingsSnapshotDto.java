package jp.co.translacat.infrastructure.languagelearning.client.dto;

import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;

public record UserSettingsSnapshotDto(Long userId, java.time.LocalDate learningDate, java.time.LocalDateTime revision, UserSettingResponseDto settings) { }
