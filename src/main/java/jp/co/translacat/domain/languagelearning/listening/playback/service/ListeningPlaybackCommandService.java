package jp.co.translacat.domain.languagelearning.listening.playback.service;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningPlaybackType;
import jp.co.translacat.domain.languagelearning.listening.playback.entity.ListeningPlaybackEvent;
import jp.co.translacat.domain.languagelearning.listening.playback.repository.ListeningPlaybackEventRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningPlaybackCommandService {

    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningPlaybackEventRepository eventRepository;

    @Transactional
    public void record(
            Long userId,
            Long sessionId,
            Long itemId,
            Long attemptId,
            ListeningPlaybackType playbackType,
            String clientEventId
    ) {
        String key = clientEventId == null ? "" : clientEventId.trim();
        if (playbackType == null
                || key.isBlank()
                || key.length() > 200) {
            throw invalid();
        }

        ListeningItemAttempt attempt = attemptRepository
                .findByIdAndSessionUserId(attemptId, userId)
                .orElseThrow(this::invalid);
        if (!attempt.getSession().getId().equals(sessionId)
                || !attempt.getItem().getId().equals(itemId)
                || attempt.getSubmittedAt() != null) {
            throw invalid();
        }
        if (eventRepository.existsByAttemptIdAndClientEventId(
                attemptId,
                key
        )) {
            return;
        }

        eventRepository.save(ListeningPlaybackEvent.create(
                attempt,
                playbackType,
                key,
                LocalDateTime.now()
        ));
    }

    private BusinessException invalid() {
        return new BusinessException(
                "Listening 재생 이벤트가 유효하지 않습니다.",
                LanguageLearningErrorCode.LISTENING_PLAYBACK_EVENT_INVALID
        );
    }
}
