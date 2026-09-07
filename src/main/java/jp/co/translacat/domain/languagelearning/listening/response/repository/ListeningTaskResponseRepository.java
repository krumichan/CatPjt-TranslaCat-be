package jp.co.translacat.domain.languagelearning.listening.response.repository;

import jakarta.persistence.LockModeType;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListeningTaskResponseRepository
        extends JpaRepository<ListeningTaskResponse, Long> {

    List<ListeningTaskResponse>
    findAllByAttemptIdOrderByTaskTypeAsc(Long attemptId);


    Optional<ListeningTaskResponse> findByAttemptIdAndTaskType(
            Long attemptId,
            ListeningTaskType taskType
    );

    Optional<ListeningTaskResponse>
    findByIdAndAttemptSessionUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningTaskResponse> findLockedById(Long responseId);

    List<ListeningTaskResponse>
    findTop100ByUserAudioObjectKeyIsNotNullAndAudioDeletedAtIsNullAndAudioRetentionUntilBeforeOrderByAudioRetentionUntilAsc(
            LocalDateTime now
    );
}
