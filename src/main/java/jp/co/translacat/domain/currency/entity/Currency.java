package jp.co.translacat.domain.currency.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "currency")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Currency extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JPY, KRW, USD
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    // Japanese Yen, Korean Won
    @Column(nullable = false, length = 100)
    private String name;

    // ¥, ₩, $
    @Column(length = 10)
    private String symbol;

    @Column(nullable = false)
    private Integer decimalPlaces;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean baseCurrency = false;

    private Currency(
            String code,
            String name,
            String symbol,
            Integer decimalPlaces,
            boolean baseCurrency
    ) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
        this.baseCurrency = baseCurrency;
    }

    public static Currency create(
            String code,
            String name,
            String symbol,
            Integer decimalPlaces,
            boolean baseCurrency
    ) {
        return new Currency(
                code,
                name,
                symbol,
                decimalPlaces,
                baseCurrency
        );
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setBaseCurrency() {
        this.baseCurrency = true;
    }

    public void unsetBaseCurrency() {
        this.baseCurrency = false;
    }
}