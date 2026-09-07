package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.practice.entity.VocabularyMastery;
import jp.co.translacat.domain.languagelearning.practice.repository.VocabularyMasteryRepository;
import jp.co.translacat.domain.languagelearning.practice.dto.response.VocabularyMasterySummaryResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VocabularyMasteryQueryService {
    private final VocabularyMasteryRepository repository;

    public VocabularyMasterySummaryResponseDto get(Long userId) {
        List<VocabularyMastery> values = repository.findAllByUserIdOrderByScoreAsc(userId);
        double average = values.stream()
                .filter(value -> value.getEvaluationCount() > 0)
                .mapToDouble(VocabularyMastery::getScore)
                .average()
                .orElse(0);
        int newCount = 0, learning = 0, familiar = 0, strong = 0, mastered = 0;
        for (VocabularyMastery value : values) {
            switch (stage(value)) {
                case "NEW" -> newCount++;
                case "LEARNING" -> learning++;
                case "FAMILIAR" -> familiar++;
                case "STRONG" -> strong++;
                default -> mastered++;
            }
        }
        return new VocabularyMasterySummaryResponseDto(
                values.size(),
                round(average),
                newCount,
                learning,
                familiar,
                strong,
                mastered,
                values.stream()
                        .filter(value -> value.getEvaluationCount() > 0)
                        .limit(20)
                        .map(value -> new VocabularyMasterySummaryResponseDto.Item(
                                value.getCanonicalKey(), value.getDisplayExpression(),
                                value.getScore(), stage(value), value.getEvaluationCount()
                        ))
                        .toList()
        );
    }

    private String stage(VocabularyMastery value) {
        if (value.getEvaluationCount() == 0) return "NEW";
        if (value.getScore() < 55) return "LEARNING";
        if (value.getScore() < 70) return "FAMILIAR";
        if (value.getScore() < 85) return "STRONG";
        return "MASTERED";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
