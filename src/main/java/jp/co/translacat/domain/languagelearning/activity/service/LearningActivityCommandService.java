package jp.co.translacat.domain.languagelearning.activity.service;

import jp.co.translacat.domain.languagelearning.activity.entity.LearningActivity;
import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.common.enums.LearningActivityStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LearningActivityCommandService {

    private final LearningActivityRepository activityRepository;
    private final UserRepository userRepository;

    @Transactional
    public LearningActivity getOrCreate(
            Long userId,
            LearningSource source,
            String referenceId,
            LocalDate learningDate,
            String title,
            long durationSeconds,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
        return activityRepository.findBySourceAndReferenceId(
                        source,
                        referenceId
                )
                .orElseGet(() -> activityRepository.save(
                        LearningActivity.create(
                                getUser(userId),
                                source,
                                referenceId,
                                learningDate,
                                title,
                                durationSeconds,
                                startedAt,
                                completedAt,
                                LearningActivityStatus.COMPLETED
                        )
                ));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다."
                ));
    }
}
