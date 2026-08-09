package jp.co.translacat.domain.chat.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_ai_agent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatAiAgent extends BaseAuditable {

    public static final int NICKNAME_MAX_LENGTH = 50;
    public static final int IMAGE_OBJECT_KEY_MAX_LENGTH = 500;
    public static final int BIO_MAX_LENGTH = 200;
    public static final int LANGUAGE_CODE_MAX_LENGTH = 10;
    public static final int PERSONA_PROMPT_MAX_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NICKNAME_MAX_LENGTH)
    private String nickname;

    @Column(name = "profile_image_object_key", length = IMAGE_OBJECT_KEY_MAX_LENGTH)
    private String profileImageObjectKey;

    @Column(name = "profile_background_image_object_key", length = IMAGE_OBJECT_KEY_MAX_LENGTH)
    private String profileBackgroundImageObjectKey;

    @Column(length = BIO_MAX_LENGTH)
    private String bio;

    @Column(name = "original_language_code", nullable = false, length = LANGUAGE_CODE_MAX_LENGTH)
    private String originalLanguageCode;

    @Column(name = "persona_prompt", nullable = false, columnDefinition = "TEXT")
    private String personaPrompt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private ChatAiAgent(
            String nickname,
            String bio,
            String originalLanguageCode,
            String personaPrompt
    ) {
        updateProfile(
                nickname,
                bio,
                originalLanguageCode,
                personaPrompt
        );
    }

    public static ChatAiAgent create(
            String nickname,
            String bio,
            String originalLanguageCode,
            String personaPrompt
    ) {
        return new ChatAiAgent(
                nickname,
                bio,
                originalLanguageCode,
                personaPrompt
        );
    }

    public void updateProfile(
            String nickname,
            String bio,
            String originalLanguageCode,
            String personaPrompt
    ) {
        this.nickname = normalizeRequired(
                nickname,
                NICKNAME_MAX_LENGTH,
                "AI 닉네임은 필수입니다.",
                ChatAiErrorCode.NICKNAME_REQUIRED,
                "AI 닉네임은 " + NICKNAME_MAX_LENGTH + "자 이하여야 합니다.",
                ChatAiErrorCode.NICKNAME_TOO_LONG
        );
        this.bio = normalizeOptional(
                bio,
                BIO_MAX_LENGTH,
                "AI 자기소개는 " + BIO_MAX_LENGTH + "자 이하여야 합니다.",
                ChatAiErrorCode.BIO_TOO_LONG
        );
        this.originalLanguageCode = normalizeRequired(
                originalLanguageCode,
                LANGUAGE_CODE_MAX_LENGTH,
                "AI 원문 언어는 필수입니다.",
                ChatAiErrorCode.LANGUAGE_REQUIRED,
                "AI 원문 언어 코드는 " + LANGUAGE_CODE_MAX_LENGTH + "자 이하여야 합니다.",
                ChatAiErrorCode.LANGUAGE_TOO_LONG
        ).toLowerCase();
        this.personaPrompt = normalizeRequired(
                personaPrompt,
                PERSONA_PROMPT_MAX_LENGTH,
                "AI personaPrompt는 필수입니다.",
                ChatAiErrorCode.PERSONA_REQUIRED,
                "AI personaPrompt는 " + PERSONA_PROMPT_MAX_LENGTH + "자 이하여야 합니다.",
                ChatAiErrorCode.PERSONA_TOO_LONG
        );
    }

    public String replaceProfileImageObjectKey(String objectKey) {
        String oldObjectKey = this.profileImageObjectKey;
        this.profileImageObjectKey = normalizeObjectKey(objectKey);
        return oldObjectKey;
    }

    public String replaceProfileBackgroundImageObjectKey(String objectKey) {
        String oldObjectKey = this.profileBackgroundImageObjectKey;
        this.profileBackgroundImageObjectKey = normalizeObjectKey(objectKey);
        return oldObjectKey;
    }

    public String clearProfileImage() {
        String oldObjectKey = this.profileImageObjectKey;
        this.profileImageObjectKey = null;
        return oldObjectKey;
    }

    public String clearProfileBackgroundImage() {
        String oldObjectKey = this.profileBackgroundImageObjectKey;
        this.profileBackgroundImageObjectKey = null;
        return oldObjectKey;
    }

    public void softDelete() {
        this.active = false;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    private static String normalizeObjectKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > IMAGE_OBJECT_KEY_MAX_LENGTH) {
            throw new BusinessException(
                    "AI 이미지 Object Key 길이가 허용 범위를 초과했습니다.",
                    ChatAiErrorCode.IMAGE_MEMBER_PATH_INVALID
            );
        }
        return normalized;
    }

    private static String normalizeRequired(
            String value,
            int maxLength,
            String requiredMessage,
            String requiredCode,
            String tooLongMessage,
            String tooLongCode
    ) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(requiredMessage, requiredCode);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(tooLongMessage, tooLongCode);
        }
        return normalized;
    }

    private static String normalizeOptional(
            String value,
            int maxLength,
            String tooLongMessage,
            String tooLongCode
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(tooLongMessage, tooLongCode);
        }
        return normalized;
    }
}
