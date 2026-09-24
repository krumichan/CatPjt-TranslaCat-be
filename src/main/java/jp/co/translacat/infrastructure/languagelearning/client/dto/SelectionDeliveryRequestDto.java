package jp.co.translacat.infrastructure.languagelearning.client.dto;

import java.time.LocalDateTime;
import java.util.List;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;

/** Core outbox 행 ID와 당시 관찰한 Settings revision을 전달한다. */
public record SelectionDeliveryRequestDto(long eventId, String expectedRevision, List<ListeningTaskType> taskTypes) { }
