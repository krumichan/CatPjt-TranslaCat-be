package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthCommands;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileSignalResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearningProfileSignalService {
    private final GrowthCommands commands;
    private final GrowthReadGateway growth;

    @Transactional
    public void touchAll(Long userId, ProfileSignalType type, List<String> values) {
        var filtered =
                values == null ? List.<String>of() : values.stream().filter(v -> v != null && !v.isBlank()).toList();
        if (filtered.isEmpty()) return;
        commands.append(userId, new GrowthOperation("SIGNALS:" + UUID.randomUUID(), "SIGNALS_TOUCHED",
                Map.of("type", type.name(), "values", filtered)));
    }

    public List<String> getKeys(Long userId, ProfileSignalType type, int limit) {
        return getResponses(userId, type, limit).stream().map(ProfileSignalResponseDto::key).toList();
    }

    public List<ProfileSignalResponseDto> getResponses(Long userId, ProfileSignalType type, int limit) {
        return growth.snapshot(userId)
                .signals()
                .getOrDefault(type.name(), List.of())
                .stream()
                .limit(Math.max(0, limit))
                .toList();
    }
}
