package jp.co.translacat.domain.chat.language.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "user_chat_language_setting",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_chat_language_setting_user",
                        columnNames = "user_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_user_chat_language_setting_user",
                        columnList = "user_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserChatLanguageSetting extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String originalLanguageCode;

    @Column(nullable = false, length = 10)
    private String translationLanguageCode;

    @Column(nullable = false)
    private boolean showOriginal = true;

    @Column(nullable = false)
    private boolean showTranslation = true;

    private UserChatLanguageSetting(
            User user,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        this.user = user;
        this.originalLanguageCode = originalLanguageCode;
        this.translationLanguageCode = translationLanguageCode;
        this.showOriginal = showOriginal;
        this.showTranslation = showTranslation;
    }

    public static UserChatLanguageSetting create(
            User user,
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        return new UserChatLanguageSetting(
                user,
                originalLanguageCode,
                translationLanguageCode,
                showOriginal,
                showTranslation
        );
    }

    public void update(
            String originalLanguageCode,
            String translationLanguageCode,
            boolean showOriginal,
            boolean showTranslation
    ) {
        this.originalLanguageCode = originalLanguageCode;
        this.translationLanguageCode = translationLanguageCode;
        this.showOriginal = showOriginal;
        this.showTranslation = showTranslation;
    }
}
