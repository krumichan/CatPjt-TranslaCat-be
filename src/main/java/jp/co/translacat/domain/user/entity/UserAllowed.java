package jp.co.translacat.domain.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "user_allowed")
@NoArgsConstructor
public class UserAllowed {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime expiredAt;

    @Column
    private boolean isActive = true;

    public boolean isAccessible() {
        if (!isActive) return false;

        LocalDateTime now = LocalDateTime.now();

        if (startedAt != null && now.isBefore(startedAt)) {
            return false;
        }

        return expiredAt == null || !now.isAfter(expiredAt);
    }
}
