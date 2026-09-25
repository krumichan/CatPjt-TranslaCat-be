package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.model.LevelCompletionSnapshot;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileRepository;
import jp.co.translacat.domain.languagelearning.profile.repository.LevelTestBaselineReceiptRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Core 호환 수신 기록과 기존 Profile/Activity가 동일 트랜잭션을 사용하는지 검증한다.
 */
@DataJpaTest(
        properties = "spring.datasource.url=jdbc:h2:mem:ll-level-baseline;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=USER")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QueryDslConfig.class, LevelTestBaselineApplicationService.class, LearningActivityCommandService.class})
class LevelTestBaselineJpaTest {
    @Autowired
    UserRepository users;
    @Autowired
    LearningProfileRepository profiles;
    @Autowired
    LevelTestBaselineReceiptRepository receipts;
    @Autowired
    LearningActivityRepository activities;
    @Autowired
    LevelTestBaselineApplicationService service;

    private User user() {
        String uid = UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(
                User.createLocalUser(uid + "@level.test", "pw", "level", Role.USER, uid.substring(0, 20)));
    }

    private LevelCompletionSnapshot completion(Long userId) {
        var at = LocalDateTime.parse("2026-09-25T01:00:00");
        return new LevelCompletionSnapshot(userId, 101L, UUID.randomUUID().toString(), LevelTestSessionType.INITIAL, 65,
                "INTERMEDIATE", at.toLocalDate(), at.minusMinutes(10), at);
    }

    @Test
    void baselineReceiptAndActivityRollbackTogether() {
        long profileCount = profiles.count();
        long receiptCount = receipts.count();
        long activityCount = activities.count();
        User user = user();
        service.apply(user.getId(), completion(user.getId()));
        profiles.flush();
        receipts.flush();
        activities.flush();
        assertEquals(profileCount + 1, profiles.count());
        assertEquals(receiptCount + 1, receipts.count());
        assertEquals(activityCount + 1, activities.count());
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
        assertEquals(profileCount, profiles.count());
        assertEquals(receiptCount, receipts.count());
        assertEquals(activityCount, activities.count());
    }

    @Test
    void reloadingTheSameCompletionDoesNotResetAnActiveProfile() {
        User user = user();
        var result = completion(user.getId());
        service.apply(user.getId(), result);
        var profile = profiles.findByUserId(user.getId()).orElseThrow();
        profile.advanceCalibration(result.completedDate().plusDays(8));
        profiles.flush();
        long activitiesBefore = activities.count();
        assertEquals(LearningProfileState.ACTIVE, service.apply(user.getId(), result));
        profiles.flush();
        receipts.flush();
        activities.flush();
        assertEquals(activitiesBefore, activities.count());
        assertEquals(result.completionId(), receipts.findById(user.getId()).orElseThrow().completionId());
        assertEquals(result.completedDate(), profile.getCalibrationStartedDate());
    }
}
