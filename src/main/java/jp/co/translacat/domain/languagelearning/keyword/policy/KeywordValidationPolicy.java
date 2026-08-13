package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.support.KeywordNormalizer;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

@Component
public class KeywordValidationPolicy {

    private static final int MAX_KEYWORD_LENGTH = 200;

    public void validate(String text, KeywordType type) {
        if (text == null || text.isBlank()) {
            throw invalidKeyword();
        }
        if (text.trim().length() > MAX_KEYWORD_LENGTH) {
            throw invalidKeyword();
        }
        if (type == null) {
            throw invalidKeyword();
        }
    }

    public String normalizeCanonicalKey(
            String value,
            String fallback
    ) {
        String normalized = KeywordNormalizer.normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    public BusinessException duplicateKeyword() {
        return new BusinessException(
                "동일한 Keyword가 이미 존재합니다.",
                LanguageLearningErrorCode.KEYWORD_DUPLICATED
        );
    }

    public BusinessException keywordNotFound() {
        return new BusinessException(
                "Keyword를 찾을 수 없습니다.",
                LanguageLearningErrorCode.KEYWORD_NOT_FOUND
        );
    }

    private BusinessException invalidKeyword() {
        return new BusinessException(
                "Keyword 값이 유효하지 않습니다.",
                LanguageLearningErrorCode.SETTING_INVALID
        );
    }
}
