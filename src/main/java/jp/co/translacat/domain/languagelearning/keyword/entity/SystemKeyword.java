package jp.co.translacat.domain.languagelearning.keyword.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_system_keyword",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_system_keyword_normalized_type",
                columnNames = {"normalized_text", "keyword_type"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemKeyword extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(name = "normalized_text", nullable = false, length = 200)
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "keyword_type", nullable = false, length = 30)
    private KeywordType type;

    @Column(name = "canonical_key", length = 200)
    private String canonicalKey;

    @Column(nullable = false)
    private boolean active = true;

    private SystemKeyword(
            String text,
            String normalizedText,
            KeywordType type,
            String canonicalKey
    ) {
        this.text = text;
        this.normalizedText = normalizedText;
        this.type = type;
        this.canonicalKey = canonicalKey;
    }

    public static SystemKeyword create(
            String text,
            String normalizedText,
            KeywordType type,
            String canonicalKey
    ) {
        return new SystemKeyword(
                text,
                normalizedText,
                type,
                canonicalKey
        );
    }

    public void update(
            String text,
            String normalizedText,
            KeywordType type,
            String canonicalKey,
            Boolean active
    ) {
        this.text = text;
        this.normalizedText = normalizedText;
        this.type = type;
        this.canonicalKey = canonicalKey;

        if (active != null) {
            this.active = active;
        }
    }
}
