package jp.co.translacat.domain.languagelearning.quality.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.entity.LanguageLearningGenerationFingerprint;
import jp.co.translacat.domain.languagelearning.quality.repository.LanguageLearningGenerationFingerprintRepository;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GenerationDiversityContextServiceTest {

    private final LanguageLearningGenerationFingerprintRepository repository =
            mock(LanguageLearningGenerationFingerprintRepository.class);
    private final GenerationDiversityContextService service = spy(new GenerationDiversityContextService(
            repository, mock(LanguageLearningJsonCodec.class)));

    @Test
    void committedSetItemsPopulateStrictCurrentSessionWithFullContentAndMetadata() {
        var source = LanguageLearningContentSource.WRITING;
        doReturn(DiversityContext.empty()).when(service).context(7L, "ja", source);
        var fingerprint = mock(LanguageLearningGenerationFingerprint.class);
        when(fingerprint.getSourceId()).thenReturn("11");
        when(fingerprint.getSourceType()).thenReturn(source);
        when(fingerprint.getContentExcerpt()).thenReturn("truncated excerpt");
        when(fingerprint.getContentHash()).thenReturn("content-hash");
        when(fingerprint.getScenarioCategory()).thenReturn("SERVICE");
        when(fingerprint.getCommunicativeIntent()).thenReturn("REQUEST");
        when(fingerprint.getTaskArchetype()).thenReturn("polite-request");
        when(fingerprint.getGeneratedAt()).thenReturn(LocalDateTime.now());
        when(repository.findAllByUserIdAndLearningLanguageAndSourceTypeAndSourceIdInOrderByGeneratedAtDesc(
                eq(7L), eq("ja"), eq(source), anyCollection())).thenReturn(List.of(fingerprint));
        String completeText = "a complete prompt ".repeat(40);

        var result = service.contextForSources(7L, "ja", source, Map.of("11", completeText));

        assertThat(result.currentSession()).hasSize(1);
        var priorItem = result.currentSession().getFirst();
        assertThat(priorItem.content()).isEqualTo(completeText);
        assertThat(priorItem.contentHash()).isEqualTo("content-hash");
        assertThat(priorItem.scenarioCategory()).isEqualTo("SERVICE");
        assertThat(priorItem.taskArchetype()).isEqualTo("polite-request");
    }

    @Test
    void missingFingerprintStillProvidesPreviouslyCommittedText() {
        var source = LanguageLearningContentSource.WRITING;
        doReturn(DiversityContext.empty()).when(service).context(7L, "ja", source);

        var result = service.contextForSources(7L, "ja", source, Map.of("11", "preserved item"));

        assertThat(result.currentSession()).hasSize(1);
        assertThat(result.currentSession().getFirst().content()).isEqualTo("preserved item");
        assertThat(result.currentSession().getFirst().grammarFocusCodes()).isEmpty();
    }

    @Test
    void firstItemUsesExistingHistoricalContextWithoutEmptyInQuery() {
        var source = LanguageLearningContentSource.WRITING;
        DiversityContext base = DiversityContext.empty();
        doReturn(base).when(service).context(7L, "ja", source);

        assertThat(service.contextForSources(7L, "ja", source, Map.of())).isSameAs(base);

        verifyNoInteractions(repository);
    }

    @Test
    void currentSessionKeepsLastFortyInputsWithinAiContract() {
        var source = LanguageLearningContentSource.WRITING;
        doReturn(DiversityContext.empty()).when(service).context(7L, "ja", source);
        Map<String, String> contents = new LinkedHashMap<>();
        for (int index = 0; index < 45; index++) {
            contents.put(String.valueOf(index), "item " + index);
        }

        var result = service.contextForSources(7L, "ja", source, contents);

        assertThat(result.currentSession()).hasSize(40);
        assertThat(result.currentSession().getFirst().content()).isEqualTo("item 5");
        assertThat(result.currentSession().getLast().content()).isEqualTo("item 44");
    }
}
