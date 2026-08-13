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
                today.plusDays(1)
        );

        assertThat(keyword.isActive()).isFalse();
        assertThat(keyword.desiredActive()).isTrue();
        assertThat(keyword.promoteIfEffective(today)).isFalse();
        assertThat(keyword.promoteIfEffective(today.plusDays(1))).isTrue();
        assertThat(keyword.isActive()).isTrue();
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
                today
        );
        keyword.promoteIfEffective(today);

        keyword.scheduleUpdate(
                "Business",
                "business",
                KeywordType.TOPIC,
                "business",
                true,
                today.plusDays(1)
        );

        assertThat(keyword.getText()).isEqualTo("IT");
        assertThat(keyword.desiredText()).isEqualTo("Business");

        keyword.promoteIfEffective(today.plusDays(1));

        assertThat(keyword.getText()).isEqualTo("Business");
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
