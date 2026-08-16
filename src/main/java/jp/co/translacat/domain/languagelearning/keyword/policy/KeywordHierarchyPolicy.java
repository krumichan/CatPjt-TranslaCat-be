package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

@Component
public class KeywordHierarchyPolicy {

    public void validateSystemHierarchy(
            KeywordType type,
            SystemKeyword parentKeyword,
            Long currentKeywordId
    ) {
        validateCommon(type, parentKeyword, true, currentKeywordId);
    }

    public void validateCustomHierarchy(
            KeywordType type,
            SystemKeyword parentKeyword
    ) {
        validateCommon(type, parentKeyword, false, null);
    }

    public int normalizeSortOrder(Integer sortOrder, int fallback) {
        int value = sortOrder == null ? fallback : sortOrder;
        if (value < 0) {
            throw invalidHierarchy();
        }
        return value;
    }

    public BusinessException invalidHierarchy() {
        return new BusinessException(
                "Keyword 계층 구조가 유효하지 않습니다.",
                LanguageLearningErrorCode.KEYWORD_HIERARCHY_INVALID
        );
    }

    private void validateCommon(
            KeywordType type,
            SystemKeyword parentKeyword,
            boolean parentRequiredForVocabulary,
            Long currentKeywordId
    ) {
        if (type == KeywordType.TOPIC) {
            if (parentKeyword != null) {
                throw invalidHierarchy();
            }
            return;
        }

        if (parentKeyword == null) {
            if (parentRequiredForVocabulary) {
                throw invalidHierarchy();
            }
            return;
        }

        if (!parentKeyword.isActive()
                || parentKeyword.getType() != KeywordType.TOPIC
                || parentKeyword.getParentKeyword() != null
                || (currentKeywordId != null
                        && currentKeywordId.equals(parentKeyword.getId()))) {
            throw invalidHierarchy();
        }
    }
}
