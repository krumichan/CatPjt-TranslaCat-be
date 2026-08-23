package jp.co.translacat.domain.languagelearning.listening.audio.validator;

import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class ListeningAudioValidator {

    public void validate(
            byte[] bytes,
            String contentType,
            long maxBytes,
            int durationMs,
            int maxSeconds
    ) {
        if (bytes == null || bytes.length < 4 || bytes.length > maxBytes) {
            throw invalid("Listening Audio 크기가 허용 범위를 벗어났습니다.");
        }
        if (durationMs <= 0 || durationMs > maxSeconds * 1000L) {
            throw invalid("Listening Audio 길이가 허용 범위를 벗어났습니다.");
        }
        Format format = detect(bytes);
        if (format == Format.UNKNOWN || !format.matches(contentType)) {
            throw invalid(
                    "Listening Audio 형식과 Content-Type이 일치하지 않습니다."
            );
        }
    }

    public void validateChecksum(byte[] bytes, String expectedChecksum) {
        if (bytes == null || expectedChecksum == null
                || expectedChecksum.isBlank()
                || !checksum(bytes).equalsIgnoreCase(expectedChecksum)) {
            throw invalid("Listening Audio checksum이 일치하지 않습니다.");
        }
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private Format detect(byte[] bytes) {
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF")
                && ascii(bytes, 8, "WAVE")) {
            return Format.WAV;
        }
        if (ascii(bytes, 0, "OggS")) {
            return Format.OGG;
        }
        if ((bytes[0] & 0xff) == 0x1a
                && (bytes[1] & 0xff) == 0x45
                && (bytes[2] & 0xff) == 0xdf
                && (bytes[3] & 0xff) == 0xa3) {
            return Format.WEBM;
        }
        if (ascii(bytes, 0, "ID3")
                || ((bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xe0) == 0xe0)) {
            return Format.MP3;
        }
        if (bytes.length >= 12 && ascii(bytes, 4, "ftyp")) {
            return Format.MP4;
        }
        if (ascii(bytes, 0, "fLaC")) {
            return Format.FLAC;
        }
        return Format.UNKNOWN;
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.LISTENING_AUDIO_INVALID
        );
    }

    private enum Format {
        WAV("audio/wav", "audio/x-wav", "audio/vnd.wave"),
        OGG("audio/ogg"),
        WEBM("audio/webm"),
        MP3("audio/mpeg", "audio/mp3"),
        MP4("audio/mp4", "audio/m4a", "audio/x-m4a"),
        FLAC("audio/flac", "audio/x-flac"),
        UNKNOWN();

        private final String[] contentTypes;

        Format(String... contentTypes) {
            this.contentTypes = contentTypes;
        }

        private boolean matches(String contentType) {
            if (contentType == null) {
                return false;
            }
            String normalized = contentType.toLowerCase(Locale.ROOT)
                    .split(";", 2)[0].trim();
            for (String supported : contentTypes) {
                if (supported.equals(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }
}
