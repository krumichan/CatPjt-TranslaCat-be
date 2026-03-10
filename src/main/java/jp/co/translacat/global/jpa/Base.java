package jp.co.translacat.global.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Slf4j
@Getter
@MappedSuperclass
@NoArgsConstructor
public class Base {

    public Base(String createdBy) {
        this.createdBy = createdBy;
    }

    @Column(length = 50, updatable = false)
    protected String createdBy;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    protected LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (this.createdBy == null) {
            try {
                this.createdBy = SecurityUtil.getUsername();
            } catch (Exception e) {
                this.createdBy = "SYSTEM";
                log.warn("Unable to get current username for createdBy. Falling back to 'SYSTEM'.", e);
            }
        }
    }
}
