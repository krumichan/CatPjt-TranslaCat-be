package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceSegmentStatus;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.support.VoicePolicy;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceTranslationRetryQueryService {

    private static final Set<String> NON_RETRYABLE_LANGUAGE_ERRORS = Set.of(
            VoiceErrorCode.AI_LANGUAGE_UNDETERMINED,
            VoiceErrorCode.AI_UNSUPPORTED_LANGUAGE,
            VoiceErrorCode.AI_LANGUAGE_MISMATCH
    );

    private final VoiceSegmentRepository segmentRepository;

    public VoiceTranslationRetryContext prepare(
            Long userId,
            String sessionId,
            Long segmentId
    ) {
        VoiceSegment segment = segmentRepository
                .findByIdAndSession_IdAndSession_User_Id(
                        segmentId,
                        sessionId,
                        userId
                )
                .orElseThrow(this::notFound);

        // 언어 필터가 거절한 원문은 텍스트 번역 재시도로 우회하지 않는다.
        if (segment.getStatus() != VoiceSegmentStatus.TRANSLATION_FAILED
                || (segment.getErrorCode() != null
                && NON_RETRYABLE_LANGUAGE_ERRORS.contains(segment.getErrorCode()))) {
            throw new BusinessException(
                    "Only retryable translation-failed segments can be retried.",
                    VoiceErrorCode.RETRY_NOT_ALLOWED
            );
        }
        if (segment.getSourceText() == null
                || segment.getSourceText().isBlank()) {
            throw new BusinessException(
                    "Voice source transcript is unavailable.",
                    VoiceErrorCode.RETRY_SOURCE_UNAVAILABLE
            );
        }

        // AUTO 전환 초기에는 대표 언어 lock이 과거 발화를 가리키므로 실제 감지 언어를 우선한다.
        // 실시간 AI resolver와 동일하게 신뢰도 0.5 이상일 때만 감지 결과를 채택한다.
        String detectedLanguage = normalizeLanguage(segment.getDetectedLanguage());
        String observedLanguage = segment.getDetectedLanguage();
        if (observedLanguage != null && !observedLanguage.isBlank()
                && !"und".equalsIgnoreCase(observedLanguage.trim()) && detectedLanguage == null) {
            throw new BusinessException(
                    "Detected Voice language is unsupported.",
                    VoiceErrorCode.RETRY_LANGUAGE_UNAVAILABLE
            );
        }

        Double confidence = segment.getLanguageConfidence();
        String sourceLanguage = detectedLanguage != null
                && confidence != null
                && Double.isFinite(confidence)
                && confidence >= 0.5
                ? detectedLanguage
                : normalizeLanguage(segment.getLockedLanguage());
        if (sourceLanguage == null) {
            throw new BusinessException(
                    "Voice source language is unavailable.",
                    VoiceErrorCode.RETRY_LANGUAGE_UNAVAILABLE
            );
        }

        return new VoiceTranslationRetryContext(
                segment.getSourceText(),
                sourceLanguage,
                segment.getTargetLanguage()
        );
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT).split("-", 2)[0];
        return VoicePolicy.SUPPORTED_LANGUAGES.contains(normalized) ? normalized : null;
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Voice segment was not found.",
                VoiceErrorCode.NOT_FOUND
        );
    }
}
