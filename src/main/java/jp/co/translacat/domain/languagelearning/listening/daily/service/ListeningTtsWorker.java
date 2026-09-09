package jp.co.translacat.domain.languagelearning.listening.daily.service;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;
import jp.co.translacat.domain.languagelearning.listening.ai.port.ListeningAiClient;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.validator.ListeningAudioValidator;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Slf4j
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

    public void process(ListeningOutboxTransactionService.ClaimedEvent event) {
        ListeningTtsTransactionService.TtsWork work = null;

        log.info(
                "Listening TTS worker started. eventId={} aggregateId={} attemptCount={}",
                event.id(), event.aggregateId(), event.attemptCount()
        );

        try {
            work = transactionService.prepare(event);
            log.info(
                    "Listening TTS request prepared. eventId={} itemId={} requestId={} "
                            + "voice={} contentHash={}",
                    event.id(),
                    work.request().itemId(),
                    work.request().requestId(),
                    work.request().voice().voiceKey(),
                    shortHash(work.request().contentHash())
            );
            AiListeningContract.TtsResponse response =
                    aiClient.synthesize(work.request());
            log.info(
                    "Listening TTS synthesize completed. eventId={} itemId={} status={} "
                            + "audioReference={}",
                    event.id(),
                    work.request().itemId(),
                    response == null ? null : response.status(),
                    response == null || response.audio() == null
                            ? null : response.audio().audioReference()
            );
            validate(work, response);
            String audioReference = response.audio().audioReference();
            log.info(
                    "Listening TTS audio download started. eventId={} itemId={} "
                            + "audioReference={}",
                    event.id(), work.request().itemId(), audioReference
            );
            byte[] audio = aiClient.getAudio(audioReference);
            log.info(
                    "Listening TTS audio download completed. eventId={} itemId={} "
                            + "audioReference={} bytes={}",
                    event.id(), work.request().itemId(), audioReference, audio.length
            );
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
            log.info(
                    "Listening TTS worker completed. eventId={} itemId={} objectKey={} "
                            + "contentType={} bytes={}",
                    event.id(), work.request().itemId(), work.objectKey(),
                    contentType, audio.length
            );
        } catch (ListeningAiException exception) {
            log.warn(
                    "Listening TTS AI call failed. eventId={} itemId={} code={} stage={} "
                            + "retryable={} message={}",
                    event.id(),
                    work == null ? event.aggregateId() : work.request().itemId(),
                    exception.getErrorCode(),
                    exception.getFailedStage(),
                    exception.isRetryable(),
                    exception.getMessage()
            );
            fail(work, event, exception.getMessage(), exception.isRetryable(),
                    exception.getRetryAfter());
        } catch (RuntimeException exception) {
            log.error(
                    "Listening TTS worker failed unexpectedly. eventId={} itemId={}",
                    event.id(),
                    work == null ? event.aggregateId() : work.request().itemId(),
                    exception
            );
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
        transactionService.recordFailure(event, reason, retryable, retryAfter);
    }

    private String shortHash(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 12 ? value : value.substring(0, 12);
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
