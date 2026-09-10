package jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity.SpeakingEvaluationJob.Status;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.model.SpeakingEvaluationJobKey;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.repository.SpeakingEvaluationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationJobQueryService {
    private final SpeakingEvaluationJobRepository repository;

    @Transactional(readOnly = true)
    public List<SpeakingEvaluationJobKey> findDue(int limit) {
        return repository.findAllByStatusInAndAvailableAtLessThanEqualOrderByAvailableAtAscIdAsc(
                List.of(Status.PENDING, Status.RUNNING), LocalDateTime.now(),
                PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream()
                .map(job -> new SpeakingEvaluationJobKey(job.getId(), job.getSession().getId()))
                .toList();
    }
}
