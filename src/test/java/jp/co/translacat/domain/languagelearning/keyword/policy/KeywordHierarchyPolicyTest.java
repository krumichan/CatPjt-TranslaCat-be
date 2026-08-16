package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordHierarchyPolicyTest {

    private final KeywordHierarchyPolicy policy = new KeywordHierarchyPolicy();

    @Test
    void systemTopicCanBeRootAndVocabularyRequiresTopicParent() {
        SystemKeyword topic = keyword(
                "IT",
                KeywordType.TOPIC,
                null,
                10
        );

        assertThatCode(() -> policy.validateSystemHierarchy(
                KeywordType.TOPIC,
                null,
                null
        )).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateSystemHierarchy(
                KeywordType.VOCABULARY,
                topic,
                null
        )).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateSystemHierarchy(
                KeywordType.VOCABULARY,
                null,
                null
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void hierarchyIsLimitedToTwoLevels() {
        SystemKeyword topic = keyword(
                "IT",
                KeywordType.TOPIC,
                null,
                10
        );
        SystemKeyword vocabulary = keyword(
                "Deployment",
                KeywordType.VOCABULARY,
                topic,
                11
        );

        assertThatThrownBy(() -> policy.validateCustomHierarchy(
                KeywordType.VOCABULARY,
                vocabulary
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void customVocabularyMayRemainUngrouped() {
        assertThatCode(() -> policy.validateCustomHierarchy(
                KeywordType.VOCABULARY,
                null
        )).doesNotThrowAnyException();
    }

    @Test
    void negativeSortOrderIsRejected() {
        assertThat(policy.normalizeSortOrder(null, 7)).isEqualTo(7);
        assertThatThrownBy(() -> policy.normalizeSortOrder(-1, 0))
                .isInstanceOf(BusinessException.class);
    }

    private SystemKeyword keyword(
            String text,
            KeywordType type,
            SystemKeyword parent,
            int sortOrder
    ) {
        return SystemKeyword.create(
                text,
                text.toLowerCase(),
                type,
                text.toUpperCase(),
                parent,
                sortOrder
        );
    }
}
