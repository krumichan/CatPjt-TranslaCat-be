package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.level.model.LevelCompletionSnapshot;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.profile.entity.LevelTestBaselineReceipt;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileRepository;
import jp.co.translacat.domain.languagelearning.profile.repository.LevelTestBaselineReceiptRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Profile 변경과 수신 기록을 같은 Core 트랜잭션에서 확정한다. LL 완료 자체의 소유권은 바꾸지 않는다.
 */
@Service
public class LevelTestBaselineApplicationService {
    private final UserRepository users;
    private final LearningProfileRepository profiles;
    private final LevelTestBaselineReceiptRepository receipts;
    private final LearningActivityCommandService activities;

    public LevelTestBaselineApplicationService(UserRepository users, LearningProfileRepository profiles,
                                               LevelTestBaselineReceiptRepository receipts,
                                               LearningActivityCommandService activities) {
        this.users = users;
        this.profiles = profiles;
        this.receipts = receipts;
        this.activities = activities;
    }

    @Transactional
    public LearningProfileState apply(Long userId, LevelCompletionSnapshot completion) {
        if (!userId.equals(completion.userId())) throw new IllegalArgumentException("완료 기준점 소유자가 다릅니다.");
        var user = users.findLockedById(userId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", LanguageLearningErrorCode.USER_NOT_FOUND));
        // 잠금 후의 current read를 사용하여 이전 REPEATABLE READ snapshot에 의존하지 않는다.
        var receipt = receipts.findLockedByUserId(userId).orElse(null);
        var profile = profiles.findLockedByUserId(userId).orElse(null);
        if (profile == null && receipt != null) throw new IllegalStateException("기준점 수신 기록과 Profile이 일치하지 않습니다.");
        if (profile == null) profile = profiles.save(LearningProfile.create(user));
        if (receipt != null && receipt.completionId().equals(completion.completionId())) {
            if (!receipt.completionHash().equals(completion.contentHash()))
                throw new IllegalStateException("동일 레벨 완료 ID의 내용이 변경되었습니다.");
            return profile.getState();
        }
        if (receipt != null && !completion.completedAt().isAfter(receipt.completedAt()))
            throw new IllegalStateException("이전 레벨 완료 결과로 기준점을 되돌릴 수 없습니다.");
        profile.completeLevelTest(completion.score(), completion.completedDate());
        // 과거 Core session의 숫자 ID와 충돌하지 않는 완료 UUID를 사용한다.
        activities.getOrCreate(userId, LearningSource.LEVEL_TEST, "LL_LEVEL_TEST:" + completion.completionId(),
                completion.completedDate(), "Language Level Test",
                Math.max(0, Duration.between(completion.startedAt(), completion.completedAt()).toSeconds()),
                completion.startedAt(), completion.completedAt());
        // skill score, evidence, signal, mastery를 초기화하지 않는다. 기존 completeLevelTest의 범위만 반영한다.
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (receipt == null) receipts.save(new LevelTestBaselineReceipt(completion, now));
        else receipt.update(completion, now);
        return profile.getState();
    }
}
