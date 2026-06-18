package jp.co.translacat.domain.accountbook.receiptkeyword.service;

import jp.co.translacat.domain.accountbook.receiptkeyword.dto.*;
import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;
import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptOcrSetting;
import jp.co.translacat.domain.accountbook.receiptkeyword.enums.ReceiptKeywordType;
import jp.co.translacat.domain.accountbook.receiptkeyword.repository.ReceiptKeywordRepository;
import jp.co.translacat.domain.accountbook.receiptkeyword.repository.ReceiptOcrSettingRepository;
import jp.co.translacat.domain.currency.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReceiptAiSettingService {

    private final ReceiptOcrSettingRepository receiptOcrSettingRepository;
    private final ReceiptKeywordRepository receiptKeywordRepository;
    private final CurrencyRepository currencyRepository;

    public List<AdminReceiptOcrSettingResponseDto> getOcrSettings() {
        return receiptOcrSettingRepository
                .findAllByDeletedFalseOrderByCurrencyCodeAsc()
                .stream()
                .map(AdminReceiptOcrSettingResponseDto::from)
                .toList();
    }

    @Transactional
    public AdminReceiptOcrSettingResponseDto updateOcrSetting(
            Long settingId,
            AdminReceiptOcrSettingUpdateRequestDto request
    ) {
        ReceiptOcrSetting setting = receiptOcrSettingRepository
                .findByIdAndDeletedFalse(settingId)
                .orElseThrow(() -> new IllegalArgumentException("OCR 설정을 찾을 수 없습니다."));

        setting.update(
                normalizeOcrLanguage(request.ocrLanguage()),
                request.enabled()
        );

        return AdminReceiptOcrSettingResponseDto.from(setting);
    }

    public List<AdminReceiptKeywordResponseDto> getKeywords() {
        return receiptKeywordRepository
                .findAllByDeletedFalseOrderByOcrLanguageAscCurrencyCodeAscKeywordTypeAscDisplayOrderAscIdAsc()
                .stream()
                .map(AdminReceiptKeywordResponseDto::from)
                .toList();
    }

    @Transactional
    public AdminReceiptKeywordResponseDto createKeyword(
            AdminReceiptKeywordCreateRequestDto request
    ) {
        String currencyCode = normalizeCurrencyCodeOrNull(request.currencyCode());
        String ocrLanguage = normalizeOcrLanguage(request.ocrLanguage());
        ReceiptKeywordType keywordType = request.keywordType();
        String keyword = normalizeKeyword(request.keyword());

        validateCurrencyCodeIfPresent(currencyCode);
        validateDuplicateKeyword(
                null,
                currencyCode,
                ocrLanguage,
                keywordType,
                keyword
        );

        ReceiptKeyword savedKeyword = receiptKeywordRepository.save(
                ReceiptKeyword.create(
                        currencyCode,
                        ocrLanguage,
                        keywordType,
                        keyword,
                        request.enabled() == null || Boolean.TRUE.equals(request.enabled()),
                        request.displayOrder()
                )
        );

        return AdminReceiptKeywordResponseDto.from(savedKeyword);
    }

    @Transactional
    public AdminReceiptKeywordResponseDto updateKeyword(
            Long keywordId,
            AdminReceiptKeywordUpdateRequestDto request
    ) {
        ReceiptKeyword targetKeyword = receiptKeywordRepository
                .findByIdAndDeletedFalse(keywordId)
                .orElseThrow(() -> new IllegalArgumentException("키워드를 찾을 수 없습니다."));

        String currencyCode = normalizeCurrencyCodeOrNull(request.currencyCode());
        String ocrLanguage = normalizeOcrLanguage(request.ocrLanguage());
        ReceiptKeywordType keywordType = request.keywordType();
        String keyword = normalizeKeyword(request.keyword());

        validateCurrencyCodeIfPresent(currencyCode);
        validateDuplicateKeyword(
                keywordId,
                currencyCode,
                ocrLanguage,
                keywordType,
                keyword
        );

        targetKeyword.update(
                currencyCode,
                ocrLanguage,
                keywordType,
                keyword,
                request.enabled(),
                request.displayOrder()
        );

        return AdminReceiptKeywordResponseDto.from(targetKeyword);
    }

    @Transactional
    public Boolean deleteKeyword(Long keywordId) {
        ReceiptKeyword keyword = receiptKeywordRepository
                .findByIdAndDeletedFalse(keywordId)
                .orElseThrow(() -> new IllegalArgumentException("키워드를 찾을 수 없습니다."));

        keyword.delete();

        return true;
    }

    private void validateDuplicateKeyword(
            Long currentKeywordId,
            String currencyCode,
            String ocrLanguage,
            ReceiptKeywordType keywordType,
            String keyword
    ) {
        receiptKeywordRepository
                .findFirstByCurrencyCodeAndOcrLanguageAndKeywordTypeAndKeywordAndDeletedFalse(
                        currencyCode,
                        ocrLanguage,
                        keywordType,
                        keyword
                )
                .filter(existingKeyword -> !existingKeyword.getId().equals(currentKeywordId))
                .ifPresent(existingKeyword -> {
                    throw new IllegalArgumentException("이미 등록된 키워드입니다.");
                });
    }

    private void validateCurrencyCodeIfPresent(String currencyCode) {
        if (currencyCode == null) {
            return;
        }

        if (!currencyRepository.existsByCode(currencyCode)) {
            throw new IllegalArgumentException("등록되지 않은 통화 코드입니다.");
        }
    }

    private String normalizeCurrencyCodeOrNull(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return null;
        }

        return currencyCode.trim().toUpperCase();
    }

    private String normalizeOcrLanguage(String ocrLanguage) {
        if (ocrLanguage == null || ocrLanguage.isBlank()) {
            throw new IllegalArgumentException("OCR 언어는 필수입니다.");
        }

        return ocrLanguage.trim().toLowerCase();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("키워드는 필수입니다.");
        }

        return keyword.trim();
    }
}