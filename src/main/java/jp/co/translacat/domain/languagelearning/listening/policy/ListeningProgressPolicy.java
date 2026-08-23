package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component
public class ListeningProgressPolicy {

    private static final Set<ListeningTaskStatus> LEARNING_TERMINAL = Set.of(
            ListeningTaskStatus.EVALUATED,
            ListeningTaskStatus.NOT_EVALUABLE
    );

    public boolean eligible(
            boolean official,
            boolean practice,
            boolean answerRevealed,
            boolean alreadyApplied,
            Collection<ListeningTaskStatus> selectedStatuses
    ) {
        return official
                && !practice
                && !answerRevealed
                && !alreadyApplied
                && selectedStatuses != null
                && !selectedStatuses.isEmpty()
                && selectedStatuses.stream().allMatch(LEARNING_TERMINAL::contains);
    }
}
