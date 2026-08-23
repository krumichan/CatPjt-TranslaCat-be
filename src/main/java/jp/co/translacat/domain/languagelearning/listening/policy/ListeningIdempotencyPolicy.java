package jp.co.translacat.domain.languagelearning.listening.policy;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component
public class ListeningIdempotencyPolicy {

    public void requireSamePayload(
            Long existingResourceId,
            Collection<ListeningTaskType> existingTasks,
            Long requestedResourceId,
            Collection<ListeningTaskType> requestedTasks
    ) {
        boolean sameResource = existingResourceId != null
                && existingResourceId.equals(requestedResourceId);
        boolean sameTasks = normalize(existingTasks).equals(normalize(requestedTasks));
        if (!sameResource || !sameTasks) {
            throw new BusinessException(
                    "동일한 Listening idempotencyKey에 다른 요청 Payload를 사용할 수 없습니다.",
                    LanguageLearningErrorCode.LISTENING_IDEMPOTENCY_CONFLICT
            );
        }
    }

    private Set<ListeningTaskType> normalize(Collection<ListeningTaskType> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
