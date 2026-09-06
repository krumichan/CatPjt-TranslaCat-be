package jp.co.translacat.domain.languagelearning.listening.session.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningIdempotencyPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningTaskSelectionPolicy;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListeningSessionCommandService {

    private final ListeningSessionRepository sessionRepository;
    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningTaskResponseRepository responseRepository;
    private final ListeningDailySetQueryService dailySetQueryService;
    private final ListeningTaskSelectionPolicy taskSelectionPolicy;
    private final ListeningIdempotencyPolicy idempotencyPolicy;
    private final ListeningPolicySettingQueryService policySettingService;
    private final LanguageLearningUserSettingQueryService userSettingQueryService;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public Long create(
            Long userId,
            ListeningApiContract.SessionCreateRequest request
    ) {
        if (request == null || request.dailySetId() == null) {
            throw invalid("Listening Daily Set이 필요합니다.");
        }

        ListeningDailySet dailySet = dailySetQueryService.owned(
                userId,
                request.dailySetId()
        );
        List<ListeningTaskType> requestedTasks = request.selectedTaskTypes() == null
                || request.selectedTaskTypes().isEmpty()
                ? taskSelectionPolicy.tasksForMode(dailySet.getLearningMode())
                : request.selectedTaskTypes();
        Set<ListeningTaskType> selected = taskSelectionPolicy.validateForMode(
                dailySet.getLearningMode(),
                requestedTasks
        );
        List<ListeningTaskType> ordered = selected.stream()
                .sorted(Comparator.comparingInt(ListeningTaskType::ordinal))
                .toList();
        String key = key(request.idempotencyKey());
        var existing = sessionRepository.findByUserIdAndIdempotencyKey(
                userId,
                key
        );

        if (existing.isPresent()) {
            validateIdempotentSession(existing.get(), request.dailySetId(), ordered);
            rememberSelection(userId, ordered);
            return existing.get().getId();
        }

        ListeningPolicySetting policy = policySettingService.get();
        expireOrRejectActive(userId, policy);

        if (!dailySet.isUsable()) {
            throw invalid("준비되지 않은 Listening Daily Set입니다.");
        }

        List<ListeningItem> items = dailySetQueryService.activeItems(
                dailySet.getId()
        ).stream()
                .filter(item -> item.isPlayable(LocalDateTime.now()))
                .filter(item -> !attemptRepository
                        .existsByItemIdAndEvaluationPurpose(
                                item.getId(),
                                ListeningEvaluationPurpose.OFFICIAL
                        ))
                .toList();

        if (items.isEmpty()) {
            throw invalid("재생 가능한 Listening 문항이 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        ListeningSession session = ListeningSession.create(
                dailySet.getUser(),
                dailySet,
                jsonCodec.write(ordered),
                jsonCodec.write(policySnapshot(policy)),
                jsonCodec.write(items.stream().map(ListeningItem::getId).toList()),
                key,
                now
        );

        try {
            session = sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            ListeningSession concurrent = sessionRepository
                    .findByUserIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() -> exception);
            validateIdempotentSession(concurrent, request.dailySetId(), ordered);
            rememberSelection(userId, ordered);
            return concurrent.getId();
        }

        rememberSelection(userId, ordered);

        for (ListeningItem item : items) {
            createAttempt(session, item, ListeningEvaluationPurpose.OFFICIAL,
                    ordered, 1, now);
        }

        return session.getId();
    }

    @Transactional
    public Long activeSessionId(Long userId) {
        ListeningPolicySetting policy = policySettingService.get();
        var active = sessionRepository
                .findFirstByUserIdAndStatusInOrderByStartedAtDesc(
                        userId,
                        List.of(ListeningSessionStatus.IN_PROGRESS)
                );
        if (active.isEmpty()) {
            return null;
        }
        ListeningSession session = active.get();
        if (session.isExpired(
                LocalDateTime.now(),
                Duration.ofHours(policy.getResumeHours())
        )) {
            session.abandon(LocalDateTime.now());
            return null;
        }
        return session.getId();
    }

    @Transactional
    public ResumeResult resume(
            Long userId,
            Long sessionId
    ) {
        ListeningSession session = ownedLocked(userId, sessionId);

        if (!session.isActive()) {
            throw invalid("재개 가능한 Listening Session이 아닙니다.");
        }

        if (expired(session)) {
            session.abandon(LocalDateTime.now());

            return new ResumeResult(sessionId, true);
        }

        session.touch(LocalDateTime.now());

        return new ResumeResult(sessionId, false);
    }

    @Transactional
    public CompleteResult complete(
            Long userId,
            Long sessionId
    ) {
        ListeningSession session = ownedLocked(userId, sessionId);

        if (expired(session)) {
            session.abandon(LocalDateTime.now());

            return new CompleteResult(sessionId, true);
        }

        boolean pending = attemptRepository
                .findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(sessionId)
                .stream()
                .anyMatch(value -> !value.isFinalized());

        if (pending) {
            throw invalid("완료되지 않은 Listening 문항이 있습니다.");
        }

        if (session.getStatus() == jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus.IN_PROGRESS
                || session.getStatus() == jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus.EVALUATING) {
            session.complete(LocalDateTime.now());
        }

        return new CompleteResult(sessionId, false);
    }

    @Transactional
    public boolean expireIfNeeded(Long userId, Long sessionId) {
        ListeningSession session = ownedLocked(userId, sessionId);

        if (!session.isActive() || !expired(session)) {
            return false;
        }

        session.abandon(LocalDateTime.now());

        return true;
    }

    private void rememberSelection(
            Long userId,
            List<ListeningTaskType> ordered
    ) {
        userSettingQueryService.getOrCreateEntity(userId)
                .updateDefaultListeningTaskTypes(jsonCodec.write(ordered));
    }

    private void validateIdempotentSession(
            ListeningSession existing,
            Long requestedDailySetId,
            List<ListeningTaskType> requestedTasks
    ) {
        List<ListeningTaskType> existingTasks = jsonCodec.read(
                existing.getSelectedTaskTypesJson(),
                new com.fasterxml.jackson.core.type.TypeReference<List<ListeningTaskType>>() {
                }
        );
        idempotencyPolicy.requireSamePayload(
                existing.getDailySet().getId(),
                existingTasks,
                requestedDailySetId,
                requestedTasks
        );
    }

    private void createAttempt(
            ListeningSession session,
            ListeningItem item,
            ListeningEvaluationPurpose purpose,
            List<ListeningTaskType> selected,
            int attemptNo,
            LocalDateTime now
    ) {
        String prefix = "session:" + session.getId() + ":item:" + item.getId()
                + ":" + purpose.name().toLowerCase();
        ListeningItemAttempt attempt = attemptRepository.saveAndFlush(
                ListeningItemAttempt.create(
                        session,
                        item,
                        attemptNo,
                        purpose,
                        prefix,
                        now
                )
        );

        for (ListeningTaskType taskType : ListeningTaskType.values()) {
            ListeningTaskResponse response = selected.contains(taskType)
                    ? ListeningTaskResponse.selected(
                            attempt,
                            taskType,
                            prefix + ":" + taskType.name()
                    )
                    : ListeningTaskResponse.notSelected(
                            attempt,
                            taskType,
                            prefix + ":" + taskType.name()
                    );
            responseRepository.save(response);
        }
    }

    private void expireOrRejectActive(
            Long userId,
            ListeningPolicySetting policy
    ) {
        sessionRepository.findFirstByUserIdAndStatusInOrderByStartedAtDesc(
                userId,
                List.of(ListeningSessionStatus.IN_PROGRESS)
        ).ifPresent(active -> {
            if (active.isExpired(
                    LocalDateTime.now(),
                    Duration.ofHours(policy.getResumeHours())
            )) {
                active.abandon(LocalDateTime.now());
            } else {
                throw new BusinessException(
                        "이미 진행 중인 Listening Session이 있습니다.",
                        LanguageLearningErrorCode.LISTENING_ACTIVE_SESSION_EXISTS
                );
            }
        });
    }

    private ListeningSession ownedLocked(Long userId, Long sessionId) {
        return sessionRepository.findOwnedLockedByIdAndUserId(
                sessionId,
                userId
        ).orElseThrow(() -> new BusinessException(
                "Listening Session을 찾을 수 없습니다.",
                LanguageLearningErrorCode.SESSION_NOT_FOUND
        ));
    }

    private boolean expired(ListeningSession session) {
        return session.isExpired(
                LocalDateTime.now(),
                Duration.ofHours(
                        policySettingService.get().getResumeHours()
                )
        );
    }

    private String key(String value) {
        String key = value == null || value.isBlank()
                ? UUID.randomUUID().toString()
                : value.trim();

        if (key.length() > 200) {
            throw invalid("Listening idempotencyKey가 너무 깁니다.");
        }

        return key;
    }

    private Object policySnapshot(ListeningPolicySetting policy) {
        return new Object() {
            public final int resumeHours = policy.getResumeHours();
            public final int practiceAttemptLimit = policy.getPracticeAttemptLimit();
            public final int automaticRetryLimit = policy.getAutomaticRetryLimit();
            public final int manualRetryLimit = policy.getManualRetryLimit();
            public final String profilePolicyVersion = policy.getProfilePolicyVersion();
        };
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.LISTENING_INVALID_STATE
        );
    }

    public record ResumeResult(Long sessionId, boolean expired) {
    }

    public record CompleteResult(Long sessionId, boolean expired) {
    }
}
