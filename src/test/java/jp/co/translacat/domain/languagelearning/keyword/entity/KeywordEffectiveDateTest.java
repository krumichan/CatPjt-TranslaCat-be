package jp.co.translacat.domain.languagelearning.keyword.entity;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordEffectiveDateTest {

    @Test
    void newCustomKeywordBecomesEffectiveNextDay() {
        LocalDate today = LocalDate.of(2026, 8, 12);
        CustomKeyword keyword = CustomKeyword.create(
                user(),
                "IT",
                "it",
                KeywordType.TOPIC,
                "it",
                null,
                today.plusDays(1)
        );

        assertThat(keyword.isActive()).isFalse();
        assertThat(keyword.desiredActive()).isTrue();
        assertThat(keyword.promoteIfEffective(today)).isFalse();
        assertThat(keyword.promoteIfEffective(today.plusDays(1))).isTrue();
        assertThat(keyword.isActive()).isTrue();
    }

    @Test
    void pendingCustomKeywordCanBecomeEffectiveBeforeFirstLearningStarts() {
        LocalDate today = LocalDate.of(2026, 8, 12);
        CustomKeyword keyword = CustomKeyword.create(
                user(),
                "IT",
                "it",
                KeywordType.TOPIC,
                "it",
                null,
                today.plusDays(1)
        );

        assertThat(keyword.promotePendingNow()).isTrue();
        assertThat(keyword.isActive()).isTrue();
        assertThat(keyword.getPendingEffectiveDate()).isNull();
    }

    @Test
    void pendingSystemSelectionCanBecomeEffectiveBeforeFirstLearningStarts() {
        LocalDate today = LocalDate.of(2026, 8, 12);
        UserSystemKeywordSelection selection =
                UserSystemKeywordSelection.create(
                        user(),
                        topic("IT", 10),
                        today.plusDays(1)
                );

        assertThat(selection.promotePendingNow()).isTrue();
        assertThat(selection.isActive()).isTrue();
        assertThat(selection.getPendingEffectiveDate()).isNull();
    }

    @Test
    void customKeywordUpdateKeepsTodayValueAndPromotesTomorrow() {
        LocalDate today = LocalDate.of(2026, 8, 12);
        CustomKeyword keyword = CustomKeyword.create(
                user(),
                "IT",
                "it",
                KeywordType.TOPIC,
                "it",
                null,
                today
        );
        keyword.promoteIfEffective(today);

        keyword.scheduleUpdate(
                "Business",
                "business",
                KeywordType.TOPIC,
                "business",
                null,
                true,
                today.plusDays(1)
        );

        assertThat(keyword.getText()).isEqualTo("IT");
        assertThat(keyword.desiredText()).isEqualTo("Business");

        keyword.promoteIfEffective(today.plusDays(1));

        assertThat(keyword.getText()).isEqualTo("Business");
    }

    @Test
    void customVocabularyParentChangeIsPromotedOnEffectiveDate() {
        LocalDate today = LocalDate.of(2026, 8, 12);
        SystemKeyword it = topic("IT", 10);
        SystemKeyword business = topic("Business", 20);
        CustomKeyword keyword = CustomKeyword.create(
                user(),
                "deployment",
                "deployment",
                KeywordType.VOCABULARY,
                "deployment",
                it,
                today
        );
        keyword.promoteIfEffective(today);

        keyword.scheduleUpdate(
                "deployment",
                "deployment",
                KeywordType.VOCABULARY,
                "deployment",
                business,
                true,
                today.plusDays(1)
        );

        assertThat(keyword.getParentSystemKeyword()).isSameAs(it);
        assertThat(keyword.desiredParentSystemKeyword()).isSameAs(business);

        keyword.promoteIfEffective(today.plusDays(1));

        assertThat(keyword.getParentSystemKeyword()).isSameAs(business);
    }

    private SystemKeyword topic(String text, int sortOrder) {
        return SystemKeyword.create(
                text,
                text.toLowerCase(),
                KeywordType.TOPIC,
                text.toUpperCase(),
                null,
                sortOrder
        );
    }

    private User user() {
        return User.createLocalUser(
                "keyword@test.local",
                "pw",
                "keyword",
                Role.USER,
                "LLKEYWORD1"
        );
    }
}
