package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.DailyWritingGeneratedItemDto;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyWritingGenerationStateCommandService {

    private final DailyWritingSetRepository dailyWritingSetRepository;
    private final DailyWritingItemCommandService itemCommandService;

    @Transactional
    public DailyWritingSet markGenerating(
            Long dailySetId,
            String snapshotJson
    ) {
        DailyWritingSet dailySet = getDailySet(dailySetId);
        dailySet.restartGeneration(snapshotJson);
        return dailySet;
    }

    @Transactional
    public DailyWritingSet complete(
            Long dailySetId,
            List<DailyWritingGeneratedItemDto> generatedItems,
            String promptVersion
    ) {
        DailyWritingSet dailySet = getDailySet(dailySetId);
        itemCommandService.createAll(dailySet, generatedItems);
        dailySet.ready(promptVersion);
        return dailySet;
    }

    @Transactional
    public void fail(Long dailySetId, String message) {
        getDailySet(dailySetId).fail(message);
    }

    private DailyWritingSet getDailySet(Long dailySetId) {
        return dailyWritingSetRepository.findById(dailySetId)
                .orElseThrow(() -> new BusinessException(
                        "Daily Set을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.DAILY_SET_NOT_FOUND
                ));
    }
}
