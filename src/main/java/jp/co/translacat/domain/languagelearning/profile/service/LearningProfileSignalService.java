package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.languagelearning.profile.dto.response.ProfileSignalResponseDto;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfileSignal;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileSignalRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningProfileSignalService {

    private static final int MAX_SIGNAL_KEY_LENGTH = 300;

    private final LearningProfileSignalRepository signalRepository;
    private final UserRepository userRepository;

    @Transactional
    public void touchAll(
            Long userId,
            ProfileSignalType type,
            List<String> values
    ) {
        for (String raw : safe(values)) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String key = normalizeKey(raw);
            LearningProfileSignal signal = signalRepository
                    .findByUserIdAndTypeAndKey(userId, type, key)
                    .orElseGet(() -> signalRepository.save(
                            LearningProfileSignal.create(
                                    getUser(userId),
                                    type,
                                    key
                            )
                    ));

            if (signal.getOccurrenceCount() > 0
                    && signal.getCreatedAt() != null) {
                signal.touch();
            }
        }
    }

    @Transactional(readOnly = true)
    public List<String> getKeys(
            Long userId,
            ProfileSignalType type,
            int limit
    ) {
        return signalRepository
                .findAllByUserIdAndTypeOrderByOccurrenceCountDesc(
                        userId,
                        type
                )
                .stream()
                .limit(limit)
                .map(LearningProfileSignal::getKey)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileSignalResponseDto> getResponses(
            Long userId,
            ProfileSignalType type,
            int limit
    ) {
        return signalRepository
                .findAllByUserIdAndTypeOrderByOccurrenceCountDesc(
                        userId,
                        type
                )
                .stream()
                .limit(limit)
                .map(signal -> new ProfileSignalResponseDto(
                        signal.getKey(),
                        signal.getOccurrenceCount()
                ))
                .toList();
    }

    private String normalizeKey(String raw) {
        String value = raw.trim();
        return value.substring(
                0,
                Math.min(value.length(), MAX_SIGNAL_KEY_LENGTH)
        );
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
