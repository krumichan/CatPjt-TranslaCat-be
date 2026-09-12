package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.service.GenerationFingerprintCommandService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class DailyWritingItemCommandService {

    private final DailyWritingItemRepository itemRepository;
    private final LanguageLearningJsonCodec jsonCodec;
    private final GenerationFingerprintCommandService fingerprintCommandService;

    public void createAll(
            DailyWritingSet dailySet,
            String learningLanguage,
            List<DailyWritingGeneratedItemDto> generatedItems
    ) {
        List<DailyWritingItem> entities = generatedItems.stream()
                .map(item -> createEntity(dailySet, item))
                .toList();

        List<DailyWritingItem> saved = itemRepository.saveAll(entities);
        for (int index = 0; index < saved.size(); index++) {
            DailyWritingGeneratedItemDto generated = generatedItems.get(index);
            fingerprintCommandService.register(
                    dailySet.getUser().getId(),
                    LanguageLearningContentSource.WRITING,
                    String.valueOf(saved.get(index).getId()),
                    learningLanguage,
                    generated.originText(),
                    generated.diversityMetadata()
            );
        }
    }

    public void replaceAll(
            String learningLanguage,
            List<DailyWritingItem> currentItems,
            List<DailyWritingGeneratedItemDto> generatedItems
    ) {
        List<DailyWritingItem> sortedCurrent = new ArrayList<>(currentItems);
        sortedCurrent.sort(Comparator.comparingInt(
                DailyWritingItem::getOrderNo
        ));

        Map<DailyWritingDifficulty, Deque<DailyWritingGeneratedItemDto>>
                generatedByDifficulty = new EnumMap<>(
                        DailyWritingDifficulty.class
                );
        List<DailyWritingGeneratedItemDto> sortedGenerated =
                new ArrayList<>(generatedItems);
        sortedGenerated.sort(Comparator.comparingInt(
                DailyWritingGeneratedItemDto::order
        ));
        for (DailyWritingGeneratedItemDto generated : sortedGenerated) {
            generatedByDifficulty
                    .computeIfAbsent(
                            generated.difficulty(),
                            ignored -> new ArrayDeque<>()
                    )
                    .addLast(generated);
        }

        for (DailyWritingItem current : sortedCurrent) {
            Deque<DailyWritingGeneratedItemDto> candidates =
                    generatedByDifficulty.get(current.getDifficulty());
            DailyWritingGeneratedItemDto generated =
                    candidates == null ? null : candidates.pollFirst();
            if (generated == null) {
                throw new BusinessException(
                        "재생성된 Writing 난이도가 기존 슬롯과 일치하지 않습니다.",
                        LanguageLearningErrorCode.DAILY_SET_GENERATION_FAILED
                );
            }

            replaceEntity(current, generated);
            fingerprintCommandService.register(
                    current.getDailySet().getUser().getId(),
                    LanguageLearningContentSource.WRITING,
                    String.valueOf(current.getId()) + ":regen",
                    learningLanguage,
                    generated.originText(),
                    generated.diversityMetadata()
            );
        }

        boolean remainingGeneratedItem = generatedByDifficulty.values()
                .stream()
                .anyMatch(queue -> !queue.isEmpty());
        if (remainingGeneratedItem) {
            throw new BusinessException(
                    "재생성된 Writing 문항 수가 대상 슬롯과 일치하지 않습니다.",
                    LanguageLearningErrorCode.DAILY_SET_GENERATION_FAILED
            );
        }
    }

    private DailyWritingItem createEntity(
            DailyWritingSet dailySet,
            DailyWritingGeneratedItemDto generatedItem
    ) {
        return DailyWritingItem.create(
                dailySet,
                generatedItem.order(),
                generatedItem.difficulty(),
                generatedItem.originText(),
                jsonCodec.write(generatedItem.keywords()),
                jsonCodec.write(generatedItem.focusMetrics()),
                generatedItem.focusReason(),
                jsonCodec.write(safe(generatedItem.providedFacts())),
                jsonCodec.write(safe(generatedItem.requiredIntents())),
                jsonCodec.write(safe(generatedItem.responseConstraints()))
        );
    }

    private void replaceEntity(
            DailyWritingItem currentItem,
            DailyWritingGeneratedItemDto generatedItem
    ) {
        currentItem.replace(
                generatedItem.difficulty(),
                generatedItem.originText(),
                jsonCodec.write(generatedItem.keywords()),
                jsonCodec.write(generatedItem.focusMetrics()),
                generatedItem.focusReason(),
                jsonCodec.write(safe(generatedItem.providedFacts())),
                jsonCodec.write(safe(generatedItem.requiredIntents())),
                jsonCodec.write(safe(generatedItem.responseConstraints()))
        );
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
