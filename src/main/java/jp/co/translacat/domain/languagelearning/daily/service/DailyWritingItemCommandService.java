package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DailyWritingItemCommandService {

    private final DailyWritingItemRepository itemRepository;
    private final LanguageLearningJsonCodec jsonCodec;

    public void createAll(
            DailyWritingSet dailySet,
            List<DailyWritingGeneratedItemDto> generatedItems
    ) {
        List<DailyWritingItem> entities = generatedItems.stream()
                .map(item -> createEntity(dailySet, item))
                .toList();

        itemRepository.saveAll(entities);
    }

    public void replaceAll(
            List<DailyWritingItem> currentItems,
            List<DailyWritingGeneratedItemDto> generatedItems
    ) {
        List<DailyWritingItem> sortedCurrent = new ArrayList<>(currentItems);
        sortedCurrent.sort(Comparator.comparingInt(
                DailyWritingItem::getOrderNo
        ));

        List<DailyWritingGeneratedItemDto> sortedGenerated =
                new ArrayList<>(generatedItems);
        sortedGenerated.sort(Comparator.comparingInt(
                DailyWritingGeneratedItemDto::order
        ));

        for (int index = 0; index < sortedCurrent.size(); index++) {
            replaceEntity(
                    sortedCurrent.get(index),
                    sortedGenerated.get(index)
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
                generatedItem.focusReason()
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
                generatedItem.focusReason()
        );
    }
}
