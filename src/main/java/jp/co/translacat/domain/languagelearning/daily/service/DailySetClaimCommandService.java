package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailySetClaimCommandService {

    private final DailyWritingSetRepository dailySetRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claim(
            Long userId,
            LocalDate learningDate,
            String snapshotId,
            int sentenceCount,
            String snapshotJson
    ) {
        var existing = dailySetRepository.findByUserIdAndLearningDate(
                userId,
                learningDate
        );
        if (existing.isPresent()) {
            return new ClaimResult(
                    existing.get().getId(),
                    false
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
        DailyWritingSet dailySet = DailyWritingSet.createGenerating(
                user,
                learningDate,
                snapshotId,
                sentenceCount,
                snapshotJson
        );
        DailyWritingSet savedDailySet = dailySetRepository.saveAndFlush(
                dailySet
        );

        return new ClaimResult(
                savedDailySet.getId(),
                true
        );
    }

    public record ClaimResult(
            Long dailySetId,
            boolean created
    ) {
    }
}
