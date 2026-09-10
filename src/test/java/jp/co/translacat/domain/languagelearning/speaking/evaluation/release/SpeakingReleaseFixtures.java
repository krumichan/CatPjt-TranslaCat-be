package jp.co.translacat.domain.languagelearning.speaking.evaluation.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.CorrectionMode;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.user.entity.User;
import java.time.LocalDate;
import java.util.UUID;

/** These paired fixtures are exported by the real AI service with a deterministic provider double. */
public final class SpeakingReleaseFixtures {
    private SpeakingReleaseFixtures() { }
    public static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    public static JsonNode fixture(String name) {
        try (var stream = SpeakingReleaseFixtures.class.getResourceAsStream("/speaking-release/" + name + ".json")) {
            if (stream == null) throw new IllegalArgumentException("Missing fixture: " + name);
            return JSON.readTree(stream);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static AiSpeakingEvaluationRequestDto request(String name) {
        try { return JSON.treeToValue(fixture(name).get("request"), AiSpeakingEvaluationRequestDto.class); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static AiSpeakingEvaluationResponseDto response(String name) {
        try { return JSON.treeToValue(fixture(name).get("response"), AiSpeakingEvaluationResponseDto.class); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static SpeakingSession session(User user) {
        return SpeakingSession.create(user, null, UUID.randomUUID().toString(), LocalDate.of(2026, 9, 10),
                "Free Talk", "FREE_TALK", 1, "Free Talk", null, null, "[]", "ko", "ja",
                ConversationStartMode.USER_FIRST, ConversationStartMode.USER_FIRST, CorrectionMode.CONVERSATION,
                5, 20, "Kore", "NORMAL", "{}", "{}");
    }
    public static SpeakingSessionPolicySnapshot policy(boolean enabled) {
        return new SpeakingSessionPolicySnapshot(enabled, 30, 5, 10, 20, 1, 60, 10L * 1024 * 1024,
                7, 30, 2, 2, 1, 30, 30, 60);
    }
}
