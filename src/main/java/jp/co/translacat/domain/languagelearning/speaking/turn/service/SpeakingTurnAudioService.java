package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.audio.port.SpeakingAudioStoragePort;
import jp.co.translacat.domain.languagelearning.speaking.audio.service.SpeakingAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.speaking.audio.validator.SpeakingAudioSignatureValidator;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingTurnAudioService {

    private final SpeakingAudioStoragePort audioStoragePort;
    private final SpeakingAudioKeyFactory audioKeyFactory;
    private final SpeakingAudioSignatureValidator audioSignatureValidator;
    private final LanguageLearningJsonCodec jsonCodec;

    public byte[] readAndValidate(
            MultipartFile audio,
            Double durationSeconds,
            SpeakingSessionPolicySnapshot snapshot
    ) {
        if (audio == null || audio.isEmpty()) {
            throw invalid("Audio 파일이 필요합니다.");
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(
                    "Audio 파일을 읽을 수 없습니다.",
                    e
            );
        }

        if (bytes.length == 0
                || bytes.length > snapshot.maxAudioFileBytes()) {
            throw invalid("Audio 파일 크기가 허용 범위를 벗어났습니다.");
        }
        if (durationSeconds == null
                || durationSeconds < snapshot.minValidAudioSeconds()
                || durationSeconds > snapshot.maxTurnAudioSeconds()) {
            throw invalid("Audio 길이가 허용 범위를 벗어났습니다.");
        }
        String contentType = contentType(audio);
        if (!contentType.toLowerCase().startsWith("audio/")) {
            throw invalid("지원하지 않는 Audio Content-Type입니다.");
        }
        audioSignatureValidator.validate(bytes, contentType);
        return bytes;
    }

    public void storeUserAudio(
            Long userId,
            SpeakingSession session,
            SpeakingTurn turn,
            MultipartFile audio,
            byte[] bytes,
            double durationSeconds,
            List<AssistanceType> assistanceUsage,
            SpeakingSessionPolicySnapshot snapshot
    ) {
        if (turn.getUserAudioObjectKey() != null) {
            return;
        }

        String objectKey = audioKeyFactory.userTurn(
                userId,
                session.getId(),
                turn.getId(),
                extension(audio.getOriginalFilename())
        );
        audioStoragePort.store(objectKey, bytes, contentType(audio));
        turn.markUploaded(
                objectKey,
                contentType(audio),
                audio.getOriginalFilename(),
                durationSeconds,
                jsonCodec.write(assistanceUsage),
                LocalDateTime.now().plusDays(snapshot.rawAudioRetentionDays())
        );
    }

    public byte[] loadUserAudio(SpeakingTurn turn) {
        if (turn.getUserAudioObjectKey() == null) {
            throw invalid("재시도할 원본 Audio가 없습니다.");
        }
        return audioStoragePort.load(
                turn.getUserAudioObjectKey(),
                turn.getUserAudioContentType()
        ).bytes();
    }

    public String storeAssistantAudio(
            SpeakingSession session,
            SpeakingTurn turn,
            byte[] bytes,
            String contentType
    ) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String key = audioKeyFactory.assistantTurn(
                session.getUser().getId(),
                session.getId(),
                "turn-" + turn.getId()
        );
        audioStoragePort.store(
                key,
                bytes,
                contentType == null ? "audio/wav" : contentType
        );
        return key;
    }

    public String contentType(MultipartFile audio) {
        return audio.getContentType() == null
                ? "application/octet-stream"
                : audio.getContentType();
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) {
            return "bin";
        }
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.INVALID_AUDIO
        );
    }
}
