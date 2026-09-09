package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingAnswer;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;
import jp.co.translacat.domain.languagelearning.daily.model.DailyWritingSnapshot;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class WritingEvaluationProcessor {

    private final WritingAnswerRepository answerRepository;
    private final WritingEvaluationRepository evaluationRepository;
    private final UserRepository userRepository;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final DailyWritingSnapshotService snapshotService;
    private final WritingEvaluationCommandService evaluationCommandService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public Long process(Long answerId) {
        WritingAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new BusinessException(
                        "Writing Answer를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.ANSWER_NOT_ALLOWED
                ));
        EvaluationStatus status = evaluationRepository.findByAnswerId(answerId)
                .map(evaluation -> evaluation.getStatus())
                .orElse(null);
        if (status != EvaluationStatus.PENDING) {
            return status == EvaluationStatus.SUCCESS
                    ? answer.getDailyItem().getDailySet().getId()
                    : null;
        }
        Long userId = answer.getUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        LanguageLearningErrorCode.USER_NOT_FOUND
                ));
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LocalDate today = userSettingQueryService.resolveToday(setting);
        DailyWritingSnapshot snapshot = snapshotService.read(
                answer.getDailyItem().getDailySet()
        );

        evaluationCommandService.evaluateDaily(
                user,
                answer,
                setting,
                snapshot,
                today
        );
        // The caller checks completion only after this transactional proxy has committed.
        return answer.getDailyItem().getDailySet().getId();
    }
}
