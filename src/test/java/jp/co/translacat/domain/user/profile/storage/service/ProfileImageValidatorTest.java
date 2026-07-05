package jp.co.translacat.domain.user.profile.storage.service;

import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.domain.user.profile.storage.model.ValidatedImage;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileImageValidatorTest {

    private final ProfileImageValidator validator =
            new ProfileImageValidator();

    @Test
    void pngSignature를_검증하고_png_확장자를_반환한다() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00
        };

        ValidatedImage result = validator.validate(
                new ProfileImageUploadFile(
                        "profile.png",
                        "image/png",
                        png
                ),
                ProfileImageType.PROFILE
        );

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
    }

    @Test
    void 선언_MIME과_실제_바이너리가_다르면_실패한다() {
        byte[] jpeg = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };

        assertThatThrownBy(() ->
                validator.validate(
                        new ProfileImageUploadFile(
                                "fake.png",
                                "image/png",
                                jpeg
                        ),
                        ProfileImageType.PROFILE
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("PROFILE_IMAGE_CONTENT_TYPE_MISMATCH");
    }

    @Test
    void 지원하지_않는_바이너리는_실패한다() {
        byte[] invalid = {0x01, 0x02, 0x03, 0x04};

        assertThatThrownBy(() ->
                validator.validate(
                        new ProfileImageUploadFile(
                                "profile.gif",
                                "image/gif",
                                invalid
                        ),
                        ProfileImageType.PROFILE
                )
        )
                .isInstanceOf(BusinessException.class);
    }
}
