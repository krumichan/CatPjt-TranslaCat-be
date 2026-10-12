package jp.co.translacat.domain.user.auth.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "auth_email_quota")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationQuota {
    @Id
    private Long id;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Column(name = "reserved_count", nullable = false)
    private int reservedCount;

    void resetIfNewDay(LocalDate today) {
        // 자정 전에 대기하던 요청이 늦게 잠금을 얻어도 새 날짜의 발송 카운터를 되돌리지 않는다.
        if (today.isAfter(quotaDate)) {
            quotaDate = today;
            reservedCount = 0;
        }
    }

    void reserve() {
        reservedCount++;
    }
}
