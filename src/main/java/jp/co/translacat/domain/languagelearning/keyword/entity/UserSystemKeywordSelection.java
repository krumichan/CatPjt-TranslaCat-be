package jp.co.translacat.domain.languagelearning.keyword.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Table(
        name = "language_learning_user_system_keyword",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_user_system_keyword",
                columnNames = {"user_id", "system_keyword_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSystemKeywordSelection extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_keyword_id", nullable = false, updatable = false)
    private SystemKeyword systemKeyword;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "available_from", nullable = false)
    private LocalDate availableFrom;

    @Column(name = "pending_active")
    private Boolean pendingActive;

    @Column(name = "pending_effective_date")
    private LocalDate pendingEffectiveDate;

    private UserSystemKeywordSelection(
            User user,
            SystemKeyword systemKeyword,
            LocalDate effectiveDate
    ) {
        this.user = user;
        this.systemKeyword = systemKeyword;
        this.active = false;
        this.availableFrom = effectiveDate;
        this.pendingActive = true;
        this.pendingEffectiveDate = effectiveDate;
    }

    public static UserSystemKeywordSelection create(
            User user,
            SystemKeyword systemKeyword,
            LocalDate effectiveDate
    ) {
        return new UserSystemKeywordSelection(
                user,
                systemKeyword,
                effectiveDate
        );
    }

    public void scheduleActive(
            boolean active,
            LocalDate effectiveDate
    ) {
        this.pendingActive = active;
        this.pendingEffectiveDate = effectiveDate;
    }

    public boolean promoteIfEffective(LocalDate date) {
        if (pendingEffectiveDate == null || pendingEffectiveDate.isAfter(date)) {
            return false;
        }

        this.active = Boolean.TRUE.equals(pendingActive);
        this.pendingActive = null;
        this.pendingEffectiveDate = null;

        return true;
    }

    public boolean desiredActive() {
        return pendingActive == null ? active : pendingActive;
    }
}
