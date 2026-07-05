package jp.co.translacat.domain.user.profile.storage.service;

import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.domain.user.profile.storage.model.ValidatedImage;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class ProfileImageValidator {

    private static final Set<String> ALLOWED_DECLARED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/octet-stream"
    );

    public ValidatedImage validate(
            ProfileImageUploadFile file,
            ProfileImageType imageType
    ) {
        if (file == null || file.bytes() == null || file.bytes().length == 0) {
            throw new BusinessException(
                    "이미지 파일은 필수입니다.",
                    "PROFILE_IMAGE_FILE_REQUIRED"
            );
        }

        if (file.bytes().length > imageType.getMaxBytes()) {
            throw new BusinessException(
                    "이미지 파일 용량이 허용 범위를 초과했습니다.",
                    "PROFILE_IMAGE_FILE_TOO_LARGE"
            );
        }

        DetectedImageFormat detectedFormat = detect(file.bytes());
        validateDeclaredContentType(file.contentType(), detectedFormat);

        return new ValidatedImage(
                detectedFormat.contentType(),
                detectedFormat.extension(),
                file.bytes()
        );
    }

    private void validateDeclaredContentType(
            String declaredContentType,
            DetectedImageFormat detectedFormat
    ) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return;
        }

        String normalized = declaredContentType
                .toLowerCase(Locale.ROOT)
                .split(";")[0]
                .trim();

        if (!ALLOWED_DECLARED_CONTENT_TYPES.contains(normalized)) {
            throw new BusinessException(
                    "지원하지 않는 이미지 형식입니다. JPEG, PNG, WEBP만 사용할 수 있습니다.",
                    "PROFILE_IMAGE_UNSUPPORTED_CONTENT_TYPE"
            );
        }

        if (!"application/octet-stream".equals(normalized)
                && !detectedFormat.contentType().equals(normalized)) {
            throw new BusinessException(
                    "파일의 Content-Type과 실제 이미지 형식이 일치하지 않습니다.",
                    "PROFILE_IMAGE_CONTENT_TYPE_MISMATCH"
            );
        }
    }

    private DetectedImageFormat detect(byte[] bytes) {
        if (isJpeg(bytes)) {
            return new DetectedImageFormat("image/jpeg", "jpg");
        }

        if (isPng(bytes)) {
            return new DetectedImageFormat("image/png", "png");
        }

        if (isWebp(bytes)) {
            return new DetectedImageFormat("image/webp", "webp");
        }

        throw new BusinessException(
                "지원하지 않거나 손상된 이미지 파일입니다. JPEG, PNG, WEBP만 사용할 수 있습니다.",
                "PROFILE_IMAGE_INVALID_BINARY"
        );
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = {
                0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        if (bytes.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (unsigned(bytes[i]) != signature[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private record DetectedImageFormat(
            String contentType,
            String extension
    ) {
    }
}
