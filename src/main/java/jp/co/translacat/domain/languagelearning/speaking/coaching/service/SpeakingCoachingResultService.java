package jp.co.translacat.domain.languagelearning.speaking.coaching.service;

import com.fasterxml.jackson.core.type.TypeReference;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingCoachingItemDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingCoachingRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingCoachingResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.coaching.dto.SpeakingCoachingResultResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.coaching.entity.SpeakingCoachingResult;
import jp.co.translacat.domain.languagelearning.speaking.coaching.repository.SpeakingCoachingResultRepository;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SpeakingCoachingResultService {
    private final SpeakingCoachingResultRepository repository;
    private final LanguageLearningJsonCodec jsonCodec;

    @Transactional
    public SpeakingCoachingResult apply(
            SpeakingSession session, AiSpeakingCoachingRequestDto request,
            AiSpeakingCoachingResponseDto response
    ) {
        Optional<SpeakingCoachingResult> accepted = repository.findBySessionId(session.getId());
        if (accepted.isPresent()) {
            require(accepted.get().getSourceSnapshotHash().equals(request.sourceSnapshotHash()),
                    "Accepted coaching source revision mismatch");
            return accepted.get();
        }
        validate(session, request, response);
        return repository.save(SpeakingCoachingResult.create(
                session, response.resultPolicyVersion(), response.schemaVersion(),
                response.sourceSnapshotHash(), response.contentStatus(),
                jsonCodec.write(response.limitationReasons()), jsonCodec.write(response.items()),
                response.promptVersion(), jsonCodec.write(response.usage())
        ));
    }

    @Transactional(readOnly = true)
    public SpeakingCoachingResultResponseDto find(Long sessionId) {
        return repository.findBySessionId(sessionId).map(this::toResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SpeakingCoachingResultResponseDto> findHistory(
            Long ownerId, String learningLanguage, LocalDate from, LocalDate to,
            String resultPolicyVersion
    ) {
        return repository
                .findAllBySessionUserIdAndSessionLearningLanguageAndSessionLearningDateBetweenAndResultPolicyVersionOrderByIdDesc(
                        ownerId, learningLanguage, from, to, resultPolicyVersion)
                .stream().map(this::toResponse).toList();
    }

    private void validate(SpeakingSession session, AiSpeakingCoachingRequestDto request,
                          AiSpeakingCoachingResponseDto response) {
        require(session.getResultKind() == SpeakingResultKind.SESSION_COACHING,
                "Session is not a coaching policy session");
        require(request.resultKind() == SpeakingResultKind.SESSION_COACHING,
                "Coaching request result kind mismatch");
        require(session.getResultPolicyVersion().equals(request.resultPolicyVersion()),
                "Coaching request policy mismatch");
        require(response.resultKind() == SpeakingResultKind.SESSION_COACHING,
                "Coaching result kind mismatch");
        require(java.util.Objects.equals(request.requestId(), response.requestId()),
                "Coaching request id mismatch");
        require(java.util.Objects.equals(request.sessionId(), response.sessionId()),
                "Coaching session id mismatch");
        require(request.resultPolicyVersion().equals(response.resultPolicyVersion()),
                "Coaching policy mismatch");
        require(request.sourceSnapshotHash().equals(response.sourceSnapshotHash()),
                "Coaching source snapshot mismatch");
        require(response.items() != null && response.items().size() <= 3,
                "Coaching item count is invalid");
        var turns = request.userTurns().stream().collect(java.util.stream.Collectors.toMap(
                turn -> turn.turnId(), turn -> turn));
        Set<String> observationIds = new HashSet<>();
        for (AiSpeakingCoachingItemDto item : response.items()) {
            require(item != null && item.evidence() != null, "Coaching evidence is required");
            require(observationIds.add(item.observationId()), "Coaching observation id is duplicated");
            var turn = turns.get(item.evidence().turnId());
            require(turn != null && !turn.excludedFromEvaluation(), "Coaching turn is not eligible");
            require(java.util.Objects.equals(turn.recordingRevision(), item.evidence().recordingRevision()),
                    "Coaching recording revision mismatch");
            require(turn.transcript() != null && item.evidence().transcriptExcerpt() != null
                            && turn.transcript().contains(item.evidence().transcriptExcerpt()),
                    "Coaching quote does not match transcript");
            require(sha256(turn.transcript()).equals(item.evidence().transcriptHash()),
                    "Coaching transcript hash mismatch");
            require("AUTOMATIC_SPEECH_RECOGNITION".equals(item.evidence().sourceProvenance()),
                    "Coaching source provenance mismatch");
            require(!item.evidence().verbatimAccuracyVerified(),
                    "ASR evidence cannot claim verified verbatim accuracy");
            require(!item.suggestionIsLearnerEvidence(), "Suggested expression cannot be learner evidence");
        }
        if ("GROUNDED".equals(response.contentStatus())) {
            require(!response.items().isEmpty(), "Grounded coaching requires content");
        }
        if ("NO_USABLE_EVIDENCE".equals(response.contentStatus())) {
            require(response.items().isEmpty(), "No-evidence coaching cannot contain observations");
        }
    }

    private SpeakingCoachingResultResponseDto toResponse(SpeakingCoachingResult value) {
        return new SpeakingCoachingResultResponseDto(
                value.getId(), SpeakingResultKind.SESSION_COACHING.name(),
                value.getResultPolicyVersion(), value.getSchemaVersion(), value.getSourceSnapshotHash(),
                value.getContentStatus(),
                jsonCodec.read(value.getLimitationReasonsJson(), new TypeReference<List<String>>() {}),
                jsonCodec.read(value.getItemsJson(), new TypeReference<List<AiSpeakingCoachingItemDto>>() {}),
                value.getPromptVersion(), value.getCreatedAt()
        );
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
