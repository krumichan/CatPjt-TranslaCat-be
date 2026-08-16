package jp.co.translacat.domain.languagelearning.keyword.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_system_keyword_locale",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_system_keyword_locale",
                columnNames = {"system_keyword_id", "locale"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemKeywordLocale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_keyword_id", nullable = false)
    private Long systemKeywordId;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    private SystemKeywordLocale(
            Long systemKeywordId,
            String locale,
            String displayName
    ) {
        this.systemKeywordId = systemKeywordId;
        this.locale = locale;
        this.displayName = displayName;
    }

    public static SystemKeywordLocale of(
            Long systemKeywordId,
            String locale,
            String displayName
    ) {
        return new SystemKeywordLocale(
                systemKeywordId,
                locale,
                displayName
        );
    }
}
