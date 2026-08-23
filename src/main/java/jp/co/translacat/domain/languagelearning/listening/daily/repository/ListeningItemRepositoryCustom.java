package jp.co.translacat.domain.languagelearning.listening.daily.repository;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;

public interface ListeningItemRepositoryCustom {

    long countLogicalItemsByStatus(
            Long dailySetId,
            ListeningItemStatus status
    );
}
