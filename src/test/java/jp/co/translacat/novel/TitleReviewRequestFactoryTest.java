package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelPorts.TitleCandidate;
import jp.co.translacat.novel.application.NovelPorts.TitleReference;
import jp.co.translacat.novel.infrastructure.ai.TitleReviewRequestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TitleReviewRequestFactoryTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TitleReviewRequestFactory factory = new TitleReviewRequestFactory();
    private final List<TitleCandidate> candidates = List.of(
            new TitleCandidate("chapter-4", "青山玲子の訪問", "청산 레이코의 방문"),
            new TitleCandidate("chapter-5", "静かな朝", "고요한 아침"));
    private final List<TitleReference> references = List.of(
            new TitleReference("青山玲子は歩き続けた。", "아오야마 레이코는 계속 걸었다."));

    @Test
    void reviewReturnsOnlyAnchoredCorrectionAndLeavesTheOtherSavedTitleOutOfOutput() {
        // 준비: 이름과 단순 제목은 다른 작품에서 가져온 제어 fixture다.
        var prepared = factory.prepare(candidates, references, Map.of(), "ko");
        var output = json.valueToTree(Map.of("corrections", List.of(Map.of("id", "chapter-4",
                "translation", "아오야마 레이코의 방문", "sourceTerm", "青山玲子", "referenceTranslation", "아오야마 레이코"))));

        // 실행
        var corrections = factory.restore(prepared, output);

        // 검증: 정상 제목은 재번역 출력 대상이 아니며 이름 치환도 서버가 수행하지 않는다.
        assertThat(corrections).containsExactlyEntriesOf(Map.of("chapter-4", "아오야마 레이코의 방문"));
        assertThat(prepared.candidates()).isEqualTo(candidates);
        assertThat(prepared.data()).containsEntry("acceptedReferences", references);
        assertThat(prepared.instructions()).contains("not translate the whole list", "empty corrections array is valid");
    }

    @Test
    void emptyReviewIsAValidReusableResultAndInputIdentityMustBeUnique() {
        // 준비
        var prepared = factory.prepare(candidates, references, Map.of(), "ko");

        // 실행 및 검증
        assertThat(factory.restore(prepared, json.valueToTree(Map.of("corrections", List.of())))).isEmpty();
        assertThatThrownBy(() -> factory.prepare(List.of(candidates.getFirst(), candidates.getFirst()), references, Map.of(), "ko"))
                .hasMessage("TOC_REVIEW_INPUT_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-id", "missing-source-anchor", "missing-reference-anchor", "no-change", "duplicate", "extra-property"})
    void malformedOrUnanchoredReviewCannotBecomeAStoredCorrection(String fault) {
        // 준비
        var prepared = factory.prepare(candidates, references, Map.of(), "ko");
        var patch = new java.util.LinkedHashMap<String, Object>(Map.of("id", "chapter-4",
                "translation", "아오야마 레이코의 방문", "sourceTerm", "青山玲子", "referenceTranslation", "아오야마 레이코"));
        switch (fault) {
            case "foreign-id" -> patch.put("id", "not-requested");
            case "missing-source-anchor" -> patch.put("sourceTerm", "花村");
            case "missing-reference-anchor" -> patch.put("referenceTranslation", "누군가");
            case "no-change" -> patch.put("translation", candidates.getFirst().translation());
            case "extra-property" -> patch.put("reason", "not part of the contract");
            default -> { }
        }
        var output = json.valueToTree(Map.of("corrections", fault.equals("duplicate") ? List.of(patch, patch) : List.of(patch)));

        // 실행 및 검증
        assertThatThrownBy(() -> factory.restore(prepared, output)).hasMessage("TOC_REVIEW_RESPONSE_INVALID");
        assertThat(candidates.getLast().translation()).isEqualTo("고요한 아침");
    }

    @Test
    void syosetuHundredTitlePageKeepsItsOriginalContentsContract() {
        // 준비: 기존 소설가가 되자 한 페이지는 100개이며 카쿠요무의 50개로 잘라내지 않는다.
        var hundred = java.util.stream.IntStream.rangeClosed(1, 100).mapToObj(number ->
                new TitleCandidate(Integer.toString(number), "静かな朝 " + number, "고요한 아침 " + number)).toList();
        var overflow = new java.util.ArrayList<>(hundred);
        overflow.add(new TitleCandidate("101", "次の朝", "다음 아침"));

        // 실행
        var prepared = factory.prepare(hundred, references, Map.of(), "ko");

        // 검증: 검토 입력 100개와 ID는 그대로 보존하고 예기치 않은 초과만 차단한다.
        assertThat(prepared.candidates()).hasSize(100).containsExactlyElementsOf(hundred);
        assertThat(factory.restore(prepared, json.valueToTree(Map.of("corrections", List.of())))).isEmpty();
        assertThatThrownBy(() -> factory.prepare(overflow, references, Map.of(), "ko"))
                .hasMessage("TOC_REVIEW_INPUT_INVALID");
    }
}
