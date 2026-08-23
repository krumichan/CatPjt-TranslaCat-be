package jp.co.translacat.domain.languagelearning.listening.audio.validator;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeningAudioValidatorTest {

    private final ListeningAudioValidator validator =
            new ListeningAudioValidator();

    @Test
    void acceptsMatchingSha256AndRejectsCorruption() throws Exception {
        byte[] audio = "RIFF0000WAVEdata".getBytes(StandardCharsets.US_ASCII);
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(audio)
        );

        assertThatCode(() -> validator.validateChecksum(audio, checksum))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateChecksum(audio, "deadbeef"))
                .hasMessageContaining("checksum");
    }
}
