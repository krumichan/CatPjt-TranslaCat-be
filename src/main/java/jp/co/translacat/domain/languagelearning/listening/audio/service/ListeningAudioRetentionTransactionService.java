package jp.co.translacat.domain.languagelearning.listening.audio.service;

import jp.co.translacat.domain.languagelearning.listening.daily.repository.ListeningItemRepository;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListeningAudioRetentionTransactionService {

    private final ListeningItemRepository itemRepository;
    private final ListeningTaskResponseRepository responseRepository;

    @Transactional(readOnly = true)
    public List<ExpiredAudio> expired(LocalDateTime now) {
        List<ExpiredAudio> values = new java.util.ArrayList<>();
        itemRepository
                .findTop100ByAudioObjectKeyIsNotNullAndAudioDeletedAtIsNullAndAudioRetentionUntilBeforeOrderByAudioRetentionUntilAsc(now)
                .forEach(item -> values.add(new ExpiredAudio(
                        true, item.getId(), item.getAudioObjectKey()
                )));
        responseRepository
                .findTop100ByUserAudioObjectKeyIsNotNullAndAudioDeletedAtIsNullAndAudioRetentionUntilBeforeOrderByAudioRetentionUntilAsc(now)
                .forEach(response -> values.add(new ExpiredAudio(
                        false, response.getId(), response.getUserAudioObjectKey()
                )));

        return List.copyOf(values);
    }

    @Transactional
    public void markDeleted(ExpiredAudio audio, LocalDateTime now) {
        if (audio.reference()) {
            itemRepository.findLockedById(audio.id()).ifPresent(item ->
                    item.markAudioDeleted(now));
        } else {
            responseRepository.findLockedById(audio.id()).ifPresent(response ->
                    response.markAudioDeleted(now));
        }
    }

    public record ExpiredAudio(boolean reference, Long id, String objectKey) {
    }
}
