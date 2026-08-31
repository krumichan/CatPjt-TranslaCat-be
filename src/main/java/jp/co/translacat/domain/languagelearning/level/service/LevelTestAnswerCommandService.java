package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.dto.request.LevelAnswerRequestDto;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestItemRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestResponseRepository;
import jp.co.translacat.domain.languagelearning.level.repository.LevelTestSessionRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LevelTestAnswerCommandService {

    private final LevelTestItemRepository itemRepository;
    private final LevelTestResponseRepository responseRepository;
    private final LevelTestSessionRepository sessionRepository;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public PreparedResponse prepareText(
            Long userId,
            Long sessionId,
            Long itemId,
            LevelAnswerRequestDto request
    ) {
        LevelTestItem item = lockedOwnedItem(userId, sessionId, itemId);
        if (item.getAnswerMode() == LevelTestAnswerMode.AUDIO) {
            throw answerModeMismatch();
        }

        String key = normalizeKey(
                request == null ? null : request.idempotencyKey()
        );
        LevelTestResponse existing = responseRepository
                .findByItemIdAndIdempotencyKey(itemId, key)
                .orElse(null);
        if (existing != null) {
            return new PreparedResponse(item, existing, true);
        }
        if (responseRepository.findByItemId(itemId).isPresent()) {
            throw new BusinessException(
                    "이미 제출된 Level Test 답변은 변경할 수 없습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        validateAnswerable(item);

        String option = request == null
                ? null
                : trim(request.selectedOptionKey());
        List<String> optionKeys = request == null
                || request.selectedOptionKeys() == null
                ? List.of()
                : request.selectedOptionKeys().stream()
                        .map(this::trim)
                        .toList();
        String text = request == null
                ? null
                : trimPreserve(request.textAnswer());
        validateTextPayload(item, option, optionKeys, text);

        LocalDateTime now = LocalDateTime.now();
        LevelTestResponse response = responseRepository.save(
                LevelTestResponse.text(
                        item,
                        option,
                        jsonCodec.write(optionKeys),
                        text,
                        key,
                        now
                )
        );
        markEvaluating(item, sessionId, now);
        return new PreparedResponse(item, response, false);
    }

    @Transactional
    public PreparedResponse prepareAudioMetadata(
            Long userId,
            Long sessionId,
            Long itemId,
            String objectKey,
            String contentType,
            int durationMs,
            String idempotencyKey,
            LocalDateTime retentionUntil
    ) {
        LevelTestItem item = lockedOwnedItem(userId, sessionId, itemId);
        if (item.getAnswerMode() != LevelTestAnswerMode.AUDIO) {
            throw answerModeMismatch();
        }
        if (durationMs <= 0
                || item.getMaxAudioSeconds() != null
                && durationMs > item.getMaxAudioSeconds() * 1000L) {
            throw new BusinessException(
                    "Level Test Audio 길이가 허용 범위를 벗어났습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_AUDIO_INVALID
            );
        }

        String key = normalizeKey(idempotencyKey);
        LevelTestResponse existing = responseRepository
                .findByItemIdAndIdempotencyKey(itemId, key)
                .orElse(null);
        if (existing != null) {
            return new PreparedResponse(item, existing, true);
        }
        LevelTestResponse submitted = responseRepository.findByItemId(itemId)
                .orElse(null);
        validateAnswerable(item);

        LocalDateTime now = LocalDateTime.now();
        if (submitted != null) {
            if (item.getStatus() != LevelTestItemStatus.EVALUATION_FAILED) {
                throw new BusinessException(
                        "이미 제출된 Level Test Audio 답변은 변경할 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
                );
            }
            submitted.replaceAudio(
                    objectKey,
                    contentType,
                    durationMs,
                    retentionUntil,
                    key,
                    now
            );
            LevelTestResponse response = responseRepository.save(submitted);
            markEvaluating(item, sessionId, now);
            return new PreparedResponse(item, response, false);
        }

        LevelTestResponse response = responseRepository.save(
                LevelTestResponse.audio(
                        item,
                        objectKey,
                        contentType,
                        durationMs,
                        retentionUntil,
                        key,
                        now
                )
        );
        markEvaluating(item, sessionId, now);
        return new PreparedResponse(item, response, false);
    }

    @Transactional
    public int registerRetry(Long responseId) {
        LevelTestResponse response = responseRepository
                .findById(responseId)
                .orElseThrow();
        LevelTestItem item = itemRepository
                .findLockedById(response.getItem().getId())
                .orElseThrow();
        if (item.getStatus() != LevelTestItemStatus.EVALUATION_FAILED) {
            throw new BusinessException(
                    "평가 실패 문항만 재평가할 수 있습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }

        try {
            int count = response.registerManualEvaluationRetry();
            LocalDateTime now = LocalDateTime.now();
            item.markEvaluating();
            var session = sessionRepository
                    .findLockedById(item.getSession().getId())
                    .orElseThrow();
            if (session.getStatus() != LevelTestSessionStatus.IN_PROGRESS) {
                throw new BusinessException(
                        "Level Test Session 상태가 유효하지 않습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
                );
            }
            session.markEvaluating(now);
            return count;
        } catch (IllegalStateException exception) {
            throw new BusinessException(
                    exception.getMessage(),
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
    }

    private void markEvaluating(
            LevelTestItem item,
            Long sessionId,
            LocalDateTime now
    ) {
        item.markAnswered();
        item.markEvaluating();
        var session = sessionRepository.findLockedById(sessionId).orElseThrow();
        if (session.getStatus() != LevelTestSessionStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Level Test Session 상태가 유효하지 않습니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        session.markEvaluating(now);
    }

    private LevelTestItem lockedOwnedItem(
            Long userId,
            Long sessionId,
            Long itemId
    ) {
        return itemRepository.findLockedById(itemId)
                .filter(value -> value.getSession().getId().equals(sessionId))
                .filter(value -> value.getSession().getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(
                        "Level Test 문항을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.LEVEL_TEST_NOT_FOUND
                ));
    }

    private void validateAnswerable(LevelTestItem item) {
        if (item.getQuestionNumber()
                != item.getSession().currentQuestionNumber()
                || item.getSession().getStatus()
                != LevelTestSessionStatus.IN_PROGRESS
                || item.getStatus() != LevelTestItemStatus.READY
                && item.getStatus() != LevelTestItemStatus.EVALUATION_FAILED) {
            throw new BusinessException(
                    "현재 응답 가능한 Level Test 문항이 아닙니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
    }

    private void validateTextPayload(
            LevelTestItem item,
            String option,
            List<String> optionKeys,
            String text
    ) {
        switch (item.getAnswerMode()) {
            case CHOICE -> {
                if (item.getItemType() == LevelTestItemType.GRAMMAR_SENTENCE_ORDER) {
                    if (optionKeys.isEmpty() || option != null || text != null) {
                        throw answerModeMismatch();
                    }
                } else if (option == null || !optionKeys.isEmpty() || text != null) {
                    throw answerModeMismatch();
                }
            }
            case TEXT -> {
                if (text == null
                        || text.isBlank()
                        || option != null
                        || !optionKeys.isEmpty()) {
                    throw answerModeMismatch();
                }
                if (item.getMaxAnswerLength() != null
                        && text.length() > item.getMaxAnswerLength()) {
                    throw new BusinessException(
                            "Level Test 답변 길이를 초과했습니다.",
                            LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
                    );
                }
            }
            case AUDIO -> throw answerModeMismatch();
        }
    }

    private String normalizeKey(String value) {
        String key = trim(value);
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new BusinessException(
                    "Level Test idempotencyKey가 필요합니다.",
                    LanguageLearningErrorCode.LEVEL_TEST_INVALID_STATE
            );
        }
        return key;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimPreserve(String value) {
        return value == null ? null : value.strip();
    }

    private BusinessException answerModeMismatch() {
        return new BusinessException(
                "Level Test 답변 방식이 문제 유형과 일치하지 않습니다.",
                LanguageLearningErrorCode.LEVEL_TEST_ANSWER_MODE_MISMATCH
        );
    }

    public record PreparedResponse(
            LevelTestItem item,
            LevelTestResponse response,
            boolean idempotentReplay
    ) {
    }
}
