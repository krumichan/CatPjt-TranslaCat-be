package jp.co.translacat.global.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Slf4j
@Getter
@MappedSuperclass
public class BaseAuditable extends Base {
    @Column(length = 50)
    protected String updatedBy;

    @Column(nullable = false)
    @UpdateTimestamp
    protected LocalDateTime updatedAt;

    @Override
    protected void prePersist() {
        super.prePersist();
        this.fillUpdatedBy();
    }

    @PreUpdate
    protected void preUpdate() {
        this.fillUpdatedBy();
    }

    private void fillUpdatedBy() {
        try {
            this.updatedBy = SecurityUtil.getUsername();
        } catch (NullPointerException e) {
            log.warn("No authentication found; 'updatedBy' will be set to 'SYSTEM'.");
            this.updatedBy = "SYSTEM";
        } catch (Exception e) {
            log.warn("Unable to get current username for updatedBy. Falling back to 'SYSTEM'.", e);
            this.updatedBy = "SYSTEM";
        }
    }
}
