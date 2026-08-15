package jp.co.translacat.domain.languagelearning.setting.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import jp.co.translacat.global.jpa.Base;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "language_learning_admin_setting_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanguageLearningAdminSettingAudit extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Lob
    @Column(name = "before_json", nullable = false, columnDefinition = "TEXT")
    private String beforeJson;

    @Lob
    @Column(name = "after_json", nullable = false, columnDefinition = "TEXT")
    private String afterJson;

    private LanguageLearningAdminSettingAudit(
            Long adminUserId,
            String beforeJson,
            String afterJson
    ) {
        this.adminUserId = adminUserId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
    }

    public static LanguageLearningAdminSettingAudit create(
            Long adminUserId,
            String beforeJson,
            String afterJson
    ) {
        return new LanguageLearningAdminSettingAudit(
                adminUserId,
                beforeJson,
                afterJson
        );
    }
}
