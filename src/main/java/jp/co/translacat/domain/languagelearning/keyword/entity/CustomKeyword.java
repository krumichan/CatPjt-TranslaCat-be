package jp.co.translacat.domain.languagelearning.keyword.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(
        name = "language_learning_custom_keyword",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_custom_keyword_user_normalized_type",
                columnNames = {"user_id", "normalized_text", "keyword_type"}
        ),
        indexes = @Index(
                name = "idx_ll_custom_keyword_user_active",
                columnList = "user_id,active"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomKeyword extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(name = "normalized_text", nullable = false, length = 200)
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "keyword_type", nullable = false, length = 30)
    private KeywordType type;

    @Column(name = "canonical_key", length = 200)
    private String canonicalKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_system_keyword_id")
    private SystemKeyword parentSystemKeyword;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "available_from", nullable = false)
    private LocalDate availableFrom;

    @Column(name = "pending_text", length = 200)
    private String pendingText;

    @Column(name = "pending_normalized_text", length = 200)
    private String pendingNormalizedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_keyword_type", length = 30)
    private KeywordType pendingType;

    @Column(name = "pending_canonical_key", length = 200)
    private String pendingCanonicalKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_parent_system_keyword_id")
    private SystemKeyword pendingParentSystemKeyword;

    @Column(name = "pending_parent_changed", nullable = false)
    private boolean pendingParentChanged;

    @Column(name = "pending_active")
    private Boolean pendingActive;

    @Column(name = "pending_effective_date")
    private LocalDate pendingEffectiveDate;

    private CustomKeyword(
            User user,
            String text,
            String normalizedText,
            KeywordType type,
            String canonicalKey,
            SystemKeyword parentSystemKeyword,
            LocalDate effectiveDate
    ) {
        this.user = user;
        this.text = text;
        this.normalizedText = normalizedText;
        this.type = type;
        this.canonicalKey = canonicalKey;
        this.parentSystemKeyword = parentSystemKeyword;
        this.active = false;
        this.availableFrom = effectiveDate;
        this.pendingActive = true;
        this.pendingEffectiveDate = effectiveDate;
    }

    public static CustomKeyword create(
            User user,
            String text,
            String normalizedText,
            KeywordType type,
            String canonicalKey,
            SystemKeyword parentSystemKeyword,
            LocalDate effectiveDate
    ) {
        return new CustomKeyword(
                user,
                text,
                normalizedText,
                type,
                canonicalKey,
                parentSystemKeyword,
                effectiveDate
        );
    }

    public void scheduleUpdate(
            String text,
            String normalizedText,
            KeywordType type,
            String canonicalKey,
            SystemKeyword parentSystemKeyword,
            Boolean active,
            LocalDate effectiveDate
    ) {
        this.pendingText = text;
        this.pendingNormalizedText = normalizedText;
        this.pendingType = type;
        this.pendingCanonicalKey = canonicalKey;
        this.pendingParentSystemKeyword = parentSystemKeyword;
        this.pendingParentChanged = true;
        this.pendingActive = active == null ? desiredActive() : active;
        this.pendingEffectiveDate = effectiveDate;
    }

    public boolean promoteIfEffective(LocalDate date) {
        if (pendingEffectiveDate == null || pendingEffectiveDate.isAfter(date)) {
            return false;
        }

        if (pendingText != null) {
            this.text = pendingText;
        }
        if (pendingNormalizedText != null) {
            this.normalizedText = pendingNormalizedText;
        }
        if (pendingType != null) {
            this.type = pendingType;
        }
        if (pendingCanonicalKey != null) {
            this.canonicalKey = pendingCanonicalKey;
        }
        if (pendingParentChanged) {
            this.parentSystemKeyword = pendingParentSystemKeyword;
        }
        if (pendingActive != null) {
            this.active = pendingActive;
        }

        clearPendingValues();
        return true;
    }

    public boolean desiredActive() {
        return pendingActive == null ? active : pendingActive;
    }

    public String desiredText() {
        return pendingText == null ? text : pendingText;
    }

    public KeywordType desiredType() {
        return pendingType == null ? type : pendingType;
    }

    public String desiredCanonicalKey() {
        return pendingCanonicalKey == null
                ? canonicalKey
                : pendingCanonicalKey;
    }

    public String desiredNormalizedText() {
        return pendingNormalizedText == null
                ? normalizedText
                : pendingNormalizedText;
    }

    public SystemKeyword desiredParentSystemKeyword() {
        return pendingParentChanged
                ? pendingParentSystemKeyword
                : parentSystemKeyword;
    }

    private void clearPendingValues() {
        this.pendingText = null;
        this.pendingNormalizedText = null;
        this.pendingType = null;
        this.pendingCanonicalKey = null;
        this.pendingParentSystemKeyword = null;
        this.pendingParentChanged = false;
        this.pendingActive = null;
        this.pendingEffectiveDate = null;
    }
}
