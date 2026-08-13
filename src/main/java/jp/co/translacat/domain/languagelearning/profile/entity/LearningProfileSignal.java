package jp.co.translacat.domain.languagelearning.profile.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.ProfileSignalType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_profile_signal",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_profile_signal",
                columnNames = {"user_id", "signal_type", "signal_key"}
        ),
        indexes = @Index(
                name = "idx_ll_profile_signal_user_type",
                columnList = "user_id,signal_type"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningProfileSignal extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 40)
    private ProfileSignalType type;

    @Column(name = "signal_key", nullable = false, length = 300)
    private String key;

    @Column(nullable = false)
    private int occurrenceCount;

    private LocalDateTime lastSeenAt;

    private LearningProfileSignal(
            User user,
            ProfileSignalType type,
            String key
    ) {
        this.user = user;
        this.type = type;
        this.key = key;
        this.occurrenceCount = 1;
        this.lastSeenAt = LocalDateTime.now();
    }

    public static LearningProfileSignal create(
            User user,
            ProfileSignalType type,
            String key
    ) {
        return new LearningProfileSignal(
                user,
                type,
                key
        );
    }

    public void touch() {
        this.occurrenceCount++;
        this.lastSeenAt = LocalDateTime.now();
    }
}
