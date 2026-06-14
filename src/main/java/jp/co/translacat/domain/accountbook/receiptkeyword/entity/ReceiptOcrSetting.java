package jp.co.translacat.domain.accountbook.receiptkeyword.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "receipt_ocr_setting",
        indexes = {
                @Index(name = "idx_receipt_ocr_setting_currency_code", columnList = "currency_code")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptOcrSetting extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", nullable = false, length = 10, unique = true)
    private String currencyCode;

    @Column(name = "ocr_language", nullable = false, length = 30)
    private String ocrLanguage;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Boolean deleted = false;
}
