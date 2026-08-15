package jp.co.translacat.domain.languagelearning.history.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingQueryService;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.SpeakingHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.entity.SpeakingEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.SpeakingEvaluationQueryService;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningHistoryQueryService {

    private final DailyWritingSetRepository dailyWritingSetRepository;
    private final DailyWritingQueryService dailyWritingQueryService;
    private final SpeakingSessionRepository speakingSessionRepository;
    private final SpeakingSessionQueryService speakingSessionQueryService;
    private final SpeakingTurnQueryService speakingTurnQueryService;
    private final SpeakingEvaluationQueryService speakingEvaluationQueryService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;

    public List<LearningHistoryItemResponseDto> getHistory(
            Long userId,
            LearningSource source,
            String period,
            String status
    ) {
        LanguageLearningUserSetting setting =
                userSettingQueryService.getOrCreateEntity(userId);
        LocalDate to = userSettingQueryService.resolveToday(setting);
        LocalDate from = to.minusDays(resolveDays(period) - 1L);
        List<LearningHistoryItemResponseDto> result = new ArrayList<>();

        if (source == null || source == LearningSource.WRITING) {
            dailyWritingSetRepository
                    .findAllByUserIdAndLearningDateBetweenOrderByLearningDateDesc(
                            userId,
                            from,
                            to
                    )
                    .stream()
                    .map(this::writingSummary)
                    .filter(item -> matchesStatus(item, status))
                    .forEach(result::add);
        }
        if (source == null || source == LearningSource.SPEAKING) {
            speakingSessionRepository
                    .findAllByUserIdAndLearningDateBetweenOrderByLearningDateDescStartedAtDesc(
                            userId,
                            from,
                            to
                    )
                    .stream()
                    .map(this::speakingSummary)
                    .filter(item -> matchesStatus(item, status))
                    .forEach(result::add);
        }

        return result.stream()
                .sorted(Comparator
                        .comparing(LearningHistoryItemResponseDto::learningDate)
                        .reversed()
                        .thenComparing(LearningHistoryItemResponseDto::source))
                .toList();
    }

    public LearningHistoryDetailResponseDto getDetail(
            Long userId,
            String activityId
    ) {
        ParsedActivityId parsed = parseActivityId(activityId);
        if (parsed.source == LearningSource.WRITING) {
            DailyWritingSet set = dailyWritingSetRepository
                    .findById(parsed.id)
                    .filter(value -> value.getUser().getId().equals(userId))
                    .orElseThrow(this::notFound);
            return new LearningHistoryDetailResponseDto(
                    activityId,
                    LearningSource.WRITING,
                    dailyWritingQueryService.getByDate(
                            userId,
                            set.getLearningDate()
                    )
            );
        }

        SpeakingSession session = speakingSessionQueryService.getOwnedEntity(
                userId,
                parsed.id
        );
        return new LearningHistoryDetailResponseDto(
                activityId,
                LearningSource.SPEAKING,
                new SpeakingHistoryDetailResponseDto(
                        speakingSessionQueryService.toResponse(
                                userId,
                                session
                        ),
                        speakingTurnQueryService.getResponses(
                                userId,
                                session.getId()
                        ),
                        speakingEvaluationQueryService.getResponse(
                                session.getId()
                        )
                )
        );
    }

    private LearningHistoryItemResponseDto writingSummary(
            DailyWritingSet set
    ) {
        return new LearningHistoryItemResponseDto(
                "WRITING:" + set.getId(),
                LearningSource.WRITING,
                set.getLearningDate(),
                "Daily Writing",
                null,
                0,
                null,
                set.getStatus().name(),
                set.getStatus().name()
        );
    }

    private LearningHistoryItemResponseDto speakingSummary(
            SpeakingSession session
    ) {
        SpeakingEvaluation evaluation =
                speakingEvaluationQueryService.findLatest(session.getId());
        return new LearningHistoryItemResponseDto(
                "SPEAKING:" + session.getId(),
                LearningSource.SPEAKING,
                session.getLearningDate(),
                session.getTopicTitle(),
                session.getTopicCategory(),
                session.getTotalDurationSeconds(),
                evaluation == null || evaluation.getOverallScore() == null
                        ? null
                        : evaluation.getOverallScore().doubleValue(),
                session.getStatus().name(),
                session.getEvaluationStatus().name()
        );
    }

    private boolean matchesStatus(
            LearningHistoryItemResponseDto item,
            String status
    ) {
        return status == null
                || status.isBlank()
                || status.equalsIgnoreCase(item.completionStatus())
                || status.equalsIgnoreCase(item.evaluationStatus());
    }

    private int resolveDays(String period) {
        if (period == null || period.isBlank()) {
            return 30;
        }
        String normalized = period.trim().toLowerCase();
        if (normalized.endsWith("d")) {
            try {
                return Math.max(
                        1,
                        Math.min(365, Integer.parseInt(
                                normalized.substring(0, normalized.length() - 1)
                        ))
                );
            } catch (NumberFormatException ignored) {
                return 30;
            }
        }
        return 30;
    }

    private ParsedActivityId parseActivityId(String value) {
        if (value == null || !value.contains(":")) {
            throw notFound();
        }
        String[] split = value.split(":", 2);
        try {
            return new ParsedActivityId(
                    LearningSource.valueOf(split[0].toUpperCase()),
                    Long.parseLong(split[1])
            );
        } catch (Exception e) {
            throw notFound();
        }
    }

    private BusinessException notFound() {
        return new BusinessException(
                "학습 이력을 찾을 수 없습니다.",
                LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
        );
    }

    private record ParsedActivityId(
            LearningSource source,
            Long id
    ) {
    }
}
