package jp.co.translacat.domain.accountbook.receiptkeyword.repository;

import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptOcrSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptOcrSettingRepository extends JpaRepository<ReceiptOcrSetting, Long> {

    Optional<ReceiptOcrSetting> findFirstByCurrencyCodeAndEnabledTrueAndDeletedFalse(
            String currencyCode
    );
}