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
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
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
    private final ListeningSessionLockService lockService;
    private final ListeningViewMapper viewMapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ListeningApiContract.SessionView synchronizeAndView(Long userId, Long sessionId) {
        ListeningSession session = ownedLocked(userId, sessionId);
        if (session.isActive() && expired(session)) {
            session.abandon(LocalDateTime.now());
        } else {
            synchronizeReadyItems(session);
        }
        // Keep attachment reconciliation and the poll-stop fields in one Set-
        // locked snapshot. READ_COMMITTED also avoids the ownership lookup's
        // pre-lock snapshot hiding a just-committed TTS item.
        return viewMapper.session(session);
    }

    @Transactional
    public Long create(
            Long userId,
            ListeningApiContract.SessionCreateRequest request
    ) {
        if (request == null || request.dailySetId() == null) {
            throw invalid("Listening Daily Set이 필요합니다.");
        }

        ListeningDailySet dailySet = lockService.ownedDailySet(
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
            synchronizeReadyItems(ownedLocked(userId, existing.get().getId()));
            rememberSelection(userId, ordered);
            return existing.get().getId();
        }

        ListeningPolicySetting policy = policySettingService.get();
        expireOrRejectActive(userId, policy);

        if (!dailySet.isUsable()) {
            throw invalid("준비되지 않은 Listening Daily Set입니다.");
        }

        List<ListeningItem> activeItems = dailySetQueryService.activeItems(dailySet.getId());
        if (attemptRepository.existsByItemDailySetIdAndEvaluationPurpose(
                dailySet.getId(), ListeningEvaluationPurpose.OFFICIAL)) {
            throw invalid("이 Listening Daily Set에 이미 공식 학습 이력이 있습니다. 기존 Session을 확인해 주세요.");
        }
        List<ListeningItem> items = activeItems.stream()
                .filter(item -> item.isPlayable(LocalDateTime.now()))
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

        // The Set lock serializes this snapshot with TTS completion. Subsequent
        // polling also reconciles items which become READY after this commit.

        return session.getId();
    }

    @Transactional
    public Long activeSessionId(Long userId) {
        ListeningPolicySetting policy = policySettingService.get();
        var active = sessionRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(
                        userId,
                        ListeningSessionStatus.IN_PROGRESS
                );
        if (active.isEmpty()) {
            return null;
        }
        ListeningSession session = ownedLocked(userId, active.get().getId());
        if (!session.isActive()) {
            return null;
        }
        if (session.isExpired(
                LocalDateTime.now(),
                Duration.ofHours(policy.getResumeHours())
        )) {
            session.abandon(LocalDateTime.now());
            return null;
        }
        synchronizeReadyItems(session);
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

        synchronizeReadyItems(session);
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

        synchronizeReadyItems(session);
        List<ListeningItemAttempt> official = attemptRepository
                .findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(sessionId)
                .stream().filter(ListeningItemAttempt::isOfficial).toList();
        if (!session.hasAllTargetItems(official.stream()
                .map(value -> value.getItem().getItemIndex()).toList())) {
            throw invalid("아직 준비되지 않은 Listening 문항이 있습니다.");
        }
        boolean pending = official.stream()
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

        if (!session.isActive()) {
            return false;
        }

        if (!expired(session)) {
            synchronizeReadyItems(session);
            return false;
        }

        session.abandon(LocalDateTime.now());

        return true;
    }

    private void synchronizeReadyItems(ListeningSession session) {
        if (!session.isActive()) {
            return;
        }
        List<ListeningItemAttempt> official = attemptRepository
                .findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(session.getId())
                .stream().filter(ListeningItemAttempt::isOfficial).toList();
        Set<Integer> attachedIndices = new HashSet<>(official.stream()
                .map(value -> value.getItem().getItemIndex()).toList());
        List<ListeningTaskType> selected = jsonCodec.read(
                session.getSelectedTaskTypesJson(),
                new com.fasterxml.jackson.core.type.TypeReference<List<ListeningTaskType>>() { }
        );
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        for (ListeningItem item : dailySetQueryService.activeItems(session.getDailySet().getId())) {
            if (item.getItemIndex() < 1
                    || item.getItemIndex() > session.getDailySet().getTargetItemCount()
                    || attachedIndices.contains(item.getItemIndex())
                    || !item.isPlayable(now)
                    || attemptRepository.existsByItemIdAndEvaluationPurpose(
                            item.getId(), ListeningEvaluationPurpose.OFFICIAL)) {
                continue;
            }
            createAttempt(session, item, ListeningEvaluationPurpose.OFFICIAL, selected, 1, now);
            attachedIndices.add(item.getItemIndex());
            changed = true;
        }
        if (changed) {
            session.updateSelectionSnapshot(jsonCodec.write(attemptRepository
                    .findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(session.getId())
                    .stream().filter(ListeningItemAttempt::isOfficial)
                    .map(value -> value.getItem().getId()).toList()));
        }
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
        return lockService.ownedSession(userId, sessionId);
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
