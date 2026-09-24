package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordLearningFacts;
import org.springframework.stereotype.Component;

/** 원본 판정 범위를 유지한다. Reading/Listening/Level Test까지 임의로 확대하지 않는다. */
@Component
public class CoreKeywordLearningFacts implements KeywordLearningFacts {
    private final DailyWritingSetRepository writing;
    private final SpeakingSessionRepository speaking;

    public CoreKeywordLearningFacts(DailyWritingSetRepository writing, SpeakingSessionRepository speaking) {
        this.writing = writing;
        this.speaking = speaking;
    }

    @Override
    public boolean hasStartedLearning(Long userId) {
        return writing.existsByUserId(userId) || speaking.existsByUserId(userId);
    }
}
