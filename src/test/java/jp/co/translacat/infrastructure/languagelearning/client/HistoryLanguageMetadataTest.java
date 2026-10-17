package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryItemResponseDto;
import jp.co.translacat.domain.languagelearning.level.dto.response.LevelTestHistoryItemResponseDto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryLanguageMetadataTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    record Report(List<LearningHistoryItemResponseDto> history, LevelTestHistoryItemResponseDto summary) { }

    @Test
    void nestedReportAndLevelSummaryOnlyBackfillTheNewMetadata() throws Exception {
        // 준비: 기존 report.history와 레벨 상세 summary 형태를 한 응답 안에 둔다.
        byte[] bytes = """
                {"history":[{"activityId":"WRITING:-1","source":"WRITING","learningDate":"2026-10-16",
                  "title":"Writing","topic":"FREE","durationSeconds":0,"overallScore":null,
                  "completionStatus":"READY","evaluationStatus":"NOT_STARTED"}],
                 "summary":{"sessionId":1,"sessionType":"INITIAL","overallScore":80,
                  "proficiencyBand":"ADVANCED","domainScores":null,"completedAt":"2026-10-16T01:00:00"}}
                """.getBytes(StandardCharsets.UTF_8);

        // 실행
        var report = HistoryLanguageMetadata.readValue(json, bytes, Report.class);

        // 검증: 새 언어만 null이며 원래의 상태·점수는 보존한다.
        assertNull(report.history().getFirst().originLanguage());
        assertNull(report.history().getFirst().learningLanguage());
        assertNull(report.summary().originLanguage());
        assertNull(report.summary().learningLanguage());
        assertEquals(80, report.summary().overallScore());
    }

    @Test
    void originalRequiredFieldsRemainStrictAfterMetadataBackfill() {
        // 준비: 새 언어 외에 기존 필수 title과 primitive durationSeconds도 빠진 계약이다.
        byte[] bytes = """
                {"activityId":"WRITING:-1","source":"WRITING","learningDate":"2026-10-16",
                 "topic":"FREE","overallScore":null,"completionStatus":"READY","evaluationStatus":"NOT_STARTED"}
                """.getBytes(StandardCharsets.UTF_8);

        // 실행 / 검증: 선택적 새 필드 때문에 이전 필수 필드 검증을 완화하지 않는다.
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> HistoryLanguageMetadata.readValue(json, bytes, LearningHistoryItemResponseDto.class));
    }
}
