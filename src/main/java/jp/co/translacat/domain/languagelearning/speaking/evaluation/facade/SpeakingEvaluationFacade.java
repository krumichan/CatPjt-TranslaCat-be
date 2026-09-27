package jp.co.translacat.domain.languagelearning.speaking.evaluation.facade;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response.SpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationFacade {
    private final SpeakingGateway gateway;

    public SpeakingEvaluationResponseDto get(Long userId, Long sessionId) {
        return gateway.optional(userId, "/sessions/" + sessionId + "/evaluation", SpeakingEvaluationResponseDto.class);
    }

    public void retry(Long userId, Long sessionId) {
        gateway.post(userId, "/sessions/" + sessionId + "/evaluation/retry", null, Void.class);
    }
}
