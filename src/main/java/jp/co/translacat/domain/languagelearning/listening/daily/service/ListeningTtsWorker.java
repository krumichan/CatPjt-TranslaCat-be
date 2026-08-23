package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.validator.ListeningAudioValidator;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListeningTtsWorker {

    private static final Set<String> READY_STATUSES = Set.of(
            "READY", "SUCCEEDED", "SUCCESS"
    );

    private final ListeningTtsTransactionService transactionService;
    private final ListeningAiClient aiClient;
    private final ListeningAudioStoragePort storagePort;
    private final ListeningAudioValidator audioValidator;
    private final ListeningOutboxTransactionService outboxTransactionService;
    private final ListeningPolicySettingQueryService policySettingService;

    public void process(ListeningOutboxTransactionService.ClaimedEvent event) {
        ListeningTtsTransactionService.TtsWork work = null;

        try {
            work = transactionService.prepare(event);
            AiListeningContract.TtsResponse response =
                    aiClient.synthesize(work.request());
            validate(work, response);
            byte[] audio = aiClient.getAudio(response.audio().audioReference());
            String contentType = contentType(response.audio().format());
            audioValidator.validate(
                    audio,
                    contentType,
                    work.maxAudioBytes(),
                    response.audio().durationMs(),
                    work.maxAudioSeconds()
            );
            audioValidator.validateChecksum(
                    audio,
                    response.audio().checksum()
            );
            storagePort.store(work.objectKey(), audio, contentType);
            transactionService.apply(work, response, contentType);
        } catch (ListeningAiException exception) {
            fail(work, event, exception.getMessage(), exception.isRetryable(),
                    exception.getRetryAfter());
        } catch (RuntimeException exception) {
            fail(work, event, exception.getMessage(), false, Duration.ZERO);
        }
    }

    private void validate(
            ListeningTtsTransactionService.TtsWork work,
            AiListeningContract.TtsResponse response
    ) {
        if (response == null
                || !work.request().requestId().equals(response.requestId())
                || !work.request().itemId().equals(response.itemId())
                || !work.request().sourceText().equals(response.sourceText())
                || !work.request().contentHash().equals(response.contentHash())
                || !work.request().generationVersion().equals(
                        response.generationVersion()
                )
                || response.status() == null) {
            throw invalid();
        }

        if ("FAILED".equalsIgnoreCase(response.status())) {
            AiListeningContract.AiError error = response.error();

            if (error == null || error.code() == null
                    || error.code().isBlank()) {
                throw invalid();
            }

            throw new ListeningAiException(
                    error.message() == null || error.message().isBlank()
                            ? "Listening TTS 처리에 실패했습니다."
                            : error.message(),
                    error.code(),
                    error.failedStage() == null
                            ? "TTS"
                            : error.failedStage(),
                    error.retryable(),
                    Duration.ofSeconds(1),
                    response.itemId(),
                    null
            );
        }

        if (!READY_STATUSES.contains(response.status().toUpperCase())
                || response.audio() == null
                || response.audio().audioReference() == null
                || response.audio().audioReference().isBlank()
                || response.audio().durationMs() <= 0
                || response.audio().format() == null
                || response.audio().format().isBlank()
                || response.audio().voice() == null
                || !work.request().contentHash().equals(
                        response.audio().textHash()
                )
                || response.audio().checksum() == null
                || response.audio().checksum().isBlank()) {
            throw invalid();
        }
    }

    private BusinessException invalid() {
        return new BusinessException(
                "Listening TTS 응답 계약이 올바르지 않습니다.",
                LanguageLearningErrorCode.AI_SCHEMA_INVALID
        );
    }

    private void fail(
            ListeningTtsTransactionService.TtsWork work,
            ListeningOutboxTransactionService.ClaimedEvent event,
            String reason,
            boolean retryable,
            Duration retryAfter
    ) {
        int limit = policySettingService.get().getAutomaticRetryLimit();
        var result = outboxTransactionService.fail(
                event.id(),
                reason,
                retryable,
                retryAfter,
                limit,
                LocalDateTime.now()
        );

        if (work != null) {
            transactionService.recordFailure(
                    work,
                    reason,
                    retryable && !result.exhausted(),
                    result.exhausted()
            );
        }
    }

    private String contentType(String format) {
        if (format == null) {
            return "audio/wav";
        }

        String normalized = format.toLowerCase(Locale.ROOT)
                .split(";", 2)[0]
                .trim();

        return switch (normalized) {
            case "mp3", "mpeg", "audio/mp3", "audio/mpeg" -> "audio/mpeg";
            case "ogg", "audio/ogg" -> "audio/ogg";
            case "webm", "audio/webm" -> "audio/webm";
            case "flac", "audio/flac", "audio/x-flac" -> "audio/flac";
            case "m4a", "mp4", "audio/m4a", "audio/x-m4a", "audio/mp4" ->
                    "audio/mp4";
            case "wav", "wave", "audio/wav", "audio/x-wav",
                    "audio/vnd.wave" -> "audio/wav";
            default -> "audio/wav";
        };
    }
}
