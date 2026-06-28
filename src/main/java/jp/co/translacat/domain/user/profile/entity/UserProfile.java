package jp.co.translacat.domain.user.profile.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "user_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_profile_user_id", columnNames = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseAuditable {

    public static final int NICKNAME_MAX_LENGTH = 30;
    public static final int PROFILE_IMAGE_URL_MAX_LENGTH = 500;
    public static final int BIO_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = NICKNAME_MAX_LENGTH)
    private String nickname;

    @Column(name = "profile_image_url", length = PROFILE_IMAGE_URL_MAX_LENGTH)
    private String profileImageUrl;

    @Column(length = BIO_MAX_LENGTH)
    private String bio;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private UserProfile(
            User user,
            String nickname,
            String profileImageUrl,
            String bio
    ) {
        this.user = user;
        this.nickname = validateNickname(nickname);
        this.profileImageUrl = validateProfileImageUrl(profileImageUrl);
        this.bio = validateBio(bio);
    }

    public static UserProfile createDefault(User user) {
        return new UserProfile(
                user,
                buildDefaultNickname(user),
                null,
                null
        );
    }

    public void update(
            String nickname,
            String profileImageUrl,
            String bio
    ) {
        this.nickname = validateNickname(nickname);
        this.profileImageUrl = validateProfileImageUrl(profileImageUrl);
        this.bio = validateBio(bio);
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    private static String buildDefaultNickname(User user) {
        if (hasText(user.getUsername())) {
            return trimToMax(user.getUsername(), NICKNAME_MAX_LENGTH);
        }

        if (hasText(user.getEmail())) {
            String emailPrefix = user.getEmail().split("@")[0];
            if (hasText(emailPrefix)) {
                return trimToMax(emailPrefix, NICKNAME_MAX_LENGTH);
            }
        }

        if (hasText(user.getPublicId())) {
            return trimToMax(user.getPublicId(), NICKNAME_MAX_LENGTH);
        }

        return "TranslaCat User";
    }

    private static String validateNickname(String nickname) {
        if (!hasText(nickname)) {
            throw new BusinessException(
                    "닉네임은 필수입니다.",
                    "USER_PROFILE_NICKNAME_REQUIRED"
            );
        }

        String trimmed = nickname.trim();

        if (trimmed.length() > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(
                    "닉네임은 " + NICKNAME_MAX_LENGTH + "자 이하로 입력해주세요.",
                    "USER_PROFILE_NICKNAME_TOO_LONG"
            );
        }

        return trimmed;
    }

    private static String validateProfileImageUrl(String profileImageUrl) {
        if (!hasText(profileImageUrl)) {
            return null;
        }

        String trimmed = profileImageUrl.trim();

        if (trimmed.length() > PROFILE_IMAGE_URL_MAX_LENGTH) {
            throw new BusinessException(
                    "프로필 이미지 URL은 " + PROFILE_IMAGE_URL_MAX_LENGTH + "자 이하로 입력해주세요.",
                    "USER_PROFILE_IMAGE_URL_TOO_LONG"
            );
        }

        return trimmed;
    }

    private static String validateBio(String bio) {
        if (!hasText(bio)) {
            return null;
        }

        String trimmed = bio.trim();

        if (trimmed.length() > BIO_MAX_LENGTH) {
            throw new BusinessException(
                    "자기소개는 " + BIO_MAX_LENGTH + "자 이하로 입력해주세요.",
                    "USER_PROFILE_BIO_TOO_LONG"
            );
        }

        return trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToMax(String value, int maxLength) {
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}