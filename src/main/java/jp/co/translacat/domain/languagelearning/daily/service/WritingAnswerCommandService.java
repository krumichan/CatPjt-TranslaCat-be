package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.event.WritingEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningAdminSetting;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class WritingAnswerCommandService {

    private final DailyWritingItemRepository itemRepository;
    private final WritingAnswerRepository answerRepository;
    private final WritingEvaluationRepository evaluationRepository;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(noRollbackFor = BusinessException.class)
    public WritingAnswer submit(
            Long userId,
            Long itemId,
            AnswerSubmitRequestDto request
    ) {
        validateAiEvaluationEnabled();
        validateAnswer(request);

        DailyWritingItem item = itemRepository
                .findByIdAndDailySetUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Daily Item을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND
                ));
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LocalDate today = userSettingQueryService.resolveToday(setting);
        LanguageLearningAdminSetting adminSetting =
                adminSettingQueryService.getOrCreateEntity();

        validateReviewPeriod(
                item,
                today,
                adminSetting.getReviewAvailableDays()
        );

        WritingAnswer answer = saveOrUpdateAnswer(
                userId,
                item,
                today,
                request.answer().trim()
        );
        ensurePendingEvaluation(getUser(userId), answer);
        eventPublisher.publishEvent(
                new WritingEvaluationRequestedEvent(answer.getId())
        );

        return answer;
    }

    @Transactional
    public void resumeEvaluation(Long userId, Long itemId) {
        DailyWritingItem item = itemRepository
                .findByIdAndDailySetUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Daily Item을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND
                ));
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LocalDate today = userSettingQueryService.resolveToday(setting);
        WritingAnswer answer = answerRepository
                .findByDailyItemIdAndAttemptDate(item.getId(), today)
                .orElseThrow(() -> new BusinessException(
                        "Writing Answer를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                ));
        WritingEvaluation evaluation = evaluationRepository
                .findByAnswerId(answer.getId())
                .orElseThrow(() -> new BusinessException(
                        "Writing 평가 정보를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                ));
        if (evaluation.getStatus() != EvaluationStatus.PENDING) {
            return;
        }

        eventPublisher.publishEvent(
                new WritingEvaluationRequestedEvent(answer.getId())
        );
    }

    private WritingAnswer saveOrUpdateAnswer(
            Long userId,
            DailyWritingItem item,
            LocalDate attemptDate,
            String answerText
    ) {
        WritingAnswer answer = answerRepository
                .findByDailyItemIdAndAttemptDate(
                        item.getId(),
                        attemptDate
                )
                .orElse(null);

        if (answer == null) {
            return answerRepository.save(WritingAnswer.create(
                    getUser(userId),
                    item,
                    attemptDate,
                    answerText
            ));
        }

        WritingEvaluation currentEvaluation = evaluationRepository
                .findByAnswerId(answer.getId())
                .orElse(null);
        if (currentEvaluation != null) {
            if (currentEvaluation.getStatus() == EvaluationStatus.SUCCESS) {
                throw new BusinessException(
                        "동일 문제는 하루에 한 번만 제출할 수 있습니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                );
            }
            if (currentEvaluation.getStatus() == EvaluationStatus.PENDING) {
                throw new BusinessException(
                        "AI Writing 평가가 진행 중입니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                );
            }
        }

        answer.updateAnswer(answerText);
        return answer;
    }

    private void ensurePendingEvaluation(
            User user,
            WritingAnswer answer
    ) {
        WritingEvaluation evaluation = evaluationRepository
                .findByAnswerId(answer.getId())
                .orElse(null);
        if (evaluation == null) {
            evaluationRepository.save(
                    WritingEvaluation.pendingDaily(user, answer)
            );
            return;
        }

        if (evaluation.getStatus() == EvaluationStatus.FAILED) {
            evaluation.retry();
            evaluationRepository.save(evaluation);
        }
    }

    private void validateReviewPeriod(
            DailyWritingItem item,
            LocalDate today,
            int reviewAvailableDays
    ) {
        LocalDate learningDate = item.getDailySet().getLearningDate();
        LocalDate lastReviewDate = learningDate.plusDays(
                reviewAvailableDays - 1L
        );

        if (today.isBefore(learningDate)
                || today.isAfter(lastReviewDate)) {
            throw new BusinessException(
                    "재학습 가능 기간이 지났습니다.",
                    LanguageLearningErrorCode.REVIEW_EXPIRED
            );
        }
    }

    private void validateAiEvaluationEnabled() {
        if (!adminSettingQueryService
                .getOrCreateEntity()
                .isAiEvaluationEnabled()) {
            throw new BusinessException(
                    "AI Writing 평가가 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SETTING_INVALID
            );
        }
    }

    private void validateAnswer(AnswerSubmitRequestDto request) {
        if (request == null
                || request.answer() == null
                || request.answer().isBlank()) {
            throw new BusinessException(
                    "답변이 필요합니다.",
                    LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
            );
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
    }
}
