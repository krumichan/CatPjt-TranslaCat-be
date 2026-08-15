package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SpeakingSessionLifecycleService {

    private final SpeakingSessionPolicySnapshotService snapshotService;

    @Transactional
    public void expireIfNeeded(SpeakingSession session) {
        if (!session.isActive()) {
            return;
        }
        if (!isResumable(session, LocalDateTime.now())) {
            session.expire();
        }
    }

    public boolean isResumable(SpeakingSession session) {
        return isResumable(session, LocalDateTime.now());
    }

    public boolean isResumable(
            SpeakingSession session,
            LocalDateTime now
    ) {
        if (!session.isActive()) {
            return false;
        }
        SpeakingSessionPolicySnapshot snapshot = snapshotService.read(session);
        return !session.getLastActivityAt().isBefore(
                now.minusHours(snapshot.activeSessionResumeHours())
        );
    }

    public void requireActive(SpeakingSession session) {
        if (!session.isActive()) {
            throw new BusinessException(
                    "진행 중인 Speaking Session이 아닙니다.",
                    LanguageLearningErrorCode.SESSION_NOT_ACTIVE
            );
        }
    }
}
