package jp.co.translacat.domain.languagelearning.speaking.audio.validator;

import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class SpeakingAudioSignatureValidator {

    private static final int MIN_SIGNATURE_BYTES = 4;

    public void validate(byte[] bytes, String contentType) {
        AudioFormat detected = detect(bytes);
        if (detected == AudioFormat.UNKNOWN) {
            throw invalid("Audio 파일 형식을 확인할 수 없습니다.");
        }

        if (!detected.matches(contentType)) {
            throw invalid("Audio Content-Type과 실제 파일 형식이 일치하지 않습니다.");
        }
    }

    AudioFormat detect(byte[] bytes) {
        if (bytes == null || bytes.length < MIN_SIGNATURE_BYTES) {
            return AudioFormat.UNKNOWN;
        }
        if (isWav(bytes)) {
            return AudioFormat.WAV;
        }
        if (startsWith(bytes, "OggS".getBytes(StandardCharsets.US_ASCII))) {
            return AudioFormat.OGG;
        }
        if (startsWith(bytes, new byte[] {
                0x1A,
                0x45,
                (byte) 0xDF,
                (byte) 0xA3
        })) {
            return AudioFormat.WEBM;
        }
        if (isAacAdts(bytes)) {
            return AudioFormat.AAC;
        }
        if (isMp3(bytes)) {
            return AudioFormat.MP3;
        }
        if (isMp4(bytes)) {
            return AudioFormat.MP4;
        }
        if (startsWith(bytes, "fLaC".getBytes(StandardCharsets.US_ASCII))) {
            return AudioFormat.FLAC;
        }
        return AudioFormat.UNKNOWN;
    }

    private boolean isWav(byte[] bytes) {
        return bytes.length >= 12
                && asciiEquals(bytes, 0, "RIFF")
                && asciiEquals(bytes, 8, "WAVE");
    }

    private boolean isMp3(byte[] bytes) {
        if (bytes.length >= 3 && asciiEquals(bytes, 0, "ID3")) {
            return true;
        }
        if (bytes.length < 2) {
            return false;
        }
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        int layerBits = (second >> 1) & 0x03;
        return first == 0xFF
                && (second & 0xE0) == 0xE0
                && layerBits != 0;
    }

    private boolean isMp4(byte[] bytes) {
        return bytes.length >= 12 && asciiEquals(bytes, 4, "ftyp");
    }

    private boolean isAacAdts(byte[] bytes) {
        if (bytes.length < 2) {
            return false;
        }
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 0xFF && (second & 0xF6) == 0xF0;
    }

    private boolean asciiEquals(byte[] bytes, int offset, String expected) {
        byte[] signature = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.INVALID_AUDIO
        );
    }

    enum AudioFormat {
        WAV("audio/wav", "audio/x-wav", "audio/vnd.wave"),
        OGG("audio/ogg"),
        WEBM("audio/webm"),
        MP3("audio/mpeg", "audio/mp3"),
        MP4("audio/mp4", "audio/m4a", "audio/x-m4a"),
        AAC("audio/aac", "audio/x-aac"),
        FLAC("audio/flac", "audio/x-flac"),
        UNKNOWN();

        private final String[] contentTypes;

        AudioFormat(String... contentTypes) {
            this.contentTypes = contentTypes;
        }

        boolean matches(String contentType) {
            if (this == UNKNOWN || contentType == null) {
                return false;
            }
            String normalized = contentType
                    .toLowerCase(Locale.ROOT)
                    .split(";", 2)[0]
                    .trim();
            for (String supported : contentTypes) {
                if (supported.equals(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }
}
