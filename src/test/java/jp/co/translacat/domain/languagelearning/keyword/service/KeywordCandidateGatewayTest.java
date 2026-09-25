package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionWeightPolicy;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import jp.co.translacat.domain.languagelearning.profile.dto.response.KeywordMasteryResponseDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeywordCandidateGatewayTest {
    @Test
    void llCandidateUsesLlMasteryWithUnchangedWeightPolicy() {
        var catalog = mock(KeywordCatalogGateway.class);
        var mastery = mock(GrowthReadGateway.class);
        var policy = new KeywordSelectionWeightPolicy();
        var date = LocalDate.of(2026, 9, 24);
        var existing = new KeywordMasteryResponseDto("it", 50.0, 3, 4, date.minusDays(2));
        when(catalog.candidates(123L, date)).thenReturn(
                List.of(new KeywordCandidateSnapshot("CUSTOM:8", "IT", KeywordSource.CUSTOM, KeywordType.TOPIC, "it",
                        date)));
        when(mastery.snapshot(123L, List.of("it"))).thenReturn(
                new GrowthSnapshot(123L, "source", 0, false, null, List.of(existing), Map.of()));
        var rows = new KeywordCandidateQueryService(catalog, mastery, policy).findCandidates(123L, date);
        assertEquals(1, rows.size());
        assertEquals("CUSTOM:8", rows.getFirst().keyword().key());
        assertEquals(policy.calculateRawWeight(date.atStartOfDay(), existing, date), rows.getFirst().rawWeight(),
                0.000001);
        verify(mastery).snapshot(123L, List.of("it"));
    }

    @Test
    void absentMasteryKeepsOriginalRampAndRecencyDefaults() {
        var catalog = mock(KeywordCatalogGateway.class);
        var mastery = mock(GrowthReadGateway.class);
        var date = LocalDate.of(2026, 9, 24);
        when(catalog.candidates(123L, date)).thenReturn(
                List.of(new KeywordCandidateSnapshot("SYSTEM:1", "IT", KeywordSource.SYSTEM, KeywordType.TOPIC, null,
                        date)));
        when(mastery.snapshot(123L, List.of("it"))).thenReturn(
                new GrowthSnapshot(123L, "source", 0, false, null, List.of(), Map.of()));
        var row = new KeywordCandidateQueryService(catalog, mastery, new KeywordSelectionWeightPolicy()).findCandidates(
                123L, date).getFirst();
        assertEquals(0.25 * 1.10 * 1.20, row.rawWeight(), 0.000001);
        assertEquals("it", row.keyword().canonicalKey());
        assertNull(row.keyword().selectionWeight());
    }

    @Test
    void emptyCatalogDoesNotInventFallbackKeywordsOrMastery() {
        var catalog = mock(KeywordCatalogGateway.class);
        var mastery = mock(GrowthReadGateway.class);
        var date = LocalDate.of(2026, 9, 24);
        when(catalog.candidates(123L, date)).thenReturn(List.of());
        assertTrue(
                new KeywordCandidateQueryService(catalog, mastery, new KeywordSelectionWeightPolicy()).findCandidates(
                        123L, date).isEmpty());
        verifyNoInteractions(mastery);
    }
}
