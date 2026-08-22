package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceSegmentStatus;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceTranslationRetryQueryService {

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

        if (segment.getStatus() != VoiceSegmentStatus.TRANSLATION_FAILED) {
            throw new BusinessException(
                    "Only translation-failed segments can be retried.",
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

        String sourceLanguage = segment.getLockedLanguage() != null
                ? segment.getLockedLanguage()
                : segment.getDetectedLanguage();
        if (sourceLanguage == null || sourceLanguage.isBlank()) {
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

    private BusinessException notFound() {
        return new BusinessException(
                "Voice segment was not found.",
                VoiceErrorCode.NOT_FOUND
        );
    }
}
