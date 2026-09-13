package jp.co.translacat.domain.languagelearning.practice.repository;

import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:translacat-practice-set-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class PracticeSetRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PracticeSetRepository repository;

    @Test
    void findsPendingDueSetsWithGlobalIdOrderAndLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);
        User user = entityManager.persist(User.createLocalUser(
                "practice-repository@translacat.test",
                "password",
                "practice-repository",
                Role.USER,
                "PRACTREPO01"
        ));
        List<Long> expectedDueIds = new ArrayList<>();

        for (int index = 0; index < 25; index++) {
            PracticeSet set = PracticeSet.create(
                    user,
                    LocalDate.of(2026, 9, 1).plusDays(index),
                    PracticeDomain.READING,
                    "MODE_" + index,
                    "ko",
                    "en",
                    1,
                    3
            );
            set.queueGeneration("{}");

            if (index == 1) {
                set.claimGeneration("future-token", now.minusSeconds(1));
                set.deferGeneration(now.plusMinutes(1));
            } else if (index == 2) {
                set.claimGeneration("past-token", now.minusMinutes(1));
                set.deferGeneration(now.minusSeconds(1));
            } else if (index == 3) {
                set.failGeneration("HTTP_4XX", false);
            } else if (index == 4) {
                set.claimGeneration("active-token", now);
            }

            entityManager.persist(set);
            if (index != 1 && index != 3 && index != 4) {
                expectedDueIds.add(set.getId());
            }
        }
        entityManager.flush();
        entityManager.clear();

        List<PracticeSet> results = repository.findDueByGenerationStatus(
                PENDING,
                now,
                20
        );

        assertThat(results)
                .extracting(PracticeSet::getId)
                .containsExactlyElementsOf(expectedDueIds.subList(0, 20));
    }
}
