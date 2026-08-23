package jp.co.translacat.domain.languagelearning.listening.attempt.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListeningAttemptQueryService {

    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningItemRepository itemRepository;
    private final ListeningAudioStoragePort storagePort;
    private final ListeningViewMapper viewMapper;
    private final LanguageLearningJsonCodec jsonCodec;

    public ListeningApiContract.ItemView item(
            Long userId,
            Long sessionId,
            Long itemId
    ) {
        ListeningItemAttempt attempt = attemptRepository
                .findBySessionIdAndItemIdAndEvaluationPurpose(
                        sessionId,
                        itemId,
                        ListeningEvaluationPurpose.OFFICIAL
                ).filter(value -> value.getSession().getUser().getId()
                        .equals(userId))
                .orElseThrow(() -> notFound("Listening 문항을 찾을 수 없습니다."));
        ListeningItem item = attempt.getItem();
        boolean reveal = attempt.isAnswerRevealed() || attempt.isFinalized();

        return new ListeningApiContract.ItemView(
                sessionId,
                item.getId(),
                item.getItemIndex(),
                item.getStatus(),
                item.isPlayable(LocalDateTime.now()),
                "/api/v1/language-learning/listening/items/"
                        + item.getId() + "/audio",
                item.getAudioDurationMs(),
                reveal ? item.getSourceText() : null,
                reveal
                        ? jsonCodec.read(
                                item.getReferenceMeaningsJson(),
                                new TypeReference<List<String>>() {
                                }
                        )
                        : List.of(),
                viewMapper.attempt(attempt)
        );
    }

    public ListeningAudioObject referenceAudio(Long userId, Long itemId) {
        ListeningItem item = itemRepository.findByIdAndDailySetUserId(
                itemId,
                userId
        ).orElseThrow(() -> notFound("Listening Audio를 찾을 수 없습니다."));

        if (!item.isPlayable(LocalDateTime.now())) {
            throw new BusinessException(
                    "Listening 기준 Audio 보관 기간이 만료되었습니다.",
                    LanguageLearningErrorCode.REVIEW_EXPIRED
            );
        }

        return storagePort.load(
                item.getAudioObjectKey(),
                item.getAudioContentType()
        );
    }

    private BusinessException notFound(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.DAILY_ITEM_NOT_FOUND
        );
    }
}
