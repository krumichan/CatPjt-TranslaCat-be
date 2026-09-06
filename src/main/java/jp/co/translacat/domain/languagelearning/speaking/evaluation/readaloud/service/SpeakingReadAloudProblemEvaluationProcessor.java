package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.factory.SpeakingEvaluationRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository.SpeakingReadAloudProblemEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.validator.SpeakingEvaluationResponseValidator;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingReadAloudProblemEvaluationProcessor {

    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingTurnRepository turnRepository;
    private final SpeakingReadAloudProblemEvaluationRepository evaluationRepository;
    private final SpeakingEvaluationRequestFactory requestFactory;
    private final SpeakingEvaluationResponseValidator responseValidator;
    private final SpeakingAiClient speakingAiClient;
    private final SpeakingAiUsageCommandService usageCommandService;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long sessionId, int problemIndex) {
        SpeakingSession session = sessionRepository.findById(sessionId)
                .orElseThrow();
        SpeakingReadAloudProblemEvaluation evaluation = evaluationRepository
                .findBySessionIdAndProblemIndex(sessionId, problemIndex)
                .orElseThrow();
        if (!"PENDING".equals(evaluation.getStatus())
                && !"EVALUATING".equals(evaluation.getStatus())) {
            return;
        }

        List<SpeakingTurn> attempts = validAttempts(sessionId, problemIndex);
        String referenceScript = resolveReferenceScript(session, problemIndex);
        evaluation.markEvaluating();

        try {
            AiSpeakingEvaluationResponseDto response = speakingAiClient.evaluate(
                    requestFactory.createReadAloudProblem(
                            session,
                            problemIndex,
                            attempts,
                            referenceScript
                    )
            );
            usageCommandService.record(
                    session,
                    null,
                    response == null ? null : response.usage(),
                    0
            );
            responseValidator.validate(response, attempts);
            evaluation.markEvaluated(
                    response == null ? "FAILED" : response.status(),
                    response == null ? null : response.overallScore(),
                    response == null ? null : response.evaluationConfidence(),
                    response == null ? "[]" : jsonCodec.write(response.metrics()),
                    response == null ? "[]" : jsonCodec.write(response.strengths()),
                    response == null ? "[]" : jsonCodec.write(response.improvements()),
                    response == null ? "[]" : jsonCodec.write(response.pronunciationPractice())
            );
        } catch (RuntimeException e) {
            evaluation.markFailed(e.getMessage());
            log.error(
                    "Read Aloud problem evaluation processing failed. sessionId={} problemIndex={}",
                    sessionId,
                    problemIndex,
                    e
            );
        }
    }

    private List<SpeakingTurn> validAttempts(Long sessionId, int problemIndex) {
        return turnRepository
                .findAllBySessionIdAndProblemIndexOrderByAttemptIndexAsc(
                        sessionId,
                        problemIndex
                ).stream()
                .filter(turn -> !turn.isExcludedFromEvaluation())
                .filter(turn -> turn.getTranscript() != null)
                .filter(turn -> !turn.getTranscript().isBlank())
                .toList();
    }

    private String resolveReferenceScript(
            SpeakingSession session,
            int problemIndex
    ) {
        if (problemIndex == 1) {
            return session.getOpeningAssistantText();
        }
        return turnRepository
                .findAllBySessionIdAndProblemIndexOrderByAttemptIndexAsc(
                        session.getId(),
                        problemIndex - 1
                ).stream()
                .map(SpeakingTurn::getAssistantText)
                .filter(text -> text != null && !text.isBlank())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException(
                        "다음 듣고 리피트 문제 Script를 찾을 수 없습니다."
                ));
    }
}
