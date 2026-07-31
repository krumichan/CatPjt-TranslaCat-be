package jp.co.translacat.domain.chat.openchat.support;

import jp.co.translacat.global.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class OpenChatProfileValidator {

    private static final int NICKNAME_MAX_LENGTH = 50;
    private static final int OBJECT_KEY_MAX_LENGTH = 500;

    public String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(
                    "OPEN 채팅 닉네임은 필수입니다.",
                    OpenChatErrorCode.NICKNAME_REQUIRED
            );
        }

        String normalized = nickname.trim();
        if (normalized.length() > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(
                    "OPEN 채팅 닉네임은 50자 이하여야 합니다.",
                    OpenChatErrorCode.NICKNAME_TOO_LONG
            );
        }
        return normalized;
    }

    public String normalizeObjectKey(
            String objectKey,
            Long openChatMemberId
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        String normalized = objectKey.trim();
        String expectedPrefix = OpenChatPolicy
                .profileImageObjectKeyPrefix(openChatMemberId);

        if (normalized.length() > OBJECT_KEY_MAX_LENGTH
                || !normalized.startsWith(expectedPrefix)) {
            throw new BusinessException(
                    "OPEN 채팅 프로필 이미지 Object Key가 유효하지 않습니다.",
                    OpenChatErrorCode.PROFILE_IMAGE_OBJECT_KEY_INVALID
            );
        }
        return normalized;
    }
}
