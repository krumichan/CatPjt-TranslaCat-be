package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.entity.KeywordMastery;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionWeightPolicy;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import jp.co.translacat.domain.languagelearning.keyword.repository.KeywordMasteryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeywordCandidateGatewayTest {
    @Test
    void llCandidateUsesExistingCoreMasteryAndWeightPolicy() {
        var catalog = mock(KeywordCatalogGateway.class);
        var mastery = mock(KeywordMasteryRepository.class);
        var policy = new KeywordSelectionWeightPolicy();
        var date = LocalDate.of(2026, 9, 24);
        var existing = mock(KeywordMastery.class);
        when(existing.getScore()).thenReturn(50.0);
        when(existing.getLastSelectedDate()).thenReturn(date.minusDays(2));
        when(catalog.candidates(123L, date)).thenReturn(
                List.of(new KeywordCandidateSnapshot("CUSTOM:8", "IT", KeywordSource.CUSTOM, KeywordType.TOPIC, "it",
                        date)));
        when(mastery.findByUserIdAndCanonicalKey(123L, "it")).thenReturn(Optional.of(existing));
        var rows = new KeywordCandidateQueryService(catalog, mastery, policy).findCandidates(123L, date);
        assertEquals(1, rows.size());
        assertEquals("CUSTOM:8", rows.get(0).keyword().key());
        assertEquals(policy.calculateRawWeight(date.atStartOfDay(), existing, date), rows.get(0).rawWeight(), 0.000001);
        verify(mastery, never()).save(any());
    }

    @Test
    void absentMasteryKeepsOriginalRampAndRecencyDefaults() {
        var catalog = mock(KeywordCatalogGateway.class);
        var mastery = mock(KeywordMasteryRepository.class);
        var date = LocalDate.of(2026, 9, 24);
        when(catalog.candidates(123L, date)).thenReturn(
                List.of(new KeywordCandidateSnapshot("SYSTEM:1", "IT", KeywordSource.SYSTEM, KeywordType.TOPIC, null,
                        date)));
        when(mastery.findByUserIdAndCanonicalKey(123L, "it")).thenReturn(Optional.empty());
        var rows =
                new KeywordCandidateQueryService(catalog, mastery, new KeywordSelectionWeightPolicy()).findCandidates(
                        123L, date);
        assertEquals(0.25 * 1.10 * 1.20, rows.get(0).rawWeight(), 0.000001);
        assertEquals("it", rows.get(0).keyword().canonicalKey());
        assertNull(rows.get(0).keyword().selectionWeight());
    }

    @Test
    void emptyCatalogDoesNotInventFallbackKeywordsOrMastery() {
        var catalog = mock(KeywordCatalogGateway.class);
        var mastery = mock(KeywordMasteryRepository.class);
        var date = LocalDate.of(2026, 9, 24);
        when(catalog.candidates(123L, date)).thenReturn(List.of());
        assertTrue(
                new KeywordCandidateQueryService(catalog, mastery, new KeywordSelectionWeightPolicy()).findCandidates(
                        123L, date).isEmpty());
        verifyNoInteractions(mastery);
    }
}
