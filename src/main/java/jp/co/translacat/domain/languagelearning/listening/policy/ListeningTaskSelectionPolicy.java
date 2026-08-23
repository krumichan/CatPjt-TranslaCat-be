package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class ListeningTaskSelectionPolicy {

    private static final Set<Set<ListeningTaskType>> ALLOWED = Set.of(
            Set.of(ListeningTaskType.DICTATION),
            Set.of(ListeningTaskType.REPEAT_AFTER_AUDIO),
            Set.of(
                    ListeningTaskType.DICTATION,
                    ListeningTaskType.INTERPRETATION
            ),
            Set.of(
                    ListeningTaskType.DICTATION,
                    ListeningTaskType.REPEAT_AFTER_AUDIO
            ),
            Set.of(
                    ListeningTaskType.INTERPRETATION,
                    ListeningTaskType.REPEAT_AFTER_AUDIO
            ),
            Set.of(
                    ListeningTaskType.DICTATION,
                    ListeningTaskType.INTERPRETATION,
                    ListeningTaskType.REPEAT_AFTER_AUDIO
            )
    );

    public Set<ListeningTaskType> validate(
            Collection<ListeningTaskType> requested
    ) {
        if (requested == null || requested.isEmpty()
                || requested.stream().anyMatch(value -> value == null)) {
            throw invalid();
        }
        List<ListeningTaskType> copied = List.copyOf(requested);
        if (Set.copyOf(copied).size() != copied.size()) {
            throw invalid();
        }
        Set<ListeningTaskType> normalized = Set.copyOf(
                EnumSet.copyOf(copied)
        );
        if (!ALLOWED.contains(normalized)) {
            throw invalid();
        }
        return normalized;
    }

    public boolean isAllowed(Collection<ListeningTaskType> requested) {
        try {
            validate(requested);
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private BusinessException invalid() {
        return new BusinessException(
                "Listening Task 선택 조합이 허용되지 않습니다.",
                LanguageLearningErrorCode.LISTENING_INVALID_TASK_COMBINATION
        );
    }
}
