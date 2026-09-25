package jp.co.translacat.domain.languagelearning.keyword.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthCommands;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;
import jp.co.translacat.domain.languagelearning.keyword.policy.KeywordSelectionWeightPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeywordSelectionCommandService {
    private final GrowthCommands commands;
    private final KeywordSelectionWeightPolicy weightPolicy;

    @Transactional
    public List<SelectedKeywordDto> recordSelection(Long userId, LocalDate learningDate,
                                                    List<SelectedKeywordCandidate> selected) {
        if (selected.isEmpty()) return List.of();
        double maximum = selected.stream().mapToDouble(SelectedKeywordCandidate::rawWeight).max().orElse(1.0);
        var result = selected.stream().map(value -> {
            var key = value.keyword();
            return new SelectedKeywordDto(key.key(), key.text(), key.source(), key.type(), key.canonicalKey(),
                    weightPolicy.normalizeSelectionWeight(value.rawWeight(), maximum));
        }).toList();
        commands.append(userId, new GrowthOperation("KEYWORDS:" + UUID.randomUUID(), "KEYWORDS_SELECTED",
                Map.of("learningDate", learningDate.toString(), "canonicalKeys", result.stream()
                        .map(value -> weightPolicy.normalizeCanonicalKey(value.canonicalKey(), value.text()))
                        .toList())));
        return result;
    }
}
