package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.outbox.entity.ListeningOutboxEvent;
import jp.co.translacat.domain.languagelearning.listening.outbox.repository.ListeningOutboxEventRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ListeningOutboxCommandService {

    private final ListeningOutboxEventRepository repository;
    private final LanguageLearningJsonCodec jsonCodec;

    public ListeningOutboxEvent enqueue(
            ListeningOutboxType type,
            Long aggregateId,
            Object payload,
            String idempotencyKey
    ) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> repository.save(ListeningOutboxEvent.create(
                        type,
                        aggregateId,
                        jsonCodec.write(payload == null ? new EmptyPayload() : payload),
                        idempotencyKey,
                        LocalDateTime.now()
                )));
    }

    private record EmptyPayload() {
    }
}
