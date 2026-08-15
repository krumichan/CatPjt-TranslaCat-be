package jp.co.translacat.domain.languagelearning.speaking.audio.validator;

import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingAudioSignatureValidatorTest {

    private final SpeakingAudioSignatureValidator validator =
            new SpeakingAudioSignatureValidator();

    @Test
    void acceptsWebmSignatureForWebmContentType() {
        byte[] webm = new byte[] {
                0x1A,
                0x45,
                (byte) 0xDF,
                (byte) 0xA3,
                0x00,
                0x00
        };

        assertThatCode(() -> validator.validate(webm, "audio/webm;codecs=opus"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsWavSignatureForWavContentType() {
        byte[] wav = "RIFF0000WAVEfmt ".getBytes(StandardCharsets.US_ASCII);

        assertThatCode(() -> validator.validate(wav, "audio/wav"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMp4SignatureForM4aContentType() {
        byte[] mp4 = new byte[] {
                0x00,
                0x00,
                0x00,
                0x18,
                'f',
                't',
                'y',
                'p',
                'M',
                '4',
                'A',
                ' '
        };

        assertThatCode(() -> validator.validate(mp4, "audio/x-m4a"))
                .doesNotThrowAnyException();
    }


    @Test
    void acceptsAacAdtsSignatureForAacContentType() {
        byte[] aac = new byte[] {
                (byte) 0xFF,
                (byte) 0xF1,
                0x50,
                (byte) 0x80
        };

        assertThatCode(() -> validator.validate(aac, "audio/aac"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFakeAudioEvenWhenContentTypeLooksValid() {
        byte[] fake = "this is not audio".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.validate(fake, "audio/webm"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchDetectedFormat() {
        byte[] wav = "RIFF0000WAVEfmt ".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> validator.validate(wav, "audio/webm"))
                .isInstanceOf(BusinessException.class);
    }
}
