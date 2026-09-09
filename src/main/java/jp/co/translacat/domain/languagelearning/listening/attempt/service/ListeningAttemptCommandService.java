package jp.co.translacat.domain.languagelearning.listening.attempt.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.audio.validator.ListeningAudioValidator;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceLevel;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAttemptStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.evaluation.service.ListeningAttemptFinalizationCommandService;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningIdempotencyPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningTaskSelectionPolicy;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionLockService;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListeningAttemptCommandService {

    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningTaskResponseRepository responseRepository;
    private final ListeningSessionLockService lockService;
    private final ListeningPolicySettingQueryService policySettingService;
    private final ListeningTaskSelectionPolicy taskSelectionPolicy;
    private final ListeningIdempotencyPolicy idempotencyPolicy;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningAttemptFinalizationCommandService finalizationService;
    private final ListeningAudioStoragePort storagePort;
    private final ListeningAudioValidator audioValidator;
    private final ListeningAudioKeyFactory audioKeyFactory;
    private final ListeningViewMapper viewMapper;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public ListeningApiContract.TaskView upsertText(
            Long userId,
            Long attemptId,
            ListeningTaskType taskType,
            ListeningApiContract.ResponseUpsertRequest request
    ) {
        ListeningItemAttempt attempt = mutableAttempt(userId, attemptId);
        ListeningTaskResponse response = selected(attemptId, taskType);

        if (taskType == ListeningTaskType.REPEAT_AFTER_AUDIO) {
            throw invalid("Repeat Task에는 Audio 응답이 필요합니다.");
        }

        String answer = request == null ? null : request.answer();

        if (answer == null || answer.isBlank() || answer.length() > 4000) {
            throw invalid("Listening 답변이 필요합니다.");
        }

        response.updateText(answer.trim(), normalize(answer));
        applyAssistanceInternal(
                attempt,
                response,
                request == null ? List.of() : request.assistanceUsage()
        );
        touchSessionIfActive(attempt, LocalDateTime.now());

        return viewMapper.task(response);
    }

    @Transactional
    public ListeningApiContract.TaskView applyAssistance(
            Long userId,
            Long attemptId,
            ListeningTaskType taskType,
            List<ListeningApiContract.AssistanceUsage> usage
    ) {
        ListeningItemAttempt attempt = mutableAttempt(userId, attemptId);
        ListeningTaskResponse response = selected(attemptId, taskType);
        applyAssistanceInternal(attempt, response, usage);
        touchSessionIfActive(attempt, LocalDateTime.now());

        return viewMapper.task(response);
    }

    @Transactional
    public ListeningApiContract.AttemptView useAssistance(
            Long userId,
            Long attemptId,
            ListeningAssistanceType assistanceType
    ) {
        ListeningItemAttempt attempt = mutableAttempt(userId, attemptId);
        if (assistanceType == null
                || assistanceType == ListeningAssistanceType.SHOW_ANSWER) {
            throw invalid("Listening 도움 유형이 올바르지 않습니다.");
        }

        for (ListeningTaskResponse response : selectedResponses(attemptId)) {
            applyAssistanceInternal(
                    attempt,
                    response,
                    incrementAssistance(response, assistanceType)
            );
        }
        touchSessionIfActive(attempt, LocalDateTime.now());

        return viewMapper.attempt(attempt);
    }

    @Transactional
    public ListeningApiContract.AudioUploadView uploadAudio(
            Long userId,
            Long attemptId,
            MultipartFile file,
            int durationMs
    ) {
        ListeningItemAttempt attempt = mutableAttempt(userId, attemptId);
        ListeningTaskResponse response = selected(
                attemptId,
                ListeningTaskType.REPEAT_AFTER_AUDIO
        );
        ListeningPolicySetting policy = policySettingService.get();
        byte[] bytes = bytes(file);
        String contentType = file.getContentType();
        audioValidator.validate(
                bytes,
                contentType,
                policy.getMaxAudioFileBytes(),
                durationMs,
                policy.getRepeatAudioMaxSeconds()
        );
        boolean rerecord = response.getUserAudioObjectKey() != null
                && response.getAudioDeletedAt() == null;

        if (rerecord && response.getRerecordCount()
                >= policy.getMaxRerecordCount()) {
            throw new BusinessException(
                    "Listening 재녹음 횟수를 초과했습니다.",
                    LanguageLearningErrorCode.LISTENING_RERECORD_LIMIT_EXCEEDED
            );
        }

        String oldKey = response.getUserAudioObjectKey();
        String key = audioKeyFactory.response(
                userId,
                attempt.getSession().getId(),
                response.getId(),
                extension(file.getOriginalFilename(), contentType)
        );
        storagePort.store(key, bytes, contentType);
        LocalDateTime retention = LocalDateTime.now().plusDays(
                policy.getUserAudioRetentionDays()
        );
        response.updateAudio(
                key,
                durationMs,
                contentType,
                retention,
                rerecord
        );

        if (rerecord && oldKey != null && !oldKey.equals(key)) {
            storagePort.delete(oldKey);
        }

        touchSessionIfActive(attempt, LocalDateTime.now());

        return new ListeningApiContract.AudioUploadView(
                response.getId(),
                durationMs,
                response.getRerecordCount(),
                retention
        );
    }

    @Transactional
    public ListeningApiContract.AttemptView submit(
            Long userId,
            Long attemptId,
            ListeningApiContract.SubmitRequest request
    ) {
        ListeningItemAttempt attempt = ownedAttempt(userId, attemptId);

        if (attempt.isFinalized()) {
            return viewMapper.attempt(attempt);
        }

        if (attempt.getStatus()
                == jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAttemptStatus.EVALUATING
                || attempt.getStatus()
                == jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAttemptStatus.SUBMITTED) {
            return viewMapper.attempt(attempt);
        }

        requireMutableSession(attempt);
        List<ListeningTaskResponse> selected = selectedResponses(attemptId);

        if (selected.stream().anyMatch(value -> !value.hasRequiredAnswer())) {
            throw invalid("선택한 모든 Listening Task 응답이 필요합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        selected.forEach(value -> value.submit(now));
        attempt.submit(
                jsonCodec.write(selected.stream()
                        .flatMap(value -> assistance(value).stream())
                        .toList()),
                request == null ? 0 : request.actualDurationMs(),
                now
        );

        if (attempt.isAnswerRevealed()) {
            selected.forEach(ListeningTaskResponse::markNotEvaluable);
        } else {
            attempt.markEvaluating();

            for (ListeningTaskResponse response : selected) {
                response.markEvaluating();
                outboxCommandService.enqueue(
                        ListeningOutboxType.EVALUATE_TASK,
                        response.getId(),
                        null,
                        evaluationKey(response)
                );
            }
        }

        touchSessionIfActive(attempt, now);
        updateOfficialSessionPhase(attempt, now);

        if (attempt.isAnswerRevealed()) {
            finalizationService.finalizeIfTerminal(attemptId);
        }

        return viewMapper.attempt(attempt);
    }

    @Transactional
    public ListeningApiContract.RevealAnswerView revealAnswer(
            Long userId,
            Long attemptId
    ) {
        ListeningItemAttempt attempt = mutableAttempt(userId, attemptId);
        attempt.revealAnswer();
        List<ListeningApiContract.AssistanceUsage> usage = List.of(
                new ListeningApiContract.AssistanceUsage(
                        ListeningAssistanceType.SHOW_ANSWER,
                        1
                )
        );
        selectedResponses(attemptId).forEach(response -> {
                response.applyAssistance(
                        ListeningAssistanceLevel.GUIDED,
                        jsonCodec.write(usage)
                );
                response.markNotEvaluable();
        });
        touchSessionIfActive(attempt, LocalDateTime.now());
        finalizationService.finalizeIfTerminal(attemptId);

        return new ListeningApiContract.RevealAnswerView(
                attemptId,
                attempt.getItem().getSourceText(),
                jsonCodec.read(
                        attempt.getItem().getReferenceMeaningsJson(),
                        new TypeReference<List<String>>() {
                        }
                ),
                true,
                true
        );
    }

    @Transactional
    public ListeningApiContract.AttemptView retryEvaluation(
            Long userId,
            Long attemptId,
            ListeningApiContract.RetryRequest request
    ) {
        ListeningItemAttempt attempt = ownedAttempt(userId, attemptId);
        requireEvaluationRetryAllowed(attempt);

        if (request == null || request.taskType() == null) {
            throw invalid("재시도할 Listening Task가 필요합니다.");
        }

        ListeningTaskResponse response = selected(
                attemptId,
                request.taskType()
        );

        if (response.getStatus() != ListeningTaskStatus.EVALUATION_FAILED) {
            throw invalid("평가에 실패한 Task만 재시도할 수 있습니다.");
        }

        int limit = policySettingService.get().getManualRetryLimit();

        if (response.getManualRetryCount() >= limit) {
            throw invalid("수동 평가 재시도 가능 횟수를 초과했습니다.");
        }

        prepareEvaluationRetry(
                attempt,
                response,
                limit,
                LocalDateTime.now()
        );

        return viewMapper.attempt(attempt);
    }

    @Transactional
    public ListeningApiContract.BulkRetryView retryFailedEvaluations(
            Long userId,
            Long sessionId
    ) {
        var session = lockService.ownedSession(userId, sessionId);
        if (session.getStatus() != ListeningSessionStatus.COMPLETED
                && session.getStatus() != ListeningSessionStatus.EVALUATING) {
            throw invalid("완료 또는 평가 중인 Listening Session만 재시도할 수 있습니다.");
        }

        List<Long> officialAttemptIds = attemptRepository
                .findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(sessionId)
                .stream()
                .filter(ListeningItemAttempt::isOfficial)
                .map(ListeningItemAttempt::getId)
                .toList();
        int limit = policySettingService.get().getManualRetryLimit();
        int failedTaskCount = 0;
        int retriedTaskCount = 0;
        int exhaustedTaskCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Long attemptId : officialAttemptIds) {
            ListeningItemAttempt attempt = ownedAttempt(userId, attemptId);
            for (ListeningTaskResponse response : selectedResponses(attemptId)) {
                if (response.getStatus() != ListeningTaskStatus.EVALUATION_FAILED) {
                    continue;
                }
                failedTaskCount++;
                if (response.getManualRetryCount() >= limit) {
                    exhaustedTaskCount++;
                    continue;
                }
                prepareEvaluationRetry(attempt, response, limit, now);
                retriedTaskCount++;
            }
        }

        return new ListeningApiContract.BulkRetryView(
                failedTaskCount,
                retriedTaskCount,
                exhaustedTaskCount
        );
    }

    @Transactional
    public ListeningApiContract.AttemptView createPractice(
            Long userId,
            Long sessionId,
            Long itemId,
            ListeningApiContract.PracticeAttemptRequest request
    ) {
        lockService.ownedSession(userId, sessionId);
        ListeningItemAttempt official = attemptRepository
                .findBySessionIdAndItemIdAndEvaluationPurpose(
                        sessionId,
                        itemId,
                        ListeningEvaluationPurpose.OFFICIAL
                ).filter(value -> value.getSession().getUser().getId()
                        .equals(userId))
                .orElseThrow(() -> notFound("공식 Listening Attempt가 없습니다."));
        requirePracticeCreationAllowed(official);

        if (!official.isFinalized()) {
            throw invalid("공식 Attempt 완료 후 연습할 수 있습니다.");
        }

        Set<ListeningTaskType> selected = taskSelectionPolicy.validate(
                request == null || request.selectedTaskTypes() == null
                        ? jsonCodec.read(
                                official.getSession().getSelectedTaskTypesJson(),
                                new TypeReference<List<ListeningTaskType>>() {
                                }
                        )
                        : request.selectedTaskTypes()
        );
        List<ListeningTaskType> ordered = selected.stream()
                .sorted(Comparator.comparingInt(ListeningTaskType::ordinal))
                .toList();
        String key = request == null || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank()
                ? "session:" + sessionId + ":item:" + itemId + ":practice"
                : request.idempotencyKey().trim();

        if (key.length() > 200) {
            throw invalid("Listening idempotencyKey가 너무 깁니다.");
        }

        var existing = attemptRepository.findBySessionIdAndIdempotencyKey(
                sessionId,
                key
        );
        if (existing.isPresent()) {
            validateIdempotentPractice(existing.get(), itemId, ordered);
            return viewMapper.attempt(existing.get());
        }

        int limit = policySettingService.get().getPracticeAttemptLimit();
        long used = attemptRepository.countBySessionIdAndItemIdAndEvaluationPurpose(
                sessionId,
                itemId,
                ListeningEvaluationPurpose.PRACTICE
        );

        if (used >= limit) {
            throw new BusinessException(
                    "Listening 연습 횟수를 초과했습니다.",
                    LanguageLearningErrorCode.LISTENING_PRACTICE_LIMIT_EXCEEDED
            );
        }

        ListeningItemAttempt practice;
        try {
            practice = attemptRepository.saveAndFlush(
                    ListeningItemAttempt.create(
                            official.getSession(),
                            official.getItem(),
                            2,
                            ListeningEvaluationPurpose.PRACTICE,
                            key,
                            LocalDateTime.now()
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            ListeningItemAttempt concurrent = attemptRepository
                    .findBySessionIdAndIdempotencyKey(sessionId, key)
                    .orElseThrow(() -> exception);
            validateIdempotentPractice(concurrent, itemId, ordered);
            return viewMapper.attempt(concurrent);
        }

        for (ListeningTaskType type : ListeningTaskType.values()) {
            responseRepository.save(ordered.contains(type)
                    ? ListeningTaskResponse.selected(
                            practice, type, key + ":" + type.name())
                    : ListeningTaskResponse.notSelected(
                            practice, type, key + ":" + type.name()));
        }

        return viewMapper.attempt(practice);
    }

    private void validateIdempotentPractice(
            ListeningItemAttempt existing,
            Long requestedItemId,
            List<ListeningTaskType> requestedTasks
    ) {
        List<ListeningTaskType> existingTasks = responseRepository
                .findAllByAttemptIdOrderByTaskTypeAsc(existing.getId())
                .stream()
                .filter(value -> value.getStatus() != ListeningTaskStatus.NOT_SELECTED)
                .map(ListeningTaskResponse::getTaskType)
                .toList();
        idempotencyPolicy.requireSamePayload(
                existing.getItem().getId(),
                existingTasks,
                requestedItemId,
                requestedTasks
        );
    }

    @Transactional
    public ListeningApiContract.AttemptView skip(
            Long userId,
            Long attemptId,
            ListeningApiContract.SkipRequest request
    ) {
        ListeningItemAttempt attempt = mutableAttempt(userId, attemptId);
        LocalDateTime now = LocalDateTime.now();
        selectedResponses(attemptId).forEach(ListeningTaskResponse::skip);
        attempt.skip(now);
        touchSessionIfActive(attempt, now);
        updateOfficialSessionPhase(attempt, now);

        return viewMapper.attempt(attempt);
    }

    private void applyAssistanceInternal(
            ListeningItemAttempt attempt,
            ListeningTaskResponse response,
            List<ListeningApiContract.AssistanceUsage> source
    ) {
        List<ListeningApiContract.AssistanceUsage> usage = source == null
                ? List.of()
                : List.copyOf(source);

        if (usage.size() > 20 || usage.stream().anyMatch(value -> value == null
                || value.type() == null
                || value.count() < 1
                || value.count() > 100)) {
            throw invalid("Listening 도움 사용 내역이 올바르지 않습니다.");
        }

        if (usage.stream().anyMatch(value ->
                value.type() == ListeningAssistanceType.SHOW_ANSWER)) {
            revealAnswer(attempt.getSession().getUser().getId(), attempt.getId());
            return;
        }

        ListeningAssistanceLevel level = usage.stream().anyMatch(value ->
                value.type() == ListeningAssistanceType.TOPIC_HINT
                        || value.type() == ListeningAssistanceType.KEYWORD_HINT)
                ? ListeningAssistanceLevel.ASSISTED
                : ListeningAssistanceLevel.INDEPENDENT;
        response.applyAssistance(level, jsonCodec.write(usage));
    }

    private ListeningItemAttempt mutableAttempt(Long userId, Long attemptId) {
        ListeningItemAttempt attempt = ownedAttempt(userId, attemptId);
        requireMutableSession(attempt);

        if (attempt.isFinalized()) {
            throw new BusinessException(
                    "이미 제출된 Listening 문항입니다.",
                    LanguageLearningErrorCode.LISTENING_ITEM_ALREADY_SUBMITTED
            );
        }

        return attempt;
    }

    private ListeningItemAttempt ownedAttempt(Long userId, Long attemptId) {
        return lockService.ownedAttempt(userId, attemptId);
    }

    private void requireMutableSession(ListeningItemAttempt attempt) {
        if (attempt.isPractice()
                && attempt.getSession().getStatus()
                == ListeningSessionStatus.COMPLETED) {
            return;
        }

        requireActive(attempt);
    }

    private void requirePracticeCreationAllowed(ListeningItemAttempt official) {
        ListeningSessionStatus status = official.getSession().getStatus();

        if (status == ListeningSessionStatus.COMPLETED) {
            return;
        }

        requireActive(official);
    }

    private void touchSessionIfActive(
            ListeningItemAttempt attempt,
            LocalDateTime now
    ) {
        if (attempt.getSession().isActive()) {
            attempt.getSession().touch(now);
        }
    }

    private void requireActive(ListeningItemAttempt attempt) {
        ListeningPolicySetting policy = policySettingService.get();

        if (!attempt.getSession().isActive()) {
            throw invalid("활성 Listening Session이 아닙니다.");
        }

        if (attempt.getSession().isExpired(
                LocalDateTime.now(),
                Duration.ofHours(policy.getResumeHours())
        )) {
            attempt.getSession().abandon(LocalDateTime.now());
            throw new BusinessException(
                    "Listening Session이 만료되었습니다.",
                    LanguageLearningErrorCode.LISTENING_SESSION_EXPIRED
            );
        }
    }

    private void requireEvaluationRetryAllowed(
            ListeningItemAttempt attempt
    ) {
        ListeningSessionStatus status = attempt.getSession().getStatus();

        if (status == ListeningSessionStatus.COMPLETED
                || status == ListeningSessionStatus.EVALUATING) {
            return;
        }

        requireActive(attempt);
    }

    private void updateOfficialSessionPhase(
            ListeningItemAttempt changedAttempt,
            LocalDateTime now
    ) {
        if (!changedAttempt.isOfficial()) {
            return;
        }

        List<ListeningItemAttempt> official = attemptRepository
                .findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(
                        changedAttempt.getSession().getId()
                ).stream()
                .filter(ListeningItemAttempt::isOfficial)
                .toList();
        if (!changedAttempt.getSession().hasAllTargetItems(official.stream()
                .map(value -> value.getItem().getItemIndex()).toList())) {
            return;
        }

        boolean allTerminal = official.stream()
                .allMatch(ListeningItemAttempt::isFinalized);
        if (allTerminal) {
            changedAttempt.getSession().complete(now);
            return;
        }

        boolean allSubmitted = official.stream().allMatch(value ->
                value.isFinalized()
                        || value.getStatus() == ListeningAttemptStatus.SUBMITTED
                        || value.getStatus() == ListeningAttemptStatus.EVALUATING
        );
        if (allSubmitted) {
            changedAttempt.getSession().startEvaluating(now);
        }
    }

    private ListeningTaskResponse selected(
            Long attemptId,
            ListeningTaskType taskType
    ) {
        return responseRepository.findLockedByAttemptIdAndTaskType(attemptId, taskType)
                .filter(value -> value.getStatus()
                        != ListeningTaskStatus.NOT_SELECTED)
                .orElseThrow(() -> invalid("선택하지 않은 Listening Task입니다."));
    }

    private List<ListeningTaskResponse> selectedResponses(Long attemptId) {
        return responseRepository.findAllLockedByAttemptIdOrderByTaskTypeAsc(attemptId)
                .stream()
                .filter(value -> value.getStatus()
                        != ListeningTaskStatus.NOT_SELECTED)
                .toList();
    }

    private List<ListeningApiContract.AssistanceUsage> assistance(
            ListeningTaskResponse response
    ) {
        return jsonCodec.read(
                response.getAssistanceUsageJson(),
                new TypeReference<List<ListeningApiContract.AssistanceUsage>>() {
                }
        );
    }

    private List<ListeningApiContract.AssistanceUsage> incrementAssistance(
            ListeningTaskResponse response,
            ListeningAssistanceType type
    ) {
        List<ListeningApiContract.AssistanceUsage> next = new ArrayList<>(
                assistance(response)
        );
        for (int index = 0; index < next.size(); index++) {
            ListeningApiContract.AssistanceUsage current = next.get(index);
            if (current.type() == type) {
                next.set(index, new ListeningApiContract.AssistanceUsage(
                        type,
                        Math.min(100, current.count() + 1)
                ));
                return next;
            }
        }
        next.add(new ListeningApiContract.AssistanceUsage(type, 1));
        return next;
    }

    private void prepareEvaluationRetry(
            ListeningItemAttempt attempt,
            ListeningTaskResponse response,
            int limit,
            LocalDateTime now
    ) {
        response.prepareManualRetry(limit);
        attempt.registerManualEvaluationRetry(limit);
        attempt.markEvaluating();
        response.markEvaluating();
        attempt.getSession().resumeEvaluationForRetry(now);
        outboxCommandService.enqueue(
                ListeningOutboxType.EVALUATE_TASK,
                response.getId(),
                null,
                evaluationKey(response)
        );
    }

    private String evaluationKey(ListeningTaskResponse response) {
        return "listening:response:" + response.getId()
                + ":evaluation:" + response.getManualRetryCount();
    }

    private byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Listening Audio 파일이 필요합니다.");
        }

        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("Listening Audio를 읽을 수 없습니다.", exception);
        }
    }

    private String extension(String fileName, String contentType) {
        if (fileName != null && fileName.lastIndexOf('.') >= 0) {
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        }

        return contentType == null
                ? "bin"
                : contentType.toLowerCase(Locale.ROOT)
                        .replace("audio/", "")
                        .replace("x-", "");
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.LISTENING_INVALID_STATE
        );
    }

    private BusinessException notFound(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND
        );
    }
}
