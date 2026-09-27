package jp.co.translacat.domain.languagelearning.quality;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageLearningCommonOwnershipTest {
    @Test
    void unusedQualityAndSignalBusinessOwnersAreRetiredWithoutRemovingWireDtos() throws Exception {
        // 준비: 실제 호출이 LL로 옮겨진 업무 소유자만 검사하고 외부 계약 DTO는 유지한다.
        Path source = Path.of("src/main/java/jp/co/translacat");
        Pattern retired = Pattern.compile("\\b("
                + String.join("|", List.of(
                "GenerationFingerprintCommandService", "GenerationDiversityContextService",
                "LanguageComplexityPolicy", "LanguageLearningGenerationFingerprintRepository",
                "LanguageLearningGenerationFingerprint", "LearningProfileSignalService", "KeywordNormalizer",
                "KeywordSelectionFacade", "KeywordCandidateQueryService", "KeywordSelectionCommandService",
                "KeywordSelectionPolicy", "KeywordSelectionWeightPolicy", "SelectedKeywordCandidate",
                "LearningProfileAiContextService",
                "DashboardBaseQueryService", "DashboardInsightQueryService", "DashboardProjectionPolicy",
                "LearningStreakQueryService", "SourceSkillTrendQueryService", "SpeakingDashboardQueryService",
                "RecentLearningProfileInsightQueryService", "UnifiedLearningProfileQueryService",
                "LearningProfileAggregationWeightPolicy",
                "AiDailyWritingGenerationRequestDto", "AiDailyWritingGenerationResponseDto",
                "AiPracticeGenerationRequestDto", "AiPracticeGenerationResponseDto",
                "AiWritingEvaluationRequestDto", "AiWritingEvaluationResponseDto",
                "DiversityContext", "LanguageComplexityContext", "AdminSettingsSnapshot", "UserSettingsSnapshot"))
                + ")\\b");

        // 실행 및 검증: 기존 Core 저장 경로를 다시 참조하는 코드가 없어야 한다.
        try (var paths = Files.walk(source)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertFalse(retired.matcher(Files.readString(path)).find(), path.toString());
            }
        }
        assertTrue(Files.exists(source.resolve("domain/languagelearning/ai/dto/model/BilingualMessageDto.java")));
        assertTrue(Files.exists(source.resolve("domain/languagelearning/ai/dto/model/WritingCorrectionDto.java")));
    }
}
