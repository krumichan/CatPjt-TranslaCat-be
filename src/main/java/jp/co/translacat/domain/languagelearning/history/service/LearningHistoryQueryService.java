package jp.co.translacat.domain.languagelearning.history.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.service.DailyWritingQueryService;
import jp.co.translacat.domain.languagelearning.daily.service.WritingEvaluationQueryService;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.SpeakingHistoryDetailResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestResultQueryService;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.repository.PracticeSetRepository;
import jp.co.translacat.domain.languagelearning.practice.service.PracticeQueryService;
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
    private final WritingEvaluationQueryService writingEvaluationQueryService;
    private final SpeakingSessionRepository speakingSessionRepository;
    private final SpeakingSessionQueryService speakingSessionQueryService;
    private final SpeakingTurnQueryService speakingTurnQueryService;
    private final SpeakingEvaluationQueryService speakingEvaluationQueryService;
    private final ListeningSessionRepository listeningSessionRepository;
    private final ListeningItemAttemptRepository listeningAttemptRepository;
    private final ListeningTaskResponseRepository listeningResponseRepository;
    private final ListeningViewMapper listeningViewMapper;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LevelTestSessionRepository levelTestSessionRepository;
    private final LevelTestResultQueryService levelTestResultQueryService;
    private final PracticeSetRepository practiceSetRepository;
    private final PracticeQueryService practiceQueryService;

    public List<LearningHistoryItemResponseDto> getHistory(
            Long userId,
            LearningSource source,
            String period,
            String status
    ) {
        return getHistory(userId, source, period, status, null);
    }

    public List<LearningHistoryItemResponseDto> getHistory(
            Long userId,
            LearningSource source,
            String period,
            String status,
            ListeningTaskType taskType
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
        if (source == null || source == LearningSource.LISTENING) {
            listeningSessionRepository
                    .findAllByUserIdAndDailySetLearningDateBetweenOrderByStartedAtDesc(
                            userId,
                            from,
                            to
                    )
                    .stream()
                    .filter(session -> matchesTask(session, taskType))
                    .map(this::listeningSummary)
                    .filter(item -> matchesStatus(item, status))
                    .forEach(result::add);
        }
        if (source == null || source == LearningSource.READING) {
            practiceSetRepository
                    .findAllByUserIdAndDomainAndLearningDateBetweenOrderByLearningDateDescIdDesc(
                            userId, PracticeDomain.READING, from, to
                    )
                    .stream()
                    .map(this::practiceSummary)
                    .filter(item -> matchesStatus(item, status))
                    .forEach(result::add);
        }
        if (source == null || source == LearningSource.VOCABULARY) {
            practiceSetRepository
                    .findAllByUserIdAndDomainAndLearningDateBetweenOrderByLearningDateDescIdDesc(
                            userId, PracticeDomain.VOCABULARY, from, to
                    )
                    .stream()
                    .map(this::practiceSummary)
                    .filter(item -> matchesStatus(item, status))
                    .forEach(result::add);
        }
        if (source == null || source == LearningSource.LEVEL_TEST) {
            levelTestSessionRepository
                    .findAllByUserIdAndStatusOrderByCompletedAtDesc(
                            userId,
                            LevelTestSessionStatus.COMPLETED
                    )
                    .stream()
                    .filter(session -> session.getCompletedAt() != null)
                    .filter(session -> !session.getCompletedAt()
                            .toLocalDate().isBefore(from)
                            && !session.getCompletedAt()
                            .toLocalDate().isAfter(to))
                    .map(this::levelTestSummary)
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
        return switch (parsed.source) {
            case WRITING -> writingDetail(userId, activityId, parsed.id);
            case SPEAKING -> speakingDetail(userId, activityId, parsed.id);
            case LISTENING -> listeningDetail(userId, activityId, parsed.id);
            case READING, VOCABULARY -> practiceDetail(userId, activityId, parsed.id);
            case LEVEL_TEST -> levelTestDetail(userId, activityId, parsed.id);
            default -> throw notFound();
        };
    }

    private LearningHistoryItemResponseDto writingSummary(
            DailyWritingSet set
    ) {
        return new LearningHistoryItemResponseDto(
                "WRITING:" + set.getId(),
                LearningSource.WRITING,
                set.getLearningDate(),
                "Daily Writing · " + set.getWritingType().name(),
                set.getWritingType().name(),
                0,
                writingEvaluationQueryService.findDailyAverageOverallScore(
                        set.getId(),
                        set.getLearningDate()
                ),
                set.getStatus().name(),
                writingEvaluationQueryService.resolveDailyEvaluationStatus(
                        set.getId(),
                        set.getLearningDate()
                )
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

    private LearningHistoryItemResponseDto listeningSummary(
            ListeningSession session
    ) {
        List<ListeningItemAttempt> official = listeningAttemptRepository
                .findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(
                        session.getId()
                ).stream()
                .filter(value -> value.getEvaluationPurpose()
                        == ListeningEvaluationPurpose.OFFICIAL)
                .toList();
        Double score = official.stream()
                .filter(value -> value.getOverallScore() != null)
                .mapToDouble(ListeningItemAttempt::getOverallScore)
                .average().stream().boxed().findFirst().orElse(null);
        String evaluationStatus = official.stream().allMatch(
                ListeningItemAttempt::isFinalized
        ) ? "COMPLETED" : "PENDING";
        return new LearningHistoryItemResponseDto(
                "LISTENING:" + session.getId(),
                LearningSource.LISTENING,
                session.getDailySet().getLearningDate(),
                "Daily Listening",
                null,
                session.getActualDurationMs() / 1000,
                score,
                session.getStatus().name(),
                evaluationStatus
        );
    }


    private LearningHistoryItemResponseDto practiceSummary(PracticeSet set) {
        LearningSource source = set.getDomain() == PracticeDomain.READING
                ? LearningSource.READING
                : LearningSource.VOCABULARY;
        long duration = set.getCompletedAt() == null
                ? 0
                : Math.max(0, java.time.Duration.between(
                        set.getStartedAt(), set.getCompletedAt()
                ).toSeconds());
        String prefix = source == LearningSource.READING ? "Reading" : "Vocabulary";
        return new LearningHistoryItemResponseDto(
                source.name() + ":" + set.getId(),
                source,
                set.getLearningDate(),
                prefix + " · " + set.getMode(),
                set.getMode(),
                duration,
                set.getOfficialScore(),
                set.getStatus().name(),
                set.getStatus().name()
        );
    }

    private LearningHistoryItemResponseDto levelTestSummary(
            LevelTestSession session
    ) {
        return new LearningHistoryItemResponseDto(
                "LEVEL_TEST:" + session.getId(),
                LearningSource.LEVEL_TEST,
                session.getCompletedAt().toLocalDate(),
                "Language Level Test",
                session.getSessionType().name(),
                Math.max(
                        0,
                        java.time.Duration.between(
                                session.getStartedAt(),
                                session.getCompletedAt()
                        ).toSeconds()
                ),
                session.getBaseLevelScore(),
                session.getStatus().name(),
                session.getStatus().name()
        );
    }

    private boolean matchesTask(
            ListeningSession session,
            ListeningTaskType taskType
    ) {
        if (taskType == null) {
            return true;
        }
        return listeningAttemptRepository
                .findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(
                        session.getId()
                ).stream()
                .flatMap(attempt -> listeningResponseRepository
                        .findAllByAttemptIdOrderByTaskTypeAsc(attempt.getId())
                        .stream())
                .anyMatch(response -> response.getTaskType() == taskType
                        && response.getStatus()
                        != ListeningTaskStatus.NOT_SELECTED);
    }

    private LearningHistoryDetailResponseDto writingDetail(
            Long userId,
            String activityId,
            Long id
    ) {
        DailyWritingSet set = dailyWritingSetRepository
                .findById(id)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(this::notFound);
        return new LearningHistoryDetailResponseDto(
                activityId,
                LearningSource.WRITING,
                dailyWritingQueryService.toResponse(userId, set)
        );
    }

    private LearningHistoryDetailResponseDto speakingDetail(
            Long userId,
            String activityId,
            Long id
    ) {
        SpeakingSession session = speakingSessionQueryService.getOwnedEntity(
                userId,
                id
        );
        return new LearningHistoryDetailResponseDto(
                activityId,
                LearningSource.SPEAKING,
                new SpeakingHistoryDetailResponseDto(
                        speakingSessionQueryService.toResponse(userId, session),
                        speakingTurnQueryService.getResponses(userId, session.getId()),
                        speakingEvaluationQueryService.getResponse(session.getId())
                )
        );
    }

    private LearningHistoryDetailResponseDto listeningDetail(
            Long userId,
            String activityId,
            Long id
    ) {
        ListeningSession session = listeningSessionRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(this::notFound);
        return new LearningHistoryDetailResponseDto(
                activityId,
                LearningSource.LISTENING,
                listeningViewMapper.history(session)
        );
    }


    private LearningHistoryDetailResponseDto practiceDetail(
            Long userId,
            String activityId,
            Long id
    ) {
        return new LearningHistoryDetailResponseDto(
                activityId,
                parseActivityId(activityId).source(),
                practiceQueryService.get(userId, id)
        );
    }

    private LearningHistoryDetailResponseDto levelTestDetail(
            Long userId,
            String activityId,
            Long id
    ) {
        return new LearningHistoryDetailResponseDto(
                activityId,
                LearningSource.LEVEL_TEST,
                levelTestResultQueryService.historyDetailForActivity(userId, id)
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
