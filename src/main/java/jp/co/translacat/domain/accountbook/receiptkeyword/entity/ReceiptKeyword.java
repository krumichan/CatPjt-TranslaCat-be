package jp.co.translacat.domain.accountbook.receiptkeyword.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.accountbook.receiptkeyword.enums.ReceiptKeywordType;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "receipt_keyword",
        indexes = {
                @Index(
                        name = "idx_receipt_keyword_lookup",
                        columnList = "currency_code, ocr_language, keyword_type, enabled, deleted"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptKeyword extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "ocr_language", nullable = false, length = 30)
    private String ocrLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "keyword_type", nullable = false, length = 30)
    private ReceiptKeywordType keywordType;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean deleted = false;

    private ReceiptKeyword(
            String currencyCode,
            String ocrLanguage,
            ReceiptKeywordType keywordType,
            String keyword,
            Boolean enabled,
            Integer displayOrder
    ) {
        this.currencyCode = currencyCode;
        this.ocrLanguage = ocrLanguage;
        this.keywordType = keywordType;
        this.keyword = keyword;
        this.enabled = Boolean.TRUE.equals(enabled);
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
    }

    public static ReceiptKeyword create(
            String currencyCode,
            String ocrLanguage,
            ReceiptKeywordType keywordType,
            String keyword,
            Boolean enabled,
            Integer displayOrder
    ) {
        return new ReceiptKeyword(
                currencyCode,
                ocrLanguage,
                keywordType,
                keyword,
                enabled,
                displayOrder
        );
    }

    public void update(
            String currencyCode,
            String ocrLanguage,
            ReceiptKeywordType keywordType,
            String keyword,
            Boolean enabled,
            Integer displayOrder
    ) {
        this.currencyCode = currencyCode;
        this.ocrLanguage = ocrLanguage;
        this.keywordType = keywordType;
        this.keyword = keyword;
        this.enabled = Boolean.TRUE.equals(enabled);
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
    }

    public void delete() {
        this.deleted = true;
    }
}