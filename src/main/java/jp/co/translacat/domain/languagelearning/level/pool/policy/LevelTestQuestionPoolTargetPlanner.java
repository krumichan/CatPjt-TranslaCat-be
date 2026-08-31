package jp.co.translacat.domain.languagelearning.level.pool.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LevelTestQuestionPoolTargetPlanner {

    private static final int MIN_BAND = 1;
    private static final int MAX_BAND = 5;

    private final LevelTestRecipe recipe;

    public List<BucketTarget> plan(int targetSize) {
        if (targetSize <= 0) {
            return List.of();
        }

        List<VirtualSlot> slots = new ArrayList<>();
        for (LevelTestRecipe.Entry entry : recipe.entries()) {
            for (int band = MIN_BAND; band <= MAX_BAND; band++) {
                slots.add(new VirtualSlot(
                        entry.questionNumber(),
                        entry.domain(),
                        entry.itemType(),
                        band
                ));
            }
        }

        int base = targetSize / slots.size();
        int remainder = targetSize % slots.size();
        Map<BucketKey, MutableTarget> aggregated = new LinkedHashMap<>();
        for (int index = 0; index < slots.size(); index++) {
            VirtualSlot slot = slots.get(index);
            int allocation = base + (index < remainder ? 1 : 0);
            if (allocation <= 0) {
                continue;
            }
            BucketKey key = new BucketKey(
                    slot.domain(),
                    slot.itemType(),
                    slot.complexityBand()
            );
            MutableTarget target = aggregated.computeIfAbsent(
                    key,
                    ignored -> new MutableTarget(
                            slot.questionNumber(),
                            key,
                            0
                    )
            );
            target.target += allocation;
        }

        return aggregated.values().stream()
                .map(value -> new BucketTarget(
                        value.representativeQuestionNumber,
                        value.key.domain(),
                        value.key.itemType(),
                        value.key.complexityBand(),
                        value.target
                ))
                .toList();
    }

    public int bucketTarget(
            int targetSize,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand
    ) {
        return plan(targetSize).stream()
                .filter(value -> value.domain() == domain
                        && value.itemType() == itemType
                        && value.complexityBand() == complexityBand)
                .mapToInt(BucketTarget::targetCount)
                .findFirst()
                .orElse(0);
    }

    public record BucketTarget(
            int representativeQuestionNumber,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand,
            int targetCount
    ) {
    }

    private record VirtualSlot(
            int questionNumber,
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand
    ) {
    }

    private record BucketKey(
            LevelTestDomain domain,
            LevelTestItemType itemType,
            int complexityBand
    ) {
    }

    private static class MutableTarget {
        private final int representativeQuestionNumber;
        private final BucketKey key;
        private int target;

        private MutableTarget(
                int representativeQuestionNumber,
                BucketKey key,
                int target
        ) {
            this.representativeQuestionNumber = representativeQuestionNumber;
            this.key = key;
            this.target = target;
        }
    }
}
